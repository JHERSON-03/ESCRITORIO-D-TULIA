from pydantic import BaseModel

from app.modelos.comun import DecimalJson


class SedeOut(BaseModel):
    id: int
    nombre: str
    direccion: str


class TipoProductoOut(BaseModel):
    id: int
    nombre: str
    origen: str | None


class TipoComprobanteOut(BaseModel):
    id: int
    nombre: str
    serie: str
    requiere_ruc: bool
    tasa_igv: DecimalJson


class MetodoPagoOut(BaseModel):
    id: int
    nombre: str
