package pe.dtulia.desktop.vista;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import javafx.util.Duration;
import pe.dtulia.desktop.modelo.Empleado;
import pe.dtulia.desktop.modelo.Producto;
import pe.dtulia.desktop.modelo.TipoProducto;
import pe.dtulia.desktop.servicio.Servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Grilla de inventario que consume GET /productos y se refresca automáticamente. */
public class FrmProductos extends BorderPane {

    private static final Duration INTERVALO_REFRESCO = Duration.seconds(30);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final TipoProducto TODOS_LOS_TIPOS = new TipoProducto(0, "Todos los tipos", null);
    private static final String TODAS_LAS_PRESENTACIONES = "Todas";

    private final Servicios servicios;
    private final Empleado empleado;
    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final FilteredList<Producto> filtrados = new FilteredList<>(productos, p -> true);
    private final TableView<Producto> tabla = new TableView<>();
    private final TextField txtBuscar = new TextField();
    private final ComboBox<TipoProducto> cboTipo = new ComboBox<>();
    private final ComboBox<String> cboPresentacion = new ComboBox<>();
    private final CheckBox chkConStock = new CheckBox("Solo con stock");
    private final CheckBox chkStockBajo = new CheckBox("Solo stock bajo");
    private final Label lblResumen = new Label();
    private final Label lblActualizado = new Label("Cargando…");
    private final Timeline refresco;

    public FrmProductos(Servicios servicios, Empleado empleado) {
        this.servicios = servicios;
        this.empleado = empleado;
        getStyleClass().add("formulario");

        setTop(crearBarra());
        setCenter(crearTabla());
        setBottom(crearPie());

        txtBuscar.textProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        cboTipo.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        cboPresentacion.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        chkConStock.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        chkStockBajo.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltro());

