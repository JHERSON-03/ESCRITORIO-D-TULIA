package pe.dtulia.desktop.vista;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import pe.dtulia.desktop.modelo.Producto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lista de productos con su cantidad en cajas + unidades sueltas. Se usa para lo que sale del
 * almacén hacia el jefe (limitado al stock) y para lo que la fábrica manda al almacén (sin límite).
 */
class SelectorProductos extends VBox {

    /** Línea elegida; la cantidad siempre en unidades. */
    record Linea(Producto producto, int cantidad) {
    }

    private final boolean limitarAlStock;
    private final ComboBox<Producto> cboProducto = new ComboBox<>();
    private final CampoCajas campoCajas = new CampoCajas();
    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private final TableView<Linea> tabla = new TableView<>(lineas);

    SelectorProductos(boolean limitarAlStock, String textoVacio) {
        super(10);
        this.limitarAlStock = limitarAlStock;

        cboProducto.setPromptText("Seleccione un producto");
        cboProducto.setMaxWidth(Double.MAX_VALUE);
        cboProducto.setVisibleRowCount(14);
        cboProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                if (p == null) {
                    return "";
                }
                String texto = p.tipoProducto() + " · " + p.nombreCompleto();
                return limitarAlStock ? texto + " (almacén: " + Ui.cajas(p.stock(), p.unidadesCaja()) + ")" : texto;
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });
        cboProducto.valueProperty().addListener((obs, antes, ahora) -> campoCajas.setProducto(ahora));

        Button btnAgregar = new Button("Agregar");
        btnAgregar.setOnAction(e -> agregar());
        Button btnQuitar = new Button("Quitar seleccionado");
        btnQuitar.setOnAction(e -> {
            Linea linea = tabla.getSelectionModel().getSelectedItem();
            if (linea != null) {
                lineas.remove(linea);
            }
        });
        HBox filaCantidad = new HBox(10, campoCajas, btnAgregar, btnQuitar);
        filaCantidad.setAlignment(Pos.CENTER_LEFT);

        List<TableColumn<Linea, String>> columnas = new ArrayList<>(List.of(
                Ui.<Linea>columna("Producto", l -> l.producto().nombreCompleto(), 300, false),
                Ui.<Linea>columna("Tipo", l -> l.producto().tipoProducto(), 110, false),
                Ui.<Linea>columna("Unidades", l -> String.valueOf(l.cantidad()), 90, true),
                Ui.<Linea>columna("En cajas", l -> Ui.cajas(l.cantidad(), l.producto().unidadesCaja()), 140, false)));
        if (limitarAlStock) {
            columnas.add(Ui.<Linea>columna("Quedan en almacén",
                    l -> String.valueOf(l.producto().stock() - l.cantidad()), 130, true));
        }
        tabla.getColumns().setAll(columnas);
        tabla.setPlaceholder(new Label(textoVacio));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        getChildren().addAll(cboProducto, filaCantidad, tabla);
    }

    /** Si se limita al stock, solo se ofrecen los productos que hay en el almacén. */
    void setProductos(List<Producto> productos) {
        cboProducto.getItems().setAll(productos.stream()
                .filter(p -> !limitarAlStock || p.stock() > 0)
                .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombre)
                        .thenComparingLong(Producto::presentacionId))
                .toList());
    }

    ObservableList<Linea> lineas() {
        return lineas;
    }

    private void agregar() {
        Producto producto = cboProducto.getValue();
        if (producto == null) {
            Ui.mostrarError(ventana(), "Seleccione un producto.");
            return;
        }
        int cantidad = campoCajas.total();
        if (cantidad < 1) {
            Ui.mostrarError(ventana(), "Ingrese las cajas y/o unidades (en total, mayor a 0).");
            return;
        }

        int indice = -1;
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).producto().id() == producto.id()) {
                indice = i;
            }
        }
        int total = cantidad + (indice >= 0 ? lineas.get(indice).cantidad() : 0);
        if (limitarAlStock && total > producto.stock()) {
            Ui.mostrarError(ventana(), String.format("En el almacén solo hay %d unidades (%s) de %s.", producto.stock(),
                    Ui.cajas(producto.stock(), producto.unidadesCaja()), producto.nombreCompleto()));
            return;
        }
        if (indice >= 0) {
            lineas.set(indice, new Linea(producto, total));
        } else {
            lineas.add(new Linea(producto, total));
        }
        cboProducto.getSelectionModel().clearSelection();
        campoCajas.limpiar();
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
