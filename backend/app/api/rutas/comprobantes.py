from fastapi import APIRouter, Depends, status

from app.api.deps import requerir_roles, servicio_ventas
from app.modelos.auth import EmpleadoToken
from app.modelos.comprobante import ComprobanteCrear, ComprobanteOut
from app.negocio.reglas import ROL_ADMINISTRADOR
from app.negocio.venta_servicio import VentaServicio

router = APIRouter(prefix="/comprobantes", tags=["Ventas"])


@router.post(
    "",
    response_model=ComprobanteOut,
    status_code=status.HTTP_201_CREATED,
    responses={
        404: {"description": "Producto, cliente, sede o método de pago inexistente"},
        409: {"description": "Stock insuficiente"},
        422: {"description": "Datos inválidos (p. ej. factura sin RUC)"},
    },
)
def registrar_venta(
    solicitud: ComprobanteCrear,
    empleado: EmpleadoToken = Depends(requerir_roles(ROL_ADMINISTRADOR)),
    servicio: VentaServicio = Depends(servicio_ventas),
) -> ComprobanteOut:
    """Emite boleta, factura o nota de venta en el almacén (sede de la sesión). El trigger
    tr_descontar_stock_comprobante descuenta el stock en la misma transacción."""
    return servicio.emitir(solicitud, empleado)
