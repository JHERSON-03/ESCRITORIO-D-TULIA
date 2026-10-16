package pe.dtulia.desktop.modelo;

import java.util.List;

/** Cuerpo de POST /producciones: lo que la fábrica manda al almacén. */
public record ProduccionSolicitud(String referencia, List<ItemProduccion> items) {
}
