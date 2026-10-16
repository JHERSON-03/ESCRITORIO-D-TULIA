# D'Tulia · Inventario y ventas

Sistema de control de inventario y gestión para D'Tulia, distribuidora de licores con producción propia.
Arquitectura en **N-Capas**:

```
┌────────────────────────────────────┐
│ PRESENTACIÓN · Java 17 + JavaFX    │  desktop/   FrmLogin · FrmProductos · FrmVentas · FrmMovimientos · FrmViajes · FrmProduccion
│   vista → servicio → modelo        │             HttpClient (JDK) + Jackson
└───────────────┬────────────────────┘
                │ HTTP / JSON + JWT
┌───────────────▼────────────────────┐
│ API · FastAPI                      │  backend/app/api        rutas (controladores)
│ NEGOCIO · servicios                │  backend/app/negocio    reglas de negocio
│ ENTIDADES · Pydantic               │  backend/app/modelos    validación y contratos
│ ACCESO A DATOS · repositorios      │  backend/app/datos      llamadas a funciones SQL
└───────────────┬────────────────────┘
                │ psycopg 3 (pool)
┌───────────────▼────────────────────┐
│ DATOS · Supabase / PostgreSQL      │  database/   esquema public, PL/pgSQL, trigger
└────────────────────────────────────┘
```

Cada capa solo conoce a la inmediatamente inferior: las vistas no hablan con la base de datos, las rutas no ejecutan SQL y los repositorios no deciden reglas de negocio.

## Estructura de carpetas

```
WEB D'TULIA/
├── .vscode/                     settings.json · extensions.json (extensiones recomendadas)
├── database/                    CAPA DE DATOS (sobre el modelo existente en Supabase)
│   ├── 01_complementos.sql      login, índices, presentaciones, funciones para la API y registro de ventas
│   ├── 02_datos_iniciales.sql   cargos, métodos de pago, comprobantes, cliente genérico, admin, almacén
│   ├── 03_viajes.sql            mercadería que lleva el jefe: envíos, liquidación y diferencias
│   └── 04_produccion.sql        envíos de la fábrica al almacén y su recepción
├── backend/                     CAPA DE NEGOCIO + API (Python)
│   ├── .env.example
│   ├── requirements.txt
│   ├── peticiones.http          pruebas rápidas con REST Client
│   ├── scripts/                 API en segundo plano con arranque automático (Windows)
│   └── app/
│       ├── main.py              arranque FastAPI
│       ├── core/                config (.env), pool de conexiones, JWT, manejo de errores
│       ├── modelos/             Pydantic (entidades / DTO)
│       ├── datos/               repositorios → funciones de PostgreSQL
│       ├── negocio/             servicios con las reglas de negocio
│       └── api/                 deps.py (auth, roles) + rutas/
└── desktop/                     CAPA DE PRESENTACIÓN (Java)
    ├── pom.xml
    └── src/main/
        ├── java/pe/dtulia/desktop/
        │   ├── App.java · Launcher.java
        │   ├── config/          URL de la API
        │   ├── modelo/          records (Producto, VentaSolicitud, …)
        │   ├── servicio/        ApiClient + servicios HTTP
        │   └── vista/           FrmLogin · FrmPrincipal · FrmProductos · FrmVentas · FrmMovimientos · FrmViajes · FrmProduccion
        └── resources/pe/dtulia/desktop/estilos.css
```

## Paso 1 · Base de datos en Supabase

El proyecto trabaja sobre **tu modelo existente** en el esquema `public` (`sedes`, `tipo_productos`, `productos`, `movimientos_inventario`, etc.) y **reutiliza tus funciones** (`fn_mover_stock`, `fn_registrar_entrada`, `fn_registrar_salida`, `fn_ajustar_stock`, `fn_anular_movimiento`, `fn_listar_movimientos`) y tu trigger `tr_descontar_stock_comprobante`.

En **SQL Editor** ejecuta, en orden:

