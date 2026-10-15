package pe.dtulia.desktop.modelo;

import java.time.OffsetDateTime;
import java.util.List;

/** Envío 1 = carga inicial; 2, 3… = lo que el jefe pidió durante el viaje. */
public record Envio(long id, int numero, OffsetDateTime fecha, long sedeId, String sede, String referencia,
                    boolean anulado, List<EnvioItem> items) {
}
