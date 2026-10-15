package pe.dtulia.desktop.vista;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import pe.dtulia.desktop.modelo.Empleado;
import pe.dtulia.desktop.modelo.Envio;
import pe.dtulia.desktop.modelo.EnvioItem;
import pe.dtulia.desktop.modelo.VentaCiudad;
import pe.dtulia.desktop.modelo.ViajeDetalle;
import pe.dtulia.desktop.modelo.ViajeProducto;
import pe.dtulia.desktop.modelo.ViajeResumen;
import pe.dtulia.desktop.servicio.Servicios;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mercadería que lleva el jefe: carga inicial, envíos durante el viaje (cajas o unidades), hoja de
 * carga impresa y liquidación al volver (vendido por ciudad, lo que regresó y la merma).
 * Visible para ADMINISTRADOR y ALMACENERO; solo el administrador anula envíos.
 */
public class FrmViajes extends BorderPane {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String TODOS = "TODOS";
    private static final String ABIERTOS = "ABIERTOS";
    private static final String LIQUIDADOS = "LIQUIDADOS";
    private static final String PENDIENTE = "—";

    private final Servicios servicios;
    private final Empleado empleado;
    private final Runnable alCambiarStock;

    private final ComboBox<String> cboEstado = new ComboBox<>(FXCollections.observableArrayList(TODOS, ABIERTOS, LIQUIDADOS));
    private final ObservableList<ViajeResumen> viajes = FXCollections.observableArrayList();
    private final TableView<ViajeResumen> tablaViajes = new TableView<>(viajes);
    private final Label lblViajeAbierto = new Label();
    private final Label lblTituloDetalle = new Label();
    private final ObservableList<ViajeProducto> productos = FXCollections.observableArrayList();
    private final TableView<ViajeProducto> tablaProductos = new TableView<>(productos);
    private final ObservableList<Envio> envios = FXCollections.observableArrayList();
    private final TableView<Envio> tablaEnvios = new TableView<>(envios);
    private final ObservableList<VentaCiudad> ventas = FXCollections.observableArrayList();
    private final TableView<VentaCiudad> tablaVentas = new TableView<>(ventas);
    private final Button btnAnularEnvio = new Button("Anular envío seleccionado");
    private final ObjectProperty<ViajeDetalle> detalle = new SimpleObjectProperty<>();
    private String filtroAplicado = TODOS;

