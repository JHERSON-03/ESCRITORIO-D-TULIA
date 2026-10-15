package pe.dtulia.desktop.servicio;

import pe.dtulia.desktop.modelo.Cliente;
import pe.dtulia.desktop.modelo.ClienteNuevo;
import pe.dtulia.desktop.modelo.ComprobanteEmitido;
import pe.dtulia.desktop.modelo.VentaSolicitud;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class VentaServicio {

    private final ApiClient api;

    public VentaServicio(ApiClient api) {
        this.api = api;
    }

    /** GET /clientes/{dni_ruc}. Un 404 se traduce en Optional.empty(). */
    public CompletableFuture<Optional<Cliente>> buscarCliente(String dniRuc) {
        return api.get("/clientes/" + dniRuc, Cliente.class)
                .thenApply(Optional::of)
                .exceptionally(error -> {
                    Throwable causa = error instanceof CompletionException ? error.getCause() : error;
                    if (causa instanceof ApiException apiError && apiError.getEstado() == 404) {
                        return Optional.empty();
                    }
                    throw error instanceof CompletionException ce ? ce : new CompletionException(error);
                });
    }

    public CompletableFuture<Cliente> registrarCliente(ClienteNuevo cliente) {
        return api.post("/clientes", cliente, Cliente.class);
    }

    /** POST /comprobantes: la base de datos valida y descuenta el stock en la misma transacción. */
    public CompletableFuture<ComprobanteEmitido> emitirComprobante(VentaSolicitud solicitud) {
        return api.post("/comprobantes", solicitud, ComprobanteEmitido.class);
    }
}
