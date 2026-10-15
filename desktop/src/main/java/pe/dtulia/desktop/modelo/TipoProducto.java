package pe.dtulia.desktop.modelo;

public record TipoProducto(long id, String nombre, String origen) {

    @Override
    public String toString() {
        return nombre;
    }
}
