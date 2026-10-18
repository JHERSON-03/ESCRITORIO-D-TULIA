package pe.dtulia.desktop.vista;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
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
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import pe.dtulia.desktop.modelo.EnvioSolicitud;
import pe.dtulia.desktop.modelo.ItemViaje;
import pe.dtulia.desktop.modelo.Sede;
import pe.dtulia.desktop.modelo.ViajeDetalle;
import pe.dtulia.desktop.servicio.Servicios;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Mercadería que sale del almacén hacia el jefe: la carga inicial de un viaje o lo que pide
 * durante el viaje. Cada producto se ingresa en cajas + unidades sueltas.
 */
class DlgEnvio extends Stage {

    private final Servicios servicios;
    private final Long ciudadSugerida;
    private final Function<EnvioSolicitud, CompletableFuture<ViajeDetalle>> registrar;
    private final Consumer<ViajeDetalle> alRegistrar;

    private final ComboBox<Sede> cboCiudad = new ComboBox<>();
    private final TextField txtReferencia = new TextField();
    private final SelectorProductos selector =
            new SelectorProductos(true, "Agregue los productos que salen del almacén");
    private final Label lblTotal = Ui.insignia("", "vino");
    private final Button btnRegistrar;
    private final String textoBoton;
    private final ProgressIndicator progreso = new ProgressIndicator();

    /**
     * @param ciudadSugerida ciudad elegida al abrir (la del último envío) o null
     * @param registrar      llamada a la API (abrir viaje o enviar al viaje)
     */
    DlgEnvio(Window duenio, Servicios servicios, String titulo, String textoBoton, Long ciudadSugerida,
             Function<EnvioSolicitud, CompletableFuture<ViajeDetalle>> registrar, Consumer<ViajeDetalle> alRegistrar) {
        this.servicios = servicios;
        this.ciudadSugerida = ciudadSugerida;
        this.registrar = registrar;
        this.alRegistrar = alRegistrar;
        this.textoBoton = textoBoton;
        this.btnRegistrar = Ui.boton(textoBoton, "mdi2c-check", "boton-primario");

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · " + titulo);
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(crearDatos(titulo));
        raiz.setCenter(crearDetalle());
        raiz.setBottom(crearPie());
        Scene escena = new Scene(raiz, 900, 620);
        Ui.aplicarEstilos(escena);
        setScene(escena);

        // Esc cierra (pregunta si ya hay productos); Ctrl+Enter registra
        Ui.cerrarConEsc(this, () -> !selector.lineas().isEmpty());
        Ui.atajo(raiz, new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!btnRegistrar.isDisabled()) {
                registrar();
            }
        });

        selector.lineas().addListener((ListChangeListener<SelectorProductos.Linea>) c -> actualizarTotal());
        actualizarTotal();
        cargarDatos();
    }

    private VBox crearDatos(String titulo) {
        cboCiudad.setPromptText("Cargando ciudades…");
        cboCiudad.setMaxWidth(Double.MAX_VALUE);
        txtReferencia.setPromptText("Agencia, guía, placa… (opcional)");
        txtReferencia.setMaxWidth(Double.MAX_VALUE);

        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(8);
        grilla.addRow(0, etiqueta("Ciudad"), cboCiudad, etiqueta("Referencia"), txtReferencia);
        ColumnConstraints etiqueta = new ColumnConstraints();
        ColumnConstraints campo = new ColumnConstraints(180, 240, Double.MAX_VALUE);
        campo.setHgrow(Priority.ALWAYS);
        grilla.getColumnConstraints().setAll(etiqueta, campo, etiqueta, campo);
        return Ui.seccion(titulo, "mdi2t-truck-delivery-outline", grilla);
    }

    private static Label etiqueta(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-filtro");
        return etiqueta;
    }

    private VBox crearDetalle() {
        Label aviso = new Label("Al registrar, estas unidades salen del stock del almacén (movimiento SALIDA_VIAJE).",
                Ui.icono("mdi2i-information-outline"));
        aviso.getStyleClass().add("ayuda");
        VBox detalle = Ui.seccion("Productos", "mdi2b-bottle-wine-outline", selector, aviso);
        VBox.setVgrow(selector, Priority.ALWAYS);
        VBox.setVgrow(detalle, Priority.ALWAYS);
        VBox contenedor = new VBox(detalle);
        contenedor.setPadding(new Insets(10, 0, 10, 0));
        return contenedor;
    }

    private HBox crearPie() {
        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setTooltip(new Tooltip("Esc"));
        btnCancelar.setOnAction(e -> close());
        btnRegistrar.setTooltip(new Tooltip("Ctrl+Enter"));
        btnRegistrar.setOnAction(e -> registrar());
        progreso.setPrefSize(16, 16);
        progreso.setMaxSize(16, 16);
        Label atajos = new Label("Enter agrega · Supr quita · Ctrl+Enter registra · Esc cierra",
                Ui.icono("mdi2k-keyboard-outline"));
        atajos.getStyleClass().add("ayuda");

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(12, atajos, espacio, lblTotal, btnCancelar, btnRegistrar);
        pie.setAlignment(Pos.CENTER_RIGHT);
        return pie;
    }

    private void cargarDatos() {
        Ui.alCompletar(servicios.catalogos().sedes(),
                sedes -> {
                    List<Sede> ciudades = sedes.stream().filter(s -> !s.esAlmacen()).toList();
                    cboCiudad.getItems().setAll(ciudades);
                    cboCiudad.setPromptText("Seleccione la ciudad");
                    ciudades.stream().filter(s -> ciudadSugerida != null && s.id() == ciudadSugerida).findFirst()
                            .ifPresent(cboCiudad::setValue);
                },
                mensaje -> Ui.mostrarError(this, mensaje));
        Ui.alCompletar(servicios.productos().listar(), selector::setProductos, mensaje -> Ui.mostrarError(this, mensaje));
    }

    private void registrar() {
        Sede ciudad = cboCiudad.getValue();
        if (ciudad == null) {
            Ui.mostrarError(this, "Seleccione la ciudad a la que va la mercadería.");
            return;
        }
        if (selector.lineas().isEmpty()) {
            Ui.mostrarError(this, "Agregue al menos un producto.");
            return;
        }
        String referencia = txtReferencia.getText().trim();
        EnvioSolicitud solicitud = new EnvioSolicitud(ciudad.id(), referencia.isEmpty() ? null : referencia,
                selector.lineas().stream().map(l -> new ItemViaje(l.producto().id(), l.cantidad())).toList());

        ocupado(true);
        Ui.alCompletar(registrar.apply(solicitud),
                detalle -> {
                    close();
                    alRegistrar.accept(detalle);
                },
                mensaje -> {
                    ocupado(false);
                    Ui.mostrarError(this, mensaje);
                });
    }

    private void ocupado(boolean ocupado) {
        btnRegistrar.setDisable(ocupado);
        btnRegistrar.setText(ocupado ? "Registrando…" : textoBoton);
        btnRegistrar.setGraphic(ocupado ? progreso : Ui.icono("mdi2c-check"));
    }

    private void actualizarTotal() {
        List<SelectorProductos.Linea> lineas = selector.lineas();
        int unidades = lineas.stream().mapToInt(SelectorProductos.Linea::cantidad).sum();
        lblTotal.setText(lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + "  ·  " + unidades + " unidades");
    }
}
