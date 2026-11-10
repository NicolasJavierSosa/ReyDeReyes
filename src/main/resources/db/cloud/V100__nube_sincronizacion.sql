-- =====================================================================
-- Solo para la nube (Supabase). Se aplica despues de db/migration con:
--   java -jar demo.jar --migrar-nube
-- Define las funciones que usa el local para sincronizar, el rol
-- restringido sync_local y las reglas de acceso (RLS) de la web del duenio.
-- =====================================================================

-- En la nube, lo que se edita desde la web queda marcado como origen PROD.
create or replace function sync_origen_por_defecto() returns varchar
language sql immutable as $$
    select 'PROD'::varchar
$$;

-- Cambios ya recibidos: reenviar el mismo cambio nunca lo aplica dos veces.
create table if not exists sync_recibidos (
    cambio_id    uuid         primary key,
    resultado    jsonb        not null,
    recibido_en  timestamptz  not null default now()
);

-- ---------------------------------------------------------------------
-- sync_push: el local envia sus cambios (filas completas).
--   Entrada: [{cambioId, tabla, id, versionBase, fila}, ...]
--   Salida : [{cambioId, estado, version?, fila?, conflicto?, error?}, ...]
--   estado : APLICADO | LOCAL_GANA | REMOTO_GANA | FUSION | DUPLICADO | ERROR
-- ---------------------------------------------------------------------
create or replace function sync_push(p_cambios jsonb) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
    c            jsonb;
    v_res        jsonb;
    v_prev       jsonb;
    v_resultados jsonb := '[]'::jsonb;
    v_cambio     uuid;
    v_tabla      varchar;
    v_id         varchar;
    v_base       bigint;
    v_fila       jsonb;
    v_actual     jsonb;
    v_otro       jsonb;
    v_modo       varchar;
    v_ver_actual bigint;
begin
    perform set_config('app.sync_aplicando', 'on', true);

    for c in select value from jsonb_array_elements(p_cambios) loop
        v_cambio := (c ->> 'cambioId')::uuid;
        select resultado into v_prev from sync_recibidos where cambio_id = v_cambio;
        if found then
            v_resultados := v_resultados || jsonb_build_array(v_prev || jsonb_build_object('estado', 'DUPLICADO'));
            continue;
        end if;

        v_tabla := c ->> 'tabla';
        v_id    := c ->> 'id';
        v_base  := (c ->> 'versionBase')::bigint;
        v_fila  := c -> 'fila';

        begin
            select modo into v_modo from sync_tablas where tabla = v_tabla;
            if v_modo is null then
                raise exception 'Tabla no sincronizable: %', v_tabla;
            end if;
            v_actual := sync_leer_fila(v_tabla, v_id);

            if v_modo = 'SUBIDA' then
                -- Solo el local escribe estas tablas: se guarda tal cual.
                perform sync_aplicar_fila(v_tabla, v_fila);
                v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'APLICADO');

            elsif v_actual is null then
                -- Alta nueva. Si ya existe otra fila con la misma clave natural, se fusionan.
                v_otro := null;
                if v_tabla = 'productos' then
                    select to_jsonb(p) into v_otro from productos p
                     where p.codigo_barra = v_fila ->> 'codigo_barra' and p.id::text <> v_id;
                elsif v_tabla = 'categorias' then
                    select to_jsonb(k) into v_otro from categorias k
                     where lower(k.nombre) = lower(v_fila ->> 'nombre') and k.id::text <> v_id;
                end if;
                if v_otro is not null then
                    v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'FUSION', 'fila', v_otro);
                    insert into sync_conflictos (tabla, entidad_id, ganador, datos_ganador, datos_perdedor)
                    values (v_tabla, v_otro ->> 'id', 'FUSION', v_otro, v_fila);
                else
                    perform sync_aplicar_fila(v_tabla, v_fila);
                    v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'APLICADO');
                end if;

            else
                v_ver_actual := (v_actual ->> 'version')::bigint;
                if v_ver_actual = v_base then
                    -- Nadie lo toco en la nube desde la ultima sincronizacion.
                    perform sync_aplicar_fila(v_tabla, v_fila);
                    v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'APLICADO');
                elsif (v_fila ->> 'actualizado_en')::timestamptz >= (v_actual ->> 'actualizado_en')::timestamptz then
                    -- Conflicto: gana el ultimo cambio (el del local).
                    v_fila := jsonb_set(v_fila, '{version}', to_jsonb(v_ver_actual + 1));
                    perform sync_aplicar_fila(v_tabla, v_fila);
                    insert into sync_conflictos (tabla, entidad_id, ganador, datos_ganador, datos_perdedor)
                    values (v_tabla, v_id, 'LOCAL', v_fila, v_actual);
                    v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'LOCAL_GANA',
                                                'version', v_ver_actual + 1, 'perdedor', v_actual);
                else
                    -- Conflicto: gana el ultimo cambio (el de la nube).
                    insert into sync_conflictos (tabla, entidad_id, ganador, datos_ganador, datos_perdedor)
                    values (v_tabla, v_id, 'NUBE', v_actual, v_fila);
                    v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'REMOTO_GANA', 'fila', v_actual);
                end if;
            end if;

            insert into sync_recibidos (cambio_id, resultado) values (v_cambio, v_res);
        exception when others then
            v_res := jsonb_build_object('cambioId', v_cambio, 'estado', 'ERROR', 'error', sqlerrm);
        end;
        v_resultados := v_resultados || jsonb_build_array(v_res);
    end loop;

    return v_resultados;
end;
$$;

