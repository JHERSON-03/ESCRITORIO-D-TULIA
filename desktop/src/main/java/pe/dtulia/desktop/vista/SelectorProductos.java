package pe.dtulia.desktop.vista;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import pe.dtulia.desktop.modelo.Producto;
import pe.dtulia.desktop.modelo.TipoProducto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lista de productos con su cantidad en cajas + unidades sueltas. Se usa para lo que sale del
 * almacén hacia el jefe (limitado al stock) y para lo que la fábrica manda al almacén (sin límite).
 * El producto se elige con tres desplegables en cadena: tipo → nombre → presentación.
 */
class SelectorProductos extends VBox {

    /** Línea elegida; la cantidad siempre en unidades. */
    record Linea(Producto producto, int cantidad) {
    }

    private final boolean limitarAlStock;
    private List<Producto> productos = List.of();
    private final ComboBox<TipoProducto> cboTipo = new ComboBox<>();
    private final ComboBox<String> cboNombre = new ComboBox<>();
    /** Cada presentación es un producto distinto. */
    private final ComboBox<Producto> cboProducto = new ComboBox<>();
    private final Label lblStock = new Label();
    private final CampoCajas campoCajas = new CampoCajas();
    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private final TableView<Linea> tabla = new TableView<>(lineas);

    SelectorProductos(boolean limitarAlStock, String textoVacio) {
        super(10);
        this.limitarAlStock = limitarAlStock;

        cboTipo.setPromptText("Tipo");
        cboTipo.setPrefWidth(180);
        cboTipo.setVisibleRowCount(14);
        cboNombre.setPromptText("Seleccione un producto");
        cboNombre.setMaxWidth(Double.MAX_VALUE);
        cboNombre.setVisibleRowCount(14);
        cboNombre.disableProperty().bind(cboTipo.valueProperty().isNull());
        HBox.setHgrow(cboNombre, Priority.ALWAYS);
        cboProducto.setPromptText("Presentación");
        cboProducto.setPrefWidth(170);
        cboProducto.setVisibleRowCount(14);
        cboProducto.disableProperty().bind(cboNombre.valueProperty().isNull());
        cboProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                return p == null ? "" : p.presentacion();
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });

        cboTipo.valueProperty().addListener((obs, antes, ahora) -> mostrarNombres());
        cboNombre.valueProperty().addListener((obs, antes, ahora) -> mostrarPresentaciones());
        cboProducto.valueProperty().addListener((obs, antes, ahora) -> {
            campoCajas.setProducto(ahora);
            lblStock.setText(ahora == null ? "" : "En almacén: " + Ui.cajas(ahora.stock(), ahora.unidadesCaja()));
            lblStock.setVisible(limitarAlStock && ahora != null);
        });
        HBox filaProducto = new HBox(8, cboTipo, cboNombre, cboProducto);
        filaProducto.setAlignment(Pos.CENTER_LEFT);

        // Enter en las cajas o unidades agrega; Supr en la tabla quita la línea elegida
        Button btnAgregar = Ui.boton("Agregar", "mdi2p-plus", "boton-primario");
        btnAgregar.setTooltip(new Tooltip("Enter"));
        btnAgregar.setOnAction(e -> agregar());
        campoCajas.setAlConfirmar(this::agregar);
        Runnable quitar = () -> {
            Linea linea = tabla.getSelectionModel().getSelectedItem();
            if (linea != null) {
                lineas.remove(linea);
            }
        };
        Button btnQuitar = Ui.boton("Quitar", "mdi2d-delete-outline");
        btnQuitar.setTooltip(new Tooltip("Quitar la línea seleccionada (Supr)"));
        btnQuitar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        btnQuitar.setOnAction(e -> quitar.run());
        tabla.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                quitar.run();
            }
        });
        lblStock.getStyleClass().add("stock-actual");
        lblStock.setGraphic(Ui.icono("mdi2p-package-variant-closed"));
        lblStock.setVisible(false);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox filaCantidad = new HBox(10, campoCajas, btnAgregar, btnQuitar, espacio, lblStock);
        filaCantidad.setAlignment(Pos.CENTER_LEFT);

        List<TableColumn<Linea, String>> columnas = new ArrayList<>(List.of(
                Ui.<Linea>columna("Producto", l -> l.producto().nombreCompleto(), 300, false),
                Ui.columnaInsignia("Tipo", l -> l.producto().tipoProducto(), 110, Ui::tonoTipo),
                Ui.<Linea>columna("Unidades", l -> String.valueOf(l.cantidad()), 90, true),
                Ui.<Linea>columna("En cajas", l -> Ui.cajas(l.cantidad(), l.producto().unidadesCaja()), 140, false)));
        if (limitarAlStock) {
            columnas.add(Ui.<Linea>columna("Quedan en almacén",
                    l -> String.valueOf(l.producto().stock() - l.cantidad()), 130, true));
        }
        tabla.getColumns().setAll(columnas);
        tabla.setPlaceholder(Ui.estadoVacio("mdi2p-package-variant", textoVacio,
                "Elija tipo, producto y presentación, escriba la cantidad y presione Enter."));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        getChildren().addAll(filaProducto, filaCantidad, tabla);
    }

    /** Si se limita al stock, solo se ofrecen los productos que hay en el almacén. */
    void setProductos(List<Producto> lista) {
        productos = lista.stream()
                .filter(p -> !limitarAlStock || p.stock() > 0)
                .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombre)
                        .thenComparingLong(Producto::presentacionId))
                .toList();
        // Solo los tipos que tienen productos
        cboTipo.getItems().setAll(productos.stream()
                .map(p -> new TipoProducto(p.tipoProductoId(), p.tipoProducto(), null))
                .distinct()
                .toList());
    }

    ObservableList<Linea> lineas() {
        return lineas;
    }

    /** Llena los nombres del tipo elegido. */
    private void mostrarNombres() {
        TipoProducto tipo = cboTipo.getValue();
        cboNombre.getItems().setAll(tipo == null ? List.of()
                : productos.stream().filter(p -> p.tipoProductoId() == tipo.id()).map(Producto::nombre).distinct().toList());
        cboNombre.setValue(null);
    }

    /** Llena las presentaciones del producto elegido; si solo tiene una, queda elegida. */
    private void mostrarPresentaciones() {
        TipoProducto tipo = cboTipo.getValue();
        String nombre = cboNombre.getValue();
        List<Producto> presentaciones = tipo == null || nombre == null ? List.of()
                : productos.stream().filter(p -> p.tipoProductoId() == tipo.id() && p.nombre().equals(nombre)).toList();
        cboProducto.getItems().setAll(presentaciones);
        cboProducto.setValue(presentaciones.size() == 1 ? presentaciones.get(0) : null);
    }

    private void agregar() {
        Producto producto = cboProducto.getValue();
        if (producto == null) {
            Ui.mostrarError(ventana(), cboNombre.getValue() == null ? "Seleccione un producto." : "Seleccione la presentación.");
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
        // Se mantiene el tipo para seguir agregando productos parecidos
        cboNombre.setValue(null);
        campoCajas.limpiar();
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