        refresco = new Timeline(new KeyFrame(INTERVALO_REFRESCO, e -> recargar()));
        refresco.setCycleCount(Animation.INDEFINITE);
        refresco.play();
        cargarTipos();
        recargar();
    }

    public void recargar() {
        Ui.alCompletar(servicios.productos().listar(),
                lista -> {
                    Producto seleccionado = tabla.getSelectionModel().getSelectedItem();
                    productos.setAll(lista);
                    actualizarPresentaciones(lista);
                    if (seleccionado != null) {
                        lista.stream()
                                .filter(p -> p.id() == seleccionado.id())
                                .findFirst()
                                .ifPresent(p -> tabla.getSelectionModel().select(p));
                    }
                    lblActualizado.setText("Actualizado a las " + LocalTime.now().format(HORA));
                    actualizarResumen();
                },
                mensaje -> lblActualizado.setText("⚠ " + mensaje));
    }

    public void detener() {
        refresco.stop();
    }

    private void cargarTipos() {
        Ui.alCompletar(servicios.catalogos().tiposProducto(),
                tipos -> {
                    cboTipo.getItems().setAll(TODOS_LOS_TIPOS);
                    cboTipo.getItems().addAll(tipos);
                    cboTipo.setValue(TODOS_LOS_TIPOS);
                },
                mensaje -> lblActualizado.setText("⚠ " + mensaje));
    }

    /** Presentaciones que existen en el catálogo, en el orden de la base de datos (750 ML, 1 L, …). */
    private void actualizarPresentaciones(List<Producto> lista) {
        List<String> presentaciones = lista.stream()
                .sorted(Comparator.comparingLong(Producto::presentacionId))
                .map(Producto::presentacion)
                .distinct()
                .toList();
        String actual = cboPresentacion.getValue();
        cboPresentacion.getItems().setAll(TODAS_LAS_PRESENTACIONES);
        cboPresentacion.getItems().addAll(presentaciones);
        cboPresentacion.setValue(actual != null && cboPresentacion.getItems().contains(actual)
                ? actual : TODAS_LAS_PRESENTACIONES);
    }

    private HBox crearBarra() {
        txtBuscar.setPromptText("Buscar producto…");
        txtBuscar.setPrefWidth(220);
        Button btnActualizar = new Button("Actualizar");
        btnActualizar.setOnAction(e -> recargar());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        lblActualizado.getStyleClass().add("estado");

        HBox barra = new HBox(12, txtBuscar, new Label("Tipo"), cboTipo, new Label("Presentación"), cboPresentacion,
                chkConStock, chkStockBajo, espacio, lblActualizado, btnActualizar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(0, 0, 10, 0));
        return barra;
    }

    private TableView<Producto> crearTabla() {
        List<TableColumn<Producto, ?>> columnas = List.of(
                columna("Producto", Producto::nombre, 240, String::valueOf, false),
                columna("Presentación", Producto::presentacion, 110, String::valueOf, false),
                columna("Tipo", Producto::tipoProducto, 110, String::valueOf, false),
                columna("Origen", Producto::origen, 110, String::valueOf, false),
                columna("Precio", Producto::precio, 100, p -> p.signum() > 0 ? Ui.soles(p) : "Sin precio", true),
                columna("Stock", Producto::stock, 80, String::valueOf, true),
                columna("En cajas", p -> Ui.cajas(p.stock(), p.unidadesCaja()), 130, String::valueOf, true));
        tabla.getColumns().setAll(columnas);

        SortedList<Producto> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
        tabla.setPlaceholder(new Label("Sin productos para mostrar"));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        // Resalta filas sin stock o con stock bajo
        tabla.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Producto producto, boolean vacio) {
                super.updateItem(producto, vacio);
                getStyleClass().removeAll("stock-bajo", "sin-stock");
                if (!vacio && producto != null) {
                    if (producto.stock() == 0) {
                        getStyleClass().add("sin-stock");
                    } else if (producto.stockBajo()) {
                        getStyleClass().add("stock-bajo");
                    }
                }
            }
        });
        if (empleado.esAdministrador()) {
            tabla.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    editarPrecio();
                }
            });
        }
        return tabla;
    }

    private HBox crearPie() {
        Label leyendaBajo = new Label("Stock bajo");
        leyendaBajo.getStyleClass().add("leyenda-bajo");
        Label leyendaSin = new Label("Sin stock");
        leyendaSin.getStyleClass().add("leyenda-sin");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        HBox pie = new HBox(10, lblResumen, espacio, leyendaBajo, leyendaSin);
        if (empleado.esAdministrador()) {
            Button btnPrecio = new Button("Editar precio");
            btnPrecio.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
            btnPrecio.setOnAction(e -> editarPrecio());
            pie.getChildren().add(btnPrecio);
        }
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.setPadding(new Insets(10, 0, 0, 0));
        lblResumen.getStyleClass().add("estado");
        return pie;
    }

    /** Solo ADMINISTRADOR: las presentaciones nuevas no se venden hasta tener precio. */
    private void editarPrecio() {
        Producto producto = tabla.getSelectionModel().getSelectedItem();
        if (producto == null) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog(producto.tienePrecio() ? producto.precio().toPlainString() : "");
        dialogo.initOwner(ventana());
        dialogo.setTitle("Editar precio");
        dialogo.setHeaderText(producto.nombreCompleto() + "\nPrecio actual: "
                + (producto.tienePrecio() ? Ui.soles(producto.precio()) : "sin precio"));
        dialogo.setContentText("Nuevo precio (S/):");

        dialogo.showAndWait()
                .map(texto -> texto.trim().replace(',', '.'))
                .filter(texto -> !texto.isEmpty())
                .ifPresent(texto -> {
                    BigDecimal precio;
                    try {
                        precio = new BigDecimal(texto).setScale(2, RoundingMode.HALF_UP);
                    } catch (NumberFormatException | ArithmeticException e) {
                        Ui.mostrarError(ventana(), "Ingrese un precio válido, por ejemplo 25.50");
                        return;
                    }
                    if (precio.signum() <= 0) {
                        Ui.mostrarError(ventana(), "El precio debe ser mayor a 0.");
                        return;
                    }
                    Ui.alCompletar(servicios.productos().actualizarPrecio(producto.id(), precio),
                            actualizado -> recargar(),
                            mensaje -> Ui.mostrarError(ventana(), mensaje));
                });
    }

    private void aplicarFiltro() {
        String texto = txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        TipoProducto tipo = cboTipo.getValue();
        String presentacion = cboPresentacion.getValue();
        boolean conStock = chkConStock.isSelected();
        boolean soloBajo = chkStockBajo.isSelected();
        filtrados.setPredicate(p ->
                (tipo == null || tipo == TODOS_LOS_TIPOS || p.tipoProductoId() == tipo.id())
                        && (presentacion == null || TODAS_LAS_PRESENTACIONES.equals(presentacion)
                            || presentacion.equals(p.presentacion()))
                        && (!conStock || p.stock() > 0)
                        && (!soloBajo || p.stockBajo())
                        && (texto.isEmpty() || p.nombre().toLowerCase(Locale.ROOT).contains(texto)));
        actualizarResumen();
    }

    private void actualizarResumen() {
        long bajos = productos.stream().filter(p -> p.stockBajo() && p.stock() > 0).count();
        long agotados = productos.stream().filter(p -> p.stock() == 0).count();
        long sinPrecio = productos.stream().filter(p -> !p.tienePrecio()).count();
        int unidades = productos.stream().mapToInt(Producto::stock).sum();
        lblResumen.setText(String.format(
                "%d de %d productos  ·  %d unidades en total  ·  %d con stock bajo  ·  %d sin stock  ·  %d sin precio",
                filtrados.size(), productos.size(), unidades, bajos, agotados, sinPrecio));
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static <T> TableColumn<Producto, T> columna(String titulo, Function<Producto, T> valor, double ancho,
                                                        Function<T, String> formato, boolean numerica) {
        TableColumn<Producto, T> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio || item == null ? null : formato.apply(item));
            }
        });
        columna.setPrefWidth(ancho);
        if (numerica) {
            columna.getStyleClass().add("numero");
        }
        return columna;
    }
}
