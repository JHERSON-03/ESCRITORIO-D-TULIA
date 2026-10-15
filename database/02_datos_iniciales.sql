-- =====================================================================
-- D'Tulia · CAPA DE DATOS · 02 · Datos iniciales
-- ---------------------------------------------------------------------
-- Completa los catálogos que la aplicación necesita y que están vacíos.
-- De tus datos solo marca TRUJILLO como almacén, crea las demás
-- presentaciones de cada producto y pasa las ventas al almacén.
-- Es idempotente: solo inserta lo que no existe.
--
-- Usuario inicial (cambia la contraseña al empezar a usar el sistema):
--   Documento: 00000001   Contraseña: DTulia2026!   Cargo: ADMINISTRADOR
-- =====================================================================

set search_path = public, extensions;

-- Cargos (los roles de la aplicación se basan en estos nombres)
insert into cargo_empleados (cargo_nombre)
select v.nombre
  from (values (1, 'ADMINISTRADOR'), (2, 'VENDEDOR'), (3, 'ALMACENERO')) as v (orden, nombre)
 where not exists (select 1 from cargo_empleados c where upper(c.cargo_nombre) = v.nombre)
 order by v.orden;

insert into metodos_pagos (metodo_pago_nombre)
select v.nombre
  from (values (1, 'EFECTIVO'), (2, 'YAPE'), (3, 'PLIN'), (4, 'TARJETA'), (5, 'TRANSFERENCIA')) as v (orden, nombre)
 where not exists (select 1 from metodos_pagos m where upper(m.metodo_pago_nombre) = v.nombre)
 order by v.orden;

-- Correlativo = último número emitido (el siguiente comprobante será el 1)
insert into tipo_comprobantes (tipo_comprobante_nombre, tipo_comprobante_serie, tipo_comprobante_correlativo)
select v.nombre, v.serie, 0
  from (values (1, 'BOLETA', 'B001'), (2, 'FACTURA', 'F001'), (3, 'NOTA DE VENTA', 'NV01')) as v (orden, nombre, serie)
 where not exists (select 1 from tipo_comprobantes t where upper(t.tipo_comprobante_nombre) = v.nombre)
 order by v.orden;

-- Cliente genérico para ventas sin documento
insert into clientes (cliente_tipo_documento, cliente_dni_ruc, cliente_nombre_razon_social)
values ('VARIOS', '00000000', 'CLIENTES VARIOS')
on conflict (cliente_dni_ruc) do nothing;

-- Administrador inicial (solo si aún no existe)
select fn_empleado_registrar('ADMINISTRADOR', '00000001', 'ADMINISTRADOR D''TULIA', 'DTulia2026!')
 where not exists (select 1 from empleados where empleado_documento = '00000001');

-- Todo sale del almacén de Trujillo; las demás sedes son ciudades de destino
update sedes set sede_es_almacen = true
 where upper(sede_nombre) = 'TRUJILLO'
   and not exists (select 1 from sedes where sede_es_almacen);

-- Las ventas del sistema se hacen en el almacén (antes tomaban la sede del login)
update pedidos set pedido_sede_id = (select sede_id from sedes where sede_es_almacen)
 where exists (select 1 from sedes where sede_es_almacen)
   and pedido_sede_id <> (select sede_id from sedes where sede_es_almacen);

-- Por única vez: cada licor en todas las presentaciones, con precio y stock en 0
-- (los existentes quedaron como 750 ML). No se repite si ya se generaron, para
-- no recrear presentaciones que luego se eliminen a propósito.
do $$
begin
  if not exists (select 1
                   from productos p
                   join presentaciones pr on pr.presentacion_id = p.producto_presentacion_id
                  where pr.presentacion_nombre <> '750 ML') then
    insert into productos (producto_tipo_id, producto_nombre, producto_precio, producto_stock_actual,
                           producto_presentacion_id)
    select p.producto_tipo_id, p.producto_nombre, 0, 0, pr.presentacion_id
      from productos p
      join presentaciones base on base.presentacion_id = p.producto_presentacion_id
                              and base.presentacion_nombre = '750 ML'
     cross join presentaciones pr
     where pr.presentacion_nombre <> '750 ML'
     order by p.producto_id, pr.presentacion_orden
    on conflict (producto_nombre, producto_presentacion_id) do nothing;
  end if;
end $$;
