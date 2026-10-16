import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.rutas import auth, catalogos, clientes, comprobantes, inventario, producciones, productos, viajes
from app.core.config import obtener_configuracion
from app.core.database import abrir_pool, cerrar_pool, obtener_conexion
from app.core.errores import registrar_manejadores

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


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

for modulo in (auth, productos, comprobantes, catalogos, clientes, inventario, viajes, producciones):
    app.include_router(modulo.router)


@app.get("/salud", tags=["Sistema"])
def salud() -> dict[str, str]:
    with obtener_conexion() as conn:
        conn.execute("select 1")
    return {"estado": "ok", "base_de_datos": "ok"}
