package pe.dtulia.desktop.servicio;

/** Servicios de acceso a la API que comparten los formularios. */
public record Servicios(CatalogoServicio catalogos, ProductoServicio productos, VentaServicio ventas,
                        InventarioServicio inventario, ViajeServicio viajes, ProduccionServicio producciones) {
}
