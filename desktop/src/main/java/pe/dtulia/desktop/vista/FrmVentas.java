package pe.dtulia.desktop.vista;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
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
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Registro de ventas: arma el pedido y lo envía a POST /comprobantes. */
public class FrmVentas extends BorderPane {

    private static final String CLIENTES_VARIOS = "CLIENTES VARIOS";
    private static final String CANAL_POR_DEFECTO = "TIENDA";

    /** Línea del carrito. El precio mostrado es referencial: el definitivo lo fija la base de datos. */
    record LineaVenta(Producto producto, int cantidad) {
        BigDecimal subtotal() {
            return producto.precio().multiply(BigDecimal.valueOf(cantidad));
        }
    }

    private final Servicios servicios;
    private final Runnable alRegistrarVenta;

    private final ComboBox<TipoComprobante> cboTipo = new ComboBox<>();
    private final ComboBox<MetodoPago> cboMetodo = new ComboBox<>();
    private final ComboBox<String> cboCanal = new ComboBox<>();
    private final TextField txtDocumento = new TextField();
    private final Button btnBuscarCliente = new Button("Buscar");
    private final Label lblCliente = new Label(CLIENTES_VARIOS);
    private final ComboBox<Producto> cboProducto = new ComboBox<>();
    private final Spinner<Integer> spnCantidad = new Spinner<>(1, 9999, 1);
    private final ObservableList<LineaVenta> carrito = FXCollections.observableArrayList();
    private final TableView<LineaVenta> tablaCarrito = new TableView<>(carrito);
    private final Label lblSubtotal = new Label();
    private final Label lblIgv = new Label();
    private final Label lblTotal = new Label();
    private final Button btnEmitir = new Button("Emitir comprobante");

    private Cliente cliente; // null = CLIENTES VARIOS

    public FrmVentas(Servicios servicios, Runnable alRegistrarVenta) {
        this.servicios = servicios;
        this.alRegistrarVenta = alRegistrarVenta;
        getStyleClass().add("formulario");

        setTop(crearDatosComprobante());
        setCenter(crearDetalle());
        setBottom(crearTotales());

        carrito.addListener((ListChangeListener<LineaVenta>) c -> actualizarTotales());
        cboTipo.valueProperty().addListener((obs, antes, ahora) -> actualizarTotales());
        actualizarTotales();
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

        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(8);
        grilla.addRow(0, new Label("Comprobante"), cboTipo, new Label("Método de pago"), cboMetodo);
        grilla.addRow(1, new Label("Cliente"), new HBox(6, txtDocumento, btnBuscarCliente),
                new Label("Canal"), cboCanal);
        grilla.add(lblCliente, 1, 2, 3, 1);
        ColumnConstraints etiqueta = new ColumnConstraints();
        ColumnConstraints campo = new ColumnConstraints(220, 260, Double.MAX_VALUE);
        campo.setHgrow(Priority.ALWAYS);
        grilla.getColumnConstraints().setAll(etiqueta, campo, etiqueta, campo);

        return Ui.seccion("Datos del comprobante", grilla);
    }

