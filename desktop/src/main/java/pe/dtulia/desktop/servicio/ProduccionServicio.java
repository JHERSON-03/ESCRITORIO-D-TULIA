package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import pe.dtulia.desktop.modelo.AnulacionSolicitud;
import pe.dtulia.desktop.modelo.ProduccionDetalle;
import pe.dtulia.desktop.modelo.ProduccionResumen;
import pe.dtulia.desktop.modelo.ProduccionSolicitud;
import pe.dtulia.desktop.modelo.RecepcionSolicitud;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Envíos de la fábrica al almacén (GET/POST /producciones). */
public class ProduccionServicio {

    private final ApiClient api;

    public ProduccionServicio(ApiClient api) {
        this.api = api;
    }

    /** estado: EN_CAMINO, RECIBIDO, ANULADO o null para todos. */
    public CompletableFuture<List<ProduccionResumen>> listar(String estado) {
        String ruta = estado == null ? "/producciones" : "/producciones?estado=" + estado;
        return api.get(ruta, new TypeReference<List<ProduccionResumen>>() {
        });
    }

    public CompletableFuture<ProduccionDetalle> detalle(long produccionId) {
        return api.get("/producciones/" + produccionId, ProduccionDetalle.class);
    }

    /** La fábrica registra lo que manda; el stock no cambia hasta que el almacén lo recibe. */
    public CompletableFuture<ProduccionDetalle> registrar(ProduccionSolicitud solicitud) {
        return api.post("/producciones", solicitud, ProduccionDetalle.class);
    }

    public CompletableFuture<ProduccionDetalle> recibir(long produccionId, RecepcionSolicitud solicitud) {
        return api.post("/producciones/" + produccionId + "/recibir", solicitud, ProduccionDetalle.class);
    }

    public CompletableFuture<ProduccionDetalle> anular(long produccionId, String observacion) {
        return api.post("/producciones/" + produccionId + "/anular", new AnulacionSolicitud(observacion),
                ProduccionDetalle.class);
    }
}
