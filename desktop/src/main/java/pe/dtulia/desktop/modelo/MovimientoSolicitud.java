package pe.dtulia.desktop.modelo;

/** Cuerpo de POST /inventario/movimientos. ENTRADA/MERMA usan cantidad; AJUSTE usa stockReal. */
public record MovimientoSolicitud(long productoId, String tipo, Integer cantidad, Integer stockReal,
                                  String referencia) {
}
