"""Dependencias compartidas: autenticación, autorización y ensamblado de capas."""

from collections.abc import Callable

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.seguridad import decodificar_token
from app.datos.catalogo_repositorio import CatalogoRepositorio
from app.datos.cliente_repositorio import ClienteRepositorio
from app.datos.empleado_repositorio import EmpleadoRepositorio
from app.datos.inventario_repositorio import InventarioRepositorio
from app.datos.producto_repositorio import ProductoRepositorio
from app.datos.venta_repositorio import VentaRepositorio
from app.modelos.auth import EmpleadoToken
from app.negocio.auth_servicio import AuthServicio
from app.negocio.catalogo_servicio import CatalogoServicio
from app.negocio.cliente_servicio import ClienteServicio
from app.negocio.inventario_servicio import InventarioServicio
from app.negocio.producto_servicio import ProductoServicio
from app.negocio.venta_servicio import VentaServicio

_bearer = HTTPBearer(auto_error=False)


def _no_autenticado(detalle: str) -> HTTPException:
    return HTTPException(status.HTTP_401_UNAUTHORIZED, detalle, headers={"WWW-Authenticate": "Bearer"})


def empleado_actual(credenciales: HTTPAuthorizationCredentials | None = Depends(_bearer)) -> EmpleadoToken:
    if credenciales is None:
        raise _no_autenticado("No autenticado")
    try:
        payload = decodificar_token(credenciales.credentials)
    except jwt.ExpiredSignatureError:
        raise _no_autenticado("La sesión expiró, vuelva a iniciar sesión") from None
    except jwt.InvalidTokenError:
        raise _no_autenticado("Token inválido") from None
    return EmpleadoToken(id=int(payload["sub"]), rol=payload["rol"], sede_id=payload["sede_id"])


def requerir_roles(*roles: str) -> Callable[..., EmpleadoToken]:
    def _verificar(empleado: EmpleadoToken = Depends(empleado_actual)) -> EmpleadoToken:
        if empleado.rol not in roles:
            raise HTTPException(status.HTTP_403_FORBIDDEN, "No tiene permisos para esta operación")
        return empleado

    return _verificar


# --- Ensamblado capa de negocio ← capa de datos (sobrescribibles en pruebas) ---
def servicio_auth() -> AuthServicio:
    return AuthServicio(EmpleadoRepositorio(), CatalogoRepositorio())


def servicio_productos() -> ProductoServicio:
    return ProductoServicio(ProductoRepositorio())


def servicio_ventas() -> VentaServicio:
    return VentaServicio(VentaRepositorio())


def servicio_catalogos() -> CatalogoServicio:
    return CatalogoServicio(CatalogoRepositorio())


def servicio_clientes() -> ClienteServicio:
    return ClienteServicio(ClienteRepositorio())


def servicio_inventario() -> InventarioServicio:
    return InventarioServicio(InventarioRepositorio())
