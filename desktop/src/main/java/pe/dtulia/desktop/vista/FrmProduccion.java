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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
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
    private final Label lblEnCamino = new Label();
    private final Label lblTituloDetalle = new Label();
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

        setTop(crearListado());
        setCenter(crearDetalle());

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

    private VBox crearListado() {
        cboEstado.setValue(TODOS);
        Button btnMostrar = new Button("Mostrar");
        btnMostrar.getStyleClass().add("boton-primario");
        btnMostrar.setOnAction(e -> {
            filtroAplicado = cboEstado.getValue();
            recargar();
        });
        lblEnCamino.getStyleClass().add("cliente");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(10, new Label("Estado"), cboEstado, btnMostrar, espacio, lblEnCamino);
        barra.setAlignment(Pos.CENTER_LEFT);

        tablaEnvios.getColumns().setAll(List.of(
                Ui.<ProduccionResumen>columna("#", p -> "#" + p.id(), 55, true),
                Ui.<ProduccionResumen>columna("Enviado", p -> FrmViajes.fecha(p.fechaEnvio()), 95, false),
                Ui.<ProduccionResumen>columna("Días", p -> String.valueOf(p.dias()), 55, true,
                        p -> p.enCamino() && p.dias() > 0 ? "texto-alerta" : null),
                Ui.<ProduccionResumen>columna("Envió", ProduccionResumen::enviadoPor, 160, false),
                Ui.<ProduccionResumen>columna("Referencia", p -> p.referencia() == null ? "" : p.referencia(), 120, false),
                Ui.<ProduccionResumen>columna("Productos", p -> String.valueOf(p.productos()), 80, true),
                Ui.<ProduccionResumen>columna("Unidades", p -> String.valueOf(p.enviado()), 80, true),
                Ui.<ProduccionResumen>columna("Llegaron", p -> p.recibido() == null ? PENDIENTE : String.valueOf(p.recibido()), 80, true),
                Ui.<ProduccionResumen>columna("Diferencia", p -> p.diferencia() == null ? PENDIENTE : String.valueOf(p.diferencia()), 85,
                        true, p -> p.diferencia() == null ? null : p.diferencia() == 0 ? "texto-ok" : "texto-alerta"),
                Ui.<ProduccionResumen>columna("Estado", ProduccionResumen::estadoTexto, 95, false),
                Ui.<ProduccionResumen>columna("Recibió / anuló", p -> p.cerradoPor() == null ? "" : p.cerradoPor(), 160, false),
                Ui.<ProduccionResumen>columna("Observación", p -> p.observacion() == null ? "" : p.observacion(), 200, false)));
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
        tablaEnvios.setPlaceholder(new Label("No hay envíos de producción. La fábrica los registra con \"Nuevo envío\"."));
        tablaEnvios.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tablaEnvios.setPrefHeight(240);

        Button btnNuevo = new Button("Nuevo envío (fábrica)");
        btnNuevo.getStyleClass().add("boton-primario");
        btnNuevo.setOnAction(e -> new DlgProduccion(ventana(), servicios, this::despuesDeOperar).show());
        Button btnRecibir = new Button("Recibir (almacén)");
        btnRecibir.setOnAction(e -> recibir());
        Button btnAnular = new Button("Anular envío");
        btnAnular.setOnAction(e -> anular());
        var noEnCamino = Bindings.createBooleanBinding(
                () -> detalle.get() == null || !detalle.get().produccion().enCamino(), detalle);
        btnRecibir.disableProperty().bind(noEnCamino);
        btnAnular.disableProperty().bind(noEnCamino);

        Label ayuda = new Label("La fábrica registra lo que manda; el almacén confirma lo que llegó y recién ahí sube el stock.");
        ayuda.getStyleClass().add("estado");
        Region espacioAcciones = new Region();
        HBox.setHgrow(espacioAcciones, Priority.ALWAYS);
        HBox acciones = new HBox(10, ayuda, espacioAcciones, btnNuevo, btnRecibir, btnAnular);
        acciones.setAlignment(Pos.CENTER_RIGHT);

        return Ui.seccion("Envíos de la fábrica al almacén", barra, tablaEnvios, acciones);
    }

    private VBox crearDetalle() {
        tablaProductos.getColumns().setAll(List.of(
                Ui.<ProduccionProducto>columna("Producto", ProduccionProducto::nombreCompleto, 240, false),
                Ui.<ProduccionProducto>columna("Tipo", ProduccionProducto::tipoProducto, 100, false),
                Ui.<ProduccionProducto>columna("Envió", p -> String.valueOf(p.enviado()), 70, true),
                Ui.<ProduccionProducto>columna("Envió en cajas", p -> Ui.cajas(p.enviado(), p.unidadesCaja()), 130, false),
                Ui.<ProduccionProducto>columna("Llegó", p -> p.recibido() == null ? PENDIENTE : String.valueOf(p.recibido()), 70, true),
                Ui.<ProduccionProducto>columna("Llegó en cajas", p -> p.recibido() == null ? "" : Ui.cajas(p.recibido(), p.unidadesCaja()), 130, false),
                Ui.<ProduccionProducto>columna("Diferencia", p -> p.diferencia() == null ? PENDIENTE
                                : p.diferencia() == 0 ? "0 ✓" : p.diferencia() > 0 ? "Faltan " + p.diferencia()
                                : "Sobran " + (-p.diferencia()), 100, false,
                        p -> p.diferencia() == null ? null : p.diferencia() == 0 ? "texto-ok" : "texto-alerta")));
        tablaProductos.setPlaceholder(new Label("Seleccione un envío"));
        tablaProductos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tablaProductos, Priority.ALWAYS);

        lblTituloDetalle.getStyleClass().add("titulo-seccion");
        VBox seccion = new VBox(10, lblTituloDetalle, tablaProductos);
        seccion.getStyleClass().add("seccion");
        VBox.setVgrow(seccion, Priority.ALWAYS);
        VBox contenedor = new VBox(seccion);
        contenedor.setPadding(new Insets(10, 0, 0, 0));
        return contenedor;
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
                    // El pedido, el primero en camino o el más reciente
                    lista.stream().filter(p -> seleccionar != null && p.id() == seleccionar).findFirst()
                            .or(() -> lista.stream().filter(ProduccionResumen::enCamino).findFirst())
                            .or(() -> lista.stream().findFirst())
                            .ifPresent(p -> tablaEnvios.getSelectionModel().select(p));
                    actualizarEnCamino();
                },
                mensaje -> lblEnCamino.setText("⚠ " + mensaje));
    }

    /** El contador de la pestaña siempre cuenta todos los envíos en camino, sin importar el filtro. */
    private void actualizarEnCamino() {
        Ui.alCompletar(servicios.producciones().listar("EN_CAMINO"),
                enCamino -> {
                    lblEnCamino.setText(enCamino.isEmpty() ? "Nada en camino"
                            : enCamino.size() + (enCamino.size() == 1 ? " envío en camino" : " envíos en camino"));
                    alCambiarEnCamino.accept(enCamino.size());
                },
                mensaje -> lblEnCamino.setText("⚠ " + mensaje));
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
            productos.clear();
            return;
        }
        ProduccionResumen p = nuevo.produccion();
        lblTituloDetalle.setText(String.format("Envío #%d  ·  %s  ·  enviado el %s por %s%s", p.id(), p.estadoTexto(),
                FrmViajes.fecha(p.fechaEnvio()), p.enviadoPor(),
                p.fechaCierre() == null ? "" : "  ·  cerrado el " + FrmViajes.fecha(p.fechaCierre()) + " por " + p.cerradoPor()));
        productos.setAll(nuevo.productos());
    }

    // ------------------------------------------------------------------ acciones

    private void recibir() {
        ProduccionDetalle actual = detalle.get();
        if (actual != null && actual.produccion().enCamino()) {
            new DlgRecepcion(ventana(), servicios, actual, recibido -> {
                despuesDeOperar(recibido);
                ProduccionResumen p = recibido.produccion();
                Ui.mostrarInfo(ventana(), "Envío #" + p.id() + " recibido", String.format(
                        "Llegaron %d unidades y se sumaron al stock del almacén.%s", p.recibido(),
                        p.diferencia() != null && p.diferencia() != 0
                                ? "\nDiferencia: " + p.diferencia() + " unidades (quedó en la observación)." : ""));
            }).show();
        }
    }

    private void anular() {
        ProduccionDetalle actual = detalle.get();
        if (actual == null || !actual.produccion().enCamino()) {
            return;
        }
        TextInputDialog dialogo = new TextInputDialog();
        dialogo.initOwner(ventana());
        dialogo.setTitle("Anular envío de producción");
        dialogo.setHeaderText("¿Anular el envío #" + actual.produccion().id() + "?\n"
                + "Hágalo solo si se registró por error. El stock no cambia.");
        dialogo.setContentText("Motivo:");
        dialogo.showAndWait().ifPresent(motivo ->
                Ui.alCompletar(servicios.producciones().anular(actual.produccion().id(),
                                motivo.isBlank() ? null : motivo.trim()),
                        this::despuesDeOperar,
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
