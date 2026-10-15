# D'Tulia · Inventario y ventas

Sistema de control de inventario y gestión para D'Tulia, distribuidora de licores con producción propia.
Arquitectura en **N-Capas**:

```
┌────────────────────────────────────┐
│ PRESENTACIÓN · Java 17 + JavaFX    │  desktop/   FrmLogin · FrmProductos · FrmVentas · FrmMovimientos
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
│   ├── 01_complementos.sql      login, índices, funciones para la API y registro de ventas
│   └── 02_datos_iniciales.sql   cargos, métodos de pago, comprobantes, cliente genérico, admin
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
        │   └── vista/           FrmLogin · FrmPrincipal · FrmProductos · FrmVentas · FrmMovimientos
        └── resources/pe/dtulia/desktop/estilos.css
```

## Paso 1 · Base de datos en Supabase

El proyecto trabaja sobre **tu modelo existente** en el esquema `public` (`sedes`, `tipo_productos`, `productos`, `movimientos_inventario`, etc.) y **reutiliza tus funciones** (`fn_mover_stock`, `fn_registrar_entrada`, `fn_registrar_salida`, `fn_ajustar_stock`, `fn_anular_movimiento`, `fn_listar_movimientos`) y tu trigger `tr_descontar_stock_comprobante`.

En **SQL Editor** ejecuta, en orden:

1. `01_complementos.sql`: agrega sin modificar nada existente
2. `02_datos_iniciales.sql`: completa los catálogos vacíos (no toca sedes, tipos ni productos)

Ambos son idempotentes (se pueden ejecutar más de una vez).

### Qué agrega `01_complementos.sql`

| Objeto | Qué hace |
|---|---|
| `empleados.empleado_password_hash` | Contraseña bcrypt (pgcrypto) para el login de la app |
| Índices `idx_*` | Índices en todas las llaves foráneas + unicidad `serie-número` del comprobante |
| `fn_empleado_login` / `fn_empleado_registrar` | Login por documento y alta/actualización de empleados con contraseña |
| `fn_productos_listar` | Catálogo con tipo, origen, stock y marca de stock bajo |
| `fn_producto_crear / actualizar / eliminar` | CRUD de productos (el stock solo cambia por movimientos) |
| `fn_cliente_buscar / registrar` | Clientes por DNI/RUC |
| `fn_registrar_venta` | Pedido + detalle + comprobante en **una sola transacción**: valida stock (bloqueando productos en orden), toma el correlativo, exige RUC en facturas y usa el precio de la BD. Al insertar el comprobante, **tu trigger** descuenta el stock y registra `SALIDA_VENTA` |

Si falta stock o algo falla, PostgreSQL **revierte todo** (pedido, correlativo y stock).

### Crear empleados

```sql
-- cargo: ADMINISTRADOR | VENDEDOR | ALMACENERO
select fn_empleado_registrar('VENDEDOR', '45678912', 'Luis Paredes Soto', 'Contraseña2026!');
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
| GET | `/catalogos/sedes` | — (público) | Sedes para la pantalla de login |
| POST | `/auth/login` | — | `{documento, password, sede_id}` → JWT y datos del empleado |
| GET | `/productos` | cualquiera | Catálogo con stock actual (`?buscar=`, `?tipo_producto_id=`) |
| GET | `/productos/{id}` | cualquiera | Detalle de un producto |
| POST · PATCH · DELETE | `/productos[/{id}]` | ADMINISTRADOR | CRUD de productos |
| POST | `/comprobantes` | cualquiera | Registra la venta en la sede de la sesión (boleta, factura o nota de venta) |
| POST | `/inventario/movimientos` | ADMINISTRADOR, ALMACENERO | `ENTRADA` (cantidad), `MERMA` (cantidad + motivo) o `AJUSTE` (stock_real) |
| POST | `/inventario/movimientos/{id}/anular` | ADMINISTRADOR | Revierte una entrada, merma o ajuste |
| GET | `/inventario/kardex/{producto_id}` | cualquiera | Movimientos del producto (más reciente primero) |
| GET | `/catalogos/tipos-producto` · `/tipos-comprobante` · `/metodos-pago` · `/canales-venta` | cualquiera | Combos de los formularios |
| GET · POST | `/clientes/{dni_ruc}` · `/clientes` | cualquiera | Buscar / registrar cliente |

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

- **FrmLogin**: documento, contraseña y **sede** de trabajo (lista desde la tabla `sedes`).
- **FrmProductos**: grilla de inventario (`GET /productos`) con búsqueda, filtro por **tipo de producto**, filtro de stock bajo, colores de alerta y refresco automático cada 30 s.
- **FrmVentas**: comprobante, método de pago, **canal**, cliente (busca o registra por DNI/RUC), carrito y envío a `POST /comprobantes`.
- **FrmMovimientos** (solo ADMINISTRADOR y ALMACENERO): entradas, mermas y ajustes de stock con el Kardex del producto; el administrador puede anular movimientos.

Se eligió **JavaFX** sobre Swing porque ofrece `TableView` con ordenamiento y filtrado, estilos CSS y un modelo de hilos claro (`Platform.runLater`) para peticiones asíncronas. Las llamadas HTTP usan `java.net.http.HttpClient` del JDK y **Jackson** convierte JSON (`snake_case`) ⇄ records Java.

## Usuario inicial

| Documento | Contraseña | Cargo |
|---|---|---|
| 00000001 | DTulia2026! | ADMINISTRADOR |

Crea a tu personal con `fn_empleado_registrar` (ver Paso 1) y cambia la contraseña del administrador inicial ejecutando la misma función con una nueva clave.
