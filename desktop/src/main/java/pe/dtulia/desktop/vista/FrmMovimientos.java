package pe.dtulia.desktop.vista;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
import pe.dtulia.desktop.modelo.TipoProducto;
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

    private List<Producto> productos = List.of();
    /** Tipo → nombre → presentación: cada presentación es un producto distinto (cboProducto). */
    private final ComboBox<TipoProducto> cboTipoProducto = new ComboBox<>();
    private final ComboBox<String> cboNombre = new ComboBox<>();
    private final ComboBox<Producto> cboProducto = new ComboBox<>();
    /** Mientras se rellenan los desplegables en cadena, sus oyentes no deben volver a dispararla. */
    private boolean encadenando;
    private final ComboBox<String> cboTipo = new ComboBox<>(FXCollections.observableArrayList(ENTRADA, MERMA, AJUSTE));
    private final Label lblCantidad = new Label("Cantidad");
    private final Spinner<Integer> spnCantidad = new Spinner<>();
    private final CampoCajas campoCajas = new CampoCajas();
    private final TextField txtReferencia = new TextField();
    private final Label lblAyuda = new Label();
    private final Label lblStockActual = new Label("—");
    private final Button btnRegistrar = Ui.boton("Registrar movimiento", "mdi2c-content-save-outline", "boton-primario");
    private final ProgressIndicator progresoRegistro = new ProgressIndicator();
    private final ObservableList<Movimiento> movimientos = FXCollections.observableArrayList();
    private final FilteredList<Movimiento> movimientosVisibles = new FilteredList<>(movimientos);
    private final TableView<Movimiento> tablaKardex = new TableView<>(movimientosVisibles);
    private final ComboBox<String> cboFiltro = new ComboBox<>(FXCollections.observableArrayList(FILTROS_KARDEX));
    private final Button btnMostrar = Ui.boton("Mostrar", "mdi2f-filter-outline");
    private String filtroAplicado = TODOS;
    private final Label lblResumen = new Label();
    private final Label lblEntradas = Ui.insignia("", "exito");
    private final Label lblSalidas = Ui.insignia("", "peligro");
    private final Button btnAnular = Ui.boton("Anular movimiento seleccionado", "mdi2c-cancel", "boton-peligro");

    public FrmMovimientos(Servicios servicios, Empleado empleado, Runnable alCambiarStock) {
        this.servicios = servicios;
        this.empleado = empleado;
        this.alCambiarStock = alCambiarStock;
        getStyleClass().add("formulario");

        setTop(crearFormulario());
        setCenter(crearKardex());

        cboTipo.valueProperty().addListener((obs, antes, ahora) -> adaptarAlTipo());
        cboTipoProducto.valueProperty().addListener((obs, antes, ahora) -> {
            if (!encadenando) {
                mostrarNombresDelTipo(cboProducto.getValue());
            }
        });
        cboNombre.valueProperty().addListener((obs, antes, ahora) -> {
            if (!encadenando) {
                mostrarPresentaciones(cboProducto.getValue());
            }
        });
        cboProducto.valueProperty().addListener((obs, antes, ahora) -> {
            lblStockActual.setText(ahora == null ? "—"
                    : ahora.stock() + " unidades (" + Ui.cajas(ahora.stock(), ahora.unidadesCaja()) + ")");
            campoCajas.setProducto(ahora);
            cargarKardex();
            actualizarResumen();
        });
        cboTipo.setValue(ENTRADA);
        if (empleado.esAdministrador()) {
            Ui.atajo(this, new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), () -> {
                if (!btnRegistrar.isDisabled()) {
                    registrar();
                }
            });
        }
        cargarProductos();
    }

    // ------------------------------------------------------------------ construcción

    private VBox crearFormulario() {
        cboTipoProducto.setPromptText("Seleccione un tipo");
        cboTipoProducto.setMaxWidth(Double.MAX_VALUE);
        cboTipoProducto.setVisibleRowCount(14);
        cboNombre.setPromptText("Seleccione un producto");
        cboNombre.setMaxWidth(Double.MAX_VALUE);
        cboNombre.setVisibleRowCount(14);
        cboNombre.disableProperty().bind(cboTipoProducto.valueProperty().isNull());
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
        cboTipo.setMaxWidth(Double.MAX_VALUE);
        spnCantidad.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1_000_000, 1));
        spnCantidad.setEditable(true);
        spnCantidad.setMaxWidth(Double.MAX_VALUE);
        txtReferencia.setMaxWidth(Double.MAX_VALUE);
        lblAyuda.setGraphic(Ui.icono("mdi2i-information-outline"));
        lblAyuda.getStyleClass().add("ayuda");
        lblAyuda.setWrapText(true);
        lblStockActual.getStyleClass().add("stock-actual");
        lblStockActual.setGraphic(Ui.icono("mdi2p-package-variant-closed"));

        btnRegistrar.setTooltip(new Tooltip("Ctrl+Enter"));
        btnRegistrar.setOnAction(e -> registrar());
        progresoRegistro.setPrefSize(16, 16);
        progresoRegistro.setMaxSize(16, 16);
        lblCantidad.getStyleClass().add("etiqueta-filtro");

        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(8);
        grilla.addRow(0, etiqueta("Tipo de producto"), cboTipoProducto);
        HBox producto = new HBox(8, cboNombre, cboProducto);
        grilla.addRow(1, etiqueta("Producto"), producto, etiqueta("Stock actual"), lblStockActual);
        ColumnConstraints etiqueta = new ColumnConstraints();
        ColumnConstraints campo = new ColumnConstraints(200, 300, Double.MAX_VALUE);
        campo.setHgrow(Priority.ALWAYS);
        grilla.getColumnConstraints().setAll(etiqueta, campo, etiqueta, campo);
        if (!empleado.esAdministrador()) {
            // El almacenero solo consulta
            VBox consulta = Ui.seccion("Consultar Kardex", "mdi2h-history", grilla);
            Label soloConsulta = new Label("Modo consulta: los movimientos los registra la administradora.",
                    Ui.icono("mdi2l-lock-outline"));
            soloConsulta.getStyleClass().add("ayuda");
            consulta.getChildren().add(soloConsulta);
            return consulta;
        }

        // ENTRADA: cajas + unidades; MERMA y AJUSTE: unidades
        HBox cantidad = new HBox(spnCantidad, campoCajas);
        cantidad.setAlignment(Pos.CENTER_LEFT);
        // Tipo y cantidad en la misma fila, ocupando las tres columnas para que la cantidad no se recorte
        HBox movimiento = new HBox(16, crearSegmentoTipo(), lblCantidad, cantidad);
        movimiento.setAlignment(Pos.CENTER_LEFT);
        grilla.addRow(2, etiqueta("Movimiento"), movimiento);
        GridPane.setColumnSpan(movimiento, 3);
        grilla.addRow(3, etiqueta("Referencia"), txtReferencia);
        GridPane.setColumnSpan(txtReferencia, 3);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox acciones = new HBox(10, lblAyuda, espacio, btnRegistrar);
        acciones.setAlignment(Pos.CENTER_LEFT);
        return Ui.seccion("Registrar movimiento de stock", "mdi2s-swap-horizontal", grilla, acciones);
    }

    /** Entrada / Merma / Ajuste como botones segmentados; manejan el mismo cboTipo que usa la lógica. */
    private HBox crearSegmentoTipo() {
        ToggleGroup grupo = new ToggleGroup();
        HBox segmento = new HBox();
        segmento.getStyleClass().add("segmento");
        String[][] opciones = {
                {ENTRADA, "Entrada", "mdi2p-plus-circle-outline"},
                {MERMA, "Merma", "mdi2m-minus-circle-outline"},
                {AJUSTE, "Ajuste", "mdi2t-tune-variant"}};
        for (String[] opcion : opciones) {
            ToggleButton boton = new ToggleButton(opcion[1], Ui.icono(opcion[2]));
            boton.setToggleGroup(grupo);
            boton.setUserData(opcion[0]);
            boton.setMinWidth(Region.USE_PREF_SIZE);
            boton.setSelected(opcion[0].equals(cboTipo.getValue()));
            boton.setOnAction(e -> {
                if (!boton.isSelected()) {
                    boton.setSelected(true); // siempre queda uno elegido
                }
                cboTipo.setValue(opcion[0]);
            });
            segmento.getChildren().add(boton);
        }
        cboTipo.valueProperty().addListener((obs, antes, ahora) -> grupo.getToggles()
                .forEach(t -> t.setSelected(t.getUserData().equals(ahora))));
        return segmento;
    }

    private static Label etiqueta(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-filtro");
        return etiqueta;
    }

    private VBox crearKardex() {
        tablaKardex.getColumns().setAll(List.of(
                columna("Fecha", m -> m.fecha().atZoneSameInstant(ZoneId.systemDefault()).format(FECHA), 140, false),
                Ui.columnaInsignia("Movimiento", Movimiento::tipo, 170, FrmMovimientos::tonoMovimiento),
                columnaVariacion(),
                columna("Stock previo", m -> String.valueOf(m.stockPrevio()), 100, true),
                columna("Stock final", m -> String.valueOf(m.stockPosterior()), 100, true),
                columna("Referencia", m -> m.referencia() == null ? "" : m.referencia(), 260, false)));
        tablaKardex.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaKardex.setMinHeight(150);
        VBox.setVgrow(tablaKardex, Priority.ALWAYS);

        btnAnular.setVisible(empleado.esAdministrador());
        btnAnular.setManaged(empleado.esAdministrador());
        btnAnular.disableProperty().bind(tablaKardex.getSelectionModel().selectedItemProperty().isNull());
        btnAnular.setOnAction(e -> anular());
        lblResumen.getStyleClass().add("estado");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(10, lblResumen, lblEntradas, lblSalidas, espacio, btnAnular);
        pie.setAlignment(Pos.CENTER_LEFT);

        movimientosVisibles.addListener((ListChangeListener<Movimiento>) cambio -> actualizarResumen());
        actualizarResumen();

        VBox kardex = Ui.seccion("Kardex del producto (más reciente primero)", "mdi2h-history", crearFiltro(),
                tablaKardex, pie);
        VBox.setVgrow(kardex, Priority.ALWAYS);
        VBox contenedor = new VBox(kardex);
        contenedor.setPadding(new Insets(10, 0, 0, 0));
        return contenedor;
    }

    /** Desplegable con el tipo de movimiento y botón Mostrar (p. ej. ver solo las SALIDA_VENTA). */
    private HBox crearFiltro() {
        cboFiltro.setValue(TODOS);
        cboFiltro.setPrefWidth(200);
        btnMostrar.setOnAction(e -> mostrarFiltrado());
        HBox barra = new HBox(10, etiqueta("Tipo de movimiento"), cboFiltro, btnMostrar);
        barra.setAlignment(Pos.CENTER_LEFT);
        return barra;
    }

    /** Vuelve a leer el Kardex guardado y deja solo el tipo elegido en el desplegable. */
    private void mostrarFiltrado() {
        if (cboProducto.getValue() == null) {
            Ui.mostrarError(ventana(), cboNombre.getValue() == null ? "Seleccione un producto." : "Seleccione la presentación.");
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
        // Suma de lo que entró y salió entre los movimientos visibles
        int entradas = movimientosVisibles.stream().mapToInt(FrmMovimientos::variacion).filter(v -> v > 0).sum();
        int salidas = movimientosVisibles.stream().mapToInt(FrmMovimientos::variacion).filter(v -> v < 0).sum();
        lblEntradas.setText("Entradas +" + entradas);
        lblSalidas.setText("Salidas " + salidas);
        lblEntradas.setVisible(visibles > 0);
        lblSalidas.setVisible(visibles > 0);
        if (cboProducto.getValue() == null) {
            tablaKardex.setPlaceholder(Ui.estadoVacio("mdi2h-history", "Seleccione un producto",
                    "Elija el tipo, el producto y la presentación para ver sus movimientos."));
        } else if (total == 0) {
            tablaKardex.setPlaceholder(Ui.estadoVacio("mdi2p-package-variant", "Este producto aún no tiene movimientos",
                    null));
        } else {
            tablaKardex.setPlaceholder(Ui.estadoVacio("mdi2f-filter-outline",
                    "No hay movimientos de tipo " + filtroAplicado, "Elija \"TODOS\" y presione Mostrar."));
        }
    }

    private static int variacion(Movimiento m) {
        return m.stockPosterior() - m.stockPrevio();
    }

    /** Verde lo que suma, rojo lo que se pierde, ámbar los ajustes, azul las salidas normales. */
    private static String tonoMovimiento(String tipo) {
        return switch (tipo) {
            case ENTRADA, "ENTRADA_PRODUCCION", "RETORNO_VIAJE" -> "exito";
            case MERMA -> "peligro";
            case AJUSTE -> "aviso";
            case "SALIDA_VENTA", "SALIDA_VIAJE" -> "info";
            default -> "neutro";
        };
    }

    /** Variación con signo: verde si suma, rojo si resta. */
    private static TableColumn<Movimiento, Integer> columnaVariacion() {
        TableColumn<Movimiento, Integer> columna = new TableColumn<>("Variación");
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(variacion(d.getValue())));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Integer valor, boolean vacio) {
                super.updateItem(valor, vacio);
                getStyleClass().removeAll("texto-ok", "texto-alerta");
                setText(vacio || valor == null ? null : String.format("%+d", valor));
                if (!vacio && valor != null && valor != 0) {
                    getStyleClass().add(valor > 0 ? "texto-ok" : "texto-alerta");
                }
            }
        });
        columna.setPrefWidth(90);
        columna.setSortable(false);
        columna.getStyleClass().add("numero");
        return columna;
    }

    // ------------------------------------------------------------------ datos

    /** Recarga los productos manteniendo seleccionados el tipo y el producto actuales (con su stock al día). */
    public void cargarProductos() {
        Ui.alCompletar(servicios.productos().listar(),
                lista -> {
                    Producto productoAnterior = cboProducto.getValue();
                    productos = lista.stream()
                            .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombre)
                                    .thenComparingLong(Producto::presentacionId))
                            .toList();
                    // Solo los tipos que tienen productos
                    List<TipoProducto> tipos = productos.stream()
                            .map(p -> new TipoProducto(p.tipoProductoId(), p.tipoProducto(), null))
                            .distinct()
                            .toList();
                    TipoProducto tipoAnterior = cboTipoProducto.getValue();
                    encadenando = true;
                    cboTipoProducto.getItems().setAll(tipos);
                    cboTipoProducto.setValue(tipoAnterior != null && tipos.contains(tipoAnterior) ? tipoAnterior : null);
                    encadenando = false;
                    mostrarNombresDelTipo(productoAnterior);
                },
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    /** Llena los nombres del tipo elegido, conservando el de {@code anterior} si es de ese tipo. */
    private void mostrarNombresDelTipo(Producto anterior) {
        TipoProducto tipo = cboTipoProducto.getValue();
        List<String> nombres = tipo == null ? List.of()
                : productos.stream().filter(p -> p.tipoProductoId() == tipo.id()).map(Producto::nombre).distinct().toList();
        String nombreAnterior = anterior != null ? anterior.nombre() : cboNombre.getValue();
        encadenando = true;
        cboNombre.getItems().setAll(nombres);
        // List.of() no admite contains(null)
        cboNombre.setValue(nombreAnterior != null && nombres.contains(nombreAnterior) ? nombreAnterior : null);
        encadenando = false;
        mostrarPresentaciones(anterior);
    }

    /** Llena las presentaciones del producto elegido; si solo tiene una, queda elegida. */
    private void mostrarPresentaciones(Producto anterior) {
        TipoProducto tipo = cboTipoProducto.getValue();
        String nombre = cboNombre.getValue();
        List<Producto> presentaciones = tipo == null || nombre == null ? List.of()
                : productos.stream().filter(p -> p.tipoProductoId() == tipo.id() && p.nombre().equals(nombre)).toList();
        Producto elegido = anterior == null ? null
                : presentaciones.stream().filter(p -> p.id() == anterior.id()).findFirst().orElse(null);
        if (elegido == null && presentaciones.size() == 1) {
            elegido = presentaciones.get(0);
        }
        cboProducto.getItems().setAll(presentaciones);
        cboProducto.setValue(elegido);
    }

    private void cargarKardex() {
        Producto producto = cboProducto.getValue();
        if (producto == null) {
            movimientos.clear();
            return;
        }
        tablaKardex.setPlaceholder(Ui.cargando("Cargando Kardex…"));
        Ui.alCompletar(servicios.inventario().kardex(producto.id()),
                lista -> {
                    movimientos.setAll(lista);
                    actualizarResumen();
                },
                mensaje -> {
                    actualizarResumen();
                    Ui.mostrarError(ventana(), mensaje);
                });
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
            Ui.mostrarError(ventana(), cboNombre.getValue() == null ? "Seleccione un producto." : "Seleccione la presentación.");
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

        ocupado(true);
        Ui.alCompletar(servicios.inventario().registrarMovimiento(solicitud),
                movimiento -> {
                    ocupado(false);
                    Ui.toast(this, String.format("Movimiento guardado · %s de %s · Stock %d → %d", movimiento.tipo(),
                            producto.nombreCompleto(), movimiento.stockPrevio(), movimiento.stockPosterior()));
                    txtReferencia.clear();
                    spnCantidad.getValueFactory().setValue(1);
                    campoCajas.limpiar();
                    refrescarTodo();
                },
                mensaje -> {
                    ocupado(false);
                    Ui.mostrarError(ventana(), mensaje);
                });
    }

    private void ocupado(boolean ocupado) {
        btnRegistrar.setDisable(ocupado);
        btnRegistrar.setText(ocupado ? "Guardando…" : "Registrar movimiento");
        btnRegistrar.setGraphic(ocupado ? progresoRegistro : Ui.icono("mdi2c-content-save-outline"));
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

        if (!Ui.confirmar(ventana(), "¿Anular el movimiento #" + movimiento.id() + "?",
                String.format("%s, %+d unidades.%nSe registrará una ANULACION que revierte el stock. "
                        + "Esta acción no se puede deshacer.", movimiento.tipo(), variacion(movimiento)),
                "Anular movimiento")) {
            return;
        }

        Ui.alCompletar(servicios.inventario().anularMovimiento(movimiento.id()),
                anulacion -> {
                    Ui.toast(this, "Movimiento #" + movimiento.id() + " anulado: el stock se revirtió");
                    refrescarTodo();
                },
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
