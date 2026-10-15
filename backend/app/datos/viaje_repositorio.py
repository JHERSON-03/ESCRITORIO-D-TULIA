"""Acceso a las funciones de viajes (database/03_viajes.sql)."""

from typing import Any

from psycopg.types.json import Jsonb

from app.core.database import obtener_conexion

_SELECT_RESUMEN = """
    select viaje_id                as id,
           viaje_fecha_salida      as fecha_salida,
           viaje_estado            as estado,
           viaje_fecha_liquidacion as fecha_liquidacion,
           dias, ciudades, envios, llevado, vendido, devuelto, merma, diferencia,
           viaje_observacion       as observacion,
           registrado_por
      from public.fn_viajes_listar(%s)
"""


class ViajeRepositorio:
    def listar(self, estado: str | None) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(_SELECT_RESUMEN, (estado,)).fetchall()

    def detalle(self, viaje_id: int) -> dict[str, Any] | None:
        """Resumen, productos, envíos y ventas por ciudad en una sola conexión."""
        with obtener_conexion() as conn:
            resumen = conn.execute(_SELECT_RESUMEN + " where viaje_id = %s", (None, viaje_id)).fetchone()
            if resumen is None:
                return None
            productos = conn.execute(
                """
                select producto_id, producto_nombre as nombre, presentacion_nombre as presentacion,
                       presentacion_unidades_caja as unidades_caja, tipo_producto_nombre as tipo_producto,
                       llevado, vendido, devuelto, merma, motivo_merma, diferencia
                  from public.fn_viaje_productos(%s)
                """,
                (viaje_id,),
            ).fetchall()
            envios = conn.execute("select * from public.fn_viaje_envios(%s)", (viaje_id,)).fetchall()
            ventas = conn.execute(
                "select sede_id, sede_nombre as sede, producto_id, cantidad from public.fn_viaje_ventas(%s)",
                (viaje_id,),
            ).fetchall()
        return {"viaje": resumen, "productos": productos, "envios": envios, "ventas": ventas}

    def abrir(self, empleado_id: int, sede_id: int, items: list[dict[str, int]], referencia: str | None) -> int:
        with obtener_conexion() as conn:
            return conn.execute(
                "select viaje_id from public.fn_viaje_abrir(%s, %s, %s, %s)",
                (empleado_id, sede_id, Jsonb(items), referencia),
            ).fetchone()["viaje_id"]

    def enviar(self, viaje_id: int, empleado_id: int, sede_id: int, items: list[dict[str, int]],
               referencia: str | None) -> None:
        with obtener_conexion() as conn:
            conn.execute(
                "select 1 from public.fn_viaje_enviar(%s, %s, %s, %s, %s)",
                (viaje_id, empleado_id, sede_id, Jsonb(items), referencia),
            )

    def anular_envio(self, envio_id: int) -> int:
        """Devuelve el viaje al que pertenece el envío."""
        with obtener_conexion() as conn:
            return conn.execute(
                "select envio_viaje_id from public.fn_viaje_anular_envio(%s)", (envio_id,)
            ).fetchone()["envio_viaje_id"]

    def liquidar(self, viaje_id: int, empleado_id: int, ventas: list[dict[str, Any]],
                 retornos: list[dict[str, Any]], observacion: str | None) -> None:
        with obtener_conexion() as conn:
            conn.execute(
                "select 1 from public.fn_viaje_liquidar(%s, %s, %s, %s, %s)",
                (viaje_id, empleado_id, Jsonb(ventas), Jsonb(retornos), observacion),
            )
