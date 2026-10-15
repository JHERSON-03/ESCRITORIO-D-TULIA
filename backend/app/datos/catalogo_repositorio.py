from typing import Any

from app.core.database import obtener_conexion


class CatalogoRepositorio:
    def sedes(self) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select sede_id as id, sede_nombre as nombre, sede_direccion as direccion
                  from public.sedes
                 order by sede_id
                """
            ).fetchall()

    def obtener_sede(self, sede_id: int) -> dict[str, Any] | None:
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select sede_id as id, sede_nombre as nombre, sede_direccion as direccion
                  from public.sedes
                 where sede_id = %s
                """,
                (sede_id,),
            ).fetchone()

    def tipos_producto(self) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select tipo_producto_id as id, tipo_producto_nombre as nombre, tipo_producto_origen as origen
                  from public.tipo_productos
                 order by tipo_producto_nombre
                """
            ).fetchall()

    def tipos_comprobante(self) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select tipo_comprobante_id as id, tipo_comprobante_nombre as nombre, tipo_comprobante_serie as serie
                  from public.tipo_comprobantes
                 order by tipo_comprobante_id
                """
            ).fetchall()

    def metodos_pago(self) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                "select metodo_pago_id as id, metodo_pago_nombre as nombre from public.metodos_pagos order by metodo_pago_id"
            ).fetchall()
