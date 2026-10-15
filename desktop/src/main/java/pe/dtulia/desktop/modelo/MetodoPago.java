package pe.dtulia.desktop.modelo;

public record MetodoPago(long id, String nombre) {

    @Override
    public String toString() {
        return nombre;
    }
}
