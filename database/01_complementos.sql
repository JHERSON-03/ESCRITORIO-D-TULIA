-- =====================================================================
-- D'Tulia · CAPA DE DATOS · 01 · Complementos sobre el modelo existente
-- ---------------------------------------------------------------------
-- Se ejecuta sobre las tablas y funciones que ya existen en el esquema
-- public (productos, sedes, tipo_productos, fn_mover_stock, etc.).
-- NO modifica ni reemplaza nada existente; solo agrega:
--   1. Columna de contraseña en empleados (login de la app)
--   2. Índices en las llaves foráneas y unicidad serie-número
--   3. Funciones para la API: login, catálogo, CRUD de productos,
--      clientes y registro atómico de ventas
-- Es idempotente: puede ejecutarse más de una vez.
--
-- Errores de negocio con SQLSTATE propios (el backend los traduce a HTTP):
--   DT404 → no encontrado   DT409 → conflicto/stock   DT422 → datos inválidos
-- Las funciones existentes usan RAISE EXCEPTION simple (P0001 → HTTP 422).
-- =====================================================================

create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

set search_path = public, extensions;

-- ---------------------------------------------------------------------
-- 1. Login: contraseña (bcrypt) del empleado
-- ---------------------------------------------------------------------
alter table public.empleados add column if not exists empleado_password_hash varchar(100);

-- ---------------------------------------------------------------------
-- 2. Índices de llaves foráneas (PostgreSQL no los crea solo)
-- ---------------------------------------------------------------------
create index if not exists idx_productos_tipo           on public.productos (producto_tipo_id);
create index if not exists idx_empleados_cargo          on public.empleados (empleado_cargo_id);
create index if not exists idx_pedidos_cliente          on public.pedidos (pedido_cliente_id);
create index if not exists idx_pedidos_empleado         on public.pedidos (pedido_empleado_id);
create index if not exists idx_pedidos_sede             on public.pedidos (pedido_sede_id);
create index if not exists idx_detalle_pedidos_pedido   on public.detalle_pedidos (detalle_pedido_id);
create index if not exists idx_detalle_pedidos_producto on public.detalle_pedidos (detalle_producto_id);
create index if not exists idx_comprobantes_tipo        on public.comprobantes (comprobante_tipo_id);
create index if not exists idx_comprobantes_metodo_pago on public.comprobantes (comprobante_metodo_pago_id);
create index if not exists idx_movimientos_producto_fecha
  on public.movimientos_inventario (movimiento_producto_id, movimiento_fecha);

-- Un mismo número de comprobante no puede repetirse dentro de su serie
do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'uq_comprobantes_serie_numero') then
    alter table public.comprobantes
      add constraint uq_comprobantes_serie_numero unique (comprobante_serie, comprobante_numero_correlativo);
  end if;
end $$;

-- =====================================================================
-- 3. EMPLEADOS · login con documento + contraseña
-- =====================================================================
create or replace function public.fn_empleado_login(p_documento varchar, p_password varchar)
returns table (empleado_id integer, empleado_nombre_completo varchar, empleado_documento varchar, cargo_nombre varchar)
language sql stable
set search_path = public, pg_temp
as $$
  select e.empleado_id, e.empleado_nombre_completo, e.empleado_documento, c.cargo_nombre
    from empleados e
    join cargo_empleados c on c.cargo_id = e.empleado_cargo_id
   where e.empleado_documento = trim(p_documento)
     and e.empleado_password_hash is not null
     and e.empleado_password_hash = extensions.crypt(p_password, e.empleado_password_hash);
$$;

-- Crea el empleado o, si el documento ya existe, actualiza cargo, nombre y contraseña
create or replace function public.fn_empleado_registrar(
  p_cargo_nombre    varchar,
  p_documento       varchar,
  p_nombre_completo varchar,
  p_password        varchar
) returns integer
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_cargo_id    integer;
  v_empleado_id integer;
