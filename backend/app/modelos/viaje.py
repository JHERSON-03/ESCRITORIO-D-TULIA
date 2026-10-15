from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ItemViaje(BaseModel):
    """Cantidad siempre en unidades (la aplicación convierte las cajas)."""

    producto_id: int = Field(gt=0)
    cantidad: int = Field(gt=0, le=100_000)


class EnvioCrear(BaseModel):
    """Mercadería que sale del almacén hacia el jefe: la carga inicial o lo que pide durante el viaje."""

    model_config = ConfigDict(str_strip_whitespace=True)

    sede_id: int = Field(gt=0, description="Ciudad a la que va la mercadería")
    referencia: str | None = Field(default=None, max_length=100, description="Agencia, guía o nota")
    items: list[ItemViaje] = Field(min_length=1, max_length=200)


class VentaViaje(BaseModel):
    producto_id: int = Field(gt=0)
    sede_id: int = Field(gt=0, description="Ciudad donde se vendió")
    cantidad: int = Field(ge=0, le=100_000)


class RetornoViaje(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    producto_id: int = Field(gt=0)
    devuelto: int = Field(default=0, ge=0, le=100_000, description="Unidades que regresaron al almacén")
    merma: int = Field(default=0, ge=0, le=100_000, description="Unidades rotas, regaladas o perdidas")
    motivo: str | None = Field(default=None, max_length=100, description="Obligatorio si hay merma")


class LiquidacionCrear(BaseModel):
    """Lo que el jefe anotó en su hoja al regresar."""

    model_config = ConfigDict(str_strip_whitespace=True)

    ventas: list[VentaViaje] = Field(default_factory=list, max_length=2000)
    retornos: list[RetornoViaje] = Field(default_factory=list, max_length=500)
    observacion: str | None = Field(default=None, max_length=300, description="Obligatoria si hay diferencia")


class ViajeResumenOut(BaseModel):
    id: int
    fecha_salida: datetime
    estado: str
    fecha_liquidacion: datetime | None
    dias: int = Field(description="Días fuera: hasta hoy si está abierto, hasta la liquidación si se cerró")
    ciudades: str | None
    envios: int
    llevado: int
    vendido: int
    devuelto: int
    merma: int
    diferencia: int = Field(description="Suma de las diferencias de cada producto (en valor absoluto)")
    observacion: str | None
    registrado_por: str


class ViajeProductoOut(BaseModel):
    producto_id: int
    nombre: str
    presentacion: str
    unidades_caja: int
    tipo_producto: str
    llevado: int
    vendido: int
    devuelto: int
    merma: int
    motivo_merma: str | None
    diferencia: int


class EnvioItemOut(BaseModel):
    producto_id: int
    nombre: str
    presentacion: str
    unidades_caja: int
    cantidad: int


class EnvioOut(BaseModel):
    id: int
    numero: int
    fecha: datetime
    sede_id: int
    sede: str
    referencia: str | None
    anulado: bool
    items: list[EnvioItemOut]


class VentaCiudadOut(BaseModel):
    sede_id: int
    sede: str
    producto_id: int
    cantidad: int


class ViajeDetalleOut(BaseModel):
    viaje: ViajeResumenOut
    productos: list[ViajeProductoOut]
    envios: list[EnvioOut]
    ventas: list[VentaCiudadOut]
