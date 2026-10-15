import re

from app.core.errores import ErrorNegocio, NoEncontrado
from app.datos.cliente_repositorio import ClienteRepositorio
from app.modelos.cliente import ClienteCrear, ClienteOut

_FORMATO_DOCUMENTO = {
    "DNI": (re.compile(r"^\d{8}$"), "El DNI debe tener 8 dígitos"),
    "RUC": (re.compile(r"^(10|15|17|20)\d{9}$"), "El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20"),
    "CE": (re.compile(r"^[A-Za-z0-9]{9,12}$"), "El carné de extranjería debe tener entre 9 y 12 caracteres"),
}


class ClienteServicio:
    def __init__(self, repositorio: ClienteRepositorio):
        self._repo = repositorio

    def buscar(self, dni_ruc: str) -> ClienteOut:
        fila = self._repo.buscar_por_documento(dni_ruc.strip())
        if fila is None:
            raise NoEncontrado(f"No hay un cliente registrado con el documento {dni_ruc}")
        return ClienteOut.model_validate(fila)

    def registrar(self, datos: ClienteCrear) -> ClienteOut:
        patron, mensaje = _FORMATO_DOCUMENTO[datos.tipo_documento]
        if not patron.match(datos.dni_ruc):
            raise ErrorNegocio(mensaje)
        fila = self._repo.registrar(datos.tipo_documento, datos.dni_ruc, datos.nombre_razon_social)
        return ClienteOut.model_validate(fila)
