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
        long presentacionId,
        String presentacion,
        int unidadesCaja,
        boolean stockBajo) {

    /** "VINO ROSÉ · 750 ML": cada presentación es un producto distinto. */
    public String nombreCompleto() {
        return nombre + " · " + presentacion;
    }

    /** Las presentaciones nuevas nacen con precio 0 y no se pueden vender hasta tener precio. */
    public boolean tienePrecio() {
        return precio != null && precio.signum() > 0;
    }
}
