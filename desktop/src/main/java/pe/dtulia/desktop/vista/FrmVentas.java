package pe.dtulia.desktop.vista;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import pe.dtulia.desktop.modelo.Cliente;
import pe.dtulia.desktop.modelo.ClienteNuevo;
import pe.dtulia.desktop.modelo.ItemVenta;
import pe.dtulia.desktop.modelo.MetodoPago;
import pe.dtulia.desktop.modelo.Producto;
import pe.dtulia.desktop.modelo.TipoComprobante;
import pe.dtulia.desktop.modelo.VentaSolicitud;
import pe.dtulia.desktop.servicio.Servicios;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Registro de ventas: arma el pedido y lo envía a POST /comprobantes. Los productos se eligen en una
 * tabla de precios y stock (una fila por licor, una columna por presentación) y la cantidad se ingresa
 * en cajas + unidades. El precio es siempre por unidad.
 */
public class FrmVentas extends BorderPane {

    private static final String CLIENTES_VARIOS = "CLIENTES VARIOS";
    private static final String CANAL_POR_DEFECTO = "TIENDA";
    private static final String NO_DISPONIBLE = "—";

    /** Línea del carrito. El precio mostrado es referencial: el definitivo lo fija la base de datos. */
    record LineaVenta(Producto producto, int cantidad) {
        BigDecimal subtotal() {
            return producto.precio().multiply(BigDecimal.valueOf(cantidad));
        }
    }

    /** Fila de la tabla de elección: un licor y cada una de sus presentaciones (cada una es un producto). */
    record FilaLicor(String nombre, String tipo, Map<Long, Producto> presentaciones) {
        boolean tieneStock() {
            return presentaciones.values().stream().anyMatch(p -> p.stock() > 0);
        }
    }

    private final Servicios servicios;
    private final Runnable alRegistrarVenta;

    private final ComboBox<TipoComprobante> cboTipo = new ComboBox<>();
    private final ComboBox<MetodoPago> cboMetodo = new ComboBox<>();
    private final ComboBox<String> cboCanal = new ComboBox<>();
    private final TextField txtDocumento = new TextField();
    private final Button btnBuscarCliente = Ui.boton("Buscar", "mdi2a-account-search-outline");
    private final Label lblCliente = new Label(CLIENTES_VARIOS, Ui.icono("mdi2a-account-outline"));

    private final TextField txtBuscar = new TextField();
    private final HBox botonesTipo = new HBox(6);
    private final ToggleGroup grupoTipo = new ToggleGroup();
    private final ObservableList<FilaLicor> filas = FXCollections.observableArrayList();
    private final FilteredList<FilaLicor> filasVisibles = new FilteredList<>(filas);
    private final TableView<FilaLicor> tablaProductos = new TableView<>(filasVisibles);
    private final Label lblSeleccionado = new Label();
    private final HBox cajaSeleccion = new HBox(8);
    private final CampoCajas campoCajas = new CampoCajas();
    private Producto seleccionado;

    private final ObservableList<LineaVenta> carrito = FXCollections.observableArrayList();
    private final TableView<LineaVenta> tablaCarrito = new TableView<>(carrito);
    private final Label lblSubtotal = new Label();
    private final Label lblIgv = new Label();
    private final Label lblTotal = new Label();
    private final Button btnEmitir = Ui.boton("Emitir comprobante", "mdi2r-receipt", "boton-primario", "boton-grande");
    private final ProgressIndicator progresoEmision = new ProgressIndicator();

    private Cliente cliente; // null = CLIENTES VARIOS

