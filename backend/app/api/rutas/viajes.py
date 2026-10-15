from fastapi import APIRouter, Depends, Query, status

from app.api.deps import requerir_roles, servicio_viajes
from app.modelos.auth import EmpleadoToken
from app.modelos.viaje import EnvioCrear, LiquidacionCrear, ViajeDetalleOut, ViajeResumenOut
from app.negocio.reglas import ROL_ADMINISTRADOR, ROL_ALMACENERO
from app.negocio.viaje_servicio import ViajeServicio

router = APIRouter(prefix="/viajes", tags=["Viajes"])

gestiona_inventario = requerir_roles(ROL_ADMINISTRADOR, ROL_ALMACENERO)


@router.get("", response_model=list[ViajeResumenOut], dependencies=[Depends(gestiona_inventario)])
def listar_viajes(
    estado: str | None = Query(default=None, description="ABIERTO o LIQUIDADO (vacío = todos)"),
    servicio: ViajeServicio = Depends(servicio_viajes),
) -> list[ViajeResumenOut]:
    """Viajes del más reciente al más antiguo, con lo llevado, vendido, regresado y la diferencia."""
    return servicio.listar(estado)


@router.get("/{viaje_id}", response_model=ViajeDetalleOut, dependencies=[Depends(gestiona_inventario)])
def detalle_viaje(viaje_id: int, servicio: ViajeServicio = Depends(servicio_viajes)) -> ViajeDetalleOut:
    return servicio.detalle(viaje_id)


@router.post(
    "",
    response_model=ViajeDetalleOut,
    status_code=status.HTTP_201_CREATED,
    responses={409: {"description": "Ya hay un viaje abierto o falta stock en el almacén"}},
)
def abrir_viaje(
    datos: EnvioCrear,
    empleado: EmpleadoToken = Depends(gestiona_inventario),
    servicio: ViajeServicio = Depends(servicio_viajes),
) -> ViajeDetalleOut:
    """Carga inicial: la mercadería sale del almacén (SALIDA_VIAJE). Solo puede haber un viaje abierto."""
    return servicio.abrir(datos, empleado)


@router.post("/{viaje_id}/envios", response_model=ViajeDetalleOut, status_code=status.HTTP_201_CREATED)
def enviar_al_viaje(
    viaje_id: int,
    datos: EnvioCrear,
    empleado: EmpleadoToken = Depends(gestiona_inventario),
    servicio: ViajeServicio = Depends(servicio_viajes),
) -> ViajeDetalleOut:
    """Cajas o unidades que el jefe pide durante el viaje."""
    return servicio.enviar(viaje_id, datos, empleado)


@router.post(
    "/envios/{envio_id}/anular",
    response_model=ViajeDetalleOut,
    dependencies=[Depends(requerir_roles(ROL_ADMINISTRADOR))],
)
def anular_envio(envio_id: int, servicio: ViajeServicio = Depends(servicio_viajes)) -> ViajeDetalleOut:
    """Devuelve al almacén un envío registrado por error (RETORNO_VIAJE). Solo con el viaje abierto."""
    return servicio.anular_envio(envio_id)


@router.post("/{viaje_id}/liquidar", response_model=ViajeDetalleOut)
def liquidar_viaje(
    viaje_id: int,
    datos: LiquidacionCrear,
    empleado: EmpleadoToken = Depends(gestiona_inventario),
    servicio: ViajeServicio = Depends(servicio_viajes),
) -> ViajeDetalleOut:
    """Cierra el viaje con lo vendido por ciudad, lo que regresó (RETORNO_VIAJE) y la merma.
    Si lo declarado no cuadra con lo llevado, exige una observación."""
    return servicio.liquidar(viaje_id, datos, empleado)
