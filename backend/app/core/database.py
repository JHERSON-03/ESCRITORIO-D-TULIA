"""Conexión a Supabase (PostgreSQL) mediante un pool de psycopg 3.

Cada ida y vuelta a Supabase cuesta ~160 ms (servidor en us-west-2), así que cada consulta hace
un solo viaje: autocommit (sin BEGIN/COMMIT aparte) y la conexión solo se revisa si estuvo quieta.
Es seguro porque toda escritura es UNA llamada a una función de PostgreSQL, que ya es atómica.
"""

import time
from collections.abc import Iterator
from contextlib import contextmanager

import psycopg
from psycopg.rows import dict_row
from psycopg_pool import ConnectionPool

from app.core.config import obtener_configuracion

# Una conexión quieta más de este tiempo se revisa antes de usarla (el pooler pudo cortarla)
_SEGUNDOS_SIN_REVISAR = 60

_pool: ConnectionPool | None = None


def _revisar_si_estuvo_quieta(conexion: psycopg.Connection) -> None:
    ultimo_uso = getattr(conexion, "_dtulia_ultimo_uso", 0.0)
    if time.monotonic() - ultimo_uso > _SEGUNDOS_SIN_REVISAR:
        ConnectionPool.check_connection(conexion)  # descarta conexiones cortadas por Supabase


def _marcar_uso(conexion: psycopg.Connection) -> None:
    conexion._dtulia_ultimo_uso = time.monotonic()


def abrir_pool() -> None:
    global _pool
    config = obtener_configuracion()
    _pool = ConnectionPool(
        conninfo=config.database_url,
        min_size=config.db_pool_min,
        max_size=config.db_pool_max,
        name="dtulia",
        open=False,
        check=_revisar_si_estuvo_quieta,
        reset=_marcar_uso,
        kwargs={
            "row_factory": dict_row,
            # Sin sentencias preparadas: compatible con el Transaction pooler (puerto 6543)
            "prepare_threshold": None,
            "autocommit": True,
        },
    )
    _pool.open(wait=False)


def cerrar_pool() -> None:
    if _pool is not None:
        _pool.close()


@contextmanager
def obtener_conexion() -> Iterator[psycopg.Connection]:
    """Entrega una conexión del pool en autocommit: cada sentencia es su propia transacción."""
    if _pool is None:
        raise RuntimeError("El pool de conexiones no está inicializado")
    with _pool.connection() as conexion:
        yield conexion