1. `01_complementos.sql`: agrega sin modificar nada existente
2. `02_datos_iniciales.sql`: completa los catálogos vacíos, marca TRUJILLO como almacén y crea las presentaciones de cada producto
3. `03_viajes.sql`: control de la mercadería que lleva el jefe
4. `04_produccion.sql`: envíos de la fábrica al almacén

Todos son idempotentes (se pueden ejecutar más de una vez).

### Modelo del negocio

- **Un solo almacén (Trujillo)**: todo sale de ahí, también lo que se vende en las tiendas. La sede marcada con `sede_es_almacen` es donde se registran las ventas; las demás sedes son **ciudades de destino**.
- **Presentaciones**: cada tamaño es un producto propio con su precio y su stock (*VINO ROSÉ · 750 ML* y *VINO ROSÉ · 4 L*). El stock se guarda en unidades; las cajas solo sirven para ingresar y mostrar cantidades.
- **Viajes del jefe**: lleva mercadería, la vende en otras ciudades con comprobantes en papel y no tiene fecha fija de regreso. El sistema controla productos (no dinero): llevó − vendió − regresó − merma = diferencia.
- **Producción**: en la fábrica hacen, embotellan y encajan, y lo anotan en un cuaderno. La administradora de la fábrica lo registra como *envío de producción*; la del almacén confirma lo que llegó y recién ahí sube el stock. Si no coincide, queda la diferencia con una observación.
- **Cargos**: `ADMINISTRADOR` (las administradoras del almacén y de la fábrica, con acceso a todo; el sistema guarda quién hizo cada cosa) y `ALMACENERO` (saca las cajas; en el sistema solo consulta Inventario y Kardex). La boleta se emite cuando las cajas ya salieron, así el stock baja en el momento correcto; los envíos al jefe se registran igual: cuando las cajas ya salieron.

### Qué agrega `01_complementos.sql`

| Objeto | Qué hace |
|---|---|
| `empleados.empleado_password_hash` | Contraseña bcrypt (pgcrypto) para el login de la app |
| Índices `idx_*` | Índices en todas las llaves foráneas + unicidad `serie-número` del comprobante |
| `fn_empleado_login` / `fn_empleado_registrar` | Login por documento y alta/actualización de empleados con contraseña |
| `presentaciones` | 750 ML (12 por caja), 1 L (12), 2 L (9), 4 L (4) y CHATA (24). `productos.producto_presentacion_id`; los productos que ya existían quedaron en 750 ML |
| `sedes.sede_es_almacen` | Marca la sede desde la que sale toda la mercadería (una sola) |
| `fn_productos_listar` | Catálogo con tipo, origen, presentación, unidades por caja, stock y marca de stock bajo |
| `fn_producto_crear / actualizar / eliminar` | CRUD de productos con presentación (el stock solo cambia por movimientos) |
| `fn_cliente_buscar / registrar` | Clientes por DNI/RUC |
| `fn_registrar_venta` | Pedido + detalle + comprobante en **una sola transacción**: valida stock (bloqueando productos en orden), rechaza productos con precio 0, toma el correlativo, exige RUC en facturas y usa el precio de la BD. Al insertar el comprobante, **tu trigger** descuenta el stock y registra `SALIDA_VENTA` |

Si falta stock o algo falla, PostgreSQL **revierte todo** (pedido, correlativo y stock).

Las presentaciones creadas por `02_datos_iniciales.sql` nacen con **precio 0 y stock 0**: no se pueden vender hasta que el administrador les ponga precio (Inventario → *Editar precio*).

### Qué agrega `03_viajes.sql`

