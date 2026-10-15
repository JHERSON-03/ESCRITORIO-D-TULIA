package pe.dtulia.desktop.modelo;

import java.time.OffsetDateTime;

/** Fila del Kardex (movimientos_inventario). */
public record Movimiento(long id, long productoId, String tipo, int cantidad, int stockPrevio, int stockPosterior,
                         String referencia, OffsetDateTime fecha) {
}
