package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import pe.dtulia.desktop.modelo.EnvioSolicitud;
import pe.dtulia.desktop.modelo.LiquidacionSolicitud;
import pe.dtulia.desktop.modelo.ViajeDetalle;
import pe.dtulia.desktop.modelo.ViajeResumen;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Mercadería que lleva el jefe (GET/POST /viajes). */
public class ViajeServicio {

    private final ApiClient api;

    public ViajeServicio(ApiClient api) {
        this.api = api;
    }

    /** estado: ABIERTO, LIQUIDADO o null para todos. */
    public CompletableFuture<List<ViajeResumen>> listar(String estado) {
        String ruta = estado == null ? "/viajes" : "/viajes?estado=" + estado;
        return api.get(ruta, new TypeReference<List<ViajeResumen>>() {
        });
    }

    public CompletableFuture<ViajeDetalle> detalle(long viajeId) {
        return api.get("/viajes/" + viajeId, ViajeDetalle.class);
    }

    /** Carga inicial: la mercadería sale del almacén. */
    public CompletableFuture<ViajeDetalle> abrir(EnvioSolicitud solicitud) {
        return api.post("/viajes", solicitud, ViajeDetalle.class);
    }

    public CompletableFuture<ViajeDetalle> enviar(long viajeId, EnvioSolicitud solicitud) {
        return api.post("/viajes/" + viajeId + "/envios", solicitud, ViajeDetalle.class);
    }

    public CompletableFuture<ViajeDetalle> anularEnvio(long envioId) {
        return api.post("/viajes/envios/" + envioId + "/anular", null, ViajeDetalle.class);
    }

    public CompletableFuture<ViajeDetalle> liquidar(long viajeId, LiquidacionSolicitud solicitud) {
        return api.post("/viajes/" + viajeId + "/liquidar", solicitud, ViajeDetalle.class);
    }
}
