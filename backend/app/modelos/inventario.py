from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

TipoMovimientoManual = Literal["ENTRADA", "MERMA", "AJUSTE"]


class MovimientoCrear(BaseModel):
    """ENTRADA y MERMA usan `cantidad`; AJUSTE usa `stock_real` (conteo físico)."""

    model_config = ConfigDict(str_strip_whitespace=True)

    producto_id: int = Field(gt=0)
    tipo: TipoMovimientoManual
    cantidad: int | None = Field(default=None, gt=0, le=100_000)
    stock_real: int | None = Field(default=None, ge=0, le=1_000_000)
    referencia: str | None = Field(default=None, max_length=100, description="Lote, guía o motivo")


class MovimientoOut(BaseModel):
    id: int
    producto_id: int
    tipo: str
    cantidad: int
    stock_previo: int
    stock_posterior: int
    referencia: str | None
    fecha: datetime
