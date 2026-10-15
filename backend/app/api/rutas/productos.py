from fastapi import APIRouter, Depends, Query, Response, status

from app.api.deps import empleado_actual, requerir_roles, servicio_productos
from app.modelos.producto import ProductoActualizar, ProductoCrear, ProductoOut
from app.negocio.producto_servicio import ProductoServicio
from app.negocio.reglas import ROL_ADMINISTRADOR

router = APIRouter(prefix="/productos", tags=["Productos"])

solo_admin = Depends(requerir_roles(ROL_ADMINISTRADOR))


@router.get("", response_model=list[ProductoOut], dependencies=[Depends(empleado_actual)])
def listar_productos(
    buscar: str | None = Query(default=None, max_length=60, description="Texto contenido en el nombre"),
    tipo_producto_id: int | None = Query(default=None, gt=0),
    servicio: ProductoServicio = Depends(servicio_productos),
) -> list[ProductoOut]:
    """Catálogo de licores con el stock actual leído directamente de la base de datos."""
    return servicio.listar(buscar, tipo_producto_id)


@router.get("/{producto_id}", response_model=ProductoOut, dependencies=[Depends(empleado_actual)])
def obtener_producto(producto_id: int, servicio: ProductoServicio = Depends(servicio_productos)) -> ProductoOut:
    return servicio.obtener(producto_id)


@router.post("", response_model=ProductoOut, status_code=status.HTTP_201_CREATED, dependencies=[solo_admin])
def crear_producto(datos: ProductoCrear, servicio: ProductoServicio = Depends(servicio_productos)) -> ProductoOut:
    """Crea el producto con stock 0; el stock se carga con POST /inventario/movimientos."""
    return servicio.crear(datos)


@router.patch("/{producto_id}", response_model=ProductoOut, dependencies=[solo_admin])
def actualizar_producto(
    producto_id: int, datos: ProductoActualizar, servicio: ProductoServicio = Depends(servicio_productos)
) -> ProductoOut:
    return servicio.actualizar(producto_id, datos)


@router.delete("/{producto_id}", status_code=status.HTTP_204_NO_CONTENT, dependencies=[solo_admin])
def eliminar_producto(producto_id: int, servicio: ProductoServicio = Depends(servicio_productos)) -> Response:
    """Solo se pueden eliminar productos sin movimientos ni ventas."""
    servicio.eliminar(producto_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
