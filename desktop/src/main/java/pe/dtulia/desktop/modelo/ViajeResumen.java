package pe.dtulia.desktop.modelo;

import java.time.OffsetDateTime;

/** Fila de GET /viajes. */
public record ViajeResumen(long id, OffsetDateTime fechaSalida, String estado, OffsetDateTime fechaLiquidacion,
                           int dias, String ciudades, int envios, int llevado, int vendido, int devuelto, int merma,
                           int diferencia, String observacion, String registradoPor) {

    public boolean abierto() {
        return "ABIERTO".equals(estado);
    }
}
