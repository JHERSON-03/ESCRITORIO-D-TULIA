package pe.dtulia.desktop.modelo;

import java.util.List;

/** Cuerpo de POST /viajes/{id}/liquidar: lo que el jefe anotó en su hoja. */
public record LiquidacionSolicitud(List<VentaViaje> ventas, List<RetornoViaje> retornos, String observacion) {
}
