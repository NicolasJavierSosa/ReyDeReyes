-- =====================================================================
-- Rey de Reyes - esquema inicial
-- Se aplica igual en la base local (PostgreSQL de la PC del local) y en
-- Supabase (produccion). Lo especifico de la nube vive en db/cloud.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------------
-- Registro de tablas sincronizadas
--   modo CATALOGO: se edita en el local y en la web, viaja en ambos sentidos
--   modo SUBIDA  : solo se edita en el local y sube como respaldo
--   orden        : orden de envio para respetar las claves foraneas
-- ---------------------------------------------------------------------
create table sync_tablas (
    tabla   varchar(60) primary key,
    pk      varchar(60) not null,
    modo    varchar(20) not null check (modo in ('CATALOGO', 'SUBIDA')),
    orden   integer     not null
);

-- Cambios pendientes de enviar (local) / feed de cambios hechos en la web (nube)
create table sync_cambios (
    seq           bigserial    primary key,
    id            uuid         not null default gen_random_uuid() unique,
    tabla         varchar(60)  not null references sync_tablas (tabla),
    entidad_id    varchar(80)  not null,
    version_base  bigint,
    creado_en     timestamptz  not null default now(),
    estado        varchar(20)  not null default 'PENDIENTE' check (estado in ('PENDIENTE', 'ENVIADO')),
    enviado_en    timestamptz
);
create index idx_sync_cambios_pendientes on sync_cambios (estado, seq);
create index idx_sync_cambios_entidad on sync_cambios (tabla, entidad_id, estado);

-- Conflictos detectados (gana el ultimo cambio; el perdedor queda aqui)
create table sync_conflictos (
    id               uuid         primary key default gen_random_uuid(),
    tabla            varchar(60)  not null,
    entidad_id       varchar(80)  not null,
    ganador          varchar(20)  not null,
    datos_ganador    jsonb,
    datos_perdedor   jsonb,
    detectado_en     timestamptz  not null default now(),
    revisado         boolean      not null default false
);

-- Configuracion clave/valor (cursor de sincronizacion, etc.). No se sincroniza.
create table configuracion (
    clave  varchar(80)  primary key,
    valor  varchar(2000)
);

-- ---------------------------------------------------------------------
-- Usuario unico: el duenio. No se sincroniza.
-- ---------------------------------------------------------------------
create table usuarios (
    id             uuid          primary key default gen_random_uuid(),
    username       varchar(80)   not null unique,
    password_hash  varchar(120)  not null,
    nombre         varchar(120)  not null,
    activo         boolean       not null default true,
    creado_en      timestamptz   not null default now()
);

-- ---------------------------------------------------------------------
-- Catalogo
-- ---------------------------------------------------------------------
create table categorias (
    id             uuid          primary key default gen_random_uuid(),
    nombre         varchar(120)  not null,
    activa         boolean       not null default true,
    version        bigint        not null default 0,
    actualizado_en timestamptz   not null default now(),
    origen         varchar(10)   not null default 'LOCAL'
);
create unique index uq_categoria_nombre on categorias (lower(nombre));

create table unidades_medida (
    abreviatura    varchar(10)   primary key,
    nombre         varchar(80)   not null,
    version        bigint        not null default 0,
    actualizado_en timestamptz   not null default now(),
    origen         varchar(10)   not null default 'LOCAL'
);

-- La clave de tipos es el nombre en minusculas, para que el local y la web
-- generen la misma clave sin coordinarse.
create table tipos (
    id             varchar(120)  primary key,
    nombre         varchar(120)  not null,
    version        bigint        not null default 0,
    actualizado_en timestamptz   not null default now(),
    origen         varchar(10)   not null default 'LOCAL'
);

create table proveedores (
    telefono       varchar(40)   primary key,
    nombre         varchar(120)  not null,
    activo         boolean       not null default true,
    version        bigint        not null default 0,
    actualizado_en timestamptz   not null default now(),
    origen         varchar(10)   not null default 'LOCAL'
);

