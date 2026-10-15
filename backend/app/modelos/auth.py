from pydantic import BaseModel, Field


class LoginIn(BaseModel):
    documento: str = Field(pattern=r"^[A-Za-z0-9]{8,20}$", description="Documento del empleado (DNI)")
    password: str = Field(min_length=1, max_length=128)
    sede_id: int = Field(gt=0, description="Sede en la que trabajará durante la sesión")


class EmpleadoSesion(BaseModel):
    id: int
    nombre_completo: str
    documento: str
    rol: str
    sede_id: int
    sede: str


class LoginOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expira_en: int = Field(description="Segundos de vigencia del token")
    empleado: EmpleadoSesion


class EmpleadoToken(BaseModel):
    """Datos del empleado autenticado extraídos del JWT."""

    id: int
    rol: str
    sede_id: int
