package pe.dtulia.desktop.vista;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import pe.dtulia.desktop.modelo.Empleado;
import pe.dtulia.desktop.modelo.Producto;
import pe.dtulia.desktop.modelo.TipoProducto;
import pe.dtulia.desktop.servicio.Servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Grilla de inventario que consume GET /productos y se refresca automáticamente. */
public class FrmProductos extends BorderPane {

    private static final Duration INTERVALO_REFRESCO = Duration.seconds(30);
    private static final TipoProducto TODOS_LOS_TIPOS = new TipoProducto(0, "Todos los tipos", null);
    private static final String TODAS_LAS_PRESENTACIONES = "Todas";
    private static final PseudoClass ACTIVA = PseudoClass.getPseudoClass("activa");

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
    private final Label lblMensaje = new Label(null, Ui.icono("mdi2a-alert-circle-outline"));
    private final ProgressIndicator progreso = new ProgressIndicator();
    private final Label lblTotal = new Label("—");
    private final Label lblUnidades = new Label("—");
    private final Label lblBajos = new Label("—");
    private final Label lblAgotados = new Label("—");
    private final Label lblSinPrecio = new Label("—");
    private final Timeline refresco;

    public FrmProductos(Servicios servicios, Empleado empleado) {
        this.servicios = servicios;
        this.empleado = empleado;
        getStyleClass().add("formulario");

        setTop(new VBox(10, crearResumen(), crearBarra()));
        setCenter(crearTabla());
        setBottom(crearPie());
        BorderPane.setMargin(getCenter(), new Insets(10, 0, 0, 0));

        txtBuscar.textProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        cboTipo.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        cboPresentacion.valueProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        chkConStock.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltro());
        chkStockBajo.selectedProperty().addListener((obs, antes, ahora) -> aplicarFiltro());

        Ui.atajo(this, new KeyCodeCombination(KeyCode.F2), () -> {
            txtBuscar.requestFocus();
            txtBuscar.selectAll();
        });

