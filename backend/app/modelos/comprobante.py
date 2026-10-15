from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, computed_field

from app.modelos.comun import DecimalJson


class ItemVenta(BaseModel):
    producto_id: int = Field(gt=0)
    cantidad: int = Field(gt=0, le=10_000)


class ComprobanteCrear(BaseModel):
    """Venta a registrar. El precio lo pone la base de datos, nunca el cliente."""

    model_config = ConfigDict(str_strip_whitespace=True)

    cliente_id: int | None = Field(default=None, gt=0, description="Si se omite se usa CLIENTES VARIOS")
    tipo_comprobante_id: int = Field(gt=0, description="Boleta, factura o nota de venta")
    metodo_pago_id: int = Field(gt=0)
    canal: str = Field(default="TIENDA", max_length=50, description="Canal de venta del pedido")
    items: list[ItemVenta] = Field(min_length=1, max_length=200)


class ComprobanteOut(BaseModel):
    id: int
    pedido_id: int
    tipo_comprobante: str
    serie: str
    numero: str
    fecha: datetime
    subtotal: DecimalJson
    igv: DecimalJson
    total: DecimalJson

    @computed_field
    @property
    def numero_completo(self) -> str:
        return f"{self.serie}-{self.numero}"
