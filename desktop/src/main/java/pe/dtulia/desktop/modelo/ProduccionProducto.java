package pe.dtulia.desktop.modelo;

/** Por producto: lo que envió la fábrica y lo que llegó (null mientras está en camino). */
public record ProduccionProducto(long productoId, String nombre, String presentacion, int unidadesCaja,
                                 String tipoProducto, int enviado, Integer recibido, Integer diferencia) {

    public String nombreCompleto() {
        return nombre + " · " + presentacion;
    }
}
