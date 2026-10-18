package pe.dtulia.desktop.vista;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import pe.dtulia.desktop.modelo.ItemProduccion;
import pe.dtulia.desktop.modelo.ProduccionDetalle;
import pe.dtulia.desktop.modelo.ProduccionSolicitud;
import pe.dtulia.desktop.servicio.Servicios;

import java.util.List;
import java.util.function.Consumer;

/**
 * La administradora de la fábrica pasa al sistema lo que anotaron en el cuaderno: las cajas que
 * salen hacia el almacén. El stock no cambia hasta que el almacén confirma lo que llegó.
 */
class DlgProduccion extends Stage {

    private final Servicios servicios;
    private final Consumer<ProduccionDetalle> alRegistrar;
    private final TextField txtReferencia = new TextField();
    private final SelectorProductos selector =
            new SelectorProductos(false, "Agregue las cajas que salen de la fábrica hacia el almacén");
    private final Label lblTotal = Ui.insignia("", "vino");
    private final Button btnRegistrar = Ui.boton("Registrar envío", "mdi2c-check", "boton-primario");
    private final ProgressIndicator progreso = new ProgressIndicator();

    DlgProduccion(Window duenio, Servicios servicios, Consumer<ProduccionDetalle> alRegistrar) {
        this.servicios = servicios;
        this.alRegistrar = alRegistrar;

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · Envío de producción");

        txtReferencia.setPromptText("Lote, vehículo o nota (opcional)");
        HBox.setHgrow(txtReferencia, Priority.ALWAYS);
        Label lblReferencia = new Label("Referencia");
        lblReferencia.getStyleClass().add("etiqueta-filtro");
        HBox fila = new HBox(10, lblReferencia, txtReferencia);
        fila.setAlignment(Pos.CENTER_LEFT);
        Label ayuda = new Label("Registre lo que la fábrica manda al almacén. El stock sube cuando el almacén "
                + "confirma lo que llegó.", Ui.icono("mdi2i-information-outline"));
        ayuda.getStyleClass().add("ayuda");
        ayuda.setWrapText(true);

        VBox productos = Ui.seccion("Productos", "mdi2b-bottle-wine-outline", selector);
        VBox.setVgrow(selector, Priority.ALWAYS);
        VBox.setVgrow(productos, Priority.ALWAYS);
        VBox centro = new VBox(productos);
        centro.setPadding(new Insets(10, 0, 10, 0));

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

        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(Ui.seccion("Envío de la fábrica al almacén", "mdi2f-factory", fila, ayuda));
        raiz.setCenter(centro);
        raiz.setBottom(pie);
        Scene escena = new Scene(raiz, 880, 620);
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
        Ui.alCompletar(servicios.productos().listar(), selector::setProductos, mensaje -> Ui.mostrarError(this, mensaje));
    }

    private void registrar() {
        if (selector.lineas().isEmpty()) {
            Ui.mostrarError(this, "Agregue al menos un producto.");
            return;
        }
        String referencia = txtReferencia.getText().trim();
        ProduccionSolicitud solicitud = new ProduccionSolicitud(referencia.isEmpty() ? null : referencia,
                selector.lineas().stream().map(l -> new ItemProduccion(l.producto().id(), l.cantidad())).toList());

        ocupado(true);
        Ui.alCompletar(servicios.producciones().registrar(solicitud),
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
        btnRegistrar.setText(ocupado ? "Registrando…" : "Registrar envío");
        btnRegistrar.setGraphic(ocupado ? progreso : Ui.icono("mdi2c-check"));
    }

    private void actualizarTotal() {
        List<SelectorProductos.Linea> lineas = selector.lineas();
        int unidades = lineas.stream().mapToInt(SelectorProductos.Linea::cantidad).sum();
        lblTotal.setText(lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + "  ·  " + unidades + " unidades");
    }
}
