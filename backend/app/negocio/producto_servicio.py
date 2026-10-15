from app.core.config import obtener_configuracion
from app.core.errores import ErrorNegocio, NoEncontrado
from app.datos.producto_repositorio import ProductoRepositorio
from app.modelos.producto import ProductoActualizar, ProductoCrear, ProductoOut


class ProductoServicio:
    def __init__(self, repositorio: ProductoRepositorio):
        self._repo = repositorio
        self._stock_minimo = obtener_configuracion().stock_minimo

    def listar(self, buscar: str | None, tipo_producto_id: int | None) -> list[ProductoOut]:
        buscar = (buscar or "").strip() or None
        filas = self._repo.listar(buscar, tipo_producto_id, self._stock_minimo)
        return [ProductoOut.model_validate(fila) for fila in filas]

    def obtener(self, producto_id: int) -> ProductoOut:
        fila = self._repo.obtener(producto_id, self._stock_minimo)
        if fila is None:
            raise NoEncontrado(f"El producto {producto_id} no existe")
        return ProductoOut.model_validate(fila)

    def crear(self, datos: ProductoCrear) -> ProductoOut:
        producto_id = self._repo.crear(datos.tipo_producto_id, datos.nombre, datos.precio, datos.presentacion_id)
        return self.obtener(producto_id)

    def actualizar(self, producto_id: int, datos: ProductoActualizar) -> ProductoOut:
        if not datos.model_dump(exclude_none=True):
            raise ErrorNegocio("No se enviaron campos para actualizar")
        self._repo.actualizar(producto_id, datos.tipo_producto_id, datos.nombre, datos.precio, datos.presentacion_id)
        return self.obtener(producto_id)

    def eliminar(self, producto_id: int) -> None:
        self._repo.eliminar(producto_id)
