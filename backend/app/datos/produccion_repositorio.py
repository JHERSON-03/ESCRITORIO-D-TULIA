"""Acceso a las funciones de envíos de producción (database/04_produccion.sql)."""

from typing import Any

from psycopg.types.json import Jsonb

from app.core.database import obtener_conexion

_SELECT_RESUMEN = """
    select produccion_id           as id,
           produccion_fecha_envio  as fecha_envio,
           produccion_estado       as estado,
           produccion_fecha_cierre as fecha_cierre,
           dias, enviado_por, cerrado_por,
           produccion_referencia   as referencia,
           productos, enviado, recibido, diferencia,
           produccion_observacion  as observacion
      from public.fn_producciones_listar(%s)
"""


class ProduccionRepositorio:
    def listar(self, estado: str | None) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(_SELECT_RESUMEN, (estado,)).fetchall()

    def detalle(self, produccion_id: int) -> dict[str, Any] | None:
        with obtener_conexion() as conn:
            resumen = conn.execute(_SELECT_RESUMEN + " where produccion_id = %s", (None, produccion_id)).fetchone()
            if resumen is None:
                return None
            productos = conn.execute(
                """
                select producto_id, producto_nombre as nombre, presentacion_nombre as presentacion,
                       presentacion_unidades_caja as unidades_caja, tipo_producto_nombre as tipo_producto,
                       enviado, recibido, diferencia
                  from public.fn_produccion_productos(%s)
                """,
                (produccion_id,),
            ).fetchall()
        return {"produccion": resumen, "productos": productos}

    def registrar(self, empleado_id: int, items: list[dict[str, int]], referencia: str | None) -> int:
        with obtener_conexion() as conn:
            return conn.execute(
                "select produccion_id from public.fn_produccion_registrar(%s, %s, %s)",
                (empleado_id, Jsonb(items), referencia),
            ).fetchone()["produccion_id"]

    def recibir(self, produccion_id: int, empleado_id: int, items: list[dict[str, int]],
                observacion: str | None) -> None:
        with obtener_conexion() as conn:
            conn.execute(
                "select 1 from public.fn_produccion_recibir(%s, %s, %s, %s)",
                (produccion_id, empleado_id, Jsonb(items), observacion),
            )

    def anular(self, produccion_id: int, empleado_id: int, observacion: str | None) -> None:
        with obtener_conexion() as conn:
            conn.execute(
                "select 1 from public.fn_produccion_anular(%s, %s, %s)", (produccion_id, empleado_id, observacion)
            )
