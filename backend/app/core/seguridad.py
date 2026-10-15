from datetime import datetime, timedelta, timezone
from typing import Any

import jwt

from app.core.config import obtener_configuracion


def crear_token(sub: str, **claims: Any) -> tuple[str, int]:
    """Devuelve (token, segundos de vigencia)."""
    config = obtener_configuracion()
    vigencia = timedelta(minutes=config.jwt_expira_minutos)
    ahora = datetime.now(timezone.utc)
    payload = {"sub": sub, "iat": ahora, "exp": ahora + vigencia, **claims}
    token = jwt.encode(payload, config.jwt_secreto, algorithm=config.jwt_algoritmo)
    return token, int(vigencia.total_seconds())


def decodificar_token(token: str) -> dict[str, Any]:
    """Lanza jwt.ExpiredSignatureError o jwt.InvalidTokenError si no es válido."""
    config = obtener_configuracion()
    return jwt.decode(token, config.jwt_secreto, algorithms=[config.jwt_algoritmo])
