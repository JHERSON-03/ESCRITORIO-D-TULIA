package pe.dtulia.desktop.modelo;

/** Respuesta de POST /auth/login. */
public record Sesion(String accessToken, String tokenType, long expiraEn, Empleado empleado) {
}
