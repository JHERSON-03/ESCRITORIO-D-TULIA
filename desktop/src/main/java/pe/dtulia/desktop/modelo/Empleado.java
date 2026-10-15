package pe.dtulia.desktop.modelo;

public record Empleado(long id, String nombreCompleto, String documento, String rol, long sedeId, String sede) {

    public boolean esAdministrador() {
        return "ADMINISTRADOR".equals(rol);
    }

    /** Entradas, mermas y ajustes de stock. */
    public boolean gestionaInventario() {
        return esAdministrador() || "ALMACENERO".equals(rol);
    }
}
