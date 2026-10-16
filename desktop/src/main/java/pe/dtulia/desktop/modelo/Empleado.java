package pe.dtulia.desktop.modelo;

public record Empleado(long id, String nombreCompleto, String documento, String rol, long sedeId, String sede) {

    /** Las administradoras (almacén y fábrica) tienen acceso a todo; el ALMACENERO solo consulta. */
    public boolean esAdministrador() {
        return "ADMINISTRADOR".equals(rol);
    }
}
