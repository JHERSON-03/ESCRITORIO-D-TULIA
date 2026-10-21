package pe.dtulia.desktop.vista;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import pe.dtulia.desktop.modelo.ProduccionDetalle;
import pe.dtulia.desktop.modelo.ProduccionProducto;
import pe.dtulia.desktop.modelo.ProduccionResumen;
import pe.dtulia.desktop.servicio.Servicios;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Cajas que la fábrica manda al almacén. La administradora de la fábrica registra el envío
 * (EN CAMINO) y la del almacén confirma lo que llegó (el stock sube recién ahí). La lista se
 * refresca sola para que el almacén vea los envíos nuevos. Solo ADMINISTRADOR.
 */
public class FrmProduccion extends BorderPane {

    private static final Duration INTERVALO_REFRESCO = Duration.seconds(60);
    private static final String TODOS = "TODOS";
    private static final String EN_CAMINO = "EN CAMINO";
    private static final String RECIBIDOS = "RECIBIDOS";
    private static final String ANULADOS = "ANULADOS";
    private static final String PENDIENTE = "—";

    private final Servicios servicios;
    private final Runnable alCambiarStock;
    private final IntConsumer alCambiarEnCamino;

    private final ComboBox<String> cboEstado =
            new ComboBox<>(FXCollections.observableArrayList(TODOS, EN_CAMINO, RECIBIDOS, ANULADOS));
    private final ObservableList<ProduccionResumen> envios = FXCollections.observableArrayList();
    private final TableView<ProduccionResumen> tablaEnvios = new TableView<>(envios);
    private final Label lblEnCamino = new Label(null, Ui.icono("mdi2a-alert-circle-outline"));
    private final Label lblEnviosEnCamino = new Label("—");
    private final Label lblUnidadesEnCamino = new Label("—");
    private final Label lblMasAntiguo = new Label("—");
    private final Label lblTituloDetalle = new Label();
    private final Label lblEstadoDetalle = Ui.insignia("", "neutro");
    /** Observación del envío (lo que anotó quien lo recibió o anuló); oculta si no hay. */
    private final Label lblObservacion = new Label();
    private final ObservableList<ProduccionProducto> productos = FXCollections.observableArrayList();
    private final TableView<ProduccionProducto> tablaProductos = new TableView<>(productos);
    private final ObjectProperty<ProduccionDetalle> detalle = new SimpleObjectProperty<>();
    private final Timeline refresco;
    private String filtroAplicado = TODOS;

    /**
     * @param alCambiarEnCamino recibe cuántos envíos siguen en camino (para el título de la pestaña)
     */
    public FrmProduccion(Servicios servicios, Runnable alCambiarStock, IntConsumer alCambiarEnCamino) {
        this.servicios = servicios;
        this.alCambiarStock = alCambiarStock;
        this.alCambiarEnCamino = alCambiarEnCamino;
        getStyleClass().add("formulario");

        // Lista arriba y detalle abajo en un panel dividido: se puede arrastrar el separador
        SplitPane division = new SplitPane(crearListado(), crearDetalle());
        division.setOrientation(Orientation.VERTICAL);
        division.setDividerPositions(0.55);
        division.getStyleClass().add("division-vertical");
        setTop(crearResumen());
        setCenter(division);
        BorderPane.setMargin(division, new Insets(10, 0, 0, 0));

        tablaEnvios.getSelectionModel().selectedItemProperty().addListener((obs, antes, ahora) -> {
            if (ahora == null) {
                mostrarDetalle(null);
            } else {
                cargarDetalle(ahora.id());
            }
        });
        refresco = new Timeline(new KeyFrame(INTERVALO_REFRESCO, e -> recargar()));
        refresco.setCycleCount(Animation.INDEFINITE);
        refresco.play();
        mostrarDetalle(null);
        recargar();
    }

    public void detener() {
        refresco.stop();
    }

    // ------------------------------------------------------------------ construcción

    /** Tarjetas de lo pendiente de recibir (siempre todos los envíos en camino, sin importar el filtro). */
    private HBox crearResumen() {
        return new HBox(10,
                Ui.tarjetaResumen("mdi2t-truck-fast-outline", "Envíos de fábrica en camino", lblEnviosEnCamino, "aviso"),
                Ui.tarjetaResumen("mdi2p-package-variant-closed", "Unidades por recibir", lblUnidadesEnCamino, "vino"),
                Ui.tarjetaResumen("mdi2t-timer-sand", "El más antiguo en camino", lblMasAntiguo, "neutro"));
    }

