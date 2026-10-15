-- =====================================================================
-- D'Tulia · CAPA DE DATOS · 02 · Datos iniciales
-- ---------------------------------------------------------------------
-- Completa los catálogos que la aplicación necesita y que están vacíos.
-- NO toca sedes, tipo_productos ni productos (ya tienen tus datos).
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
