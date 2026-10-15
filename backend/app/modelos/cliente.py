from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class ClienteCrear(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    tipo_documento: Literal["DNI", "RUC", "CE"]
    dni_ruc: str = Field(pattern=r"^[A-Za-z0-9]{8,12}$")
    nombre_razon_social: str = Field(min_length=2, max_length=150)


class ClienteOut(BaseModel):
    id: int
    tipo_documento: str
    dni_ruc: str
    nombre_razon_social: str