create table productos (
    id                        uuid           primary key default gen_random_uuid(),
    codigo_barra              varchar(64)    not null unique,
    nombre                    varchar(120)   not null,
    descripcion               varchar(500),
    precio_costo              numeric(14,2)  not null default 0,
    precio_venta              numeric(14,2)  not null default 0,
    activo                    boolean        not null default true,
    stock_minimo              numeric(14,3)  not null default 0,
    categoria_id              uuid           references categorias (id),
    unidad_medida             varchar(10)    references unidades_medida (abreviatura),
    tipo_id                   varchar(120)   references tipos (id),
    proveedor_id              varchar(40)    references proveedores (telefono),
    combo                     boolean        not null default false,
    -- Combo fijo: [{"productoId": "...", "cantidad": 1}, ...]
    combo_items               jsonb          not null default '[]'::jsonb,
    -- Combo de grupo: N unidades a elegir de una categoria
    combo_grupo_categoria_id  uuid           references categorias (id),
    combo_grupo_cantidad      integer,
    version                   bigint         not null default 0,
    actualizado_en            timestamptz    not null default now(),
    origen                    varchar(10)    not null default 'LOCAL'
);
create index idx_producto_nombre on productos (lower(nombre));
create index idx_producto_categoria on productos (categoria_id);
create index idx_producto_proveedor on productos (proveedor_id);

-- ---------------------------------------------------------------------
-- Datos que solo se editan en el local
-- ---------------------------------------------------------------------
create table clientes (
    id             uuid          primary key default gen_random_uuid(),
    nombre         varchar(120)  not null,
    apellido       varchar(120)  not null,
    telefono       varchar(32)   not null,
    email          varchar(120)  not null,
    activo         boolean       not null default true,
    version        bigint        not null default 0,
    actualizado_en timestamptz   not null default now(),
    origen         varchar(10)   not null default 'LOCAL'
);
create unique index uq_cliente_email on clientes (lower(email));

-- Metodos de pago: lista que mantiene el duenio. Solo registran como se cobro.
-- afecta_caja = true significa que suma al efectivo fisico de la caja.
create table metodos_pago (
    id             uuid          primary key default gen_random_uuid(),
    codigo         varchar(40)   not null unique,
    nombre         varchar(80)   not null,
    afecta_caja    boolean       not null default false,
    activo         boolean       not null default true,
    orden          integer       not null default 0,
    version        bigint        not null default 0,
    actualizado_en timestamptz   not null default now(),
    origen         varchar(10)   not null default 'LOCAL'
);

create table cajas (
    id                 uuid           primary key default gen_random_uuid(),
    fecha_apertura     timestamp      not null,
    fecha_cierre       timestamp,
    monto_apertura     numeric(14,2)  not null default 0,
    efectivo_esperado  numeric(14,2),
    efectivo_contado   numeric(14,2),
    diferencia         numeric(14,2),
    detalle_cierre     varchar(2000),
    estado             varchar(20)    not null check (estado in ('ABIERTA', 'CERRADA')),
    observaciones      varchar(500),
    version            bigint         not null default 0,
    actualizado_en     timestamptz    not null default now(),
    origen             varchar(10)    not null default 'LOCAL'
);
create index idx_caja_estado on cajas (estado);
create index idx_caja_fecha_cierre on cajas (fecha_cierre);
-- Nunca puede haber dos cajas abiertas a la vez
create unique index uq_caja_abierta on cajas (estado) where estado = 'ABIERTA';

create sequence ventas_numero_seq;

