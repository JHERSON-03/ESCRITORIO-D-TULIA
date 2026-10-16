from fastapi import APIRouter, Depends, Path, status

from app.api.deps import requerir_roles, servicio_clientes
from app.modelos.cliente import ClienteCrear, ClienteOut
from app.negocio.cliente_servicio import ClienteServicio
from app.negocio.reglas import ROL_ADMINISTRADOR

# Los clientes solo se usan al emitir comprobantes
router = APIRouter(prefix="/clientes", tags=["Clientes"], dependencies=[Depends(requerir_roles(ROL_ADMINISTRADOR))])


@router.get("/{dni_ruc}", response_model=ClienteOut)
def buscar_cliente(
    dni_ruc: str = Path(pattern=r"^[A-Za-z0-9]{8,12}$"),
    servicio: ClienteServicio = Depends(servicio_clientes),
) -> ClienteOut:
    return servicio.buscar(dni_ruc)


@router.post("", response_model=ClienteOut, status_code=status.HTTP_201_CREATED)
def registrar_cliente(datos: ClienteCrear, servicio: ClienteServicio = Depends(servicio_clientes)) -> ClienteOut:
    """Crea el cliente o actualiza su nombre si el documento ya existe."""
    return servicio.registrar(datos)
