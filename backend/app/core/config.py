from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Configuracion(BaseSettings):
    """Variables de entorno del backend (se leen desde .env)."""

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_nombre: str = "D'Tulia API"

    database_url: str
    db_pool_min: int = Field(default=1, ge=1)
    db_pool_max: int = Field(default=10, ge=1)

    jwt_secreto: str = Field(min_length=32)
    jwt_algoritmo: str = "HS256"
    jwt_expira_minutos: int = Field(default=480, gt=0)

    # Un producto se marca con "stock bajo" cuando su stock es menor o igual a este valor
    stock_minimo: int = Field(default=5, ge=0)


@lru_cache
def obtener_configuracion() -> Configuracion:
    return Configuracion()
