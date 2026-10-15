"""Acceso a las funciones de inventario ya existentes en la base de datos
(fn_registrar_entrada, fn_registrar_salida, fn_ajustar_stock, fn_anular_movimiento,
fn_listar_movimientos)."""

from typing import Any

from app.core.database import obtener_conexion

_COLUMNAS_MOVIMIENTO = """
    movimiento_id              as id,
    movimiento_producto_id     as producto_id,
    movimiento_tipo            as tipo,
    movimiento_cantidad        as cantidad,
    movimiento_stock_previo    as stock_previo,
    movimiento_stock_posterior as stock_posterior,
    movimiento_referencia      as referencia,
    movimiento_fecha           as fecha
"""


class InventarioRepositorio:
    def registrar_entrada(self, producto_id: int, cantidad: int, referencia: str | None) -> dict[str, Any]:
        return self._movimiento("public.fn_registrar_entrada(%s, %s, %s)", (producto_id, cantidad, referencia))

    def registrar_merma(self, producto_id: int, cantidad: int, referencia: str) -> dict[str, Any]:
        return self._movimiento(
            "public.fn_registrar_salida(%s, %s, %s, 'MERMA')", (producto_id, cantidad, referencia)
        )

    def ajustar_stock(self, producto_id: int, stock_real: int, referencia: str) -> dict[str, Any]:
        return self._movimiento("public.fn_ajustar_stock(%s, %s, %s)", (producto_id, stock_real, referencia))

    def obtener_tipo_movimiento(self, movimiento_id: int) -> str | None:
        with obtener_conexion() as conn:
            fila = conn.execute(
                "select movimiento_tipo from public.movimientos_inventario where movimiento_id = %s",
                (movimiento_id,),
            ).fetchone()
        return fila["movimiento_tipo"] if fila else None

    def anular_movimiento(self, movimiento_id: int) -> dict[str, Any]:
        return self._movimiento("public.fn_anular_movimiento(%s)", (movimiento_id,))

    def kardex(self, producto_id: int, limite: int) -> list[dict[str, Any]]:
        with obtener_conexion() as conn:
            return conn.execute(
                f"""
                select {_COLUMNAS_MOVIMIENTO}
                  from public.fn_listar_movimientos(%s)
                 order by movimiento_fecha desc, movimiento_id desc
                 limit %s
                """,
                (producto_id, limite),
            ).fetchall()

    @staticmethod
    def _movimiento(llamada: str, parametros: tuple[Any, ...]) -> dict[str, Any]:
        with obtener_conexion() as conn:
            return conn.execute(f"select {_COLUMNAS_MOVIMIENTO} from {llamada}", parametros).fetchone()