    public FrmVentas(Servicios servicios, Runnable alRegistrarVenta) {
        this.servicios = servicios;
        this.alRegistrarVenta = alRegistrarVenta;
        getStyleClass().add("formulario");

        // Izquierda: elegir productos (toda la altura). Derecha: el "ticket" (comprobante, pedido y totales)
        VBox ticket = new VBox(10, crearDatosComprobante(), crearPedido());
        ticket.setMinWidth(320);
        ScrollPane panelTicket = Ui.desplazable(ticket);
        SplitPane columnas = new SplitPane(Ui.desplazable(crearEleccion()), panelTicket);
        columnas.getStyleClass().add("columnas");
        columnas.setDividerPositions(0.6);
        SplitPane.setResizableWithParent(panelTicket, false);
        setCenter(columnas);

        carrito.addListener((ListChangeListener<LineaVenta>) c -> actualizarTotales());
        cboTipo.valueProperty().addListener((obs, antes, ahora) -> actualizarTotales());
        txtBuscar.textProperty().addListener((obs, antes, ahora) -> aplicarFiltro());

        // Atajos: F2 busca, Ctrl+Enter emite (Enter agrega: lo atienden la tabla y la cantidad)
        Ui.atajo(this, new KeyCodeCombination(KeyCode.F2), () -> {
            txtBuscar.requestFocus();
            txtBuscar.selectAll();
        });
        Ui.atajo(this, new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!btnEmitir.isDisabled()) {
                emitir();
            }
        });

        actualizarTotales();
        seleccionar(null);
        cargarCatalogos();
    }

    // ------------------------------------------------------------------ construcción

    private VBox crearDatosComprobante() {
        cboTipo.setMaxWidth(Double.MAX_VALUE);
        cboMetodo.setMaxWidth(Double.MAX_VALUE);
        cboCanal.setMaxWidth(Double.MAX_VALUE);
        txtDocumento.setPromptText("DNI o RUC (vacío = clientes varios)");
        HBox.setHgrow(txtDocumento, Priority.ALWAYS);
        txtDocumento.setOnAction(e -> buscarCliente());
        btnBuscarCliente.setOnAction(e -> buscarCliente());
        lblCliente.getStyleClass().add("cliente");

        // Comprobante, pago y canal en una fila (etiqueta arriba) para que el ticket entre sin desplazarse
        GridPane grilla = new GridPane();
        grilla.setHgap(8);
        grilla.setVgap(4);
        grilla.addRow(0, etiqueta("Comprobante"), etiqueta("Pago"), etiqueta("Canal"));
        grilla.addRow(1, cboTipo, cboMetodo, cboCanal);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints columna = new ColumnConstraints(90, 120, Double.MAX_VALUE);
            columna.setHgrow(Priority.ALWAYS);
            grilla.getColumnConstraints().add(columna);
        }
        HBox filaCliente = new HBox(6, txtDocumento, btnBuscarCliente);
        VBox cliente = new VBox(4, etiqueta("Cliente"), filaCliente, lblCliente);

        return Ui.seccion("Comprobante", "mdi2r-receipt", grilla, cliente);
    }

    private static Label etiqueta(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-filtro");
        return etiqueta;
    }

    /** Tabla de precios y stock: clic en una celda la elige; doble clic o Enter la agrega. */
    private VBox crearEleccion() {
        txtBuscar.setPromptText("Buscar licor…  (F2)");
        txtBuscar.setPrefWidth(190);
        txtBuscar.setMinWidth(130);
        StackPane buscador = new StackPane(txtBuscar);
        FontIcon lupa = Ui.icono("mdi2m-magnify");
        lupa.setMouseTransparent(true);
        buscador.getChildren().add(lupa);
        StackPane.setAlignment(lupa, Pos.CENTER_LEFT);
        StackPane.setMargin(lupa, new Insets(0, 0, 0, 10));
        buscador.getStyleClass().add("buscador");
        Button btnActualizar = Ui.boton(null, "mdi2r-refresh");
        btnActualizar.setTooltip(new Tooltip("Actualizar precios y stock"));
        btnActualizar.setMinWidth(Region.USE_PREF_SIZE);
        btnActualizar.setOnAction(e -> cargarProductos());
        botonesTipo.setAlignment(Pos.CENTER_LEFT);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, buscador, botonesTipo, espacio, btnActualizar);
        barra.setAlignment(Pos.CENTER_LEFT);
        tablaProductos.getStyleClass().add("tabla-precios");

        tablaProductos.getSelectionModel().setCellSelectionEnabled(true);
        tablaProductos.getSelectionModel().getSelectedCells()
                .addListener((ListChangeListener<TablePosition>) c -> alSeleccionarCelda());
        tablaProductos.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && seleccionado != null) {
                agregarProducto();
            }
        });
        tablaProductos.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && seleccionado != null) {
                agregarProducto();
            }
        });
        tablaProductos.setPlaceholder(Ui.cargando("Cargando productos…"));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaProductos.setMinHeight(160);
        VBox.setVgrow(tablaProductos, Priority.ALWAYS);

        lblSeleccionado.getStyleClass().add("cliente");
        lblSeleccionado.setWrapText(true);
        lblSeleccionado.setMaxWidth(Double.MAX_VALUE);
        cajaSeleccion.getChildren().setAll(Ui.icono("mdi2b-bottle-wine-outline"), lblSeleccionado);
        cajaSeleccion.setAlignment(Pos.CENTER_LEFT);
        cajaSeleccion.getStyleClass().add("seleccion-producto");
        campoCajas.setAlConfirmar(this::agregarProducto);
        Button btnAgregar = Ui.boton("Agregar", "mdi2c-cart-plus", "boton-primario");
        btnAgregar.setTooltip(new Tooltip("Enter"));
        btnAgregar.setOnAction(e -> agregarProducto());
        HBox filaCantidad = new HBox(10, campoCajas, btnAgregar);
        filaCantidad.setAlignment(Pos.CENTER_LEFT);

        Label ayuda = new Label("Clic en un precio para elegirlo · Enter o doble clic lo agrega · "
                + "Ctrl+Enter emite · F2 busca", Ui.icono("mdi2k-keyboard-outline"));
        ayuda.getStyleClass().add("ayuda");
        ayuda.setWrapText(true);
        VBox eleccion = Ui.seccion("Elegir producto", "mdi2b-bottle-wine-outline", barra, tablaProductos, ayuda,
                cajaSeleccion, filaCantidad);
        eleccion.setMinWidth(420);
        return eleccion;
    }

    /** Pedido, totales y emisión: todo junto para revisar y emitir sin mover la vista. */
    private VBox crearPedido() {
        tablaCarrito.getColumns().setAll(List.of(
                columnaProductoCarrito(),
                columna("Cant.", l -> String.valueOf(l.cantidad()), 60, true),
                columna("Subtotal", l -> Ui.soles(l.subtotal()), 100, true)));
        // El precio unitario se ve al pasar el mouse sobre la línea
        tablaCarrito.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(LineaVenta linea, boolean vacio) {
                super.updateItem(linea, vacio);
                setTooltip(vacio || linea == null ? null
                        : new Tooltip(Ui.soles(linea.producto().precio()) + " c/u × " + linea.cantidad()));
            }
        });
        tablaCarrito.setPlaceholder(Ui.estadoVacio("mdi2c-cart-outline", "El pedido está vacío",
                "Elija un precio en la tabla y presione Enter para agregarlo."));
        tablaCarrito.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tablaCarrito.setMinHeight(170);
        tablaCarrito.setPrefHeight(150);
        VBox.setVgrow(tablaCarrito, Priority.ALWAYS);

        Runnable quitar = () -> {
            LineaVenta linea = tablaCarrito.getSelectionModel().getSelectedItem();
            if (linea != null) {
                carrito.remove(linea);
            }
        };
        tablaCarrito.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) {
                quitar.run();
            }
        });
        Button btnQuitar = Ui.boton("Quitar", "mdi2d-delete-outline");
        btnQuitar.setTooltip(new Tooltip("Quitar la línea seleccionada (Supr)"));
        btnQuitar.disableProperty().bind(tablaCarrito.getSelectionModel().selectedItemProperty().isNull());
        btnQuitar.setOnAction(e -> quitar.run());
        Button btnLimpiar = Ui.boton("Limpiar", "mdi2b-broom");
        btnLimpiar.setOnAction(e -> {
            if (carrito.isEmpty() || Ui.confirmar(ventana(), "¿Limpiar la venta?",
                    "Se quitarán los " + carrito.size() + " productos del pedido y el cliente elegido.", "Limpiar")) {
                limpiar();
            }
        });
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox acciones = new HBox(10, btnQuitar, espacio, btnLimpiar);

        lblTotal.getStyleClass().add("total");
        lblSubtotal.getStyleClass().add("importe");
        lblIgv.getStyleClass().add("importe");
        Label etiquetaTotal = new Label("TOTAL A PAGAR");
        etiquetaTotal.getStyleClass().add("etiqueta-total");
        GridPane importes = new GridPane();
        importes.setHgap(12);
        importes.setVgap(2);
        importes.addRow(0, new Label("Op. gravada"), lblSubtotal);
        importes.addRow(1, new Label("IGV"), lblIgv);
        importes.addRow(2, etiquetaTotal, lblTotal);
        ColumnConstraints colEtiqueta = new ColumnConstraints();
        colEtiqueta.setHgrow(Priority.ALWAYS);
        ColumnConstraints colImporte = new ColumnConstraints();
        colImporte.setHalignment(HPos.RIGHT);
        importes.getColumnConstraints().setAll(colEtiqueta, colImporte);
        GridPane.setValignment(etiquetaTotal, VPos.CENTER);
        importes.getStyleClass().add("totales");

        progresoEmision.setPrefSize(18, 18);
        progresoEmision.setMaxSize(18, 18);
        btnEmitir.setMaxWidth(Double.MAX_VALUE);
        btnEmitir.setTooltip(new Tooltip("Ctrl+Enter"));
        btnEmitir.setOnAction(e -> emitir());

        VBox pedido = Ui.seccion("Pedido", "mdi2c-cart-outline", tablaCarrito, acciones, importes, btnEmitir);
        pedido.setMinWidth(320);
        VBox.setVgrow(pedido, Priority.ALWAYS);
        return pedido;
    }

    /** Nombre del producto y, debajo en gris, la cantidad en cajas. */
    private static TableColumn<LineaVenta, LineaVenta> columnaProductoCarrito() {
        TableColumn<LineaVenta, LineaVenta> columna = new TableColumn<>("Producto");
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LineaVenta linea, boolean vacio) {
                super.updateItem(linea, vacio);
                if (vacio || linea == null) {
                    setGraphic(null);
                    return;
                }
                Label cajas = new Label(Ui.cajas(linea.cantidad(), linea.producto().unidadesCaja()));
                cajas.getStyleClass().add("estado");
                setGraphic(new VBox(1, new Label(linea.producto().nombreCompleto()), cajas));
            }
        });
        columna.setPrefWidth(200);
        columna.setSortable(false);
        return columna;
    }

    // ------------------------------------------------------------------ datos

    private void cargarCatalogos() {
        Ui.alCompletar(servicios.catalogos().tiposComprobante(), tipos -> {
            cboTipo.getItems().setAll(tipos);
            tipos.stream().filter(t -> t.nombre().toUpperCase().startsWith("BOLETA")).findFirst()
                    .ifPresentOrElse(cboTipo::setValue, () -> cboTipo.getSelectionModel().selectFirst());
        }, mensaje -> Ui.mostrarError(ventana(), mensaje));

        Ui.alCompletar(servicios.catalogos().metodosPago(), metodos -> {
            cboMetodo.getItems().setAll(metodos);
            cboMetodo.getSelectionModel().selectFirst();
        }, mensaje -> Ui.mostrarError(ventana(), mensaje));

        Ui.alCompletar(servicios.catalogos().canalesVenta(), canales -> {
            cboCanal.getItems().setAll(canales);
            cboCanal.setValue(canales.contains(CANAL_POR_DEFECTO) ? CANAL_POR_DEFECTO : canales.get(0));
        }, mensaje -> Ui.mostrarError(ventana(), mensaje));

        cargarProductos();
    }

    /** Recarga precios y stock conservando el producto elegido y el filtro de tipo. */
    public void cargarProductos() {
        Ui.alCompletar(servicios.productos().listar(), this::construirTabla,
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void construirTabla(List<Producto> productos) {
        Long elegido = seleccionado == null ? null : seleccionado.id();

        // Columnas: las presentaciones en el orden de la base de datos (750 ML, 1 L, 2 L, 4 L, CHATA)
        Map<Long, String> presentaciones = new LinkedHashMap<>();
        productos.stream().sorted(Comparator.comparingLong(Producto::presentacionId))
                .forEach(p -> presentaciones.putIfAbsent(p.presentacionId(), p.presentacion()));
        List<TableColumn<FilaLicor, ?>> columnas = new ArrayList<>();
        TableColumn<FilaLicor, FilaLicor> colNombre = new TableColumn<>("Producto");
        colNombre.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue()));
        // Nombre en negrita y el tipo debajo
        colNombre.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(FilaLicor fila, boolean vacio) {
                super.updateItem(fila, vacio);
                setText(null);
                if (vacio || fila == null) {
                    setGraphic(null);
                    return;
                }
                Label nombre = new Label(fila.nombre());
                nombre.getStyleClass().add("nombre-celda");
                Label tipo = new Label(fila.tipo());
                tipo.getStyleClass().add("detalle-celda");
                setGraphic(new VBox(0, nombre, tipo));
            }
        });
        colNombre.setPrefWidth(170);
        colNombre.setSortable(false);
        columnas.add(colNombre);
        presentaciones.forEach((id, nombre) -> columnas.add(columnaPresentacion(id, nombre)));

        // Filas: un licor por fila, en orden de tipo y nombre
        Map<String, FilaLicor> porLicor = new LinkedHashMap<>();
        productos.stream()
                .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombre))
                .forEach(p -> porLicor.computeIfAbsent(p.tipoProducto() + "|" + p.nombre(),
                                clave -> new FilaLicor(p.nombre(), p.tipoProducto(), new LinkedHashMap<>()))
                        .presentaciones().put(p.presentacionId(), p));

        tablaProductos.getColumns().setAll(columnas);
        filas.setAll(porLicor.values());
        actualizarBotonesTipo();
        aplicarFiltro();

        // Vuelve a marcar lo que estaba elegido (con su precio y stock ya actualizados)
        seleccionar(null);
        if (elegido != null) {
            for (int fila = 0; fila < filasVisibles.size(); fila++) {
                for (TableColumn<FilaLicor, ?> columna : tablaProductos.getColumns()) {
                    if (columna.getCellData(fila) instanceof Producto p && p.id() == elegido) {
                        tablaProductos.getSelectionModel().clearAndSelect(fila, columna);
                    }
                }
            }
        }
    }

    /** Celda con el precio por unidad y debajo el stock; apagada si no hay stock o no tiene precio. */
    private TableColumn<FilaLicor, Producto> columnaPresentacion(long presentacionId, String nombre) {
        TableColumn<FilaLicor, Producto> columna = new TableColumn<>(nombre);
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(d.getValue().presentaciones().get(presentacionId)));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Producto p, boolean vacio) {
                super.updateItem(p, vacio);
                getStyleClass().remove("celda-apagada");
                setGraphic(null);
                if (vacio) {
                    setText(null);
                    return;
                }
                if (p == null || p.stock() <= 0) {
                    setText(NO_DISPONIBLE);
                    getStyleClass().add("celda-apagada");
                } else if (!p.tienePrecio()) {
                    setText("Sin precio · " + p.stock());
                    getStyleClass().add("celda-apagada");
                } else {
                    setText(null);
                    Label precio = new Label(Ui.soles(p.precio()));
                    precio.getStyleClass().add("precio-celda");
                    Label stock = new Label(p.stock() + " disp.");
                    stock.getStyleClass().add("stock-celda");
                    VBox caja = new VBox(0, precio, stock);
                    caja.setAlignment(Pos.CENTER);
                    setGraphic(caja);
                }
            }
        });
        columna.setPrefWidth(105);
        columna.setSortable(false);
        columna.getStyleClass().add("centrado");
        return columna;
    }

    /** Un botón por tipo con stock, más "Todos". Conserva el tipo elegido si sigue existiendo. */
    private void actualizarBotonesTipo() {
        String actual = grupoTipo.getSelectedToggle() == null ? null
                : (String) grupoTipo.getSelectedToggle().getUserData();
        List<String> tipos = filas.stream().filter(FilaLicor::tieneStock).map(FilaLicor::tipo).distinct().toList();

        grupoTipo.getToggles().clear();
        botonesTipo.getChildren().clear();
        ToggleButton todos = botonTipo("Todos", null);
        botonesTipo.getChildren().add(todos);
        tipos.forEach(tipo -> botonesTipo.getChildren().add(botonTipo(tipo, tipo)));
        grupoTipo.getToggles().stream()
                .filter(t -> actual != null && actual.equals(t.getUserData()))
                .findFirst()
                .ifPresentOrElse(grupoTipo::selectToggle, () -> grupoTipo.selectToggle(todos));
    }

    private ToggleButton botonTipo(String texto, String tipo) {
        ToggleButton boton = new ToggleButton(texto);
        boton.setMinWidth(Region.USE_PREF_SIZE);
        boton.setUserData(tipo);
        boton.getStyleClass().add("filtro-tipo");
        boton.setToggleGroup(grupoTipo);
        // Siempre queda uno elegido: volver a pulsar el activo no lo desmarca
        boton.setOnAction(e -> {
            if (!boton.isSelected()) {
                boton.setSelected(true);
            }
            aplicarFiltro();
        });
        return boton;
    }

    /** Solo licores con algo en stock, del tipo elegido y que coincidan con lo buscado. */
    private void aplicarFiltro() {
        String texto = txtBuscar.getText().trim().toLowerCase(Locale.ROOT);
        String tipo = grupoTipo.getSelectedToggle() == null ? null : (String) grupoTipo.getSelectedToggle().getUserData();
        filasVisibles.setPredicate(f -> f.tieneStock()
                && (tipo == null || tipo.equals(f.tipo()))
                && (texto.isEmpty() || f.nombre().toLowerCase(Locale.ROOT).contains(texto)));
        tablaProductos.setPlaceholder(filas.stream().anyMatch(FilaLicor::tieneStock)
                ? Ui.estadoVacio("mdi2m-magnify", "Ningún producto coincide con la búsqueda",
                        "Borre el texto buscado o elija \"Todos\".")
                : Ui.estadoVacio("mdi2p-package-variant", "No hay productos con stock en el almacén",
                        "Registre una entrada en Movimientos de stock o reciba un envío de la fábrica."));
    }

    // ------------------------------------------------------------------ acciones

    private void alSeleccionarCelda() {
        List<TablePosition> celdas = tablaProductos.getSelectionModel().getSelectedCells();
        if (celdas.isEmpty() || celdas.get(0).getTableColumn() == null) {
            seleccionar(null);
            return;
        }
        TablePosition<?, ?> celda = celdas.get(0);
        Object dato = celda.getTableColumn().getCellData(celda.getRow());
        seleccionar(dato instanceof Producto p ? p : null);
    }

    private void seleccionar(Producto producto) {
        seleccionado = producto;
        campoCajas.setProducto(producto);
        cajaSeleccion.getStyleClass().remove("vacia");
        if (producto == null) {
            lblSeleccionado.setText("Ningún producto elegido: haga clic en un precio de la tabla");
            cajaSeleccion.getStyleClass().add("vacia");
            return;
        }
        campoCajas.setUnidades(1);
        String estado = producto.stock() <= 0 ? "  (sin stock)"
                : !producto.tienePrecio() ? "  (sin precio: no se puede vender)" : "";
        lblSeleccionado.setText(String.format("%s — %s c/u · stock %d (%s)%s", producto.nombreCompleto(),
                producto.tienePrecio() ? Ui.soles(producto.precio()) : "sin precio", producto.stock(),
                Ui.cajas(producto.stock(), producto.unidadesCaja()), estado));
    }

    private void buscarCliente() {
        String documento = txtDocumento.getText().trim();
        if (documento.isEmpty()) {
            asignarCliente(null);
            return;
        }
        if (!documento.matches("\\d{8}|\\d{11}")) {
            Ui.mostrarError(ventana(), "Ingrese un DNI (8 dígitos) o un RUC (11 dígitos).");
            return;
        }

        btnBuscarCliente.setDisable(true);
        Ui.alCompletar(servicios.ventas().buscarCliente(documento),
                encontrado -> {
                    btnBuscarCliente.setDisable(false);
                    encontrado.ifPresentOrElse(this::asignarCliente, () -> ofrecerRegistro(documento));
                },
                mensaje -> {
                    btnBuscarCliente.setDisable(false);
                    Ui.mostrarError(ventana(), mensaje);
                });
    }

    private void ofrecerRegistro(String documento) {
        TextInputDialog dialogo = new TextInputDialog();
        Ui.aplicarEstilos(dialogo);
        dialogo.initOwner(ventana());
        dialogo.setTitle("Cliente no registrado");
        dialogo.setHeaderText("No existe un cliente con el documento " + documento + ".");
        dialogo.setContentText(documento.length() == 11 ? "Razón social:" : "Nombres y apellidos:");

        dialogo.showAndWait()
                .map(String::trim)
                .filter(nombre -> !nombre.isEmpty())
                .ifPresent(nombre -> {
                    String tipo = documento.length() == 11 ? "RUC" : "DNI";
                    Ui.alCompletar(servicios.ventas().registrarCliente(new ClienteNuevo(tipo, documento, nombre)),
                            this::asignarCliente,
                            mensaje -> Ui.mostrarError(ventana(), mensaje));
                });
    }

    private void asignarCliente(Cliente nuevo) {
        cliente = nuevo;
        lblCliente.setText(nuevo == null
                ? CLIENTES_VARIOS
                : nuevo.tipoDocumento() + " " + nuevo.dniRuc() + "  ·  " + nuevo.nombreRazonSocial());
    }

    private void agregarProducto() {
        Producto producto = seleccionado;
        if (producto == null) {
            Ui.mostrarError(ventana(), "Haga clic en el precio del producto que quiere vender.");
            return;
        }
        if (producto.stock() <= 0) {
            Ui.mostrarError(ventana(), "No hay stock de " + producto.nombreCompleto() + ".");
            return;
        }
        if (!producto.tienePrecio()) {
            Ui.mostrarError(ventana(), producto.nombreCompleto() + " no tiene precio definido. "
                    + "El administrador debe asignarle un precio en Inventario antes de venderlo.");
            return;
        }
        int cantidad = campoCajas.total();
        if (cantidad < 1) {
            Ui.mostrarError(ventana(), "Ingrese las cajas y/o unidades (en total, mayor a 0).");
            return;
        }

        int indice = indiceEnCarrito(producto.id());
        int total = cantidad + (indice >= 0 ? carrito.get(indice).cantidad() : 0);
        if (total > producto.stock()) {
            Ui.mostrarError(ventana(), String.format("Solo hay %d unidades (%s) de %s.", producto.stock(),
                    Ui.cajas(producto.stock(), producto.unidadesCaja()), producto.nombreCompleto()));
            return;
        }

        if (indice >= 0) {
            carrito.set(indice, new LineaVenta(producto, total));
        } else {
            carrito.add(new LineaVenta(producto, total));
        }
        campoCajas.setUnidades(1);
        tablaProductos.requestFocus();
    }

    private void emitir() {
        TipoComprobante tipo = cboTipo.getValue();
        MetodoPago metodo = cboMetodo.getValue();
        if (carrito.isEmpty()) {
            Ui.mostrarError(ventana(), "Agregue al menos un producto.");
            return;
        }
        if (tipo == null || metodo == null || cboCanal.getValue() == null) {
            Ui.mostrarError(ventana(), "Seleccione el comprobante, el método de pago y el canal.");
            return;
        }
        if (tipo.requiereRuc() && (cliente == null || !"RUC".equalsIgnoreCase(cliente.tipoDocumento()))) {
            Ui.mostrarError(ventana(), "La factura requiere un cliente con RUC. Búsquelo por su número de RUC.");
            return;
        }

        List<ItemVenta> items = carrito.stream()
                .map(l -> new ItemVenta(l.producto().id(), l.cantidad()))
                .toList();
        VentaSolicitud solicitud = new VentaSolicitud(
                cliente == null ? null : cliente.id(), tipo.id(), metodo.id(), cboCanal.getValue(), items);

        btnEmitir.setDisable(true);
        btnEmitir.setText("Emitiendo…");
        btnEmitir.setGraphic(progresoEmision);
        Ui.alCompletar(servicios.ventas().emitirComprobante(solicitud),
                comprobante -> {
                    restaurarBoton();
                    Ui.toast(this, "Venta registrada · " + comprobante.tipoComprobante() + " "
                            + comprobante.numeroCompleto() + " · Total " + Ui.soles(comprobante.total()));
                    limpiar();
                    cargarProductos();
                    alRegistrarVenta.run();
                },
                mensaje -> {
                    restaurarBoton();
                    Ui.mostrarError(ventana(), mensaje);
                    cargarProductos(); // el stock pudo cambiar por otra venta
                });
    }

    private void limpiar() {
        carrito.clear();
        txtDocumento.clear();
        asignarCliente(null);
        tablaProductos.getSelectionModel().clearSelection();
        if (cboCanal.getItems().contains(CANAL_POR_DEFECTO)) {
            cboCanal.setValue(CANAL_POR_DEFECTO);
        }
    }

    // ------------------------------------------------------------------ apoyo

    private void actualizarTotales() {
        BigDecimal total = carrito.stream().map(LineaVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tasa = cboTipo.getValue() == null ? new BigDecimal("0.18") : cboTipo.getValue().tasaIgv();
        BigDecimal subtotal = total.divide(BigDecimal.ONE.add(tasa), 2, RoundingMode.HALF_UP);
        lblSubtotal.setText(Ui.soles(subtotal));
        lblIgv.setText(Ui.soles(total.subtract(subtotal)));
        lblTotal.setText(Ui.soles(total));
    }

    private int indiceEnCarrito(long productoId) {
        for (int i = 0; i < carrito.size(); i++) {
            if (carrito.get(i).producto().id() == productoId) {
                return i;
            }
        }
        return -1;
    }

    private void restaurarBoton() {
        btnEmitir.setDisable(false);
        btnEmitir.setText("Emitir comprobante");
        btnEmitir.setGraphic(Ui.icono("mdi2r-receipt"));
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static TableColumn<LineaVenta, String> columna(String titulo, Function<LineaVenta, String> valor,
                                                          double ancho, boolean numerica) {
        TableColumn<LineaVenta, String> columna = new TableColumn<>(titulo);
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
