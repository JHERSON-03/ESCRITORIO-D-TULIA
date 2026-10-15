from app.core.errores import CredencialesInvalidas, ErrorNegocio
from app.core.seguridad import crear_token
from app.datos.catalogo_repositorio import CatalogoRepositorio
from app.datos.empleado_repositorio import EmpleadoRepositorio
from app.modelos.auth import EmpleadoSesion, LoginIn, LoginOut


class AuthServicio:
    def __init__(self, empleados: EmpleadoRepositorio, catalogos: CatalogoRepositorio):
        self._empleados = empleados
        self._catalogos = catalogos

    def iniciar_sesion(self, credenciales: LoginIn) -> LoginOut:
        fila = self._empleados.autenticar(credenciales.documento, credenciales.password)
        if fila is None:
            raise CredencialesInvalidas("Documento o contraseña incorrectos")

        # Todos trabajan desde el almacén: ahí se registran las ventas del sistema
        sede = self._catalogos.obtener_almacen()
        if sede is None:
            raise ErrorNegocio("No hay una sede marcada como almacén. Ejecute database/02_datos_iniciales.sql",
                               estado_http=503)

        empleado = EmpleadoSesion(**fila, sede_id=sede["id"], sede=sede["nombre"])
        token, expira_en = crear_token(str(empleado.id), rol=empleado.rol, sede_id=empleado.sede_id)
        return LoginOut(access_token=token, expira_en=expira_en, empleado=empleado)
