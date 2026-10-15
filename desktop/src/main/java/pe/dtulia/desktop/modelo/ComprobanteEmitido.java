package pe.dtulia.desktop.modelo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Respuesta de POST /comprobantes. */
public record ComprobanteEmitido(
        long id,
        long pedidoId,
        String tipoComprobante,
        String serie,
        String numero,
        String numeroCompleto,
        OffsetDateTime fecha,
        BigDecimal subtotal,
        BigDecimal igv,
        BigDecimal total) {
}