    private VBox crearListado() {
        cboEstado.setValue(TODOS);
        Button btnMostrar = Ui.boton("Mostrar", "mdi2f-filter-outline");
        btnMostrar.setOnAction(e -> {
            filtroAplicado = cboEstado.getValue();
            recargar();
        });
        lblEnCamino.getStyleClass().add("mensaje-barra");
        lblEnCamino.visibleProperty().bind(lblEnCamino.textProperty().isNotEmpty());
        lblEnCamino.managedProperty().bind(lblEnCamino.visibleProperty());

        tablaEnvios.getColumns().setAll(List.of(
                Ui.<ProduccionResumen>columna("#", p -> "#" + p.id(), 50, true),
                Ui.<ProduccionResumen>columna("Enviado", p -> FrmViajes.fecha(p.fechaEnvio()), 92, false),
                Ui.<ProduccionResumen>columna("Días", p -> String.valueOf(p.dias()), 52, true,
                        p -> p.enCamino() && p.dias() > 0 ? "texto-alerta" : null),
                Ui.conTextoCompleto(Ui.<ProduccionResumen>columna("Envió", ProduccionResumen::enviadoPor, 110, false)),
                Ui.conTextoCompleto(Ui.<ProduccionResumen>columna("Referencia",
                        p -> p.referencia() == null ? "" : p.referencia(), 95, false)),
                Ui.<ProduccionResumen>columna("Productos", p -> String.valueOf(p.productos()), 82, true),
                Ui.<ProduccionResumen>columna("Unidades", p -> String.valueOf(p.enviado()), 80, true),
                Ui.<ProduccionResumen>columna("Llegaron", p -> p.recibido() == null ? PENDIENTE : String.valueOf(p.recibido()), 78, true),
                Ui.<ProduccionResumen>columna("Diferencia", p -> p.diferencia() == null ? PENDIENTE : String.valueOf(p.diferencia()), 86,
                        true, p -> p.diferencia() == null ? null : p.diferencia() == 0 ? "texto-ok" : "texto-alerta"),
                Ui.columnaInsignia("Estado", ProduccionResumen::estadoTexto, 108, Ui::tonoEstado),
                Ui.conTextoCompleto(Ui.<ProduccionResumen>columna("Recibió / anuló",
                        p -> p.cerradoPor() == null ? "" : p.cerradoPor(), 110, false))));
        tablaEnvios.setRowFactory(t -> new TableRow<>() {
            @Override
            protected void updateItem(ProduccionResumen p, boolean vacio) {
                super.updateItem(p, vacio);
                getStyleClass().remove("anulado");
                if (!vacio && p != null && "ANULADO".equals(p.estado())) {
                    getStyleClass().add("anulado");
                }
            }
        });
        tablaEnvios.setPlaceholder(Ui.cargando("Cargando envíos de producción…"));
        tablaEnvios.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaEnvios.setMinHeight(110);
        VBox.setVgrow(tablaEnvios, Priority.ALWAYS);

        Button btnNuevo = Ui.boton("Nuevo envío (fábrica)", "mdi2p-plus", "boton-primario");
        btnNuevo.setTooltip(new Tooltip("La fábrica registra lo que manda; el almacén confirma lo que llegó "
                + "y recién ahí sube el stock."));
        btnNuevo.setOnAction(e -> new DlgProduccion(ventana(), servicios, registrado -> {
            despuesDeOperar(registrado);
            Ui.toast(this, "Envío #" + registrado.produccion().id() + " registrado: queda EN CAMINO hasta que el almacén lo reciba");
        }).show());
        Button btnRecibir = Ui.boton("Recibir (almacén)", "mdi2i-inbox-arrow-down-outline");
        btnRecibir.setOnAction(e -> recibir());
        Button btnAnular = Ui.boton("Anular envío", "mdi2c-cancel", "boton-peligro");
        btnAnular.setOnAction(e -> anular());
        var noEnCamino = Bindings.createBooleanBinding(
                () -> detalle.get() == null || !detalle.get().produccion().enCamino(), detalle);
        btnRecibir.disableProperty().bind(noEnCamino);
        btnAnular.disableProperty().bind(noEnCamino);

        Label etiquetaEstado = new Label("Estado:");
        etiquetaEstado.getStyleClass().add("etiqueta-filtro");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, etiquetaEstado, cboEstado, btnMostrar, lblEnCamino, espacio, btnNuevo, btnRecibir,
                btnAnular);
        barra.setAlignment(Pos.CENTER_LEFT);

