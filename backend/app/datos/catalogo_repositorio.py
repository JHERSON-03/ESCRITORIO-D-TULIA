from typing import Any

from app.core.database import obtener_conexion


class CatalogoRepositorio:
    def sedes(self) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select sede_id as id, sede_nombre as nombre, sede_direccion as direccion, sede_es_almacen as es_almacen
                  from public.sedes
                 order by sede_es_almacen desc, sede_nombre
                """
            ).fetchall()

    def obtener_almacen(self) -> dict[str, Any] | None:
        """Sede desde la que sale toda la mercadería (Trujillo)."""
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select sede_id as id, sede_nombre as nombre, sede_direccion as direccion, sede_es_almacen as es_almacen
                  from public.sedes
                 where sede_es_almacen
                """
            ).fetchone()

    def presentaciones(self) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select presentacion_id as id, presentacion_nombre as nombre,
                       presentacion_unidades_caja as unidades_caja
                  from public.presentaciones
                 order by presentacion_orden, presentacion_nombre
                """
            ).fetchall()

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
