from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class ItemProduccion(BaseModel):
    """Cantidad siempre en unidades (la aplicación convierte las cajas)."""

    producto_id: int = Field(gt=0)
    cantidad: int = Field(ge=0, le=100_000)


class ProduccionCrear(BaseModel):
    """Lo que la fábrica manda al almacén (lo anotado en el cuaderno)."""

    model_config = ConfigDict(str_strip_whitespace=True)

    referencia: str | None = Field(default=None, max_length=100, description="Lote, vehículo o nota")
    items: list[ItemProduccion] = Field(min_length=1, max_length=200)


class RecepcionCrear(BaseModel):
    """Lo que realmente llegó al almacén. Un producto del envío que no venga se toma como 0."""

    model_config = ConfigDict(str_strip_whitespace=True)

    items: list[ItemProduccion] = Field(default_factory=list, max_length=200)
    observacion: str | None = Field(default=None, max_length=300, description="Obligatoria si no coincide con lo enviado")


class AnulacionProduccion(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    observacion: str | None = Field(default=None, max_length=300)


class ProduccionResumenOut(BaseModel):
    id: int
    fecha_envio: datetime
    estado: str
    fecha_cierre: datetime | None
    dias: int = Field(description="Días en camino (hasta hoy) o lo que tardó en recibirse")
    enviado_por: str
    cerrado_por: str | None
    referencia: str | None
    productos: int
    enviado: int
    recibido: int | None
    diferencia: int | None
    observacion: str | None


class ProduccionProductoOut(BaseModel):
    producto_id: int
    nombre: str
    presentacion: str
    unidades_caja: int
    tipo_producto: str
    enviado: int
    recibido: int | None
    diferencia: int | None


class ProduccionDetalleOut(BaseModel):
    produccion: ProduccionResumenOut
    productos: list[ProduccionProductoOut]