        VBox seccion = Ui.seccion("Envíos de la fábrica al almacén", "mdi2f-factory", barra, tablaEnvios);
        seccion.setMinHeight(190);
        return seccion;
    }

    private VBox crearDetalle() {
        tablaProductos.getColumns().setAll(List.of(
                Ui.<ProduccionProducto>columna("Producto", ProduccionProducto::nombreCompleto, 240, false),
                Ui.<ProduccionProducto>columna("Envió", p -> String.valueOf(p.enviado()), 70, true),
                Ui.<ProduccionProducto>columna("Envió en cajas", p -> Ui.cajas(p.enviado(), p.unidadesCaja()), 130, false),
                Ui.<ProduccionProducto>columna("Llegó", p -> p.recibido() == null ? PENDIENTE : String.valueOf(p.recibido()), 70, true),
                Ui.<ProduccionProducto>columna("Llegó en cajas", p -> p.recibido() == null ? "" : Ui.cajas(p.recibido(), p.unidadesCaja()), 130, false),
                Ui.<ProduccionProducto>columna("Diferencia", p -> p.diferencia() == null ? PENDIENTE
                                : p.diferencia() == 0 ? "0 ✓" : p.diferencia() > 0 ? "Faltan " + p.diferencia()
                                : "Sobran " + (-p.diferencia()), 100, false,
                        p -> p.diferencia() == null ? null : p.diferencia() == 0 ? "texto-ok" : "texto-alerta")));
        tablaProductos.setPlaceholder(Ui.estadoVacio("mdi2c-clipboard-list-outline", "Seleccione un envío",
                "Elija un envío de la lista para ver sus productos."));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaProductos.setMinHeight(100);
        VBox.setVgrow(tablaProductos, Priority.ALWAYS);

        lblTituloDetalle.setGraphic(Ui.icono("mdi2t-truck-delivery-outline"));
        lblTituloDetalle.getStyleClass().add("titulo-seccion");
        lblEstadoDetalle.managedProperty().bind(lblEstadoDetalle.visibleProperty());
        HBox encabezado = new HBox(10, lblTituloDetalle, lblEstadoDetalle);
        encabezado.setAlignment(Pos.CENTER_LEFT);
        Label etiquetaObservacion = new Label("Observación:", Ui.icono("mdi2c-comment-text-outline"));
        etiquetaObservacion.getStyleClass().add("etiqueta-filtro");
        etiquetaObservacion.setMinWidth(Region.USE_PREF_SIZE);
        lblObservacion.setWrapText(true);
        lblObservacion.getStyleClass().add("observacion");
        HBox observacion = new HBox(8, etiquetaObservacion, lblObservacion);
        observacion.setAlignment(Pos.TOP_LEFT);
        observacion.visibleProperty().bind(lblObservacion.textProperty().isNotEmpty());
        observacion.managedProperty().bind(observacion.visibleProperty());
        VBox seccion = new VBox(10, encabezado, observacion, tablaProductos);
        seccion.getStyleClass().add("seccion");
        seccion.setMinHeight(170);
        return seccion;
    }

    // ------------------------------------------------------------------ datos

    /** Recarga la lista conservando el envío seleccionado. */
    public void recargar() {
        ProduccionResumen seleccionado = tablaEnvios.getSelectionModel().getSelectedItem();
        recargar(seleccionado == null ? null : seleccionado.id());
    }

    private void recargar(Long seleccionar) {
        String estado = switch (filtroAplicado) {
            case EN_CAMINO -> "EN_CAMINO";
            case RECIBIDOS -> "RECIBIDO";
            case ANULADOS -> "ANULADO";
            default -> null;
        };
        Ui.alCompletar(servicios.producciones().listar(estado),
                lista -> {
                    envios.setAll(lista);
                    lblEnCamino.setText(null);
                    tablaEnvios.setPlaceholder(TODOS.equals(filtroAplicado)
                            ? Ui.estadoVacio("mdi2f-factory", "No hay envíos de producción",
                                    "La fábrica los registra con \"Nuevo envío (fábrica)\".")
                            : Ui.estadoVacio("mdi2f-filter-outline", "No hay envíos " + filtroAplicado,
                                    "Elija \"TODOS\" y presione Mostrar."));
                    // El pedido, el primero en camino o el más reciente
                    lista.stream().filter(p -> seleccionar != null && p.id() == seleccionar).findFirst()
                            .or(() -> lista.stream().filter(ProduccionResumen::enCamino).findFirst())
                            .or(() -> lista.stream().findFirst())
                            .ifPresent(p -> tablaEnvios.getSelectionModel().select(p));
                    actualizarEnCamino();
                },
                mensaje -> {
                    lblEnCamino.setText(mensaje);
                    if (envios.isEmpty()) {
                        tablaEnvios.setPlaceholder(Ui.estadoVacio("mdi2l-lan-disconnect",
                                "No se pudo cargar los envíos", mensaje + " Se reintentará en un minuto."));
                    }
                });
    }

    /** El contador de la pestaña siempre cuenta todos los envíos en camino, sin importar el filtro. */
    private void actualizarEnCamino() {
        Ui.alCompletar(servicios.producciones().listar("EN_CAMINO"),
                enCamino -> {
                    lblEnviosEnCamino.setText(enCamino.isEmpty() ? "Ninguno" : String.valueOf(enCamino.size()));
                    lblUnidadesEnCamino.setText(enCamino.stream().mapToInt(ProduccionResumen::enviado).sum() + " u.");
                    lblMasAntiguo.setText(enCamino.stream().mapToInt(ProduccionResumen::dias).max()
                            .stream().mapToObj(d -> d == 0 ? "Hoy" : d + (d == 1 ? " día" : " días"))
                            .findFirst().orElse("—"));
                    alCambiarEnCamino.accept(enCamino.size());
                },
                lblEnCamino::setText);
    }

    private void cargarDetalle(long produccionId) {
        Ui.alCompletar(servicios.producciones().detalle(produccionId),
                cargado -> {
                    ProduccionResumen actual = tablaEnvios.getSelectionModel().getSelectedItem();
                    if (actual != null && actual.id() == produccionId) { // descarta respuestas de una selección anterior
                        mostrarDetalle(cargado);
                    }
                },
                mensaje -> Ui.mostrarError(ventana(), mensaje));
    }

    private void mostrarDetalle(ProduccionDetalle nuevo) {
        detalle.set(nuevo);
        if (nuevo == null) {
            lblTituloDetalle.setText("Detalle del envío");
            lblEstadoDetalle.setVisible(false);
            lblObservacion.setText("");
            productos.clear();
            return;
        }
        ProduccionResumen p = nuevo.produccion();
        lblTituloDetalle.setText(String.format("Envío #%d  ·  enviado el %s por %s%s", p.id(),
                FrmViajes.fecha(p.fechaEnvio()), p.enviadoPor(),
                p.fechaCierre() == null ? "" : "  ·  cerrado el " + FrmViajes.fecha(p.fechaCierre()) + " por " + p.cerradoPor()));
        lblEstadoDetalle.setText(p.estadoTexto());
        lblEstadoDetalle.getStyleClass().removeIf(c -> c.startsWith("insignia-"));
        lblEstadoDetalle.getStyleClass().add("insignia-" + Ui.tonoEstado(p.estadoTexto()));
        lblEstadoDetalle.setVisible(true);
        lblObservacion.setText(p.observacion() == null ? "" : p.observacion().trim());
        productos.setAll(nuevo.productos());
    }

    // ------------------------------------------------------------------ acciones

    private void recibir() {
        ProduccionDetalle actual = detalle.get();
        if (actual != null && actual.produccion().enCamino()) {
            new DlgRecepcion(ventana(), servicios, actual, recibido -> {
                despuesDeOperar(recibido);
                ProduccionResumen p = recibido.produccion();
                boolean conDiferencia = p.diferencia() != null && p.diferencia() != 0;
                Ui.toast(this, String.format("Envío #%d recibido · %d unidades sumadas al stock%s", p.id(), p.recibido(),
                        conDiferencia ? " · Diferencia " + p.diferencia() + " u. (en la observación)" : ""), conDiferencia);
            }).show();
        }
    }

    /** Pide el motivo y confirma: el diálogo es rojo y Enter no anula por accidente. */
    private void anular() {
        ProduccionDetalle actual = detalle.get();
        if (actual == null || !actual.produccion().enCamino()) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog();
        Ui.aplicarEstilos(dialogo);
        dialogo.initOwner(ventana());
        dialogo.setTitle("Anular envío de producción");
        dialogo.setHeaderText("¿Anular el envío #" + actual.produccion().id() + "?\n"
                + "Hágalo solo si se registró por error. El stock no cambia y no se puede deshacer.");
        dialogo.setGraphic(Ui.icono("mdi2a-alert-outline", 32));
        dialogo.setContentText("Motivo:");
        dialogo.getDialogPane().getStyleClass().add("dialogo-peligro");
        ButtonType anular = new ButtonType("Anular envío", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogo.getDialogPane().getButtonTypes().setAll(cancelar, anular);
        Button btnAnular = (Button) dialogo.getDialogPane().lookupButton(anular);
        btnAnular.getStyleClass().add("boton-peligro-solido");
        btnAnular.setDefaultButton(false);
        ((Button) dialogo.getDialogPane().lookupButton(cancelar)).setDefaultButton(true);
        dialogo.setResultConverter(boton -> boton == anular ? dialogo.getEditor().getText() : null);

        long id = actual.produccion().id();
        dialogo.showAndWait().ifPresent(motivo ->
                Ui.alCompletar(servicios.producciones().anular(id, motivo.isBlank() ? null : motivo.trim()),
                        resultado -> {
                            despuesDeOperar(resultado);
                            Ui.toast(this, "Envío #" + id + " anulado");
                        },
                        mensaje -> Ui.mostrarError(ventana(), mensaje)));
    }

    private void despuesDeOperar(ProduccionDetalle resultado) {
        alCambiarStock.run();
        recargar(resultado.produccion().id());
    }

    private Window ventana() {
        return getScene() == null ? null : getScene().getWindow();
    }
}