begin
  if length(coalesce(p_password, '')) < 8 then
    raise exception 'La contraseña debe tener al menos 8 caracteres' using errcode = 'DT422';
  end if;

  select cargo_id into v_cargo_id from cargo_empleados where upper(cargo_nombre) = upper(trim(p_cargo_nombre));
  if v_cargo_id is null then
    raise exception 'El cargo % no existe', p_cargo_nombre using errcode = 'DT404';
  end if;

  insert into empleados (empleado_cargo_id, empleado_documento, empleado_nombre_completo, empleado_password_hash)
  values (v_cargo_id, trim(p_documento), upper(trim(p_nombre_completo)),
          extensions.crypt(p_password, extensions.gen_salt('bf', 10)))
  on conflict (empleado_documento) do update
     set empleado_cargo_id        = excluded.empleado_cargo_id,
         empleado_nombre_completo = excluded.empleado_nombre_completo,
         empleado_password_hash   = excluded.empleado_password_hash
  returning empleado_id into v_empleado_id;

  return v_empleado_id;
end;
$$;

-- =====================================================================
-- 4. PRODUCTOS · catálogo con stock y CRUD
-- ---------------------------------------------------------------------
-- El stock NO se edita aquí: solo cambia con fn_registrar_entrada,
-- fn_registrar_salida, fn_ajustar_stock y las ventas (trigger existente).
-- =====================================================================
create or replace function public.fn_productos_listar(
  p_buscar       varchar default null,
  p_tipo_id      integer default null,
  p_stock_minimo integer default 5
) returns table (
  producto_id integer, producto_nombre varchar, producto_precio numeric, producto_stock_actual integer,
  tipo_producto_id integer, tipo_producto_nombre varchar, tipo_producto_origen varchar, stock_bajo boolean
)
language sql stable
set search_path = public, pg_temp
as $$
  select p.producto_id, p.producto_nombre, p.producto_precio, coalesce(p.producto_stock_actual, 0),
         t.tipo_producto_id, t.tipo_producto_nombre, t.tipo_producto_origen,
         coalesce(p.producto_stock_actual, 0) <= p_stock_minimo
    from productos p
    join tipo_productos t on t.tipo_producto_id = p.producto_tipo_id
   where (p_tipo_id is null or p.producto_tipo_id = p_tipo_id)
     and (p_buscar is null or p.producto_nombre ilike '%' || p_buscar || '%')
   order by t.tipo_producto_nombre, p.producto_nombre;
$$;

create or replace function public.fn_producto_crear(p_tipo_id integer, p_nombre varchar, p_precio numeric)
returns setof public.productos
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if p_precio is null or p_precio < 0 then
    raise exception 'El precio debe ser mayor o igual a cero' using errcode = 'DT422';
  end if;

  return query
    insert into productos (producto_tipo_id, producto_nombre, producto_precio, producto_stock_actual)
    values (p_tipo_id, upper(trim(p_nombre)), p_precio, 0)
    returning *;
end;
$$;

-- Actualización parcial: los parámetros NULL conservan el valor actual
create or replace function public.fn_producto_actualizar(
  p_producto_id integer,
  p_tipo_id     integer default null,
  p_nombre      varchar default null,
  p_precio      numeric default null
) returns setof public.productos
language plpgsql
set search_path = public, pg_temp
as $$
begin
  if p_precio is not null and p_precio < 0 then
    raise exception 'El precio debe ser mayor o igual a cero' using errcode = 'DT422';
  end if;

  return query
    update productos
       set producto_tipo_id = coalesce(p_tipo_id, producto_tipo_id),
           producto_nombre  = coalesce(upper(trim(p_nombre)), producto_nombre),
           producto_precio  = coalesce(p_precio, producto_precio)
     where producto_id = p_producto_id
    returning *;

  if not found then
    raise exception 'El producto % no existe', p_producto_id using errcode = 'DT404';
  end if;
end;
$$;

create or replace function public.fn_producto_eliminar(p_producto_id integer)
returns void
language plpgsql
set search_path = public, pg_temp
as $$
begin
  delete from productos where producto_id = p_producto_id;
  if not found then
    raise exception 'El producto % no existe', p_producto_id using errcode = 'DT404';
  end if;
exception
  when foreign_key_violation then
    raise exception 'El producto % tiene movimientos o ventas registradas y no puede eliminarse', p_producto_id
      using errcode = 'DT409';
end;
$$;

