package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.type.TypeReference;
import pe.dtulia.desktop.modelo.Producto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

    /** PATCH /productos/{id} (solo ADMINISTRADOR). */
    public CompletableFuture<Producto> actualizarPrecio(long productoId, BigDecimal precio) {
        return api.patch("/productos/" + productoId, Map.of("precio", precio), Producto.class);
    }
}
