from decimal import Decimal
from typing import Any

from app.core.database import obtener_conexion

# Columnas de fn_productos_listar → nombres de la entidad ProductoOut
_SELECT_PRODUCTOS = """
    select producto_id           as id,
           producto_nombre       as nombre,
           producto_precio       as precio,
           producto_stock_actual as stock,
           tipo_producto_id,
           tipo_producto_nombre  as tipo_producto,
           tipo_producto_origen  as origen,
           stock_bajo
      from public.fn_productos_listar(%(buscar)s, %(tipo_id)s, %(stock_minimo)s)
"""


class ProductoRepositorio:
    def listar(self, buscar: str | None, tipo_producto_id: int | None, stock_minimo: int) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                _SELECT_PRODUCTOS,
                {"buscar": buscar, "tipo_id": tipo_producto_id, "stock_minimo": stock_minimo},
            ).fetchall()

    def obtener(self, producto_id: int, stock_minimo: int) -> dict[str, Any] | None:
        with obtener_conexion() as conn:
            return conn.execute(
                _SELECT_PRODUCTOS + " where producto_id = %(id)s",
                {"buscar": None, "tipo_id": None, "stock_minimo": stock_minimo, "id": producto_id},
            ).fetchone()

    def crear(self, tipo_producto_id: int, nombre: str, precio: Decimal) -> int:
        with obtener_conexion() as conn:
            return conn.execute(
                "select producto_id from public.fn_producto_crear(%s, %s, %s)",
                (tipo_producto_id, nombre, precio),
            ).fetchone()["producto_id"]

    def actualizar(self, producto_id: int, tipo_producto_id: int | None, nombre: str | None,
                   precio: Decimal | None) -> None:
        with obtener_conexion() as conn:
            conn.execute(
                "select 1 from public.fn_producto_actualizar(%s, %s, %s, %s)",
                (producto_id, tipo_producto_id, nombre, precio),
            )

    def eliminar(self, producto_id: int) -> None:
        with obtener_conexion() as conn:
            conn.execute("select public.fn_producto_eliminar(%s)", (producto_id,))
