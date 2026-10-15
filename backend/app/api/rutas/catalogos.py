from fastapi import APIRouter, Depends

from app.api.deps import empleado_actual, servicio_catalogos
from app.modelos.catalogo import MetodoPagoOut, PresentacionOut, SedeOut, TipoComprobanteOut, TipoProductoOut
from app.negocio.catalogo_servicio import CatalogoServicio

router = APIRouter(prefix="/catalogos", tags=["Catálogos"])

autenticado = [Depends(empleado_actual)]


@router.get("/sedes", response_model=list[SedeOut], dependencies=autenticado)
def sedes(servicio: CatalogoServicio = Depends(servicio_catalogos)) -> list[SedeOut]:
    """El almacén (Trujillo) primero; las demás son ciudades de destino de los viajes."""
    return servicio.sedes()


@router.get("/presentaciones", response_model=list[PresentacionOut], dependencies=autenticado)
def presentaciones(servicio: CatalogoServicio = Depends(servicio_catalogos)) -> list[PresentacionOut]:
    """Tamaños de botella con sus unidades por caja."""
    return servicio.presentaciones()


@router.get("/tipos-producto", response_model=list[TipoProductoOut], dependencies=autenticado)
def tipos_producto(servicio: CatalogoServicio = Depends(servicio_catalogos)) -> list[TipoProductoOut]:
    return servicio.tipos_producto()


@router.get("/tipos-comprobante", response_model=list[TipoComprobanteOut], dependencies=autenticado)
def tipos_comprobante(servicio: CatalogoServicio = Depends(servicio_catalogos)) -> list[TipoComprobanteOut]:
    return servicio.tipos_comprobante()


@router.get("/metodos-pago", response_model=list[MetodoPagoOut], dependencies=autenticado)
def metodos_pago(servicio: CatalogoServicio = Depends(servicio_catalogos)) -> list[MetodoPagoOut]:
    return servicio.metodos_pago()


@router.get("/canales-venta", response_model=list[str], dependencies=autenticado)
def canales_venta(servicio: CatalogoServicio = Depends(servicio_catalogos)) -> list[str]:
    return servicio.canales_venta()
