package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import pe.dtulia.desktop.modelo.Movimiento;
import pe.dtulia.desktop.modelo.MovimientoSolicitud;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class InventarioServicio {

    private final ApiClient api;

    public InventarioServicio(ApiClient api) {
        this.api = api;
    }

    /** POST /inventario/movimientos: ENTRADA, MERMA o AJUSTE. */
    public CompletableFuture<Movimiento> registrarMovimiento(MovimientoSolicitud solicitud) {
        return api.post("/inventario/movimientos", solicitud, Movimiento.class);
    }

    public CompletableFuture<Movimiento> anularMovimiento(long movimientoId) {
        return api.post("/inventario/movimientos/" + movimientoId + "/anular", null, Movimiento.class);
    }

    public CompletableFuture<List<Movimiento>> kardex(long productoId) {
        return api.get("/inventario/kardex/" + productoId + "?limite=200", new TypeReference<List<Movimiento>>() {
        });
    }
}