-- =====================================================================
-- 5. CLIENTES
-- =====================================================================
create or replace function public.fn_cliente_buscar(p_dni_ruc varchar)
returns setof public.clientes
language sql stable
set search_path = public, pg_temp
as $$
  select * from clientes where cliente_dni_ruc = trim(p_dni_ruc);
$$;

create or replace function public.fn_cliente_registrar(
  p_tipo_documento      varchar,
  p_dni_ruc             varchar,
  p_nombre_razon_social varchar
) returns setof public.clientes
language sql
set search_path = public, pg_temp
as $$
  insert into clientes (cliente_tipo_documento, cliente_dni_ruc, cliente_nombre_razon_social)
  values (upper(trim(p_tipo_documento)), trim(p_dni_ruc), upper(trim(p_nombre_razon_social)))
  on conflict (cliente_dni_ruc) do update
     set cliente_tipo_documento      = excluded.cliente_tipo_documento,
         cliente_nombre_razon_social = excluded.cliente_nombre_razon_social
  returning *;
$$;

-- =====================================================================
-- 6. VENTAS · pedido + detalle + comprobante en UNA transacción
-- ---------------------------------------------------------------------
-- 1. Valida cliente, tipo de comprobante (FACTURA exige RUC) y método de pago
-- 2. Bloquea los productos en orden de id y valida stock (mensaje con nombre)
-- 3. Toma el correlativo bloqueando el tipo de comprobante (sin huecos)
-- 4. Inserta pedido y detalle con el precio vigente de la base de datos
-- 5. Inserta el comprobante → el trigger existente tr_descontar_stock_comprobante
--    descuenta el stock y registra SALIDA_VENTA en movimientos_inventario
-- Cualquier error revierte todo.
-- p_items: [{"producto_id": 1, "cantidad": 2}, ...]
-- =====================================================================
create or replace function public.fn_registrar_venta(
  p_empleado_id         integer,
  p_sede_id             integer,
  p_tipo_comprobante_id integer,
  p_metodo_pago_id      integer,
  p_items               jsonb,
  p_cliente_id          integer default null,
  p_canal               varchar default 'TIENDA'
) returns setof public.comprobantes
language plpgsql
set search_path = public, pg_temp
as $$
declare
  v_cliente     clientes%rowtype;
  v_tipo        tipo_comprobantes%rowtype;
  v_pedido_id   integer;
  v_total       numeric(10,2);
  v_solicitados integer;
  v_existentes  integer;
  v_falta       record;
  v_comprobante comprobantes%rowtype;
