from app.core.errores import ErrorNegocio, NoEncontrado
from app.datos.inventario_repositorio import InventarioRepositorio
from app.modelos.inventario import MovimientoCrear, MovimientoOut

# Las salidas por venta se revierten anulando el comprobante, no desde el Kardex
_TIPOS_ANULABLES = ("ENTRADA", "MERMA", "AJUSTE")


class InventarioServicio:
    def __init__(self, repositorio: InventarioRepositorio):
        self._repo = repositorio

    def registrar_movimiento(self, datos: MovimientoCrear) -> MovimientoOut:
        referencia = datos.referencia or None

        if datos.tipo == "ENTRADA":
            fila = self._repo.registrar_entrada(datos.producto_id, self._cantidad(datos), referencia)
        elif datos.tipo == "MERMA":
            if not referencia:
                raise ErrorNegocio("Indique el motivo de la merma en 'referencia' (rotura, vencimiento…)")
            fila = self._repo.registrar_merma(datos.producto_id, self._cantidad(datos), referencia)
        else:  # AJUSTE: el conteo físico reemplaza al stock del sistema
            if datos.stock_real is None:
                raise ErrorNegocio("El ajuste requiere 'stock_real' (cantidad contada físicamente)")
            fila = self._repo.ajustar_stock(datos.producto_id, datos.stock_real,
                                            referencia or "AJUSTE POR INVENTARIO")
        return MovimientoOut.model_validate(fila)

    def anular_movimiento(self, movimiento_id: int) -> MovimientoOut:
        tipo = self._repo.obtener_tipo_movimiento(movimiento_id)
        if tipo is None:
            raise NoEncontrado(f"El movimiento {movimiento_id} no existe")
        if tipo not in _TIPOS_ANULABLES:
            raise ErrorNegocio(f"Un movimiento {tipo} no puede anularse desde el Kardex", estado_http=409)
        return MovimientoOut.model_validate(self._repo.anular_movimiento(movimiento_id))

    def kardex(self, producto_id: int, limite: int) -> list[MovimientoOut]:
        return [MovimientoOut.model_validate(f) for f in self._repo.kardex(producto_id, limite)]

    @staticmethod
    def _cantidad(datos: MovimientoCrear) -> int:
        if datos.cantidad is None:
            raise ErrorNegocio(f"Indique la cantidad de la {datos.tipo.lower()}")
        return datos.cantidad
