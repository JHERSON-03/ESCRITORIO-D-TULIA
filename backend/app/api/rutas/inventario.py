from fastapi import APIRouter, Depends, Query, status

from app.api.deps import empleado_actual, requerir_roles, servicio_inventario
from app.modelos.inventario import MovimientoCrear, MovimientoOut
from app.negocio.inventario_servicio import InventarioServicio
from app.negocio.reglas import ROL_ADMINISTRADOR

router = APIRouter(prefix="/inventario", tags=["Inventario / Kardex"])


@router.post(
    "/movimientos",
    response_model=MovimientoOut,
    status_code=status.HTTP_201_CREATED,
    dependencies=[Depends(requerir_roles(ROL_ADMINISTRADOR))],
)
def registrar_movimiento(
    datos: MovimientoCrear, servicio: InventarioServicio = Depends(servicio_inventario)
) -> MovimientoOut:
    """ENTRADA (fn_registrar_entrada), MERMA (fn_registrar_salida) o AJUSTE al conteo físico (fn_ajustar_stock)."""
    return servicio.registrar_movimiento(datos)


@router.post(
    "/movimientos/{movimiento_id}/anular",
    response_model=MovimientoOut,
    dependencies=[Depends(requerir_roles(ROL_ADMINISTRADOR))],
)
def anular_movimiento(
    movimiento_id: int, servicio: InventarioServicio = Depends(servicio_inventario)
) -> MovimientoOut:
    """Revierte un movimiento con fn_anular_movimiento (queda registrado como ANULACION)."""
    return servicio.anular_movimiento(movimiento_id)


@router.get("/kardex/{producto_id}", response_model=list[MovimientoOut], dependencies=[Depends(empleado_actual)])
def kardex(
    producto_id: int,
    limite: int = Query(default=100, ge=1, le=1000),
    servicio: InventarioServicio = Depends(servicio_inventario),
) -> list[MovimientoOut]:
    """Movimientos del producto, del más reciente al más antiguo."""
    return servicio.kardex(producto_id, limite)
