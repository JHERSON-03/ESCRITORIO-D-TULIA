"""Reglas de negocio de D'Tulia compartidas por los servicios."""

from decimal import ROUND_HALF_UP, Decimal

# ADMINISTRADOR: las administradoras del almacén y de la fábrica (acceso a todo).
# ALMACENERO: saca las cajas; en el sistema solo consulta inventario y Kardex.
ROL_ADMINISTRADOR = "ADMINISTRADOR"
ROL_ALMACENERO = "ALMACENERO"

CANALES_VENTA = ("TIENDA", "DELIVERY", "WHATSAPP", "REDES SOCIALES")

_TASA_IGV = Decimal("0.18")
_CENTIMO = Decimal("0.01")


def requiere_ruc(tipo_comprobante: str) -> bool:
    """La factura solo puede emitirse a clientes con RUC."""
    return tipo_comprobante.strip().upper().startswith("FACTURA")


def tasa_igv(tipo_comprobante: str) -> Decimal:
    """Boleta y factura están afectas al IGV; la nota de venta no es comprobante tributario."""
    return Decimal("0") if "NOTA" in tipo_comprobante.upper() else _TASA_IGV


def desglosar_igv(total: Decimal, tasa: Decimal) -> tuple[Decimal, Decimal]:
    """Los precios incluyen IGV: devuelve (valor de venta, IGV)."""
    subtotal = (total / (1 + tasa)).quantize(_CENTIMO, rounding=ROUND_HALF_UP)
    return subtotal, total - subtotal
