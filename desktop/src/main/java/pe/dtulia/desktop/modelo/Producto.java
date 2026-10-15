package pe.dtulia.desktop.modelo;

import java.math.BigDecimal;

public record Producto(
        long id,
        String nombre,
        BigDecimal precio,
        int stock,
        long tipoProductoId,
        String tipoProducto,
        String origen,
        boolean stockBajo) {
}
