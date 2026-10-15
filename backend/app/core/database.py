"""Conexión a Supabase (PostgreSQL) mediante un pool de psycopg 3."""

from collections.abc import Iterator
from contextlib import contextmanager

import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.core.config import obtener_configuracion

_pool: ConnectionPool | None = None


def abrir_pool() -> None:
    global _pool
    config = obtener_configuracion()
    _pool = ConnectionPool(
        conninfo=config.database_url,
        min_size=config.db_pool_min,
        max_size=config.db_pool_max,
        name="dtulia",
        open=False,
        # Descarta conexiones cortadas por el pooler de Supabase antes de entregarlas
        check=ConnectionPool.check_connection,
        kwargs={
            "row_factory": dict_row,
            # Sin sentencias preparadas: compatible con el Transaction pooler (puerto 6543)
            "prepare_threshold": None,
        },
    )
    _pool.open(wait=False)


def cerrar_pool() -> None:
    if _pool is not None:
        _pool.close()


@contextmanager
def obtener_conexion() -> Iterator[psycopg.Connection]:
    """Entrega una conexión del pool: COMMIT al salir, ROLLBACK si hay excepción."""
    if _pool is None:
        raise RuntimeError("El pool de conexiones no está inicializado")
    with _pool.connection() as conexion:
        yield conexion
