package pe.dtulia.desktop.modelo;

import java.util.List;

/** Cuerpo de POST /viajes (carga inicial) y POST /viajes/{id}/envios. */
public record EnvioSolicitud(long sedeId, String referencia, List<ItemViaje> items) {
}
