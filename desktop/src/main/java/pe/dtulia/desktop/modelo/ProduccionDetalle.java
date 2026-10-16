package pe.dtulia.desktop.modelo;

import java.util.List;

/** Respuesta de GET /producciones/{id} y de las operaciones sobre un envío de producción. */
public record ProduccionDetalle(ProduccionResumen produccion, List<ProduccionProducto> productos) {
}
