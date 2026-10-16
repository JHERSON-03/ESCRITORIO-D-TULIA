package pe.dtulia.desktop.vista;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import pe.dtulia.desktop.modelo.Empleado;
import pe.dtulia.desktop.modelo.Movimiento;
import pe.dtulia.desktop.modelo.MovimientoSolicitud;
import pe.dtulia.desktop.modelo.Producto;
import pe.dtulia.desktop.servicio.Servicios;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Entradas, mermas y ajustes de stock (POST /inventario/movimientos) y Kardex del producto.
 * Visible para ADMINISTRADOR y ALMACENERO; solo el administrador puede anular movimientos.
 * Las entradas se pueden registrar en cajas + unidades sueltas.
 */
public class FrmMovimientos extends BorderPane {

    private static final String ENTRADA = "ENTRADA";
    private static final String MERMA = "MERMA";
    private static final String AJUSTE = "AJUSTE";
    private static final Set<String> ANULABLES = Set.of(ENTRADA, MERMA, AJUSTE);
    private static final String TODOS = "TODOS";
    /** Opciones del filtro del Kardex: los tipos manuales más los que genera la base de datos. */
    private static final List<String> FILTROS_KARDEX = List.of(TODOS, ENTRADA, "ENTRADA_PRODUCCION", MERMA, AJUSTE,
            "SALIDA_VENTA", "SALIDA_VIAJE", "RETORNO_VIAJE", "ANULACION");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Servicios servicios;
    private final Empleado empleado;
    private final Runnable alCambiarStock;

    private final ComboBox<Producto> cboProducto = new ComboBox<>();
    private final ComboBox<String> cboTipo = new ComboBox<>(FXCollections.observableArrayList(ENTRADA, MERMA, AJUSTE));
    private final Label lblCantidad = new Label("Cantidad");
    private final Spinner<Integer> spnCantidad = new Spinner<>();
    private final CampoCajas campoCajas = new CampoCajas();
    private final TextField txtReferencia = new TextField();
    private final Label lblAyuda = new Label();
    private final Label lblStockActual = new Label("—");
    private final Button btnRegistrar = new Button("Registrar movimiento");
    private final ObservableList<Movimiento> movimientos = FXCollections.observableArrayList();
    private final FilteredList<Movimiento> movimientosVisibles = new FilteredList<>(movimientos);
    private final TableView<Movimiento> tablaKardex = new TableView<>(movimientosVisibles);
    private final ComboBox<String> cboFiltro = new ComboBox<>(FXCollections.observableArrayList(FILTROS_KARDEX));
    private final Button btnMostrar = new Button("Mostrar");
    private String filtroAplicado = TODOS;
    private final Label lblResumen = new Label();
    private final Button btnAnular = new Button("Anular movimiento seleccionado");

    public FrmMovimientos(Servicios servicios, Empleado empleado, Runnable alCambiarStock) {
        this.servicios = servicios;
        this.empleado = empleado;
        this.alCambiarStock = alCambiarStock;
        getStyleClass().add("formulario");

        setTop(crearFormulario());
        setCenter(crearKardex());

        cboTipo.valueProperty().addListener((obs, antes, ahora) -> adaptarAlTipo());
        cboProducto.valueProperty().addListener((obs, antes, ahora) -> {
            lblStockActual.setText(ahora == null ? "—"
                    : ahora.stock() + " unidades (" + Ui.cajas(ahora.stock(), ahora.unidadesCaja()) + ")");
            campoCajas.setProducto(ahora);
            cargarKardex();
            actualizarResumen();
        });
        cboTipo.setValue(ENTRADA);
        cargarProductos();
    }

    // ------------------------------------------------------------------ construcción