    public FrmViajes(Servicios servicios, Empleado empleado, Runnable alCambiarStock) {
        this.servicios = servicios;
        this.empleado = empleado;
        this.alCambiarStock = alCambiarStock;
        getStyleClass().add("formulario");

        setTop(crearListado());
        setCenter(crearDetalle());

        tablaViajes.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                mostrarDetalle(null);
            } else {
                cargarDetalle(ahora.id());
            }
        });
        mostrarDetalle(null);
        recargar();
    }

    /** Fechas en la hora de Perú (la API las envía en UTC). */
    static String fecha(OffsetDateTime fecha) {
        return fecha == null ? "" : fecha.atZoneSameInstant(ZoneId.systemDefault()).format(FECHA);
    }

    // ------------------------------------------------------------------ construcción

    private VBox crearListado() {
        cboEstado.setValue(TODOS);
        Button btnMostrar = new Button("Mostrar");
        btnMostrar.getStyleClass().add("boton-primario");
        btnMostrar.setOnAction(e -> {
            filtroAplicado = cboEstado.getValue();
            recargar();
        });
        lblViajeAbierto.getStyleClass().add("cliente");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, new Label("Estado"), cboEstado, btnMostrar, espacio, lblViajeAbierto);
        barra.setAlignment(Pos.CENTER_LEFT);

        tablaViajes.getColumns().setAll(List.of(
                Ui.<ViajeResumen>columna("#", v -> "#" + v.id(), 55, true),
                Ui.<ViajeResumen>columna("Salida", v -> fecha(v.fechaSalida()), 95, false),
                Ui.<ViajeResumen>columna("Días", v -> String.valueOf(v.dias()), 55, true),
                Ui.<ViajeResumen>columna("Ciudades", v -> v.ciudades() == null ? "" : v.ciudades(), 200, false),
                Ui.<ViajeResumen>columna("Envíos", v -> String.valueOf(v.envios()), 65, true),
                Ui.<ViajeResumen>columna("Llevó", v -> String.valueOf(v.llevado()), 70, true),
                Ui.<ViajeResumen>columna("Vendió", v -> v.abierto() ? PENDIENTE : String.valueOf(v.vendido()), 70, true),
                Ui.<ViajeResumen>columna("Regresó", v -> v.abierto() ? PENDIENTE : String.valueOf(v.devuelto()), 75, true),
                Ui.<ViajeResumen>columna("Merma", v -> v.abierto() ? PENDIENTE : String.valueOf(v.merma()), 65, true),
                Ui.<ViajeResumen>columna("Diferencia", v -> v.abierto() ? PENDIENTE : String.valueOf(v.diferencia()), 85,
                        true, v -> v.abierto() ? null : v.diferencia() == 0 ? "texto-ok" : "texto-alerta"),
                Ui.<ViajeResumen>columna("Estado", ViajeResumen::estado, 90, false),
                Ui.<ViajeResumen>columna("Observación", v -> v.observacion() == null ? "" : v.observacion(), 220, false)));
        tablaViajes.setPlaceholder(new Label("No hay viajes registrados. Use \"Nuevo viaje\" cuando el jefe salga con mercadería."));
        tablaViajes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaViajes.setPrefHeight(210);

        Button btnNuevo = new Button("Nuevo viaje");
        btnNuevo.getStyleClass().add("boton-primario");
        btnNuevo.setOnAction(e -> nuevoViaje());
        Button btnEnviar = new Button("Enviar al viaje");
        btnEnviar.setOnAction(e -> enviar());
        Button btnImprimir = new Button("Imprimir hoja");
        btnImprimir.setOnAction(e -> HojaCarga.imprimir(ventana(), detalle.get()));
        Button btnLiquidar = new Button("Liquidar viaje");
        btnLiquidar.setOnAction(e -> liquidar());

        var sinViajeAbierto = Bindings.createBooleanBinding(
                () -> detalle.get() == null || !detalle.get().viaje().abierto(), detalle);
        btnEnviar.disableProperty().bind(sinViajeAbierto);
        btnLiquidar.disableProperty().bind(sinViajeAbierto);
        btnImprimir.disableProperty().bind(detalle.isNull());

        Label ayuda = new Label("Un viaje a la vez. Lo que el jefe pida durante el viaje se registra con \"Enviar al viaje\".");
        ayuda.getStyleClass().add("estado");
        Region espacioAcciones = new Region();
        HBox.setHgrow(espacioAcciones, Priority.ALWAYS);
        HBox acciones = new HBox(10, ayuda, espacioAcciones, btnNuevo, btnEnviar, btnImprimir, btnLiquidar);
        acciones.setAlignment(Pos.CENTER_RIGHT);

        return Ui.seccion("Viajes del jefe", barra, tablaViajes, acciones);
    }

    private VBox crearDetalle() {
        tablaProductos.getColumns().setAll(List.of(
                Ui.<ViajeProducto>columna("Producto", ViajeProducto::nombreCompleto, 230, false),
                Ui.<ViajeProducto>columna("Tipo", ViajeProducto::tipoProducto, 90, false),
                Ui.<ViajeProducto>columna("Llevó", p -> String.valueOf(p.llevado()), 65, true),
                Ui.<ViajeProducto>columna("En cajas", p -> Ui.cajas(p.llevado(), p.unidadesCaja()), 120, false),
                Ui.<ViajeProducto>columna("Vendió", p -> liquidado() ? String.valueOf(p.vendido()) : PENDIENTE, 65, true),
                Ui.<ViajeProducto>columna("Regresó", p -> liquidado() ? String.valueOf(p.devuelto()) : PENDIENTE, 70, true),
                Ui.<ViajeProducto>columna("Merma", p -> liquidado() ? String.valueOf(p.merma()) : PENDIENTE, 65, true),
                Ui.<ViajeProducto>columna("Motivo", p -> p.motivoMerma() == null ? "" : p.motivoMerma(), 120, false),
                Ui.<ViajeProducto>columna("Diferencia", p -> !liquidado() ? PENDIENTE : p.diferencia() == 0 ? "0 ✓"
                                : p.diferencia() > 0 ? "Faltan " + p.diferencia() : "Sobran " + (-p.diferencia()), 100, false,
                        p -> !liquidado() ? null : p.diferencia() == 0 ? "texto-ok" : "texto-alerta")));
        tablaProductos.setPlaceholder(new Label("Sin productos"));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tablaEnvios.getColumns().setAll(List.of(
                Ui.<Envio>columna("N°", e -> e.numero() == 1 ? "1 · Carga inicial" : String.valueOf(e.numero()), 110, false),
                Ui.<Envio>columna("Fecha", e -> e.fecha().atZoneSameInstant(ZoneId.systemDefault()).format(FECHA_HORA), 125, false),
                Ui.<Envio>columna("Ciudad", Envio::sede, 110, false),
                Ui.<Envio>columna("Referencia", e -> e.referencia() == null ? "" : e.referencia(), 130, false),
                Ui.<Envio>columna("Productos", FrmViajes::resumirEnvio, 330, false),
                Ui.<Envio>columna("Unidades", e -> String.valueOf(e.items().stream().mapToInt(EnvioItem::cantidad).sum()), 80, true),
                Ui.<Envio>columna("Estado", e -> e.anulado() ? "ANULADO" : "", 80, false)));
        tablaEnvios.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(Envio envio, boolean vacio) {
                super.updateItem(envio, vacio);
                getStyleClass().remove("anulado");
                if (!vacio && envio != null && envio.anulado()) {
                    getStyleClass().add("anulado");
                }
            }
        });
        tablaEnvios.setPlaceholder(new Label("Sin envíos"));
        tablaEnvios.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tablaVentas.getColumns().setAll(List.of(
                Ui.<VentaCiudad>columna("Ciudad", VentaCiudad::sede, 140, false),
                Ui.<VentaCiudad>columna("Producto", v -> nombreProducto(v.productoId()), 280, false),
                Ui.<VentaCiudad>columna("Vendió", v -> String.valueOf(v.cantidad()), 80, true),
                Ui.<VentaCiudad>columna("En cajas", v -> Ui.cajas(v.cantidad(), unidadesCaja(v.productoId())), 140, false)));
        tablaVentas.setPlaceholder(new Label("Las ventas por ciudad se registran al liquidar el viaje"));
        tablaVentas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TabPane pestanas = new TabPane(new Tab("Productos", tablaProductos), new Tab("Envíos", tablaEnvios),
                new Tab("Ventas por ciudad", tablaVentas));
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(pestanas, Priority.ALWAYS);

        btnAnularEnvio.setVisible(empleado.esAdministrador());
        btnAnularEnvio.setManaged(empleado.esAdministrador());
        btnAnularEnvio.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            Envio envio = tablaEnvios.getSelectionModel().getSelectedItem();
            return envio == null || envio.anulado() || detalle.get() == null || !detalle.get().viaje().abierto();
        }, tablaEnvios.getSelectionModel().selectedItemProperty(), detalle));
        btnAnularEnvio.setOnAction(e -> anularEnvio());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(espacio, btnAnularEnvio);

        lblTituloDetalle.getStyleClass().add("titulo-seccion");
        VBox seccion = new VBox(10, lblTituloDetalle, pestanas, pie);
        seccion.getStyleClass().add("seccion");
        VBox.setVgrow(seccion, Priority.ALWAYS);
        VBox contenedor = new VBox(seccion);
        contenedor.setPadding(new Insets(10, 0, 0, 0));
        return contenedor;
    }

    // ------------------------------------------------------------------ datos

    /** Recarga la lista conservando el viaje seleccionado. */
    public void recargar() {
        ViajeResumen seleccionado = tablaViajes.getSelectionModel().getSelectedItem();
        recargar(seleccionado == null ? null : seleccionado.id());
    }

    private void recargar(Long seleccionar) {
        String estado = switch (filtroAplicado) {
            case ABIERTOS -> "ABIERTO";
            case LIQUIDADOS -> "LIQUIDADO";
            default -> null;
        };
        Ui.alCompletar(servicios.viajes().listar(estado),
                lista -> {
                    viajes.setAll(lista);
                    // El pedido, el abierto o el más reciente
                    lista.stream().filter(v -> seleccionar != null && v.id() == seleccionar).findFirst()
                            .or(() -> lista.stream().filter(ViajeResumen::abierto).findFirst())
                            .or(() -> lista.stream().findFirst())
                            .ifPresent(v -> tablaViajes.getSelectionModel().select(v));
                    lblViajeAbierto.setText(lista.stream().filter(ViajeResumen::abierto).findFirst()
                            .map(v -> "Viaje abierto: #" + v.id() + " · " + v.dias() + (v.dias() == 1 ? " día" : " días")
                                    + " fuera · " + (v.ciudades() == null ? "" : v.ciudades()))
                            .orElse(ABIERTOS.equals(filtroAplicado) || TODOS.equals(filtroAplicado)
                                    ? "No hay viaje abierto" : ""));
                },
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void cargarDetalle(long viajeId) {
        Ui.alCompletar(servicios.viajes().detalle(viajeId),
                cargado -> {
                    ViajeResumen actual = tablaViajes.getSelectionModel().getSelectedItem();
                    if (actual != null && actual.id() == viajeId) { // descarta respuestas de una selección anterior
                        mostrarDetalle(cargado);
                    }
                },
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void mostrarDetalle(ViajeDetalle nuevo) {
        detalle.set(nuevo);
        if (nuevo == null) {
            lblTituloDetalle.setText("Detalle del viaje");
            productos.clear();
            envios.clear();
            ventas.clear();
            return;
        }
        ViajeResumen v = nuevo.viaje();
        lblTituloDetalle.setText(String.format("Viaje #%d  ·  %s  ·  salió el %s  ·  %d %s%s", v.id(), v.estado(),
                fecha(v.fechaSalida()), v.dias(), v.dias() == 1 ? "día" : "días",
                v.abierto() ? " fuera" : "  ·  liquidado el " + fecha(v.fechaLiquidacion())));
        productos.setAll(nuevo.productos());
        envios.setAll(nuevo.envios());
        ventas.setAll(nuevo.ventas());
        tablaProductos.refresh(); // las columnas dependen del estado del viaje
    }

    // ------------------------------------------------------------------ acciones

    private void nuevoViaje() {
        new DlgEnvio(ventana(), servicios, "Nuevo viaje · carga inicial", "Registrar salida", null,
                solicitud -> servicios.viajes().abrir(solicitud),
                abierto -> {
                    despuesDeOperar(abierto);
                    Ui.mostrarInfo(ventana(), "Viaje #" + abierto.viaje().id() + " abierto",
                            abierto.viaje().llevado() + " unidades salieron del almacén hacia " + abierto.viaje().ciudades()
                                    + ".\nPuede imprimir la hoja de carga para el jefe.");
                }).show();
    }

    private void enviar() {
        ViajeDetalle actual = detalle.get();
        if (actual == null || !actual.viaje().abierto()) {
            return;
        }
        // Sugiere la ciudad del último envío
        Long ciudad = actual.envios().stream().filter(e -> !e.anulado())
                .reduce((primero, segundo) -> segundo).map(Envio::sedeId).orElse(null);
        long viajeId = actual.viaje().id();
        new DlgEnvio(ventana(), servicios, "Enviar al viaje #" + viajeId, "Registrar envío", ciudad,
                solicitud -> servicios.viajes().enviar(viajeId, solicitud),
                this::despuesDeOperar).show();
    }

    private void anularEnvio() {
        Envio envio = tablaEnvios.getSelectionModel().getSelectedItem();
        ViajeDetalle actual = detalle.get();
        if (envio == null || actual == null) {
            return;
        }
        int unidades = envio.items().stream().mapToInt(EnvioItem::cantidad).sum();
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                String.format("Las %d unidades vuelven al stock del almacén.%nHágalo solo si esa mercadería no salió.",
                        unidades), ButtonType.YES, ButtonType.NO);
        confirmacion.initOwner(ventana());
        confirmacion.setHeaderText(String.format("¿Anular el envío %d del viaje #%d (%s → %s)?", envio.numero(),
                actual.viaje().id(), fecha(envio.fecha()), envio.sede()));
        if (confirmacion.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }
        Ui.alCompletar(servicios.viajes().anularEnvio(envio.id()), this::despuesDeOperar,
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void liquidar() {
        ViajeDetalle actual = detalle.get();
        if (actual == null || !actual.viaje().abierto()) {
            return;
        }
        long viajeId = actual.viaje().id();
        Ui.alCompletar(servicios.catalogos().sedes(),
                sedes -> new DlgLiquidacion(ventana(), actual, sedes.stream().filter(s -> !s.esAlmacen()).toList(),
                        solicitud -> servicios.viajes().liquidar(viajeId, solicitud),
                        liquidado -> {
                            despuesDeOperar(liquidado);
                            ViajeResumen v = liquidado.viaje();
                            Ui.mostrarInfo(ventana(), "Viaje #" + v.id() + " liquidado", String.format(
                                    "Llevó %d · Vendió %d · Regresó %d · Merma %d%n%s", v.llevado(), v.vendido(),
                                    v.devuelto(), v.merma(), v.diferencia() == 0 ? "Todo cuadra ✓"
                                            : "Diferencia: " + v.diferencia() + " unidades (quedó en la observación)"));
                        }).show(),
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    /** Tras abrir, enviar, anular o liquidar: el stock del almacén cambió. */
    private void despuesDeOperar(ViajeDetalle resultado) {
        alCambiarStock.run();
        recargar(resultado.viaje().id());
    }

    // ------------------------------------------------------------------ apoyo

    private boolean liquidado() {
        return detalle.get() != null && !detalle.get().viaje().abierto();
    }

    private ViajeProducto producto(long productoId) {
        ViajeDetalle actual = detalle.get();
        return actual == null ? null
                : actual.productos().stream().filter(p -> p.productoId() == productoId).findFirst().orElse(null);
    }

    private String nombreProducto(long productoId) {
        ViajeProducto p = producto(productoId);
        return p == null ? "#" + productoId : p.nombreCompleto();
    }

    private int unidadesCaja(long productoId) {
        ViajeProducto p = producto(productoId);
        return p == null ? 1 : p.unidadesCaja();
    }

    private static String resumirEnvio(Envio envio) {
        Function<EnvioItem, String> linea = i -> i.nombre() + " " + i.presentacion() + " ×" + i.cantidad();
        return envio.items().stream().map(linea).collect(Collectors.joining(", "));
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
