from collections import Counter

from app.core.errores import ErrorNegocio
from app.datos.venta_repositorio import VentaRepositorio
from app.modelos.auth import EmpleadoToken
from app.modelos.comprobante import ComprobanteCrear, ComprobanteOut, ItemVenta
from app.negocio.reglas import CANALES_VENTA, desglosar_igv, tasa_igv


class VentaServicio:
    def __init__(self, repositorio: VentaRepositorio):
        self._repo = repositorio

    def emitir(self, solicitud: ComprobanteCrear, empleado: EmpleadoToken) -> ComprobanteOut:
        """Registra la venta en la sede de la sesión. Stock, correlativo y la regla
        de factura con RUC los resuelve PostgreSQL de forma atómica."""
        canal = solicitud.canal.upper()
        if canal not in CANALES_VENTA:
            raise ErrorNegocio(f"Canal de venta no válido. Use: {', '.join(CANALES_VENTA)}")

        fila = self._repo.registrar(
            empleado_id=empleado.id,
            sede_id=empleado.sede_id,
            tipo_comprobante_id=solicitud.tipo_comprobante_id,
            metodo_pago_id=solicitud.metodo_pago_id,
            items=self._consolidar(solicitud.items),
            cliente_id=solicitud.cliente_id,
            canal=canal,
        )
        subtotal, igv = desglosar_igv(fila["total"], tasa_igv(fila["tipo_comprobante"]))
        return ComprobanteOut(**fila, subtotal=subtotal, igv=igv)

    @staticmethod
    def _consolidar(items: list[ItemVenta]) -> list[dict[str, int]]:
        """Suma las cantidades si el mismo producto llega en varias líneas."""
        cantidades: Counter[int] = Counter()
        for item in items:
            cantidades[item.producto_id] += item.cantidad
        return [{"producto_id": pid, "cantidad": cant} for pid, cant in cantidades.items()]