create table ventas (
    id              uuid           primary key default gen_random_uuid(),
    numero          bigint         not null unique,
    fecha_hora      timestamp      not null,
    subtotal        numeric(14,2)  not null,
    descuento_total numeric(14,2)  not null default 0,
    total           numeric(14,2)  not null,
    estado          varchar(20)    not null check (estado in ('COMPLETADA', 'ANULADA')),
    caja_id         uuid           not null references cajas (id),
    observaciones   varchar(500),
    fecha_anulacion timestamp,
    version         bigint         not null default 0,
    actualizado_en  timestamptz    not null default now(),
    origen          varchar(10)    not null default 'LOCAL'
);
create index idx_venta_fecha_hora on ventas (fecha_hora);
create index idx_venta_estado on ventas (estado);
create index idx_venta_caja on ventas (caja_id);

create table detalles_venta (
    id              uuid           primary key default gen_random_uuid(),
    venta_id        uuid           not null references ventas (id),
    producto_id     uuid           not null references productos (id),
    cantidad        numeric(14,3)  not null,
    precio_unitario numeric(14,2)  not null,
    costo_unitario  numeric(14,2)  not null default 0,
    subtotal        numeric(14,2)  not null,
    descuento_linea numeric(14,2)  not null default 0,
    total_linea     numeric(14,2)  not null,
    -- Para combos: que componentes se descontaron [{"productoId","cantidad"}]
    componentes     jsonb,
    version         bigint         not null default 0,
    actualizado_en  timestamptz    not null default now(),
    origen          varchar(10)    not null default 'LOCAL'
);
create index idx_detalle_venta_venta on detalles_venta (venta_id);
create index idx_detalle_venta_producto on detalles_venta (producto_id);

create table pagos (
    id              uuid           primary key default gen_random_uuid(),
    venta_id        uuid           not null references ventas (id),
    metodo_pago_id  uuid           not null references metodos_pago (id),
    metodo_nombre   varchar(80)    not null,
    monto           numeric(14,2)  not null,
    entregado       numeric(14,2),
    cambio          numeric(14,2),
    fecha_hora      timestamp      not null,
    version         bigint         not null default 0,
    actualizado_en  timestamptz    not null default now(),
    origen          varchar(10)    not null default 'LOCAL'
);
create index idx_pago_venta on pagos (venta_id);

create table solicitudes_compra (
    id              uuid           primary key default gen_random_uuid(),
    proveedor_id    varchar(40)    not null references proveedores (telefono),
    estado          varchar(20)    not null check (estado in ('SOLICITADA', 'PARCIAL', 'RECIBIDA')),
    fecha_hora      timestamp      not null,
    version         bigint         not null default 0,
    actualizado_en  timestamptz    not null default now(),
    origen          varchar(10)    not null default 'LOCAL'
);
create index idx_solicitud_compra_estado on solicitudes_compra (estado);

create table solicitudes_compra_items (
    id                   uuid           primary key default gen_random_uuid(),
    solicitud_id         uuid           not null references solicitudes_compra (id),
    producto_id          uuid           not null references productos (id),
    cantidad_solicitada  numeric(14,3)  not null,
    cantidad_recibida    numeric(14,3)  not null default 0,
    version              bigint         not null default 0,
    actualizado_en       timestamptz    not null default now(),
    origen               varchar(10)    not null default 'LOCAL'
);
create index idx_solicitud_item_solicitud on solicitudes_compra_items (solicitud_id);

-- Stock: la cantidad nunca se guarda, se calcula sumando movimientos.
create table movimientos_stock (
    id                   uuid           primary key default gen_random_uuid(),
    producto_id          uuid           not null references productos (id),
    tipo                 varchar(20)    not null check (tipo in ('INICIAL', 'VENTA', 'ANULACION', 'COMPRA', 'AJUSTE')),
    cantidad             numeric(14,3)  not null,
    motivo               varchar(255),
    fecha_hora           timestamp      not null,
    venta_id             uuid           references ventas (id),
    solicitud_compra_id  uuid           references solicitudes_compra (id),
    version              bigint         not null default 0,
    actualizado_en       timestamptz    not null default now(),
    origen               varchar(10)    not null default 'LOCAL'
);
create index idx_mov_stock_producto on movimientos_stock (producto_id);
create index idx_mov_stock_fecha on movimientos_stock (fecha_hora);

