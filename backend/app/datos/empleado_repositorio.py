from typing import Any

from app.core.database import obtener_conexion


class EmpleadoRepositorio:
    def autenticar(self, documento: str, password: str) -> dict[str, Any] | None:
        """La contraseña se verifica dentro de PostgreSQL con bcrypt (pgcrypto)."""
        with obtener_conexion() as conn:
            return conn.execute(
                """
                select empleado_id              as id,
                       empleado_nombre_completo as nombre_completo,
                       empleado_documento       as documento,
                       upper(cargo_nombre)      as rol
                  from public.fn_empleado_login(%s, %s)
                """,
                (documento, password),
            ).fetchone()
