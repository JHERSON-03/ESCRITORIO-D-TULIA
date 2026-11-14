-- =====================================================================
-- D'Tulia · Diagnóstico previo a 05_correcciones_auditoria.sql y al rol
-- de aplicación (06_rol_aplicacion_BORRADOR.sql)
-- ---------------------------------------------------------------------
-- SOLO LECTURA: ninguna consulta modifica datos. El editor SQL de Supabase
-- muestra solo el resultado de la última consulta, así que ejecútelas
-- una por una y guarde los resultados para revisarlos.
-- Ninguna consulta muestra contraseñas ni hashes.
-- =====================================================================

-- 1. Con qué rol se conecta el backend y qué atributos tiene
select rolname, rolsuper, rolbypassrls, rolcreaterole, rolcreatedb
  from pg_roles
 where rolname = current_user;

-- 2. Límites de tiempo efectivos (hallazgo 13) y los fijados por rol o BD
show statement_timeout;
show lock_timeout;
show idle_in_transaction_session_timeout;
select coalesce(r.rolname, '(todos)') as rol, coalesce(d.datname, '(todas)') as base, s.setconfig
  from pg_db_role_setting s
  left join pg_roles r    on r.oid = s.setrole
  left join pg_database d on d.oid = s.setdatabase;

-- 3. Funciones del esquema public: dueño, SECURITY DEFINER y permisos
--    (revisar sobre todo las del esquema base: fn_mover_stock,
--    fn_registrar_entrada, fn_registrar_salida, fn_ajustar_stock,
--    fn_listar_movimientos y la función del trigger de comprobantes)
select p.proname as funcion, pg_get_function_identity_arguments(p.oid) as argumentos,
       pg_get_userbyid(p.proowner) as duenio, p.prosecdef as security_definer,
       p.proconfig as configuracion, p.proacl as permisos
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
 order by p.proname;

-- 4. Definición de las funciones del esquema base que usan las correcciones
select p.proname, pg_get_functiondef(p.oid) as definicion
  from pg_proc p
  join pg_namespace n on n.oid = p.pronamespace
 where n.nspname = 'public'
   and p.proname in ('fn_mover_stock', 'fn_registrar_entrada', 'fn_registrar_salida', 'fn_ajustar_stock',
                     'fn_listar_movimientos');

-- 5. Tablas: dueño, RLS y permisos
select c.relname as tabla, pg_get_userbyid(c.relowner) as duenio,
       c.relrowsecurity as rls, c.relforcerowsecurity as rls_forzado, c.relacl as permisos
  from pg_class c
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and c.relkind in ('r', 'p', 'v')
 order by c.relname;

-- 6. Políticas RLS existentes
select tablename, policyname, roles, cmd, qual, with_check
  from pg_policies
 where schemaname = 'public'
 order by tablename, policyname;

-- 7. Triggers (en especial los de movimientos_inventario, comprobantes,
--    productos y empleados)
select t.tgrelid::regclass as tabla, t.tgname as trigger, pg_get_triggerdef(t.oid) as definicion
  from pg_trigger t
  join pg_class c     on c.oid = t.tgrelid
  join pg_namespace n on n.oid = c.relnamespace
 where n.nspname = 'public' and not t.tgisinternal
 order by 1, 2;

-- 8. Estructura de movimientos_inventario (columnas, restricciones e índices)
select column_name, data_type, is_nullable, column_default
  from information_schema.columns
 where table_schema = 'public' and table_name = 'movimientos_inventario'
 order by ordinal_position;
select conname, pg_get_constraintdef(oid) from pg_constraint
 where conrelid = 'public.movimientos_inventario'::regclass;
select indexname, indexdef from pg_indexes
 where schemaname = 'public' and tablename = 'movimientos_inventario';

-- 9. Hallazgo 1: movimientos anulados más de una vez (debe dar 0 filas;
--    si da filas, el stock de esos productos se revirtió de más)
select a.movimiento_referencia, count(*) as anulaciones,
       min(a.movimiento_producto_id) as producto_id,
       string_agg(a.movimiento_id::text, ', ' order by a.movimiento_id) as movimientos_anulacion
  from public.movimientos_inventario a
 where a.movimiento_tipo = 'ANULACION' and a.movimiento_referencia like 'ANULA MOV #%'
 group by a.movimiento_referencia
having count(*) > 1;

-- 10. Hallazgo 5: pedidos por sede (para saber si el UPDATE que se quitó
--     de 02_datos_iniciales.sql llegó a reescribir sedes)
select s.sede_nombre, s.sede_es_almacen, count(p.pedido_id) as pedidos
  from public.sedes s
  left join public.pedidos p on p.pedido_sede_id = s.sede_id
 group by s.sede_id, s.sede_nombre, s.sede_es_almacen
 order by pedidos desc;

-- 11. Administrador inicial: cambia su contraseña con fn_empleado_registrar
--     si todavía usa la clave de ejemplo que tuvo este proyecto.
--     (La consulta que la comparaba se quitó para no dejar la clave escrita.)
select e.empleado_documento, e.empleado_nombre_completo
  from public.empleados e
 where e.empleado_documento = '00000001';