create view v_stock as
    select p.id as producto_id, coalesce(sum(m.cantidad), 0)::numeric(14,3) as cantidad
    from productos p
    left join movimientos_stock m on m.producto_id = p.id
    group by p.id;

create table movimientos_tesoreria (
    id              uuid           primary key default gen_random_uuid(),
    fecha_hora      timestamp      not null,
    tipo            varchar(20)    not null check (tipo in ('INGRESO', 'EGRESO', 'VENTA')),
    concepto        varchar(120)   not null,
    detalle         varchar(500),
    monto           numeric(14,2)  not null,
    anulado         boolean        not null default false,
    caja_id         uuid           not null references cajas (id),
    afecta_caja     boolean        not null,
    metodo_pago_id  uuid           references metodos_pago (id),
    metodo_nombre   varchar(80),
    venta_id        uuid           references ventas (id),
    usuario         varchar(120),
    version         bigint         not null default 0,
    actualizado_en  timestamptz    not null default now(),
    origen          varchar(10)    not null default 'LOCAL'
);
create index idx_mov_tes_caja on movimientos_tesoreria (caja_id, fecha_hora);
create index idx_mov_tes_venta on movimientos_tesoreria (venta_id);

-- ---------------------------------------------------------------------
-- Tablas sincronizadas y su orden (respeta claves foraneas)
-- ---------------------------------------------------------------------
insert into sync_tablas (tabla, pk, modo, orden) values
    ('categorias',               'id',          'CATALOGO', 10),
    ('unidades_medida',          'abreviatura', 'CATALOGO', 20),
    ('tipos',                    'id',          'CATALOGO', 30),
    ('proveedores',              'telefono',    'SUBIDA',   40),
    ('productos',                'id',          'CATALOGO', 50),
    ('clientes',                 'id',          'SUBIDA',   60),
    ('metodos_pago',             'id',          'SUBIDA',   70),
    ('cajas',                    'id',          'SUBIDA',   80),
    ('ventas',                   'id',          'SUBIDA',   90),
    ('detalles_venta',           'id',          'SUBIDA',  100),
    ('pagos',                    'id',          'SUBIDA',  110),
    ('solicitudes_compra',       'id',          'SUBIDA',  120),
    ('solicitudes_compra_items', 'id',          'SUBIDA',  130),
    ('movimientos_stock',        'id',          'SUBIDA',  140),
    ('movimientos_tesoreria',    'id',          'SUBIDA',  150);

-- ---------------------------------------------------------------------
-- Triggers de sincronizacion (iguales en el local y en la nube)
--   * Antes de escribir: actualiza actualizado_en/origen y sube la version
--     si quien escribio no lo hizo (por ejemplo, la web con supabase-js).
--   * Despues de escribir: anota el cambio en sync_cambios.
--   Mientras se aplican datos que llegan del otro lado se define
--   app.sync_aplicando = 'on' y los triggers no hacen nada.
-- ---------------------------------------------------------------------
create or replace function sync_aplicando() returns boolean
language sql stable as $$
    select coalesce(current_setting('app.sync_aplicando', true), '') = 'on'
$$;

-- Origen por defecto de los cambios: 'LOCAL' aqui; la migracion de la nube
-- la redefine para devolver 'PROD'.
create or replace function sync_origen_por_defecto() returns varchar
language sql immutable as $$
    select 'LOCAL'::varchar
$$;

create or replace function sync_antes_de_escribir() returns trigger
language plpgsql as $$
begin
    if sync_aplicando() then
        return new;
    end if;
    new.actualizado_en := now();
    new.origen := sync_origen_por_defecto();
    if tg_op = 'UPDATE' and new.version is not distinct from old.version then
        new.version := coalesce(old.version, 0) + 1;
    end if;
    return new;
end;
$$;