    private VBox crearFormulario() {
        cboProducto.setPromptText("Seleccione un producto");
        cboProducto.setMaxWidth(Double.MAX_VALUE);
        cboProducto.setVisibleRowCount(14);
        cboProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                return p == null ? "" : p.tipoProducto() + " · " + p.nombreCompleto();
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });
        cboTipo.setMaxWidth(Double.MAX_VALUE);
        spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1_000_000, 1));
        spnCantidad.setEditable(true);
        spnCantidad.setMaxWidth(Double.MAX_VALUE);
        txtReferencia.setMaxWidth(Double.MAX_VALUE);
        lblAyuda.getStyleClass().add("estado");
        lblAyuda.setWrapText(true);
        lblStockActual.getStyleClass().add("cliente");

        btnRegistrar.getStyleClass().add("boton-primario");
        btnRegistrar.setOnAction(e -> registrar());

        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(8);
        grilla.addRow(0, new Label("Producto"), cboProducto, new Label("Stock actual"), lblStockActual);
        ColumnConstraints etiqueta = new ColumnConstraints();
        ColumnConstraints campo = new ColumnConstraints(200, 300, Double.MAX_VALUE);
        campo.setHgrow(Priority.ALWAYS);
        grilla.getColumnConstraints().setAll(etiqueta, campo, etiqueta, campo);
        if (!empleado.esAdministrador()) {
            return Ui.seccion("Consultar Kardex", grilla); // el almacenero solo consulta
        }

        // ENTRADA: cajas + unidades; MERMA y AJUSTE: unidades
        HBox cantidad = new HBox(spnCantidad, campoCajas);
        cantidad.setAlignment(Pos.CENTER_LEFT);
        grilla.addRow(1, new Label("Movimiento"), cboTipo, lblCantidad, cantidad);
        grilla.addRow(2, new Label("Referencia"), txtReferencia);
        GridPane.setColumnSpan(txtReferencia, 3);
        grilla.add(lblAyuda, 1, 3, 3, 1);

        HBox acciones = new HBox(btnRegistrar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        return Ui.seccion("Registrar movimiento de stock", grilla, acciones);
    }

    private VBox crearKardex() {
        tablaKardex.getColumns().setAll(List.of(
                columna("Fecha", m -> m.fecha().atZoneSameInstant(ZoneId.systemDefault()).format(FECHA), 140, false),
                columna("Movimiento", Movimiento::tipo, 130, false),
                columna("Variación", m -> String.format("%+d", m.stockPosterior() - m.stockPrevio()), 90, true),
                columna("Stock previo", m -> String.valueOf(m.stockPrevio()), 100, true),
                columna("Stock final", m -> String.valueOf(m.stockPosterior()), 100, true),
                columna("Referencia", m -> m.referencia() == null ? "" : m.referencia(), 260, false)));
        tablaKardex.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tablaKardex, Priority.ALWAYS);

        btnAnular.setVisible(empleado.esAdministrador());
        btnAnular.setManaged(empleado.esAdministrador());
        btnAnular.disableProperty().bind(tablaKardex.getSelectionModel().selectedItemProperty().isNull());
        btnAnular.setOnAction(e -> anular());
        lblResumen.getStyleClass().add("estado");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(lblResumen, espacio, btnAnular);
        pie.setAlignment(Pos.CENTER_LEFT);

        movimientosVisibles.addListener((ListChangeListener<Movimiento>) cambio -> actualizarResumen());
        actualizarResumen();

        VBox kardex = Ui.seccion("Kardex del producto (más reciente primero)", crearFiltro(), tablaKardex, pie);
        VBox.setVgrow(kardex, Priority.ALWAYS);
        VBox contenedor = new VBox(kardex);
        contenedor.setPadding(new Insets(10, 0, 0, 0));
        return contenedor;
    }

    /** Desplegable con el tipo de movimiento y botón Mostrar (p. ej. ver solo las SALIDA_VENTA). */
    private HBox crearFiltro() {
        cboFiltro.setValue(TODOS);
        cboFiltro.setPrefWidth(200);
        btnMostrar.getStyleClass().add("boton-primario");
        btnMostrar.setOnAction(e -> mostrarFiltrado());
        HBox barra = new HBox(10, new Label("Tipo de movimiento"), cboFiltro, btnMostrar);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    /** Vuelve a leer el Kardex guardado y deja solo el tipo elegido en el desplegable. */
    private void mostrarFiltrado() {
        if (cboProducto.getValue() == null) {
            Ui.mostrarError(ventana(), "Seleccione un producto.");
            return;
        }
        String tipo = cboFiltro.getValue();
        filtroAplicado = tipo;
        movimientosVisibles.setPredicate(TODOS.equals(tipo) ? null : m -> tipo.equals(m.tipo()));
        cargarKardex();
    }

    private void actualizarResumen() {
        int total = movimientos.size();
        int visibles = movimientosVisibles.size();
        lblResumen.setText(total == 0 ? "" : String.format("Mostrando %d de %d movimientos", visibles, total));
        String aviso;
        if (cboProducto.getValue() == null) {
            aviso = "Seleccione un producto para ver sus movimientos";
        } else if (total == 0) {
            aviso = "Este producto aún no tiene movimientos";
        } else {
            aviso = "Este producto no tiene movimientos de tipo " + filtroAplicado;
        }
        tablaKardex.setPlaceholder(new Label(aviso));
    }

    // ------------------------------------------------------------------ datos

    /** Recarga los productos manteniendo seleccionado el actual (con su stock al día). */
    public void cargarProductos() {
        Ui.alCompletar(servicios.productos().listar(),
                lista -> {
                    Producto anterior = cboProducto.getValue();
                    List<Producto> ordenados = lista.stream()
                            .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombre)
                                    .thenComparingLong(Producto::presentacionId))
                            .toList();
                    cboProducto.getItems().setAll(ordenados);
                    if (anterior != null) {
                        ordenados.stream().filter(p -> p.id() == anterior.id()).findFirst()
                                .ifPresent(cboProducto::setValue);
                    }
                },
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void cargarKardex() {
        Producto producto = cboProducto.getValue();
        if (producto == null) {
            movimientos.clear();
            return;
        }
        Ui.alCompletar(servicios.inventario().kardex(producto.id()), movimientos::setAll,
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    // ------------------------------------------------------------------ acciones

    private void adaptarAlTipo() {
        String tipo = cboTipo.getValue();
        boolean ajuste = AJUSTE.equals(tipo);
        boolean entrada = ENTRADA.equals(tipo);
        lblCantidad.setText(ajuste ? "Stock contado" : "Cantidad");
        spnCantidad.setVisible(!entrada);
        spnCantidad.setManaged(!entrada);
        campoCajas.setVisible(entrada);
        campoCajas.setManaged(entrada);
        txtReferencia.setPromptText(switch (tipo) {
            case MERMA -> "Motivo (obligatorio): rotura, vencimiento, degustación…";
            case AJUSTE -> "Motivo (opcional): inventario físico de fin de mes…";
            default -> "Lote o guía (opcional)";
        });
        lblAyuda.setText(switch (tipo) {
            case MERMA -> "Resta unidades perdidas o dañadas.";
            case AJUSTE -> "Reemplaza el stock del sistema por la cantidad contada físicamente.";
            default -> "Suma unidades que ingresan al almacén (producción o compra).";
        });
    }

    private void registrar() {
        Producto producto = cboProducto.getValue();
        String tipo = cboTipo.getValue();
        if (producto == null) {
            Ui.mostrarError(ventana(), "Seleccione un producto.");
            return;
        }
        boolean ajuste = AJUSTE.equals(tipo);
        int valor = ENTRADA.equals(tipo) ? campoCajas.total() : Ui.leerEntero(spnCantidad);
        if (valor < (ajuste ? 0 : 1)) {
            Ui.mostrarError(ventana(), ajuste ? "Ingrese el stock contado (0 o más)." : "Ingrese una cantidad mayor a 0.");
            return;
        }
        String referencia = txtReferencia.getText().trim();
        if (MERMA.equals(tipo) && referencia.isEmpty()) {
            Ui.mostrarError(ventana(), "Indique el motivo de la merma.");
            return;
        }

        MovimientoSolicitud solicitud = new MovimientoSolicitud(producto.id(), tipo,
                ajuste ? null : valor, ajuste ? valor : null, referencia.isEmpty() ? null : referencia);

        btnRegistrar.setDisable(true);
        Ui.alCompletar(servicios.inventario().registrarMovimiento(solicitud),
                movimiento -> {
                    btnRegistrar.setDisable(false);
                    Ui.mostrarInfo(ventana(), "Movimiento registrado", String.format(
                            "%s de %s%nStock: %d → %d", movimiento.tipo(), producto.nombreCompleto(),
                            movimiento.stockPrevio(), movimiento.stockPosterior()));
                    txtReferencia.clear();
                    spnCantidad.getValueFactory().setValue(1);
                    campoCajas.limpiar();
                    refrescarTodo();
                },
                mensaje -> {
                    btnRegistrar.setDisable(false);
                    Ui.mostrarError(ventana(), mensaje);
                });
    }

    private void anular() {
        Movimiento movimiento = tablaKardex.getSelectionModel().getSelectedItem();
        if (movimiento == null) {
            return;
        }
        if (!ANULABLES.contains(movimiento.tipo())) {
            Ui.mostrarError(ventana(), "Solo se pueden anular entradas, mermas y ajustes. Las ventas se corrigen "
                    + "desde su comprobante, los viajes desde la pestaña Viajes y lo recibido de la fábrica con un ajuste.");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("¿Anular el movimiento #%d (%s, %+d unidades)?%nSe registrará una ANULACION que revierte el stock.",
                        movimiento.id(), movimiento.tipo(), movimiento.stockPosterior() - movimiento.stockPrevio()),
                ButtonType.YES, ButtonType.NO);
        confirmacion.initOwner(ventana());
        confirmacion.setHeaderText("Confirmar anulación");
        if (confirmacion.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        Ui.alCompletar(servicios.inventario().anularMovimiento(movimiento.id()),
                anulacion -> refrescarTodo(),
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void refrescarTodo() {
        cargarProductos();
        cargarKardex();
        alCambiarStock.run();
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static TableColumn<Movimiento, String> columna(String titulo, Function<Movimiento, String> valor,
                                                          double ancho, boolean numerica) {
        TableColumn<Movimiento, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                setText(vacio ? null : texto);
            }
        });
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        if (numerica) {
            columna.getStyleClass().add("numero");
        }
        return columna;
    }
}
