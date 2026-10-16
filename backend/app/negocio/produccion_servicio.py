from app.core.errores import ErrorNegocio, NoEncontrado
from app.datos.produccion_repositorio import ProduccionRepositorio
from app.modelos.auth import EmpleadoToken
from app.modelos.produccion import (
    AnulacionProduccion,
    ProduccionCrear,
    ProduccionDetalleOut,
    ProduccionResumenOut,
    RecepcionCrear,
)

ESTADOS_PRODUCCION = ("EN_CAMINO", "RECIBIDO", "ANULADO")


class ProduccionServicio:
    """Cajas que la fábrica manda al almacén: la fábrica registra el envío y el almacén confirma
    lo que llegó. El stock sube recién al recibir (ENTRADA_PRODUCCION)."""

    def __init__(self, repositorio: ProduccionRepositorio):
        self._repo = repositorio

    def listar(self, estado: str | None) -> list[ProduccionResumenOut]:
        estado = (estado or "").strip().upper().replace(" ", "_") or None
        if estado is not None and estado not in ESTADOS_PRODUCCION:
            raise ErrorNegocio(f"Estado no válido. Use: {', '.join(ESTADOS_PRODUCCION)}")
        return [ProduccionResumenOut.model_validate(f) for f in self._repo.listar(estado)]

    def detalle(self, produccion_id: int) -> ProduccionDetalleOut:
        datos = self._repo.detalle(produccion_id)
        if datos is None:
            raise NoEncontrado(f"El envío de producción {produccion_id} no existe")
        return ProduccionDetalleOut.model_validate(datos)

    def registrar(self, datos: ProduccionCrear, empleado: EmpleadoToken) -> ProduccionDetalleOut:
        items = [i.model_dump() for i in datos.items if i.cantidad > 0]
        if not items:
            raise ErrorNegocio("El envío de producción debe incluir al menos un producto con cantidad mayor a 0")
        produccion_id = self._repo.registrar(empleado.id, items, datos.referencia or None)
        return self.detalle(produccion_id)

    def recibir(self, produccion_id: int, datos: RecepcionCrear, empleado: EmpleadoToken) -> ProduccionDetalleOut:
        self._repo.recibir(produccion_id, empleado.id, [i.model_dump() for i in datos.items],
                           datos.observacion or None)
        return self.detalle(produccion_id)

    def anular(self, produccion_id: int, datos: AnulacionProduccion, empleado: EmpleadoToken) -> ProduccionDetalleOut:
        self._repo.anular(produccion_id, empleado.id, datos.observacion or None)
        return self.detalle(produccion_id)