-- security definer: en la nube la web edita con un rol que no puede escribir sync_cambios
create or replace function sync_registrar_cambio() returns trigger
language plpgsql security definer set search_path = public as $$
declare
    v_pk     varchar(60);
    v_id     varchar(80);
    v_base   bigint;
begin
    if sync_aplicando() then
        return new;
    end if;
    select pk into v_pk from sync_tablas where tabla = tg_table_name;
    v_id := to_jsonb(new) ->> v_pk;
    if tg_op = 'UPDATE' then
        v_base := old.version;
    end if;
    -- Varios cambios sobre la misma fila sin enviar se agrupan en uno solo:
    -- se conserva la version sobre la que se hizo el primero y se mueve al
    -- final de la cola (en la nube esta cola es el feed que lee el local).
    update sync_cambios
       set seq = nextval('sync_cambios_seq_seq'), creado_en = now()
     where tabla = tg_table_name and entidad_id = v_id and estado = 'PENDIENTE';
    if not found then
        insert into sync_cambios (tabla, entidad_id, version_base)
        values (tg_table_name, v_id, v_base);
    end if;
    return new;
end;
$$;

do $$
declare
    t record;
begin
    for t in select tabla from sync_tablas loop
        execute format(
            'create trigger %I before insert or update on %I for each row execute function sync_antes_de_escribir()',
            t.tabla || '_sync_antes', t.tabla);
        execute format(
            'create trigger %I after insert or update on %I for each row execute function sync_registrar_cambio()',
            t.tabla || '_sync_despues', t.tabla);
    end loop;
end;
$$;

-- Escribe una fila completa recibida del otro lado (insert o update).
-- Quien la llama debe haber definido app.sync_aplicando = 'on'.
create or replace function sync_aplicar_fila(p_tabla varchar, p_fila jsonb) returns void
language plpgsql as $$
declare
    v_pk    varchar(60);
    v_cols  text;
begin
    select pk into v_pk from sync_tablas where tabla = p_tabla;
    if v_pk is null then
        raise exception 'Tabla no sincronizable: %', p_tabla;
    end if;
    select string_agg(quote_ident(column_name), ', ' order by ordinal_position)
      into v_cols
      from information_schema.columns
     where table_schema = current_schema() and table_name = p_tabla;
    execute format(
        'insert into %1$I (%2$s) select %2$s from jsonb_populate_record(null::%1$I, $1) '
        'on conflict (%3$I) do update set (%2$s) = (select %2$s from jsonb_populate_record(null::%1$I, $1))',
        p_tabla, v_cols, v_pk)
    using p_fila;
end;
$$;

-- Devuelve la fila actual como JSON (o null si no existe).
create or replace function sync_leer_fila(p_tabla varchar, p_id varchar) returns jsonb
language plpgsql stable as $$
declare
    v_pk    varchar(60);
    v_fila  jsonb;
begin
    select pk into v_pk from sync_tablas where tabla = p_tabla;
    execute format('select to_jsonb(t) from %I t where t.%I::text = $1', p_tabla, v_pk)
       into v_fila using p_id;
    return v_fila;
end;
$$;

-- ---------------------------------------------------------------------
-- Datos iniciales (mismas claves en el local y en la nube)
-- ---------------------------------------------------------------------
set local app.sync_aplicando = 'on';

insert into unidades_medida (abreviatura, nombre) values
    ('UN', 'Unidad'), ('KG', 'Kilogramo'), ('G', 'Gramo'), ('L', 'Litro'), ('ML', 'Mililitro');

insert into metodos_pago (id, codigo, nombre, afecta_caja, orden) values
    ('00000000-0000-0000-0000-000000000001', 'EFECTIVO',      'Efectivo',      true,  1),
    ('00000000-0000-0000-0000-000000000002', 'TRANSFERENCIA', 'Transferencia', false, 2),
    ('00000000-0000-0000-0000-000000000003', 'TARJETA',       'Tarjeta',       false, 3),
    ('00000000-0000-0000-0000-000000000004', 'QR',            'QR / Billetera', false, 4);

