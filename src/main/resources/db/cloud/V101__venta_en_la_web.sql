-- =====================================================================
-- Venta desde la web (contingencia: la PC del local no esta disponible).
-- La web tiene su propia caja (origen PROD). Cada operacion es una funcion
-- que se ejecuta entera o no se ejecuta: no se escribe sobre las tablas
-- desde el navegador. Lo que se crea aqui baja solo a la PC del local con
-- la sincronizacion (ver sync_pull al final).
-- =====================================================================

-- Los tickets de la web van en otro rango para no chocar con los del local.
create sequence if not exists ventas_numero_web_seq start 900001;

create or replace function web_ahora() returns timestamp
language sql stable as $$
    select (now() at time zone 'America/Argentina/Buenos_Aires')::timestamp
$$;

-- ---------------------------------------------------------------------
-- Estado de la caja de la web (misma forma que /api/tesoreria/caja/estado del local)
-- ---------------------------------------------------------------------
create or replace function web_estado_caja() returns jsonb
language sql stable security definer set search_path = public as $$
    select coalesce(
        (select jsonb_build_object('open', true, 'id', c.id, 'openedAt', c.fecha_apertura,
                                   'baseAmount', c.monto_apertura)
           from cajas c where c.estado = 'ABIERTA' and c.origen = 'PROD' limit 1),
        jsonb_build_object('open', false, 'id', null, 'openedAt', null, 'baseAmount', 0))
$$;