begin
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'La venta debe incluir al menos un producto' using errcode = 'DT422';
  end if;

  -- Cliente (sin cliente → CLIENTES VARIOS, documento 00000000)
  if p_cliente_id is null then
    select * into v_cliente from clientes where cliente_dni_ruc = '00000000';
    if not found then
      raise exception 'No existe el cliente genérico CLIENTES VARIOS (documento 00000000)' using errcode = 'DT422';
    end if;
  else
    select * into v_cliente from clientes where cliente_id = p_cliente_id;
    if not found then
      raise exception 'El cliente % no existe', p_cliente_id using errcode = 'DT404';
    end if;
  end if;

  if not exists (select 1 from sedes where sede_id = p_sede_id) then
    raise exception 'La sede % no existe', p_sede_id using errcode = 'DT404';
  end if;
  if not exists (select 1 from metodos_pagos where metodo_pago_id = p_metodo_pago_id) then
    raise exception 'El método de pago % no existe', p_metodo_pago_id using errcode = 'DT404';
  end if;

  -- Productos: bloqueo en orden de id (evita deadlocks) y validación de stock
  select count(*) into v_solicitados
    from (select distinct x.producto_id from jsonb_to_recordset(p_items) as x (producto_id integer)) s;

  perform 1
     from productos p
    where p.producto_id in (select x.producto_id from jsonb_to_recordset(p_items) as x (producto_id integer))
    order by p.producto_id
      for update;
  get diagnostics v_existentes = row_count;

  if v_existentes <> v_solicitados then
    raise exception 'Uno o más productos de la venta no existen' using errcode = 'DT404';
  end if;

  select p.producto_nombre, coalesce(p.producto_stock_actual, 0) as stock, i.cantidad
    into v_falta
    from (select x.producto_id, sum(x.cantidad)::integer as cantidad
            from jsonb_to_recordset(p_items) as x (producto_id integer, cantidad integer)
           group by x.producto_id) i
    join productos p on p.producto_id = i.producto_id
   where coalesce(p.producto_stock_actual, 0) < i.cantidad
   order by p.producto_id
   limit 1;

  if found then
    raise exception 'Stock insuficiente para "%": disponible %, solicitado %',
      v_falta.producto_nombre, v_falta.stock, v_falta.cantidad using errcode = 'DT409';
  end if;

  -- Correlativo: el UPDATE bloquea la fila del tipo de comprobante
  update tipo_comprobantes
     set tipo_comprobante_correlativo = tipo_comprobante_correlativo + 1
   where tipo_comprobante_id = p_tipo_comprobante_id
  returning * into v_tipo;

  if not found then
    raise exception 'El tipo de comprobante % no existe', p_tipo_comprobante_id using errcode = 'DT404';
  end if;
  if upper(v_tipo.tipo_comprobante_nombre) like 'FACTURA%' and upper(v_cliente.cliente_tipo_documento) <> 'RUC' then
    raise exception 'La factura requiere un cliente con RUC' using errcode = 'DT422';
  end if;

  -- Pedido y detalle (precio vigente de la base de datos)
  insert into pedidos (pedido_cliente_id, pedido_empleado_id, pedido_sede_id, pedido_canal, pedido_total)
  values (v_cliente.cliente_id, p_empleado_id, p_sede_id, upper(coalesce(nullif(trim(p_canal), ''), 'TIENDA')), 0)
  returning pedido_id into v_pedido_id;

  insert into detalle_pedidos (detalle_pedido_id, detalle_producto_id, detalle_cantidad,
                               detalle_precio_unitario, detalle_subtotal)
  select v_pedido_id, i.producto_id, i.cantidad, p.producto_precio, i.cantidad * p.producto_precio
    from (select x.producto_id, sum(x.cantidad)::integer as cantidad
            from jsonb_to_recordset(p_items) as x (producto_id integer, cantidad integer)
           group by x.producto_id) i
    join productos p on p.producto_id = i.producto_id;

  select sum(detalle_subtotal) into v_total from detalle_pedidos where detalle_pedido_id = v_pedido_id;
  update pedidos set pedido_total = v_total where pedido_id = v_pedido_id;

  -- Comprobante → dispara tr_descontar_stock_comprobante (descuento + Kardex)
  insert into comprobantes (comprobante_pedido_id, comprobante_tipo_id, comprobante_metodo_pago_id,
                            comprobante_serie, comprobante_numero_correlativo, comprobante_total)
  values (v_pedido_id, v_tipo.tipo_comprobante_id, p_metodo_pago_id,
          v_tipo.tipo_comprobante_serie, lpad(v_tipo.tipo_comprobante_correlativo::text, 8, '0'), v_total)
  returning * into v_comprobante;

  return next v_comprobante;
end;
$$;

-- ---------------------------------------------------------------------
-- Seguridad: estas funciones solo las usa el backend (rol postgres).
-- No quedan expuestas a la API pública de Supabase (anon / authenticated).
-- ---------------------------------------------------------------------
do $$
declare
  f text;
begin
  foreach f in array array[
    'fn_empleado_login(varchar, varchar)',
    'fn_empleado_registrar(varchar, varchar, varchar, varchar)',
    'fn_productos_listar(varchar, integer, integer)',
    'fn_producto_crear(integer, varchar, numeric)',
    'fn_producto_actualizar(integer, integer, varchar, numeric)',
    'fn_producto_eliminar(integer)',
    'fn_cliente_buscar(varchar)',
    'fn_cliente_registrar(varchar, varchar, varchar)',
    'fn_registrar_venta(integer, integer, integer, integer, jsonb, integer, varchar)'
  ] loop
    execute format('revoke execute on function public.%s from public', f);
    if exists (select 1 from pg_roles where rolname = 'anon') then
      execute format('revoke execute on function public.%s from anon, authenticated', f);
    end if;
  end loop;
end $$;
