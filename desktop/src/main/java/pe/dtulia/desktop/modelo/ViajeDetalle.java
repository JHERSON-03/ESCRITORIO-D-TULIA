package pe.dtulia.desktop.modelo;

import java.util.List;

/** Respuesta de GET /viajes/{id} y de las operaciones sobre un viaje. */
public record ViajeDetalle(ViajeResumen viaje, List<ViajeProducto> productos, List<Envio> envios,
                           List<VentaCiudad> ventas) {
}
