from typing import Any

from app.core.database import obtener_conexion

_COLUMNAS_CLIENTE = """
    cliente_id                  as id,
    cliente_tipo_documento      as tipo_documento,
    cliente_dni_ruc             as dni_ruc,
    cliente_nombre_razon_social as nombre_razon_social
"""


class ClienteRepositorio:
    def buscar_por_documento(self, dni_ruc: str) -> dict[str, Any] | None:
        with obtener_conexion() as conn:
            return conn.execute(
                f"select {_COLUMNAS_CLIENTE} from public.fn_cliente_buscar(%s)", (dni_ruc,)
            ).fetchone()

    def registrar(self, tipo_documento: str, dni_ruc: str, nombre_razon_social: str) -> dict[str, Any]:
        with obtener_conexion() as conn:
            return conn.execute(
                f"select {_COLUMNAS_CLIENTE} from public.fn_cliente_registrar(%s, %s, %s)",
                (tipo_documento, dni_ruc, nombre_razon_social),
            ).fetchone()