-- ---------------------------------------------------------------------
-- sync_pull: cambios hechos en la web (catalogo) despues de un cursor.
-- Se devuelven ordenados por tabla (claves foraneas) y luego por seq.
-- ---------------------------------------------------------------------
create or replace function sync_pull(p_cursor bigint, p_limite integer) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
    r          record;
    v_cambios  jsonb := '[]'::jsonb;
    v_max      bigint := p_cursor;
begin
    for r in
        select x.seq, x.tabla, x.entidad_id
        from (
            select c.seq, c.tabla, c.entidad_id, t.orden
            from sync_cambios c
            join sync_tablas t on t.tabla = c.tabla
            where c.seq > p_cursor and c.estado = 'PENDIENTE' and t.modo = 'CATALOGO'
            order by c.seq
            limit p_limite
        ) x
        order by x.orden, x.seq
    loop
        v_cambios := v_cambios || jsonb_build_array(jsonb_build_object(
            'seq', r.seq,
            'tabla', r.tabla,
            'id', r.entidad_id,
            'fila', sync_leer_fila(r.tabla, r.entidad_id)));
        v_max := greatest(v_max, r.seq);
    end loop;
    return jsonb_build_object('cambios', v_cambios, 'cursor', v_max);
end;
$$;

-- Cursor actual del feed (se usa al restaurar una PC desde cero).
create or replace function sync_cursor_actual() returns bigint
language sql stable security definer set search_path = public as $$
    select coalesce(max(seq), 0) from sync_cambios
$$;

-- Exporta una tabla completa por paginas (restauracion de una PC nueva).
create or replace function sync_exportar(p_tabla varchar, p_desde varchar, p_limite integer) returns jsonb
language plpgsql stable security definer set search_path = public as $$
declare
    v_pk     varchar(60);
    v_filas  jsonb;
begin
    select pk into v_pk from sync_tablas where tabla = p_tabla;
    if v_pk is null then
        raise exception 'Tabla no sincronizable: %', p_tabla;
    end if;
    execute format(
        'select coalesce(jsonb_agg(to_jsonb(t) order by t.%2$I::text), ''[]''::jsonb) '
        'from (select * from %1$I where %2$I::text > $1 order by %2$I::text limit $2) t',
        p_tabla, v_pk)
    into v_filas using coalesce(p_desde, ''), p_limite;
    return v_filas;
end;
$$;

-- ---------------------------------------------------------------------
-- Rol que usa la PC del local. Solo puede ejecutar las funciones de arriba.
-- La clave se define a mano en Supabase (ver docs/SUPABASE.md):
--   alter role sync_local with login password '...';
-- ---------------------------------------------------------------------
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'sync_local') then
        create role sync_local nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
end;
$$;

grant usage on schema public to sync_local;
revoke execute on function sync_push(jsonb) from public;
revoke execute on function sync_pull(bigint, integer) from public;
revoke execute on function sync_cursor_actual() from public;
revoke execute on function sync_exportar(varchar, varchar, integer) from public;
grant execute on function sync_push(jsonb) to sync_local;
grant execute on function sync_pull(bigint, integer) to sync_local;
grant execute on function sync_cursor_actual() to sync_local;
grant execute on function sync_exportar(varchar, varchar, integer) to sync_local;

-- ---------------------------------------------------------------------
-- Web del duenio (supabase-js con Supabase Auth):
--   * edita el catalogo: productos, combos, categorias, unidades, tipos y precios
--   * consulta en solo lectura: ventas, cajas y tesoreria
--   * el resto no es visible
-- El registro publico de usuarios debe estar desactivado en Supabase.
-- ---------------------------------------------------------------------
do $$
declare
    t text;
begin
    foreach t in array array[
        'usuarios', 'configuracion', 'sync_tablas', 'sync_cambios', 'sync_conflictos', 'sync_recibidos',
        'categorias', 'unidades_medida', 'tipos', 'proveedores', 'productos', 'clientes', 'metodos_pago',
        'cajas', 'ventas', 'detalles_venta', 'pagos', 'solicitudes_compra', 'solicitudes_compra_items',
        'movimientos_stock', 'movimientos_tesoreria'
    ] loop
        if exists (select 1 from pg_tables where schemaname = 'public' and tablename = t) then
            execute format('alter table %I enable row level security', t);
            execute format('revoke all on table %I from anon, authenticated', t);
        end if;
    end loop;

    -- Catalogo: lectura y escritura
    foreach t in array array['categorias', 'unidades_medida', 'tipos', 'productos'] loop
        execute format('grant select, insert, update on table %I to authenticated', t);
        execute format('drop policy if exists duenio_lee on %I', t);
        execute format('drop policy if exists duenio_crea on %I', t);
        execute format('drop policy if exists duenio_edita on %I', t);
        execute format('create policy duenio_lee on %I for select to authenticated using (true)', t);
        execute format('create policy duenio_crea on %I for insert to authenticated with check (true)', t);
        execute format('create policy duenio_edita on %I for update to authenticated using (true) with check (true)', t);
    end loop;

    -- Consulta: solo lectura
    foreach t in array array['ventas', 'detalles_venta', 'pagos', 'cajas', 'movimientos_tesoreria',
                             'metodos_pago', 'proveedores', 'movimientos_stock', 'sync_conflictos'] loop
        execute format('grant select on table %I to authenticated', t);
        execute format('drop policy if exists duenio_lee on %I', t);
        execute format('create policy duenio_lee on %I for select to authenticated using (true)', t);
    end loop;
end;
$$;

-- La tabla de historial de Flyway se protege desde MigradorNube, al terminar
-- (durante la migracion Flyway la tiene bloqueada).

-- La vista de stock respeta los permisos de quien consulta.
alter view v_stock set (security_invoker = true);
revoke all on v_stock from anon;
grant select on v_stock to authenticated;
