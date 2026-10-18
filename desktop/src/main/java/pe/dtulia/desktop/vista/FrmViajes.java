package pe.dtulia.desktop.vista;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
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
import java.util.Optional;
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
    private final Label lblViajeAbierto = new Label("—");
    private final Label lblEnviosAbierto = new Label("—");
    private final Label lblLlevadoAbierto = new Label("—");
    private final Label lblCiudadesAbierto = new Label("—");
    private final Label lblTituloDetalle = new Label();
    private final Label lblEstadoDetalle = Ui.insignia("", "neutro");
    private final ObservableList<ViajeProducto> productos = FXCollections.observableArrayList();
    private final TableView<ViajeProducto> tablaProductos = new TableView<>(productos);
    private final ObservableList<Envio> envios = FXCollections.observableArrayList();
    private final TableView<Envio> tablaEnvios = new TableView<>(envios);
    private final ObservableList<VentaCiudad> ventas = FXCollections.observableArrayList();
    private final TableView<VentaCiudad> tablaVentas = new TableView<>(ventas);
    private final Tab tabProductos = Ui.pestana("Productos", "mdi2b-bottle-wine-outline", tablaProductos);
    private final Tab tabEnvios = Ui.pestana("Envíos", "mdi2t-truck-delivery-outline", tablaEnvios);
    private final Tab tabVentas = Ui.pestana("Ventas por ciudad", "mdi2m-map-marker-outline", tablaVentas);
    private final Button btnAnularEnvio = Ui.boton("Anular envío seleccionado", "mdi2c-cancel", "boton-peligro");
    private final ObjectProperty<ViajeDetalle> detalle = new SimpleObjectProperty<>();
    private String filtroAplicado = TODOS;

    public FrmViajes(Servicios servicios, Empleado empleado, Runnable alCambiarStock) {
        this.servicios = servicios;
        this.empleado = empleado;
        this.alCambiarStock = alCambiarStock;
        getStyleClass().add("formulario");

        // Lista arriba y detalle abajo en un panel dividido: se puede arrastrar el separador
        SplitPane division = new SplitPane(crearListado(), crearDetalle());
        division.setOrientation(Orientation.VERTICAL);
        division.setDividerPositions(0.48);
        division.getStyleClass().add("division-vertical");
        setTop(crearResumen());
        setCenter(division);
        BorderPane.setMargin(division, new Insets(10, 0, 0, 0));

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

    /** Tarjetas del viaje abierto: cuál es, cuántos envíos lleva, cuánto salió y por dónde va. */
    private HBox crearResumen() {
        return new HBox(10,
                Ui.tarjetaResumen("mdi2t-truck-fast-outline", "Viaje abierto", lblViajeAbierto, "info"),
                Ui.tarjetaResumen("mdi2t-truck-delivery-outline", "Envíos del viaje", lblEnviosAbierto, "vino"),
                Ui.tarjetaResumen("mdi2p-package-up", "Unidades que llevó", lblLlevadoAbierto, "exito"),
                Ui.tarjetaResumen("mdi2m-map-marker-path", "Ciudades", lblCiudadesAbierto, "neutro"));
    }

    private VBox crearListado() {
        cboEstado.setValue(TODOS);
        Button btnMostrar = Ui.boton("Mostrar", "mdi2f-filter-outline");
        btnMostrar.setOnAction(e -> {
            filtroAplicado = cboEstado.getValue();
            recargar();
        });
        lblCiudadesAbierto.setWrapText(false);

        tablaViajes.getColumns().setAll(List.of(
                Ui.<ViajeResumen>columna("#", v -> "#" + v.id(), 50, true),
                Ui.<ViajeResumen>columna("Salida", v -> fecha(v.fechaSalida()), 92, false),
                Ui.<ViajeResumen>columna("Días", v -> String.valueOf(v.dias()), 52, true),
                Ui.<ViajeResumen>columna("Ciudades", v -> v.ciudades() == null ? "" : v.ciudades(), 170, false),
                Ui.<ViajeResumen>columna("Envíos", v -> String.valueOf(v.envios()), 64, true),
                Ui.<ViajeResumen>columna("Llevó", v -> String.valueOf(v.llevado()), 60, true),
                Ui.<ViajeResumen>columna("Vendió", v -> v.abierto() ? PENDIENTE : String.valueOf(v.vendido()), 66, true),
                Ui.<ViajeResumen>columna("Regresó", v -> v.abierto() ? PENDIENTE : String.valueOf(v.devuelto()), 74, true),
                Ui.<ViajeResumen>columna("Merma", v -> v.abierto() ? PENDIENTE : String.valueOf(v.merma()), 64, true),
                Ui.<ViajeResumen>columna("Diferencia", v -> v.abierto() ? PENDIENTE : String.valueOf(v.diferencia()), 86,
                        true, v -> v.abierto() ? null : v.diferencia() == 0 ? "texto-ok" : "texto-alerta"),
                Ui.columnaInsignia("Estado", ViajeResumen::estado, 104, Ui::tonoEstado),
                Ui.<ViajeResumen>columna("Observación", v -> v.observacion() == null ? "" : v.observacion(), 120, false)));
        tablaViajes.setPlaceholder(Ui.cargando("Cargando viajes…"));
        tablaViajes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaViajes.setMinHeight(110);
        VBox.setVgrow(tablaViajes, Priority.ALWAYS);

        Button btnNuevo = Ui.boton("Nuevo viaje", "mdi2p-plus", "boton-primario");
        btnNuevo.setOnAction(e -> nuevoViaje());
        Button btnEnviar = Ui.boton("Enviar al viaje", "mdi2t-truck-delivery-outline");
        btnEnviar.setTooltip(new Tooltip("Un viaje a la vez. Lo que el jefe pida durante el viaje se registra aquí."));
        btnEnviar.setOnAction(e -> enviar());
        Button btnImprimir = Ui.boton("Imprimir hoja", "mdi2p-printer");
        btnImprimir.setOnAction(e -> HojaCarga.imprimir(ventana(), detalle.get()));
        Button btnLiquidar = Ui.boton("Liquidar viaje", "mdi2c-clipboard-check-outline");
        btnLiquidar.setOnAction(e -> liquidar());

        var sinViajeAbierto = Bindings.createBooleanBinding(
                () -> detalle.get() == null || !detalle.get().viaje().abierto(), detalle);
        btnEnviar.disableProperty().bind(sinViajeAbierto);
        btnLiquidar.disableProperty().bind(sinViajeAbierto);
        btnImprimir.disableProperty().bind(detalle.isNull());

        Label etiquetaEstado = new Label("Estado:");
        etiquetaEstado.getStyleClass().add("etiqueta-filtro");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, etiquetaEstado, cboEstado, btnMostrar, espacio, btnNuevo, btnEnviar, btnImprimir,
                btnLiquidar);
        barra.setAlignment(Pos.CENTER_LEFT);

        VBox seccion = Ui.seccion("Viajes del jefe", "mdi2t-truck-outline", barra, tablaViajes);
        seccion.setMinHeight(190);
        return seccion;
    }

    private VBox crearDetalle() {
        tablaProductos.getColumns().setAll(List.of(
                Ui.<ViajeProducto>columna("Producto", ViajeProducto::nombreCompleto, 230, false),
                Ui.columnaInsignia("Tipo", ViajeProducto::tipoProducto, 100, Ui::tonoTipo),
                Ui.<ViajeProducto>columna("Llevó", p -> String.valueOf(p.llevado()), 65, true),
                Ui.<ViajeProducto>columna("En cajas", p -> Ui.cajas(p.llevado(), p.unidadesCaja()), 120, false),
                Ui.<ViajeProducto>columna("Vendió", p -> liquidado() ? String.valueOf(p.vendido()) : PENDIENTE, 65, true),
                Ui.<ViajeProducto>columna("Regresó", p -> liquidado() ? String.valueOf(p.devuelto()) : PENDIENTE, 70, true),
                Ui.<ViajeProducto>columna("Merma", p -> liquidado() ? String.valueOf(p.merma()) : PENDIENTE, 65, true),
                Ui.<ViajeProducto>columna("Motivo", p -> p.motivoMerma() == null ? "" : p.motivoMerma(), 120, false),
                Ui.<ViajeProducto>columna("Diferencia", p -> !liquidado() ? PENDIENTE : p.diferencia() == 0 ? "0 ✓"
                                : p.diferencia() > 0 ? "Faltan " + p.diferencia() : "Sobran " + (-p.diferencia()), 100, false,
                        p -> !liquidado() ? null : p.diferencia() == 0 ? "texto-ok" : "texto-alerta")));
        tablaProductos.setPlaceholder(Ui.estadoVacio("mdi2b-bottle-wine-outline", "Sin productos",
                "Elija un viaje de la lista para ver lo que llevó."));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tablaEnvios.getColumns().setAll(List.of(
                Ui.<Envio>columna("N°", e -> e.numero() == 1 ? "1 · Carga inicial" : String.valueOf(e.numero()), 110, false),
                Ui.<Envio>columna("Fecha", e -> e.fecha().atZoneSameInstant(ZoneId.systemDefault()).format(FECHA_HORA), 125, false),
                Ui.<Envio>columna("Ciudad", Envio::sede, 110, false),
                Ui.<Envio>columna("Referencia", e -> e.referencia() == null ? "" : e.referencia(), 130, false),
                Ui.<Envio>columna("Productos", FrmViajes::resumirEnvio, 330, false),
                Ui.<Envio>columna("Unidades", e -> String.valueOf(e.items().stream().mapToInt(EnvioItem::cantidad).sum()), 80, true),
                Ui.columnaInsignia("Estado", e -> e.anulado() ? "ANULADO" : "", 100, Ui::tonoEstado)));
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
        tablaEnvios.setPlaceholder(Ui.estadoVacio("mdi2t-truck-delivery-outline", "Sin envíos", null));
        tablaEnvios.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        tablaVentas.getColumns().setAll(List.of(
                Ui.<VentaCiudad>columna("Ciudad", VentaCiudad::sede, 140, false),
                Ui.<VentaCiudad>columna("Producto", v -> nombreProducto(v.productoId()), 280, false),
                Ui.<VentaCiudad>columna("Vendió", v -> String.valueOf(v.cantidad()), 80, true),
                Ui.<VentaCiudad>columna("En cajas", v -> Ui.cajas(v.cantidad(), unidadesCaja(v.productoId())), 140, false)));
        tablaVentas.setPlaceholder(Ui.estadoVacio("mdi2m-map-marker-outline", "Todavía no hay ventas por ciudad",
                "Se registran al liquidar el viaje."));
        tablaVentas.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TabPane pestanas = new TabPane(tabProductos, tabEnvios, tabVentas);
        pestanas.getStyleClass().add("pestanas-internas");
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        pestanas.setMinHeight(130);
        VBox.setVgrow(pestanas, Priority.ALWAYS);

        btnAnularEnvio.setVisible(empleado.esAdministrador());
        btnAnularEnvio.setManaged(empleado.esAdministrador());
        btnAnularEnvio.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            Envio envio = tablaEnvios.getSelectionModel().getSelectedItem();
            return envio == null || envio.anulado() || detalle.get() == null || !detalle.get().viaje().abierto();
        }, tablaEnvios.getSelectionModel().selectedItemProperty(), detalle));
        btnAnularEnvio.setOnAction(e -> anularEnvio());

        lblTituloDetalle.setGraphic(Ui.icono("mdi2c-calendar-clock"));
        lblTituloDetalle.getStyleClass().add("titulo-seccion");
        lblEstadoDetalle.managedProperty().bind(lblEstadoDetalle.visibleProperty());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox encabezado = new HBox(10, lblTituloDetalle, lblEstadoDetalle, espacio, btnAnularEnvio);
        encabezado.setAlignment(Pos.CENTER_LEFT);

        VBox seccion = new VBox(8, encabezado, pestanas);
        seccion.getStyleClass().add("seccion");
        seccion.setMinHeight(190);
        return seccion;
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
                    tablaViajes.setPlaceholder(Ui.estadoVacio("mdi2t-truck-outline", "No hay viajes registrados",
                            "Use \"Nuevo viaje\" cuando el jefe salga con mercadería."));
                    // El pedido, el abierto o el más reciente
                    lista.stream().filter(v -> seleccionar != null && v.id() == seleccionar).findFirst()
                            .or(() -> lista.stream().filter(ViajeResumen::abierto).findFirst())
                            .or(() -> lista.stream().findFirst())
                            .ifPresent(v -> tablaViajes.getSelectionModel().select(v));
                    mostrarResumen(lista.stream().filter(ViajeResumen::abierto).findFirst());
                },
                mensaje -> {
                    tablaViajes.setPlaceholder(Ui.estadoVacio("mdi2l-lan-disconnect", "No se pudo cargar los viajes",
                            mensaje));
                    Ui.mostrarError(ventana(), mensaje);
                });
    }

    private void mostrarResumen(Optional<ViajeResumen> abierto) {
        if (abierto.isEmpty()) {
            // Con el filtro LIQUIDADOS la lista no trae el abierto: no se puede afirmar que no exista
            lblViajeAbierto.setText(LIQUIDADOS.equals(filtroAplicado) ? "—" : "Ninguno");
            lblEnviosAbierto.setText("—");
            lblLlevadoAbierto.setText("—");
            lblCiudadesAbierto.setText("—");
            return;
        }
        ViajeResumen v = abierto.get();
        lblViajeAbierto.setText("#" + v.id() + " · " + v.dias() + (v.dias() == 1 ? " día" : " días") + " fuera");
        lblEnviosAbierto.setText(String.valueOf(v.envios()));
        lblLlevadoAbierto.setText(v.llevado() + " u.");
        lblCiudadesAbierto.setText(v.ciudades() == null || v.ciudades().isBlank() ? "—" : v.ciudades());
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
            lblEstadoDetalle.setVisible(false);
            productos.clear();
            envios.clear();
            ventas.clear();
            actualizarPestanas();
            return;
        }
        ViajeResumen v = nuevo.viaje();
        lblTituloDetalle.setText(String.format("Viaje #%d  ·  salió el %s  ·  %d %s%s", v.id(),
                fecha(v.fechaSalida()), v.dias(), v.dias() == 1 ? "día" : "días",
                v.abierto() ? " fuera" : "  ·  liquidado el " + fecha(v.fechaLiquidacion())));
        lblEstadoDetalle.setText(v.estado());
        lblEstadoDetalle.getStyleClass().removeIf(c -> c.startsWith("insignia-"));
        lblEstadoDetalle.getStyleClass().add("insignia-" + Ui.tonoEstado(v.estado()));
        lblEstadoDetalle.setVisible(true);
        productos.setAll(nuevo.productos());
        envios.setAll(nuevo.envios());
        ventas.setAll(nuevo.ventas());
        tablaProductos.refresh(); // las columnas dependen del estado del viaje
        actualizarPestanas();
    }

    /** Las pestañas muestran cuántas filas tiene cada una. */
    private void actualizarPestanas() {
        Ui.tituloPestana(tabProductos, "Productos (" + productos.size() + ")");
        Ui.tituloPestana(tabEnvios, "Envíos (" + envios.stream().filter(e -> !e.anulado()).count() + ")");
        Ui.tituloPestana(tabVentas, "Ventas por ciudad (" + ventas.size() + ")");
    }

    // ------------------------------------------------------------------ acciones

    private void nuevoViaje() {
        new DlgEnvio(ventana(), servicios, "Nuevo viaje · carga inicial", "Registrar salida", null,
                solicitud -> servicios.viajes().abrir(solicitud),
                abierto -> {
                    despuesDeOperar(abierto);
                    Ui.toast(this, "Viaje #" + abierto.viaje().id() + " abierto · " + abierto.viaje().llevado()
                            + " unidades salieron hacia " + abierto.viaje().ciudades() + ". Ya puede imprimir la hoja de carga.");
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
                enviado -> {
                    despuesDeOperar(enviado);
                    Ui.toast(this, "Envío registrado en el viaje #" + viajeId);
                }).show();
    }

    private void anularEnvio() {
        Envio envio = tablaEnvios.getSelectionModel().getSelectedItem();
        ViajeDetalle actual = detalle.get();
        if (envio == null || actual == null) {
            return;
        }
        int unidades = envio.items().stream().mapToInt(EnvioItem::cantidad).sum();
        if (!Ui.confirmar(ventana(),
                String.format("¿Anular el envío %d del viaje #%d (%s → %s)?", envio.numero(),
                        actual.viaje().id(), fecha(envio.fecha()), envio.sede()),
                String.format("Las %d unidades vuelven al stock del almacén.%nHágalo solo si esa mercadería no salió. "
                        + "Esta acción no se puede deshacer.", unidades),
                "Anular envío")) {
            return;
        }
        Ui.alCompletar(servicios.viajes().anularEnvio(envio.id()),
                resultado -> {
                    despuesDeOperar(resultado);
                    Ui.toast(this, "Envío " + envio.numero() + " anulado: " + unidades + " unidades volvieron al almacén");
                },
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
                            Ui.toast(this, String.format("Viaje #%d liquidado · Llevó %d · Vendió %d · Regresó %d · Merma %d · %s",
                                    v.id(), v.llevado(), v.vendido(), v.devuelto(), v.merma(), v.diferencia() == 0
                                            ? "Todo cuadra ✓" : "Diferencia " + v.diferencia() + " u. (en la observación)"),
                                    v.diferencia() != 0);
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
