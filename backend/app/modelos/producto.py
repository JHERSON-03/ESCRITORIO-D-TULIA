from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field

from app.modelos.comun import DecimalJson


class ProductoCrear(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    tipo_producto_id: int = Field(gt=0)
    nombre: str = Field(min_length=2, max_length=150)
    precio: Decimal = Field(ge=0, max_digits=10, decimal_places=2)


class ProductoActualizar(BaseModel):
    """Actualización parcial: solo se modifican los campos enviados. El stock no se edita aquí."""

    model_config = ConfigDict(str_strip_whitespace=True)

    tipo_producto_id: int | None = Field(default=None, gt=0)
    nombre: str | None = Field(default=None, min_length=2, max_length=150)
    precio: Decimal | None = Field(default=None, ge=0, max_digits=10, decimal_places=2)


class ProductoOut(BaseModel):
    id: int
    nombre: str
    precio: DecimalJson
    stock: int
    tipo_producto_id: int
    tipo_producto: str
    origen: str | None
    stock_bajo: bool