| Objeto | Qué hace |
|---|---|
| `viajes` | Fecha de salida, estado `ABIERTO` / `LIQUIDADO`, liquidación y observación. Solo puede haber **un viaje abierto** |
| `viaje_envios` · `viaje_envio_detalle` | Envío 1 = carga inicial; 2, 3… = lo que el jefe pide durante el viaje. Cada envío va a una ciudad |
| `viaje_ventas` · `viaje_retornos` | Lo vendido por ciudad, lo que regresó y la merma (con motivo) |
| `fn_viaje_abrir` · `fn_viaje_enviar` | Validan stock y descuentan del almacén con `SALIDA_VIAJE` |
| `fn_viaje_anular_envio` | Devuelve al almacén un envío registrado por error (`RETORNO_VIAJE`), solo con el viaje abierto |
| `fn_viaje_liquidar` | Guarda ventas y retornos, devuelve al almacén lo que regresó (`RETORNO_VIAJE`) y cierra el viaje. Si la diferencia no es 0, exige observación |
| `fn_viajes_listar` · `fn_viaje_productos` · `fn_viaje_envios` · `fn_viaje_ventas` | Consultas para las pantallas |
| `fn_anular_movimiento` | Tu función, con una validación más: los movimientos de un viaje no se anulan desde el Kardex |

Las ventas del viaje **no** pasan por comprobantes: esa mercadería ya salió del almacén y el trigger de ventas la descontaría dos veces.

### Qué agrega `04_produccion.sql`

| Objeto | Qué hace |
|---|---|
| `producciones` · `produccion_detalle` | Envío de la fábrica: quién lo envió, estado `EN_CAMINO` / `RECIBIDO` / `ANULADO`, quién lo recibió, y por producto lo enviado y lo recibido |
| `fn_produccion_registrar` | La fábrica registra lo que manda. El stock **no** cambia todavía |
| `fn_produccion_recibir` | El almacén confirma lo que llegó: suma al stock con `ENTRADA_PRODUCCION` (referencia `PRODUCCIÓN #n`). Si no coincide con lo enviado, exige observación |
| `fn_produccion_anular` | Envío registrado por error, solo mientras está en camino |
| `fn_producciones_listar` · `fn_produccion_productos` | Consultas para las pantallas |

Lo recibido de la fábrica no se anula desde el Kardex; si hubo un error se corrige con un **ajuste**. El cargo `VENDEDOR` se eliminó en `02_datos_iniciales.sql` (las boletas las emite la administradora del almacén).

### Crear empleados

```sql
-- cargo: ADMINISTRADOR | ALMACENERO
select fn_empleado_registrar('ADMINISTRADOR', '45678912', 'Rosa Paredes Soto', 'Contraseña2026!');
```

Si el documento ya existe, actualiza su cargo, nombre y contraseña (sirve también para cambiar claves).

## Paso 2 · Backend FastAPI

```powershell
cd backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env      # completa DATABASE_URL y JWT_SECRETO (STOCK_MINIMO opcional, por defecto 5)
uvicorn app.main:app --reload
```

### API siempre activa (arranque automático en Windows)

Para no tener que abrir `uvicorn` cada vez, la API puede correr en segundo plano: arranca sola al iniciar sesión en Windows, no abre ventanas y, si se cae, se reinicia en 5 segundos. No requiere permisos de administrador.

```powershell
cd backend
powershell -ExecutionPolicy Bypass -File .\scripts\api_automatica.ps1 instalar      # una sola vez
powershell -ExecutionPolicy Bypass -File .\scripts\api_automatica.ps1 estado
powershell -ExecutionPolicy Bypass -File .\scripts\api_automatica.ps1 reiniciar     # después de cambiar código Python
powershell -ExecutionPolicy Bypass -File .\scripts\api_automatica.ps1 detener
powershell -ExecutionPolicy Bypass -File .\scripts\api_automatica.ps1 desinstalar
```

- Registro de la API: `backend/logs/api.log`.
- Si abres un `uvicorn --reload` a mano para desarrollar, el servicio espera y retoma el puerto 8000 en un máximo de 30 s cuando lo cierres.

