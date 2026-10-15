package pe.dtulia.desktop.modelo;

import java.util.List;

/** Cuerpo de POST /comprobantes. clienteId null = CLIENTES VARIOS. */
public record VentaSolicitud(Long clienteId, long tipoComprobanteId, long metodoPagoId, String canal,
                             List<ItemVenta> items) {
}
