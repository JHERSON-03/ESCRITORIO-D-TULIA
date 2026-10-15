"""Excepciones de negocio y traducción de errores de PostgreSQL a respuestas HTTP."""

import logging

import psycopg
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from psycopg_pool import PoolTimeout

logger = logging.getLogger("dtulia")


class ErrorNegocio(Exception):
    estado_http = 422

    def __init__(self, mensaje: str, estado_http: int | None = None):
        super().__init__(mensaje)
        self.mensaje = mensaje
        if estado_http is not None:
            self.estado_http = estado_http


class NoEncontrado(ErrorNegocio):
    estado_http = 404


class CredencialesInvalidas(ErrorNegocio):
    estado_http = 401


# SQLSTATE propios (RAISE ... USING ERRCODE en PL/pgSQL) y estándar de PostgreSQL
_SQLSTATE_A_HTTP = {
    "DT404": 404,
    "DT409": 409,
    "DT422": 422,
    "P0001": 422,  # RAISE EXCEPTION de las funciones existentes (fn_mover_stock, fn_ajustar_stock…)
    "23505": 409,  # unique_violation
    "23503": 422,  # foreign_key_violation
    "23514": 422,  # check_violation
    "23502": 422,  # not_null_violation
    "22P02": 422,  # invalid_text_representation
}

_MENSAJES_ESTANDAR = {
    "23505": "Ya existe un registro con esos datos",
    "23503": "Alguno de los registros relacionados no existe",
    "23514": "Los datos no cumplen las reglas de validación",
    "23502": "Falta un dato obligatorio",
    "22P02": "Formato de dato inválido",
}


def registrar_manejadores(app: FastAPI) -> None:
    @app.exception_handler(ErrorNegocio)
    async def _error_negocio(_: Request, exc: ErrorNegocio) -> JSONResponse:
        return JSONResponse(status_code=exc.estado_http, content={"detail": exc.mensaje})

    @app.exception_handler(psycopg.Error)
    async def _error_bd(_: Request, exc: psycopg.Error) -> JSONResponse:
        sqlstate = exc.sqlstate or ""
        estado = _SQLSTATE_A_HTTP.get(sqlstate)
        if estado is None:
            if isinstance(exc, psycopg.OperationalError):
                logger.error("Base de datos no disponible: %s", exc)
                return JSONResponse(status_code=503, content={"detail": "Base de datos no disponible"})
            logger.exception("Error de base de datos no controlado", exc_info=exc)
            return JSONResponse(status_code=500, content={"detail": "Error interno de base de datos"})

        if sqlstate.startswith("DT") or sqlstate == "P0001":
            mensaje = exc.diag.message_primary or "Operación rechazada"
        else:
            mensaje = _MENSAJES_ESTANDAR[sqlstate]
            if exc.diag.constraint_name:
                mensaje = f"{mensaje} ({exc.diag.constraint_name})"
        return JSONResponse(status_code=estado, content={"detail": mensaje})

    @app.exception_handler(PoolTimeout)
    async def _pool_agotado(_: Request, exc: PoolTimeout) -> JSONResponse:
        logger.error("Pool de conexiones agotado: %s", exc)
        return JSONResponse(status_code=503, content={"detail": "Servidor ocupado, intente nuevamente"})