- `DATABASE_URL`: Supabase → **Project Settings → Database → Connection string → URI** (Session pooler).
- Documentación interactiva: <http://127.0.0.1:8000/docs>

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| POST | `/auth/login` | — | `{documento, password}` → JWT y datos del empleado (la sede es siempre el almacén) |
| GET | `/productos` | cualquiera | Catálogo con stock actual (`?buscar=`, `?tipo_producto_id=`) |
| GET | `/productos/{id}` | cualquiera | Detalle de un producto |
| POST · PATCH · DELETE | `/productos[/{id}]` | ADMINISTRADOR | CRUD de productos |
| POST | `/comprobantes` | ADMINISTRADOR | Registra la venta en el almacén (boleta, factura o nota de venta) |
| POST | `/inventario/movimientos` | ADMINISTRADOR | `ENTRADA` (cantidad), `MERMA` (cantidad + motivo) o `AJUSTE` (stock_real) |
| POST | `/inventario/movimientos/{id}/anular` | ADMINISTRADOR | Revierte una entrada, merma o ajuste |
| GET | `/inventario/kardex/{producto_id}` | cualquiera | Movimientos del producto (más reciente primero) |
| GET | `/viajes?estado=` · `/viajes/{id}` | ADMINISTRADOR | Viajes con totales / detalle (productos, envíos y ventas por ciudad) |
| POST | `/viajes` · `/viajes/{id}/envios` | ADMINISTRADOR | Carga inicial / envío durante el viaje: `{sede_id, referencia, items: [{producto_id, cantidad}]}` |
| POST | `/viajes/{id}/liquidar` | ADMINISTRADOR | `{ventas: [{producto_id, sede_id, cantidad}], retornos: [{producto_id, devuelto, merma, motivo}], observacion}` |
| POST | `/viajes/envios/{envio_id}/anular` | ADMINISTRADOR | Devuelve al almacén un envío registrado por error |
| GET | `/producciones?estado=` · `/producciones/{id}` | ADMINISTRADOR | Envíos de la fábrica (`EN_CAMINO`, `RECIBIDO`, `ANULADO`) / detalle por producto |
| POST | `/producciones` | ADMINISTRADOR | La fábrica registra lo que manda: `{referencia, items: [{producto_id, cantidad}]}` |
| POST | `/producciones/{id}/recibir` | ADMINISTRADOR | El almacén confirma lo que llegó: `{items: [{producto_id, cantidad}], observacion}` |
| POST | `/producciones/{id}/anular` | ADMINISTRADOR | Envío registrado por error (solo en camino): `{observacion}` |
| GET | `/catalogos/sedes` · `/presentaciones` · `/tipos-producto` · `/tipos-comprobante` · `/metodos-pago` · `/canales-venta` | cualquiera | Combos de los formularios |
| GET · POST | `/clientes/{dni_ruc}` · `/clientes` | ADMINISTRADOR | Buscar / registrar cliente (al emitir comprobantes) |

Errores de la BD → HTTP: stock insuficiente **409**, datos inválidos o reglas de tus funciones (`RAISE EXCEPTION`) **422**, no encontrado **404**.

### Librerías del backend

| Librería | Por qué |
|---|---|
| `fastapi` + `uvicorn[standard]` | API y servidor ASGI |
| `psycopg[binary,pool]` | Conexión directa a PostgreSQL con pool. Se usa en lugar de `supabase-py` porque llama a las funciones dentro de transacciones, no depende de RLS y lee el SQLSTATE de cada error |
| `pydantic-settings` | Lectura tipada del `.env` |
| `email-validator` | Validación de correos (Pydantic) |
| `PyJWT` | Tokens de sesión |

Sugeridas para la siguiente etapa: `pytest` + `httpx` (pruebas), `slowapi` (limitar intentos de login), `loguru` (logs), `alembic` o Supabase CLI (migraciones versionadas).

## Paso 3 · Aplicación de escritorio (JavaFX)

Requisitos: **JDK 17+** y **Maven** (o las extensiones *Extension Pack for Java* de VS Code).

```powershell
cd desktop
mvn javafx:run
```

Por defecto se conecta a `http://127.0.0.1:8000`. Para otra URL: `$env:DTULIA_API_URL = "http://192.168.1.10:8000"` antes de `mvn javafx:run` (o `-Ddtulia.api.url=...` al ejecutar el JAR con `java`).

