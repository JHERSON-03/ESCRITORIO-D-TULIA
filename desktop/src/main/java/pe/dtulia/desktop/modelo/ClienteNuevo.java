package pe.dtulia.desktop.modelo;

/** Cuerpo de POST /clientes. */
public record ClienteNuevo(String tipoDocumento, String dniRuc, String nombreRazonSocial) {
}
