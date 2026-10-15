from app.core.errores import ErrorNegocio, NoEncontrado
from app.datos.viaje_repositorio import ViajeRepositorio
from app.modelos.auth import EmpleadoToken
from app.modelos.viaje import (
    EnvioCrear,
    EnvioItemOut,
    EnvioOut,
    LiquidacionCrear,
    ViajeDetalleOut,
    ViajeResumenOut,
)

ESTADOS_VIAJE = ("ABIERTO", "LIQUIDADO")


class ViajeServicio:
    """Mercadería que lleva el jefe: sale del almacén, se vende en otras ciudades y se liquida al volver.
    Stock, numeración y cuadre los resuelve PostgreSQL de forma atómica."""

    def __init__(self, repositorio: ViajeRepositorio):
        self._repo = repositorio

    def listar(self, estado: str | None) -> list[ViajeResumenOut]:
        estado = (estado or "").strip().upper() or None
        if estado is not None and estado not in ESTADOS_VIAJE:
            raise ErrorNegocio(f"Estado no válido. Use: {', '.join(ESTADOS_VIAJE)}")
        return [ViajeResumenOut.model_validate(f) for f in self._repo.listar(estado)]

    def detalle(self, viaje_id: int) -> ViajeDetalleOut:
        datos = self._repo.detalle(viaje_id)
        if datos is None:
            raise NoEncontrado(f"El viaje {viaje_id} no existe")
        return ViajeDetalleOut(
            viaje=datos["viaje"],
            productos=datos["productos"],
            envios=self._agrupar_envios(datos["envios"]),
            ventas=datos["ventas"],
        )

    def abrir(self, datos: EnvioCrear, empleado: EmpleadoToken) -> ViajeDetalleOut:
        viaje_id = self._repo.abrir(empleado.id, datos.sede_id, self._items(datos), datos.referencia or None)
        return self.detalle(viaje_id)

    def enviar(self, viaje_id: int, datos: EnvioCrear, empleado: EmpleadoToken) -> ViajeDetalleOut:
        self._repo.enviar(viaje_id, empleado.id, datos.sede_id, self._items(datos), datos.referencia or None)
        return self.detalle(viaje_id)

    def anular_envio(self, envio_id: int) -> ViajeDetalleOut:
        return self.detalle(self._repo.anular_envio(envio_id))

    def liquidar(self, viaje_id: int, datos: LiquidacionCrear, empleado: EmpleadoToken) -> ViajeDetalleOut:
        self._repo.liquidar(
            viaje_id,
            empleado.id,
            [v.model_dump() for v in datos.ventas],
            [r.model_dump() for r in datos.retornos],
            datos.observacion or None,
        )
        return self.detalle(viaje_id)

    @staticmethod
    def _items(datos: EnvioCrear) -> list[dict[str, int]]:
        return [item.model_dump() for item in datos.items]

    @staticmethod
    def _agrupar_envios(filas: list[dict]) -> list[EnvioOut]:
        """fn_viaje_envios devuelve una fila por producto enviado; se agrupan por envío."""
        envios: dict[int, EnvioOut] = {}
        for f in filas:
            envio = envios.get(f["envio_id"])
            if envio is None:
                envio = envios[f["envio_id"]] = EnvioOut(
                    id=f["envio_id"], numero=f["envio_numero"], fecha=f["envio_fecha"], sede_id=f["sede_id"],
                    sede=f["sede_nombre"], referencia=f["envio_referencia"], anulado=f["envio_anulado"], items=[],
                )
            envio.items.append(EnvioItemOut(
                producto_id=f["producto_id"], nombre=f["producto_nombre"], presentacion=f["presentacion_nombre"],
                unidades_caja=f["presentacion_unidades_caja"], cantidad=f["cantidad"],
            ))
        return list(envios.values())
