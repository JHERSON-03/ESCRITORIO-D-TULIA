package pe.dtulia.desktop.vista;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
    private final Label lblTotal = new Label();
    private final Button btnRegistrar = new Button("Registrar envío");

    DlgProduccion(Window duenio, Servicios servicios, Consumer<ProduccionDetalle> alRegistrar) {
        this.servicios = servicios;
        this.alRegistrar = alRegistrar;

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · Envío de producción");

        txtReferencia.setPromptText("Lote, vehículo o nota (opcional)");
        HBox.setHgrow(txtReferencia, Priority.ALWAYS);
        HBox fila = new HBox(10, new Label("Referencia"), txtReferencia);
        fila.setAlignment(Pos.CENTER_LEFT);
        Label ayuda = new Label("Registre lo que la fábrica manda al almacén. El stock sube cuando el almacén "
                + "confirma lo que llegó.");
        ayuda.getStyleClass().add("estado");
        ayuda.setWrapText(true);

        VBox productos = Ui.seccion("Productos", selector);
        VBox.setVgrow(selector, Priority.ALWAYS);
        VBox.setVgrow(productos, Priority.ALWAYS);
        VBox centro = new VBox(productos);
        centro.setPadding(new Insets(10, 0, 10, 0));

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(e -> close());
        btnRegistrar.getStyleClass().add("boton-primario");
        btnRegistrar.setOnAction(e -> registrar());
        lblTotal.getStyleClass().add("cliente");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(12, espacio, lblTotal, btnCancelar, btnRegistrar);
        pie.setAlignment(Pos.CENTER_RIGHT);

        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(Ui.seccion("Envío de la fábrica al almacén", fila, ayuda));
        raiz.setCenter(centro);
        raiz.setBottom(pie);
        Scene escena = new Scene(raiz, 820, 580);
        Ui.aplicarEstilos(escena);
        setScene(escena);

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

        btnRegistrar.setDisable(true);
        Ui.alCompletar(servicios.producciones().registrar(solicitud),
                detalle -> {
                    close();
                    alRegistrar.accept(detalle);
                },
                mensaje -> {
                    btnRegistrar.setDisable(false);
                    Ui.mostrarError(this, mensaje);
                });
    }

    private void actualizarTotal() {
        List<SelectorProductos.Linea> lineas = selector.lineas();
        int unidades = lineas.stream().mapToInt(SelectorProductos.Linea::cantidad).sum();
        lblTotal.setText(lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + "  ·  " + unidades + " unidades");
    }
}