        refresco = new Timeline(new KeyFrame(INTERVALO_REFRESCO, e -> recargar()));
        refresco.setCycleCount(Animation.INDEFINITE);
        refresco.play();
        cargarTipos();
        recargar();
    }

    public void recargar() {
        progreso.setVisible(true);
        Ui.alCompletar(servicios.productos().listar(),
                lista -> {
                    progreso.setVisible(false);
                    Producto seleccionado = tabla.getSelectionModel().getSelectedItem();
                    productos.setAll(lista);
                    actualizarPresentaciones(lista);
                    if (seleccionado != null) {
                        lista.stream()
                                .filter(p -> p.id() == seleccionado.id())
                                .findFirst()
                                .ifPresent(p -> tabla.getSelectionModel().select(p));
                    }
                    lblMensaje.setText(null);
                    actualizarResumen();
                },
                mensaje -> {
                    progreso.setVisible(false);
                    lblMensaje.setText(mensaje);
                    if (productos.isEmpty()) {
                        tabla.setPlaceholder(Ui.estadoVacio("mdi2l-lan-disconnect",
                                "No se pudo cargar el inventario", mensaje + " Se reintentará en 30 segundos."));
                    }
                });
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
                lblMensaje::setText);
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

    /** Franja de tarjetas: la de stock bajo también activa el filtro "Solo stock bajo". */
    private HBox crearResumen() {
        HBox tarjetaBajos = Ui.tarjetaResumen("mdi2a-alert-outline", "Con stock bajo", lblBajos, "aviso");
        tarjetaBajos.getStyleClass().add("clicable");
        tarjetaBajos.setOnMouseClicked(e -> chkStockBajo.setSelected(!chkStockBajo.isSelected()));
        chkStockBajo.selectedProperty().addListener((obs, antes, ahora) -> tarjetaBajos.pseudoClassStateChanged(ACTIVA, ahora));

        HBox franja = new HBox(10,
                Ui.tarjetaResumen("mdi2p-package-variant-closed", "Productos", lblTotal, "vino"),
                Ui.tarjetaResumen("mdi2b-bottle-wine-outline", "Unidades en almacén", lblUnidades, "exito"),
                tarjetaBajos,
                Ui.tarjetaResumen("mdi2c-close-circle-outline", "Sin stock", lblAgotados, "peligro"),
                Ui.tarjetaResumen("mdi2t-tag-off-outline", "Sin precio", lblSinPrecio, "neutro"));
        return franja;
    }

    private HBox crearBarra() {
        txtBuscar.setPromptText("Buscar producto…  (F2)");
        txtBuscar.setPrefWidth(250);
        StackPane buscador = new StackPane(txtBuscar, Ui.icono("mdi2m-magnify"));
        buscador.getStyleClass().add("buscador");
        StackPane.setAlignment(buscador.getChildren().get(1), Pos.CENTER_LEFT);
        StackPane.setMargin(buscador.getChildren().get(1), new Insets(0, 0, 0, 10));
        buscador.getChildren().get(1).setMouseTransparent(true);

        Button btnActualizar = Ui.boton("Actualizar", "mdi2r-refresh");
        btnActualizar.setOnAction(e -> recargar());

        progreso.setPrefSize(18, 18);
        progreso.setMaxSize(18, 18);
        progreso.setVisible(false);
        lblMensaje.getStyleClass().add("mensaje-barra");
        lblMensaje.setMaxWidth(320);
        lblMensaje.visibleProperty().bind(lblMensaje.textProperty().isNotEmpty());
        lblMensaje.managedProperty().bind(lblMensaje.visibleProperty());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, buscador, etiqueta("Tipo:"), cboTipo, etiqueta("Presentación:"), cboPresentacion,
                chkConStock, chkStockBajo, espacio, lblMensaje, progreso, btnActualizar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-filtros");
        return barra;
    }

    private static Label etiqueta(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-filtro");
        return etiqueta;
    }

    private TableView<Producto> crearTabla() {
        TableColumn<Producto, String> colProducto = columna("Producto", Producto::nombre, 260, String::valueOf, false);
        colProducto.setCellFactory(c -> new CeldaProducto());
        TableColumn<Producto, String> colTipo = Ui.columnaInsignia("Tipo", Producto::tipoProducto, 110, Ui::tonoTipo);
        colTipo.setSortable(true);
        TableColumn<Producto, BigDecimal> colPrecio = columna("Precio", Producto::precio, 100,
                p -> p.signum() > 0 ? Ui.soles(p) : "Sin precio", true);
        colPrecio.setCellFactory(c -> new CeldaConClase<>(p -> p.signum() > 0 ? Ui.soles(p) : "Sin precio",
                p -> p.signum() > 0 ? null : "sin-precio"));
        TableColumn<Producto, Integer> colStock = columna("Stock", Producto::stock, 80, String::valueOf, true);
        colStock.setCellFactory(c -> new CeldaStock());

        List<TableColumn<Producto, ?>> columnas = List.of(
                colProducto,
                columna("Presentación", Producto::presentacion, 110, String::valueOf, false),
                colTipo,
                columna("Origen", Producto::origen, 110, String::valueOf, false),
                colPrecio,
                colStock,
                columna("En cajas", p -> Ui.cajas(p.stock(), p.unidadesCaja()), 130, String::valueOf, true));
        tabla.getColumns().setAll(columnas);

        SortedList<Producto> ordenados = new SortedList<>(filtrados);
        ordenados.comparatorProperty().bind(tabla.comparatorProperty());
        tabla.setItems(ordenados);
        tabla.setPlaceholder(Ui.cargando("Cargando inventario…"));
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
        HBox leyenda = new HBox(14,
                muestra("ok", "Stock suficiente"),
                muestra("bajo", "Stock bajo"),
                muestra("sin", "Sin stock"));
        leyenda.setAlignment(Pos.CENTER_LEFT);
        leyenda.getStyleClass().add("leyenda");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);

        HBox pie = new HBox(16, lblResumen, leyenda, espacio);
        if (empleado.esAdministrador()) {
            Button btnPrecio = Ui.boton("Editar precio", "mdi2p-pencil-outline", "boton-primario");
            btnPrecio.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
            btnPrecio.setOnAction(e -> editarPrecio());
            pie.getChildren().add(btnPrecio);
        }
        pie.setAlignment(Pos.CENTER_LEFT);
        pie.setPadding(new Insets(10, 0, 0, 0));
        lblResumen.getStyleClass().add("estado");
        return pie;
    }

    private static HBox muestra(String clase, String texto) {
        Region color = new Region();
        color.getStyleClass().addAll("muestra-color", clase);
        HBox muestra = new HBox(6, color, new Label(texto));
        muestra.setAlignment(Pos.CENTER_LEFT);
        return muestra;
    }

    /** Solo ADMINISTRADOR: las presentaciones nuevas no se venden hasta tener precio. */
    private void editarPrecio() {
        Producto producto = tabla.getSelectionModel().getSelectedItem();
        if (producto == null) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog(producto.tienePrecio() ? producto.precio().toPlainString() : "");
        Ui.aplicarEstilos(dialogo);
        dialogo.initOwner(ventana());
        dialogo.setTitle("Editar precio");
        dialogo.setHeaderText(producto.nombreCompleto() + "\nPrecio actual: "
                + (producto.tienePrecio() ? Ui.soles(producto.precio()) : "sin precio"));
        dialogo.setGraphic(Ui.icono("mdi2p-pencil-outline", 28));
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
                            actualizado -> {
                                Ui.toast(this, "Precio actualizado: " + producto.nombreCompleto() + " → " + Ui.soles(precio));
                                recargar();
                            },
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
        lblTotal.setText(String.valueOf(productos.size()));
        lblUnidades.setText(String.format(Locale.forLanguageTag("es-PE"), "%,d", unidades));
        lblBajos.setText(String.valueOf(bajos));
        lblAgotados.setText(String.valueOf(agotados));
        lblSinPrecio.setText(String.valueOf(sinPrecio));
        lblResumen.setText(String.format("Mostrando %d de %d productos", filtrados.size(), productos.size()));
        tabla.setPlaceholder(productos.isEmpty()
                ? Ui.estadoVacio("mdi2p-package-variant", "Todavía no hay productos",
                        "Los productos se registran en el catálogo de la base de datos.")
                : Ui.estadoVacio("mdi2m-magnify", "Ningún producto coincide con los filtros",
                        "Cambie la búsqueda o desmarque \"Solo con stock\" / \"Solo stock bajo\"."));
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

    /** Nombre con punto de color según el stock y una insignia si está bajo o agotado. */
    private static final class CeldaProducto extends TableCell<Producto, String> {
        @Override
        protected void updateItem(String nombre, boolean vacio) {
            super.updateItem(nombre, vacio);
            setText(null);
            Producto p = vacio || getTableRow() == null ? null : getTableRow().getItem();
            if (p == null) {
                setGraphic(null);
                return;
            }
            Region punto = new Region();
            punto.getStyleClass().add("punto-stock");
            HBox caja = new HBox(8, punto, new Label(nombre));
            if (p.stock() == 0) {
                punto.getStyleClass().add("sin");
                caja.getChildren().add(Ui.insignia("AGOTADO", "peligro"));
            } else if (p.stockBajo()) {
                punto.getStyleClass().add("bajo");
                caja.getChildren().add(Ui.insignia("STOCK BAJO", "aviso"));
            }
            caja.setAlignment(Pos.CENTER_LEFT);
            setGraphic(caja);
        }
    }

    /** Stock en verde, ámbar si está bajo o rojo si es 0. */
    private static final class CeldaStock extends TableCell<Producto, Integer> {
        @Override
        protected void updateItem(Integer stock, boolean vacio) {
            super.updateItem(stock, vacio);
            getStyleClass().removeAll("stock-ok", "stock-bajo-texto", "stock-cero");
            Producto p = vacio || getTableRow() == null ? null : getTableRow().getItem();
            setText(vacio || stock == null ? null : String.valueOf(stock));
            if (p != null) {
                getStyleClass().add(p.stock() == 0 ? "stock-cero" : p.stockBajo() ? "stock-bajo-texto" : "stock-ok");
            }
        }
    }

    /** Celda de texto con una clase de estilo según el valor (null = sin clase). */
    private static final class CeldaConClase<T> extends TableCell<Producto, T> {
        private final Function<T, String> formato;
        private final Function<T, String> clase;
        private String claseActual;

        CeldaConClase(Function<T, String> formato, Function<T, String> clase) {
            this.formato = formato;
            this.clase = clase;
        }

        @Override
        protected void updateItem(T item, boolean vacio) {
            super.updateItem(item, vacio);
            if (claseActual != null) {
                getStyleClass().remove(claseActual);
                claseActual = null;
            }
            setText(vacio || item == null ? null : formato.apply(item));
            if (!vacio && item != null) {
                claseActual = clase.apply(item);
                if (claseActual != null) {
                    getStyleClass().add(claseActual);
                }
            }
        }
    }
}