    private VBox crearDetalle() {
        cboProducto.setPromptText("Seleccione un producto");
        cboProducto.setMaxWidth(Double.MAX_VALUE);
        cboProducto.setVisibleRowCount(14);
        cboProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                return p == null ? "" : String.format("%s · %s — %s (stock %d)", p.tipoProducto(),
                        p.nombreCompleto(), p.tienePrecio() ? Ui.soles(p.precio()) : "SIN PRECIO", p.stock());
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });
        HBox.setHgrow(cboProducto, Priority.ALWAYS);

        spnCantidad.setEditable(true);
        spnCantidad.setPrefWidth(90);
        Button btnAgregar = new Button("Agregar");
        btnAgregar.setOnAction(e -> agregarProducto());
        Button btnRecargar = new Button("Recargar");
        btnRecargar.setOnAction(e -> cargarProductos());

        HBox filaAgregar = new HBox(8, cboProducto, new Label("Cantidad"), spnCantidad, btnAgregar, btnRecargar);
        filaAgregar.setAlignment(Pos.CENTER_LEFT);

        tablaCarrito.getColumns().setAll(List.of(
                columna("Producto", l -> l.producto().nombreCompleto(), 300, false),
                columna("Tipo", l -> l.producto().tipoProducto(), 120, false),
                columna("Cantidad", l -> String.valueOf(l.cantidad()), 90, true),
                columna("P. unitario", l -> Ui.soles(l.producto().precio()), 110, true),
                columna("Subtotal", l -> Ui.soles(l.subtotal()), 120, true)));
        tablaCarrito.setPlaceholder(new Label("Agregue productos a la venta"));
        tablaCarrito.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tablaCarrito, Priority.ALWAYS);

        VBox detalle = Ui.seccion("Detalle del pedido", filaAgregar, tablaCarrito);
        VBox.setVgrow(detalle, Priority.ALWAYS);
        VBox contenedor = new VBox(detalle);
        contenedor.setPadding(new Insets(10, 0, 10, 0));
        return contenedor;
    }

    private HBox crearTotales() {
        Button btnQuitar = new Button("Quitar seleccionado");
        btnQuitar.setOnAction(e -> {
            LineaVenta linea = tablaCarrito.getSelectionModel().getSelectedItem();
            if (linea != null) {
                carrito.remove(linea);
            }
        });
        Button btnLimpiar = new Button("Limpiar");
        btnLimpiar.setOnAction(e -> limpiar());
        btnEmitir.getStyleClass().add("boton-primario");
        btnEmitir.setOnAction(e -> emitir());

        lblTotal.getStyleClass().add("total");
        GridPane importes = new GridPane();
        importes.setHgap(12);
        importes.addRow(0, new Label("Op. gravada"), lblSubtotal);
        importes.addRow(1, new Label("IGV"), lblIgv);
        importes.addRow(2, new Label("Total"), lblTotal);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(16, btnQuitar, espacio, importes, btnLimpiar, btnEmitir);
        pie.setAlignment(Pos.CENTER_RIGHT);
        return pie;
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

    /** Solo se ofrecen productos con stock disponible, agrupados por tipo. */
    public void cargarProductos() {
        Ui.alCompletar(servicios.productos().listar(),
                lista -> cboProducto.getItems().setAll(lista.stream()
                        .filter(p -> p.stock() > 0)
                        .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombreCompleto))
                        .toList()),
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    // ------------------------------------------------------------------ acciones

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
        Producto producto = cboProducto.getValue();
        if (producto == null) {
            Ui.mostrarError(ventana(), "Seleccione un producto.");
            return;
        }
        if (!producto.tienePrecio()) {
            Ui.mostrarError(ventana(), producto.nombreCompleto() + " no tiene precio definido. "
                    + "El administrador debe asignarle un precio en Inventario antes de venderlo.");
            return;
        }
        int cantidad = Ui.leerEntero(spnCantidad);
        if (cantidad < 1) {
            Ui.mostrarError(ventana(), "Ingrese una cantidad válida.");
            return;
        }

        int indice = indiceEnCarrito(producto.id());
        int total = cantidad + (indice >= 0 ? carrito.get(indice).cantidad() : 0);
        if (total > producto.stock()) {
            Ui.mostrarError(ventana(), String.format("Solo hay %d unidades disponibles de %s.",
                    producto.stock(), producto.nombreCompleto()));
            return;
        }

        if (indice >= 0) {
            carrito.set(indice, new LineaVenta(producto, total));
        } else {
            carrito.add(new LineaVenta(producto, total));
        }
        cboProducto.getSelectionModel().clearSelection();
        spnCantidad.getValueFactory().setValue(1);
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
        Ui.alCompletar(servicios.ventas().emitirComprobante(solicitud),
                comprobante -> {
                    restaurarBoton();
                    Ui.mostrarInfo(ventana(), "Venta registrada",
                            comprobante.tipoComprobante() + " " + comprobante.numeroCompleto()
                                    + "\nTotal: " + Ui.soles(comprobante.total()));
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
        cboProducto.getSelectionModel().clearSelection();
        spnCantidad.getValueFactory().setValue(1);
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
