import logging
import time
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from uvicorn.logging import AccessFormatter

from app.api.rutas import auth, catalogos, clientes, comprobantes, inventario, producciones, productos, viajes
from app.core.config import obtener_configuracion
from app.core.database import abrir_pool, cerrar_pool, estadisticas_pool, obtener_conexion
from app.core.errores import registrar_manejadores

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger("dtulia")

# Fecha y hora en cada petición del registro (logs\api.log), para poder reconstruir cualquier falla
for _manejador in logging.getLogger("uvicorn.access").handlers:
    _manejador.setFormatter(AccessFormatter(
        '%(asctime)s %(client_addr)s - "%(request_line)s" %(status_code)s', use_colors=False))

# Una petición que tarda más que esto queda anotada junto con el estado del pool
_SEGUNDOS_PETICION_LENTA = 3


@asynccontextmanager
async def ciclo_de_vida(_: FastAPI) -> AsyncIterator[None]:
    abrir_pool()
    yield
    cerrar_pool()


app = FastAPI(
    title=obtener_configuracion().app_nombre,
    version="1.0.0",
    description="Control de inventario y ventas de D'Tulia — distribuidora de licores y producción propia.",
    lifespan=ciclo_de_vida,
)
registrar_manejadores(app)


@app.middleware("http")
async def anotar_peticiones_lentas(request: Request, siguiente):
    inicio = time.monotonic()
    respuesta = await siguiente(request)
    segundos = time.monotonic() - inicio
    if segundos > _SEGUNDOS_PETICION_LENTA:
        logger.warning("Petición lenta: %s %s tardó %.1f s | %s",
                       request.method, request.url.path, segundos, estadisticas_pool())
    return respuesta

for modulo in (auth, productos, comprobantes, catalogos, clientes, inventario, viajes, producciones):
    app.include_router(modulo.router)


@app.get("/salud", tags=["Sistema"])
def salud() -> dict[str, str]:
    with obtener_conexion() as conn:
        conn.execute("select 1")
    return {"estado": "ok", "base_de_datos": "ok"}
