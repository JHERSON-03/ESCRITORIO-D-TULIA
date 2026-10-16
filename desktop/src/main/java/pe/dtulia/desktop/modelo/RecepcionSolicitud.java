package pe.dtulia.desktop.modelo;

import java.util.List;

/** Cuerpo de POST /producciones/{id}/recibir: lo que realmente llegó al almacén. */
public record RecepcionSolicitud(List<ItemProduccion> items, String observacion) {
}
