package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import pe.dtulia.desktop.modelo.MetodoPago;
import pe.dtulia.desktop.modelo.Sede;
import pe.dtulia.desktop.modelo.TipoComprobante;
import pe.dtulia.desktop.modelo.TipoProducto;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Catálogos de la base de datos (GET /catalogos/...). */
public class CatalogoServicio {

    private final ApiClient api;

    public CatalogoServicio(ApiClient api) {
        this.api = api;
    }

    /** No requiere sesión: se usa en la pantalla de login. */
    public CompletableFuture<List<Sede>> sedes() {
        return api.get("/catalogos/sedes", new TypeReference<List<Sede>>() {
        });
    }

    public CompletableFuture<List<TipoProducto>> tiposProducto() {
        return api.get("/catalogos/tipos-producto", new TypeReference<List<TipoProducto>>() {
        });
    }

    public CompletableFuture<List<TipoComprobante>> tiposComprobante() {
        return api.get("/catalogos/tipos-comprobante", new TypeReference<List<TipoComprobante>>() {
        });
    }

    public CompletableFuture<List<MetodoPago>> metodosPago() {
        return api.get("/catalogos/metodos-pago", new TypeReference<List<MetodoPago>>() {
        });
    }

    public CompletableFuture<List<String>> canalesVenta() {
        return api.get("/catalogos/canales-venta", new TypeReference<List<String>>() {
        });
    }
}
