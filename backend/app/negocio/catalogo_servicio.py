from app.datos.catalogo_repositorio import CatalogoRepositorio
from app.modelos.catalogo import MetodoPagoOut, PresentacionOut, SedeOut, TipoComprobanteOut, TipoProductoOut
from app.negocio.reglas import CANALES_VENTA, requiere_ruc, tasa_igv


class CatalogoServicio:
    def __init__(self, repositorio: CatalogoRepositorio):
        self._repo = repositorio

    def sedes(self) -> list[SedeOut]:
        return [SedeOut.model_validate(f) for f in self._repo.sedes()]

    def presentaciones(self) -> list[PresentacionOut]:
        return [PresentacionOut.model_validate(f) for f in self._repo.presentaciones()]

    def tipos_producto(self) -> list[TipoProductoOut]:
        return [TipoProductoOut.model_validate(f) for f in self._repo.tipos_producto()]

    def tipos_comprobante(self) -> list[TipoComprobanteOut]:
        return [
            TipoComprobanteOut(**f, requiere_ruc=requiere_ruc(f["nombre"]), tasa_igv=tasa_igv(f["nombre"]))
            for f in self._repo.tipos_comprobante()
        ]

    def metodos_pago(self) -> list[MetodoPagoOut]:
        return [MetodoPagoOut.model_validate(f) for f in self._repo.metodos_pago()]

    @staticmethod
    def canales_venta() -> list[str]:
        return list(CANALES_VENTA)
