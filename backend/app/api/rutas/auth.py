from fastapi import APIRouter, Depends

from app.api.deps import servicio_auth
from app.modelos.auth import LoginIn, LoginOut
from app.negocio.auth_servicio import AuthServicio

router = APIRouter(prefix="/auth", tags=["Autenticación"])


@router.post("/login", response_model=LoginOut)
def iniciar_sesion(credenciales: LoginIn, servicio: AuthServicio = Depends(servicio_auth)) -> LoginOut:
    return servicio.iniciar_sesion(credenciales)
