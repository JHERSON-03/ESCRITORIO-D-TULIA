package pe.dtulia.desktop.modelo;

import java.time.OffsetDateTime;

/** Fila de GET /producciones. recibido y diferencia son null mientras el envío está en camino. */
public record ProduccionResumen(long id, OffsetDateTime fechaEnvio, String estado, OffsetDateTime fechaCierre,
                                int dias, String enviadoPor, String cerradoPor, String referencia, int productos,
                                int enviado, Integer recibido, Integer diferencia, String observacion) {

    public boolean enCamino() {
        return "EN_CAMINO".equals(estado);
    }

    /** "EN CAMINO", "RECIBIDO" o "ANULADO". */
    public String estadoTexto() {
        return estado.replace('_', ' ');
    }
}