- **FrmLogin**: documento y contraseña. Todos trabajan desde el almacén de Trujillo.
- **FrmProductos**: grilla de inventario (`GET /productos`) con presentación y stock en cajas, búsqueda, filtros por **tipo**, **presentación**, con stock y stock bajo, colores de alerta y refresco automático cada 30 s. El administrador edita precios (botón *Editar precio* o doble clic).
- **FrmVentas** (solo ADMINISTRADOR): comprobante, método de pago, **canal**, cliente (busca o registra por DNI/RUC), carrito y envío a `POST /comprobantes`.
- **FrmMovimientos**: para ADMINISTRADOR, entradas (en **cajas + unidades**), mermas y ajustes de stock con el Kardex del producto filtrable por tipo de movimiento, y anulación de movimientos. Para ALMACENERO es la pestaña **Kardex**: solo consulta.
- **FrmViajes** (solo ADMINISTRADOR): *Nuevo viaje* (carga inicial), *Enviar al viaje* (lo que el jefe pide, en cajas o unidades), *Imprimir hoja* (hoja de carga con columnas en blanco para que el jefe anote lo vendido en cada ciudad; si el viaje ya se liquidó imprime los valores) y *Liquidar viaje* (vendido por ciudad, lo que regresó y la merma, con la diferencia calculada mientras se escribe). El administrador puede anular un envío mientras el viaje está abierto.
- **FrmProduccion** (solo ADMINISTRADOR): *Nuevo envío (fábrica)* en cajas + unidades, *Recibir (almacén)* precargado con lo enviado y la diferencia calculada mientras se escribe, y *Anular envío*. La lista se refresca cada 60 s y el título de la pestaña muestra cuántos envíos siguen en camino, por ejemplo **Producción (2)**.

Se eligió **JavaFX** sobre Swing porque ofrece `TableView` con ordenamiento y filtrado, estilos CSS y un modelo de hilos claro (`Platform.runLater`) para peticiones asíncronas. Las llamadas HTTP usan `java.net.http.HttpClient` del JDK y **Jackson** convierte JSON (`snake_case`) ⇄ records Java.

## Usuario inicial

| Documento | Contraseña | Cargo |
|---|---|---|
| 00000001 | DTulia2026! | ADMINISTRADOR |

Crea a tu personal con `fn_empleado_registrar` (ver Paso 1) y cambia la contraseña del administrador inicial ejecutando la misma función con una nueva clave.

## Instalar en la laptop de la fábrica

La base de datos está en Supabase (en la nube); cada laptop corre su propia API local que se conecta a ella. La laptop necesita **internet** para registrar.

1. Instala **Python 3.13**, **JDK 17+**, **Maven** y **Git**.
2. Descarga el proyecto:
   ```powershell
   git clone https://github.com/JHERSON-03/ESCRITORIO-D-TULIA.git
   cd ESCRITORIO-D-TULIA\backend
   python -m venv .venv
   .venv\Scripts\pip install -r requirements.txt
   ```
3. Copia el archivo `backend\.env` de la PC del almacén a la misma carpeta de la laptop **con una memoria USB** (tiene la contraseña de la base de datos: nunca lo mandes por correo, WhatsApp ni lo subas a GitHub).
4. Deja la API siempre activa:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\api_automatica.ps1 instalar
   ```
5. Crea el usuario de la administradora de la fábrica en el **SQL Editor** de Supabase:
   ```sql
   select fn_empleado_registrar('ADMINISTRADOR', 'SU_DNI', 'NOMBRE COMPLETO', 'UnaClaveSegura2026');
   ```
6. Abre la aplicación (`cd ..\desktop` y `mvn javafx:run`) e ingresa con ese DNI. En la pestaña **Producción** registra cada envío con *Nuevo envío (fábrica)*.

Para actualizar después: `git pull` en la carpeta del proyecto y `api_automatica.ps1 reiniciar`.
