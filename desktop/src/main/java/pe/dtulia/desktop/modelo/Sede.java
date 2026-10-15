package pe.dtulia.desktop.modelo;

public record Sede(long id, String nombre, String direccion, boolean esAlmacen) {

    @Override
    public String toString() {
        return nombre;
    }
}