-- ---------------------------------------------------------------------
-- Abrir la caja de la web
-- ---------------------------------------------------------------------
create or replace function web_abrir_caja(p_monto numeric, p_notas text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
    v_id     uuid := gen_random_uuid();
    v_monto  numeric(14,2) := round(coalesce(p_monto, 0), 2);
    v_ahora  timestamp := web_ahora();
begin
    if exists (select 1 from cajas where estado = 'ABIERTA' and origen = 'PROD') then
        raise exception 'Ya hay una caja abierta en la web';
    end if;
    if v_monto < 0 then
        raise exception 'Monto de base invalido';
    end if;
    insert into cajas (id, fecha_apertura, monto_apertura, estado, observaciones)
    values (v_id, v_ahora, v_monto, 'ABIERTA', nullif(btrim(coalesce(p_notas, '')), ''));
    if v_monto > 0 then
        insert into movimientos_tesoreria (fecha_hora, tipo, concepto, detalle, monto, anulado, caja_id, afecta_caja, usuario)
        values (v_ahora, 'INGRESO', 'Cambio Inicial / Base', 'Apertura de caja', v_monto, false, v_id, true, 'Web');
    end if;
    return jsonb_build_object('id', v_id, 'openedAt', v_ahora, 'baseAmount', v_monto);
end;
$$;

-- ---------------------------------------------------------------------
-- Cobrar una venta en la caja de la web. Misma logica que el checkout del local:
--   p_items: [{barcode, quantity, components?: [{barcode, quantity}]}]
--   stock = suma de movimientos; combos descuentan sus componentes;
--   el descuento general se reparte entre las lineas.
-- ---------------------------------------------------------------------
create or replace function web_cobrar(p_items jsonb, p_descuento numeric, p_metodo text, p_recibido numeric)
returns jsonb
language plpgsql security definer set search_path = public as $$
declare
    v_caja       cajas%rowtype;
    v_metodo     metodos_pago%rowtype;
    v_item       record;
    v_prod       productos%rowtype;
    v_comp       record;
    v_cat        productos%rowtype;
    v_cant       numeric(14,3);
    v_ahora      timestamp := web_ahora();
    v_involucr   uuid[] := '{}';
    v_subtotal   numeric(14,2) := 0;
    v_descuento  numeric(14,2);
    v_total      numeric(14,2);
    v_asignado   numeric(14,2) := 0;
    v_entregado  numeric(14,2);
    v_cambio     numeric(14,2) := 0;
    v_venta      uuid := gen_random_uuid();
    v_numero     bigint;
    v_ticket     text;
    v_n          integer;
    v_cuantas    integer;
    v_requerido  numeric(14,3);
    v_elegido    numeric(14,3);
    v_faltante   numeric(14,3);
    v_tomar      numeric(14,3);
    v_stock      numeric(14,3);
    v_costo      numeric(14,2);
    v_consumo    jsonb;
    v_linea      record;
    v_det        uuid;
    v_nombre     text;
begin
    select * into v_caja from cajas where estado = 'ABIERTA' and origen = 'PROD' limit 1;
    if not found then
        raise exception 'No hay caja abierta en la web. Abra la caja antes de continuar.';
    end if;
    if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
        raise exception 'La venta no tiene productos';
    end if;
    if btrim(coalesce(p_metodo, '')) = '' then
        raise exception 'Metodo de pago requerido';
    end if;
    select * into v_metodo from metodos_pago where upper(codigo) = upper(btrim(p_metodo));
    if not found then
        raise exception 'Metodo de pago invalido: %', p_metodo;
    end if;
    if not v_metodo.activo then
        raise exception 'Metodo de pago inactivo: %', v_metodo.nombre;
    end if;

    create temp table if not exists _web_lineas (
        n int primary key, producto_id uuid, cant numeric(14,3), componentes jsonb,
        subtotal numeric(14,2), descuento numeric(14,2), consumo jsonb) on commit drop;
    create temp table if not exists _web_stock (
        producto_id uuid primary key, inicial numeric(14,3), disponible numeric(14,3),
        nombre text, categoria_id uuid, combo boolean, activo boolean, costo numeric(14,2)) on commit drop;
    delete from _web_lineas;
    delete from _web_stock;

    -- 1. Lineas y productos involucrados
    v_n := 0;
    for v_item in select value as it from jsonb_array_elements(p_items) loop
        v_n := v_n + 1;
        if btrim(coalesce(v_item.it ->> 'barcode', '')) = '' then
            raise exception 'Codigo de barra requerido';
        end if;
        v_cant := round(coalesce((v_item.it ->> 'quantity')::numeric, 0), 3);
        if v_cant <= 0 then
            raise exception 'Cantidad invalida para %', v_item.it ->> 'barcode';
        end if;
        select * into v_prod from productos where codigo_barra = btrim(v_item.it ->> 'barcode') and activo;
        if not found then
            raise exception 'Producto no encontrado: %', v_item.it ->> 'barcode';
        end if;
        if v_prod.combo and v_cant <> trunc(v_cant) then
            raise exception 'Los combos se venden por unidad entera: %', v_prod.nombre;
        end if;
        insert into _web_lineas (n, producto_id, cant, componentes) values (v_n, v_prod.id, v_cant, v_item.it -> 'components');

        if not v_prod.combo then
            v_involucr := v_involucr || v_prod.id;
        elsif v_prod.combo_grupo_categoria_id is not null then
            v_involucr := v_involucr || coalesce(array(
                select id from productos where categoria_id = v_prod.combo_grupo_categoria_id and activo and not combo), '{}');
            -- productos elegidos a mano dentro del combo
            if jsonb_typeof(v_item.it -> 'components') = 'array' then
                v_involucr := v_involucr || coalesce(array(
                    select p.id from jsonb_array_elements(v_item.it -> 'components') c
                    join productos p on p.codigo_barra = btrim(c.value ->> 'barcode') and p.activo), '{}');
            end if;
        else
            v_involucr := v_involucr || coalesce(array(
                select (e ->> 'productoId')::uuid from jsonb_array_elements(v_prod.combo_items) e), '{}');
        end if;
    end loop;

    -- 2. Bloquear (en orden, para no trabarse con otra venta) y leer el stock
    perform 1 from productos where id = any (v_involucr) order by id for update;
    insert into _web_stock (producto_id, inicial, disponible, nombre, categoria_id, combo, activo, costo)
    select p.id, coalesce(s.cantidad, 0), coalesce(s.cantidad, 0), p.nombre, p.categoria_id, p.combo, p.activo, p.precio_costo
      from productos p
      left join v_stock s on s.producto_id = p.id
     where p.id = any (v_involucr)
        or p.id in (select producto_id from _web_lineas);

    -- 3. Que se descuenta de cada linea
    for v_linea in select l.*, p.* from _web_lineas l join productos p on p.id = l.producto_id order by l.n loop
        v_consumo := '{}'::jsonb;
        if not v_linea.combo then
            v_consumo := jsonb_build_object(v_linea.producto_id::text, v_linea.cant);

        elsif v_linea.combo_grupo_categoria_id is null then
            if jsonb_array_length(coalesce(v_linea.combo_items, '[]'::jsonb)) = 0 then
                raise exception 'Combo sin productos: %', v_linea.nombre;
            end if;
            for v_comp in select e as it from jsonb_array_elements(v_linea.combo_items) e loop
                if not coalesce((select activo from productos where id = (v_comp.it ->> 'productoId')::uuid), false) then
                    raise exception 'Combo no disponible: producto inactivo en %', v_linea.nombre;
                end if;
                v_cant := round(case when coalesce((v_comp.it ->> 'cantidad')::numeric, 0) > 0
                                     then (v_comp.it ->> 'cantidad')::numeric else 1 end * v_linea.cant, 3);
                v_consumo := jsonb_set(v_consumo, array[v_comp.it ->> 'productoId'],
                    to_jsonb(coalesce((v_consumo ->> (v_comp.it ->> 'productoId'))::numeric, 0) + v_cant));
            end loop;

        else
            -- Combo de grupo: N unidades a elegir de una categoria
            v_requerido := v_linea.combo_grupo_cantidad * v_linea.cant;
            if jsonb_typeof(v_linea.componentes) = 'array' and jsonb_array_length(v_linea.componentes) > 0 then
                v_elegido := 0;
                for v_comp in select c as it from jsonb_array_elements(v_linea.componentes) c loop
                    select * into v_cat from productos where codigo_barra = btrim(v_comp.it ->> 'barcode') and activo;
                    if not found then
                        raise exception 'Producto no encontrado: %', v_comp.it ->> 'barcode';
                    end if;
                    if v_cat.combo or v_cat.categoria_id is distinct from v_linea.combo_grupo_categoria_id then
                        raise exception '% no pertenece a la categoria del combo', v_cat.nombre;
                    end if;
                    v_cant := round((v_comp.it ->> 'quantity')::numeric, 3);
                    v_consumo := jsonb_set(v_consumo, array[v_cat.id::text],
                        to_jsonb(coalesce((v_consumo ->> v_cat.id::text)::numeric, 0) + v_cant));
                    v_elegido := v_elegido + v_cant;
                end loop;
                if v_elegido <> v_requerido then
                    raise exception 'El combo % lleva % unidades y se eligieron %', v_linea.nombre,
                        trim_scale(v_requerido), trim_scale(v_elegido);
                end if;
            else
                -- Sin eleccion: se toman los productos de la categoria con mas stock
                v_faltante := v_requerido;
                for v_comp in
                    select producto_id, disponible from _web_stock
                     where not combo and activo and categoria_id = v_linea.combo_grupo_categoria_id
                     order by disponible desc, nombre
                loop
                    exit when v_faltante <= 0;
                    v_tomar := least(trunc(v_comp.disponible), v_faltante);
                    if v_tomar > 0 then
                        v_consumo := jsonb_set(v_consumo, array[v_comp.producto_id::text], to_jsonb(v_tomar));
                        v_faltante := v_faltante - v_tomar;
                    end if;
                end loop;
                if v_faltante > 0 then
                    raise exception 'Combo sin stock disponible: %', v_linea.nombre;
                end if;
            end if;
        end if;

        -- Se descuenta de lo disponible para que la siguiente linea no tome lo mismo
        update _web_lineas set consumo = v_consumo where n = v_linea.n;
        update _web_stock s set disponible = disponible - (e.value)::numeric
          from jsonb_each_text(v_consumo) e where s.producto_id = e.key::uuid;
    end loop;

    -- Stock suficiente para todo lo que se descuenta
    for v_comp in
        select e.key::uuid as producto_id, sum(e.value::numeric) as total
          from _web_lineas l, jsonb_each_text(l.consumo) e
         group by e.key
    loop
        select inicial, nombre into v_stock, v_nombre from _web_stock where producto_id = v_comp.producto_id;
        if coalesce(v_stock, 0) < v_comp.total then
            raise exception 'Stock insuficiente para %. Stock actual: %', v_nombre, trim_scale(coalesce(v_stock, 0));
        end if;
    end loop;

    -- 4. Importes
    update _web_lineas l set subtotal = round(p.precio_venta * l.cant, 2)
      from productos p where p.id = l.producto_id;
    select sum(subtotal) into v_subtotal from _web_lineas;
    v_descuento := round(coalesce(p_descuento, 0), 2);
    if v_descuento < 0 then
        raise exception 'Descuento invalido';
    end if;
    if v_descuento > v_subtotal then
        raise exception 'El descuento no puede superar el subtotal';
    end if;
    select count(*) into v_cuantas from _web_lineas;
    for v_linea in select n, subtotal from _web_lineas order by n loop
        if v_linea.n = v_cuantas then
            update _web_lineas set descuento = round(v_descuento - v_asignado, 2) where n = v_linea.n;
        elsif v_subtotal = 0 then
            update _web_lineas set descuento = 0 where n = v_linea.n;
        else
            update _web_lineas set descuento = round(round(v_descuento * v_linea.subtotal / v_subtotal, 6), 2) where n = v_linea.n;
            v_asignado := v_asignado + round(round(v_descuento * v_linea.subtotal / v_subtotal, 6), 2);
        end if;
    end loop;
    v_total := round(v_subtotal - v_descuento, 2);

    -- 5. Pago
    if v_metodo.afecta_caja then
        v_entregado := round(coalesce(p_recibido, v_total), 2);
        if v_entregado < v_total then
            raise exception 'El monto entregado es menor al total';
        end if;
        v_cambio := round(v_entregado - v_total, 2);
    end if;

    -- 6. Guardar
    v_numero := nextval('ventas_numero_web_seq');
    v_ticket := lpad(v_numero::text, 6, '0');
    insert into ventas (id, numero, fecha_hora, subtotal, descuento_total, total, estado, caja_id, observaciones)
    values (v_venta, v_numero, v_ahora, v_subtotal, v_descuento, v_total, 'COMPLETADA', v_caja.id, 'Venta POS (web)');

    for v_linea in
        select l.*, p.precio_venta, p.precio_costo, p.combo
          from _web_lineas l join productos p on p.id = l.producto_id order by l.n
    loop
        v_costo := round(coalesce(v_linea.precio_costo, 0), 2);
        if v_linea.combo and v_costo <= 0 then
            select round(coalesce(sum(p.precio_costo * e.value::numeric), 0) / v_linea.cant, 2) into v_costo
              from jsonb_each_text(v_linea.consumo) e join productos p on p.id = e.key::uuid;
        end if;
        insert into detalles_venta (venta_id, producto_id, cantidad, precio_unitario, costo_unitario, subtotal,
                                    descuento_linea, total_linea, componentes)
        values (v_venta, v_linea.producto_id, v_linea.cant, round(v_linea.precio_venta, 2), v_costo, v_linea.subtotal,
                v_linea.descuento, round(v_linea.subtotal - v_linea.descuento, 2),
                case when v_linea.combo then
                    (select coalesce(jsonb_agg(jsonb_build_object('productoId', e.key, 'cantidad', e.value::numeric)), '[]'::jsonb)
                       from jsonb_each_text(v_linea.consumo) e)
                end);
    end loop;

    insert into pagos (venta_id, metodo_pago_id, metodo_nombre, monto, entregado, cambio, fecha_hora)
    values (v_venta, v_metodo.id, v_metodo.nombre, v_total, v_entregado, v_cambio, v_ahora);

    insert into movimientos_stock (producto_id, tipo, cantidad, motivo, fecha_hora, venta_id)
    select e.key::uuid, 'VENTA', - sum(e.value::numeric), 'Venta #' || v_ticket, v_ahora, v_venta
      from _web_lineas l, jsonb_each_text(l.consumo) e
     group by e.key;

    insert into movimientos_tesoreria (fecha_hora, tipo, concepto, detalle, monto, anulado, caja_id, afecta_caja,
                                       metodo_pago_id, metodo_nombre, venta_id, usuario)
    values (v_ahora, 'VENTA', 'Venta - ' || v_metodo.nombre, 'Ticket #' || v_ticket, v_total, false, v_caja.id,
            v_metodo.afecta_caja, v_metodo.id, v_metodo.nombre, v_venta, 'Web');

    return jsonb_build_object('saleId', v_venta, 'ticket', v_ticket, 'subtotal', v_subtotal, 'discount', v_descuento,
                              'total', v_total, 'paymentMethod', v_metodo.codigo, 'amountReceived', v_entregado,
                              'change', v_cambio, 'dateTime', v_ahora);
end;
$$;

-- ---------------------------------------------------------------------
-- Cerrar la caja de la web con el recuento de billetes
--   p_billetes: [{denomination, quantity}]
-- ---------------------------------------------------------------------
create or replace function web_cerrar_caja(p_billetes jsonb, p_notas text default null) returns jsonb
language plpgsql security definer set search_path = public as $$
declare
    v_caja      cajas%rowtype;
    v_billete   record;
    v_contado   numeric(14,2) := 0;
    v_esperado  numeric(14,2);
    v_dif       numeric(14,2);
    v_ahora     timestamp := web_ahora();
    v_detalle   text := '{"billetes":{';
    v_primero   boolean := true;
begin
    select * into v_caja from cajas where estado = 'ABIERTA' and origen = 'PROD' limit 1 for update;
    if not found then
        raise exception 'No hay caja abierta en la web. Abra la caja antes de continuar.';
    end if;
    if p_billetes is null or jsonb_typeof(p_billetes) <> 'array' or jsonb_array_length(p_billetes) = 0 then
        raise exception 'Debe ingresar el recuento de billetes';
    end if;
    for v_billete in select value as b from jsonb_array_elements(p_billetes) loop
        if coalesce((v_billete.b ->> 'quantity')::numeric, -1) < 0 or coalesce((v_billete.b ->> 'denomination')::numeric, 0) <= 0 then
            raise exception 'Cantidad de billetes invalida';
        end if;
        v_contado := v_contado + (v_billete.b ->> 'denomination')::numeric * (v_billete.b ->> 'quantity')::numeric;
        if not v_primero then v_detalle := v_detalle || ','; end if;
        v_primero := false;
        v_detalle := v_detalle || '"' || trim_scale((v_billete.b ->> 'denomination')::numeric)::text || '":'
                                || (v_billete.b ->> 'quantity')::numeric::bigint;
    end loop;
    v_detalle := v_detalle || '}}';
    v_contado := round(v_contado, 2);

    select round(coalesce(sum(monto), 0), 2) into v_esperado
      from movimientos_tesoreria where caja_id = v_caja.id and not anulado and afecta_caja;
    v_dif := round(v_contado - v_esperado, 2);

    update cajas set fecha_cierre = v_ahora, estado = 'CERRADA', efectivo_esperado = v_esperado,
                     efectivo_contado = v_contado, diferencia = v_dif, detalle_cierre = v_detalle,
                     observaciones = coalesce(nullif(btrim(coalesce(p_notas, '')), ''), observaciones)
     where id = v_caja.id;

    return jsonb_build_object('expectedCash', v_esperado, 'countedCash', v_contado, 'difference', v_dif,
                              'closedAt', v_ahora, 'synced', false, 'syncMessage', null);
end;
$$;

-- Solo usuarios autenticados de la web pueden usarlas
revoke execute on function web_estado_caja() from public, anon;
revoke execute on function web_abrir_caja(numeric, text) from public, anon;
revoke execute on function web_cobrar(jsonb, numeric, text, numeric) from public, anon;
revoke execute on function web_cerrar_caja(jsonb, text) from public, anon;
grant execute on function web_estado_caja() to authenticated;
grant execute on function web_abrir_caja(numeric, text) to authenticated;
grant execute on function web_cobrar(jsonb, numeric, text, numeric) to authenticated;
grant execute on function web_cerrar_caja(jsonb, text) to authenticated;

-- ---------------------------------------------------------------------
-- sync_pull: ahora baja al local todo lo que se hizo en la web (catalogo y
-- tambien las ventas, cajas, pagos y movimientos de la caja de la web).
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
            where c.seq > p_cursor and c.estado = 'PENDIENTE'
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
revoke execute on function sync_pull(bigint, integer) from public;
grant execute on function sync_pull(bigint, integer) to sync_local;

notify pgrst, 'reload schema';
