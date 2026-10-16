from fastapi import APIRouter, Depends, Query, status

from app.api.deps import requerir_roles, servicio_producciones
from app.modelos.auth import EmpleadoToken
from app.modelos.produccion import (
    AnulacionProduccion,
    ProduccionCrear,
    ProduccionDetalleOut,
    ProduccionResumenOut,
    RecepcionCrear,
)
from app.negocio.produccion_servicio import ProduccionServicio
from app.negocio.reglas import ROL_ADMINISTRADOR

router = APIRouter(prefix="/producciones", tags=["Producción"])

solo_admin = requerir_roles(ROL_ADMINISTRADOR)


@router.get("", response_model=list[ProduccionResumenOut], dependencies=[Depends(solo_admin)])
def listar_producciones(
    estado: str | None = Query(default=None, description="EN_CAMINO, RECIBIDO o ANULADO (vacío = todos)"),
    servicio: ProduccionServicio = Depends(servicio_producciones),
) -> list[ProduccionResumenOut]:
    """Envíos de la fábrica al almacén, del más reciente al más antiguo."""
    return servicio.listar(estado)


@router.get("/{produccion_id}", response_model=ProduccionDetalleOut, dependencies=[Depends(solo_admin)])
def detalle_produccion(
    produccion_id: int, servicio: ProduccionServicio = Depends(servicio_producciones)
) -> ProduccionDetalleOut:
    return servicio.detalle(produccion_id)


@router.post("", response_model=ProduccionDetalleOut, status_code=status.HTTP_201_CREATED)
def registrar_produccion(
    datos: ProduccionCrear,
    empleado: EmpleadoToken = Depends(solo_admin),
    servicio: ProduccionServicio = Depends(servicio_producciones),
) -> ProduccionDetalleOut:
    """La fábrica registra lo que manda. El stock no cambia hasta que el almacén lo recibe."""
    return servicio.registrar(datos, empleado)


@router.post("/{produccion_id}/recibir", response_model=ProduccionDetalleOut)
def recibir_produccion(
    produccion_id: int,
    datos: RecepcionCrear,
    empleado: EmpleadoToken = Depends(solo_admin),
    servicio: ProduccionServicio = Depends(servicio_producciones),
) -> ProduccionDetalleOut:
    """El almacén confirma lo que llegó (ENTRADA_PRODUCCION). Si no coincide, exige observación."""
    return servicio.recibir(produccion_id, datos, empleado)


@router.post("/{produccion_id}/anular", response_model=ProduccionDetalleOut)
def anular_produccion(
    produccion_id: int,
    datos: AnulacionProduccion,
    empleado: EmpleadoToken = Depends(solo_admin),
    servicio: ProduccionServicio = Depends(servicio_producciones),
) -> ProduccionDetalleOut:
    """Envío registrado por error (solo mientras está en camino). No mueve stock."""
    return servicio.anular(produccion_id, datos, empleado)
