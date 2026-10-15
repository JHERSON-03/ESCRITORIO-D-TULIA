package pe.dtulia.desktop.modelo;

import java.math.BigDecimal;

public record TipoComprobante(long id, String nombre, String serie, boolean requiereRuc, BigDecimal tasaIgv) {

    @Override
    public String toString() {
        return nombre + " (" + serie + ")";
    }
}
