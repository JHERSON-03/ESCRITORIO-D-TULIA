package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import pe.dtulia.desktop.modelo.Producto;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProductoServicio {

    private static final TypeReference<List<Producto>> LISTA_PRODUCTOS = new TypeReference<>() {
    };

    private final ApiClient api;

    public ProductoServicio(ApiClient api) {
        this.api = api;
    }

    /** GET /productos: catálogo activo con stock actual. */
    public CompletableFuture<List<Producto>> listar() {
        return api.get("/productos", LISTA_PRODUCTOS);
    }
}
