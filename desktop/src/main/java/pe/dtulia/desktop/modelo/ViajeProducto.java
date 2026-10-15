package pe.dtulia.desktop.modelo;

/** Por producto: llevó (envíos no anulados), vendió, regresó, merma y diferencia. */
public record ViajeProducto(long productoId, String nombre, String presentacion, int unidadesCaja,
                            String tipoProducto, int llevado, int vendido, int devuelto, int merma,
                            String motivoMerma, int diferencia) {

    public String nombreCompleto() {
        return nombre + " · " + presentacion;
    }
}
