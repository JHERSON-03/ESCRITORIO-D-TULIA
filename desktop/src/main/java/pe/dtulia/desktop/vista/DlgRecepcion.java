package pe.dtulia.desktop.vista;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import pe.dtulia.desktop.modelo.ItemProduccion;
import pe.dtulia.desktop.modelo.ProduccionDetalle;
import pe.dtulia.desktop.modelo.ProduccionProducto;
import pe.dtulia.desktop.modelo.ProduccionResumen;
import pe.dtulia.desktop.modelo.RecepcionSolicitud;
import pe.dtulia.desktop.servicio.Servicios;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * La administradora del almacén cuenta lo que llegó de la fábrica. Viene precargado con lo enviado;
 * si algo no coincide, se corrige y se explica en la observación. El stock sube al confirmar.
 */
class DlgRecepcion extends Stage {

    private record Fila(ProduccionProducto producto, CampoCajas recibido, Label diferencia) {
    }

    private final Servicios servicios;
    private final ProduccionResumen produccion;
    private final Consumer<ProduccionDetalle> alRecibir;
    private final List<Fila> filas = new ArrayList<>();
    private final TextArea txtObservacion = new TextArea();
    private final Label lblResumen = new Label();
    private final Button btnRecibir = new Button("Confirmar recepción");

    DlgRecepcion(Window duenio, Servicios servicios, ProduccionDetalle detalle, Consumer<ProduccionDetalle> alRecibir) {
        this.servicios = servicios;
        this.produccion = detalle.produccion();
        this.alRecibir = alRecibir;

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · Recibir envío de producción #" + produccion.id());

        Label info = new Label(String.format("Enviado el %s por %s%s", FrmViajes.fecha(produccion.fechaEnvio()),
                produccion.enviadoPor(), produccion.referencia() == null ? "" : "  ·  " + produccion.referencia()));
        Label ayuda = new Label("Cuente lo que llegó. Si coincide con lo enviado, solo confirme; si no, corrija "
                + "las cantidades y escriba qué pasó.");
        ayuda.getStyleClass().add("estado");
        ayuda.setWrapText(true);

        GridPane grilla = new GridPane();
        grilla.setHgap(14);
        grilla.setVgap(8);
        grilla.setPadding(new Insets(10));
        encabezado(grilla, "Producto", 0);
        encabezado(grilla, "Envió la fábrica", 1);
        encabezado(grilla, "Llegó al almacén", 2);
        encabezado(grilla, "Diferencia", 3);
        int fila = 1;
        for (ProduccionProducto p : detalle.productos()) {
            CampoCajas recibido = new CampoCajas();
            recibido.setPorCaja(p.unidadesCaja());
            recibido.setUnidades(p.enviado());
            Fila f = new Fila(p, recibido, new Label());
            recibido.setAlCambiar(this::recalcular);
            filas.add(f);
            grilla.add(new Label(p.nombreCompleto()), 0, fila);
            grilla.add(new Label(p.enviado() + "  (" + Ui.cajas(p.enviado(), p.unidadesCaja()) + ")"), 1, fila);
            grilla.add(recibido, 2, fila);
            grilla.add(f.diferencia(), 3, fila);
            fila++;
        }
        ScrollPane desplazable = new ScrollPane(grilla);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("seccion");
        BorderPane.setMargin(desplazable, new Insets(10, 0, 10, 0));

        txtObservacion.setPromptText("Observación (obligatoria si lo que llegó no coincide con lo enviado)");
        txtObservacion.setPrefRowCount(2);
        txtObservacion.setWrapText(true);
        txtObservacion.setTextFormatter(new TextFormatter<String>(
                cambio -> cambio.getControlNewText().length() <= 300 ? cambio : null));
        lblResumen.getStyleClass().add("cliente");
        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(e -> close());
        btnRecibir.getStyleClass().add("boton-primario");
        btnRecibir.setOnAction(e -> recibir());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox botones = new HBox(12, lblResumen, espacio, btnCancelar, btnRecibir);
        botones.setAlignment(Pos.CENTER_RIGHT);

        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(Ui.seccion("Recibir envío de producción #" + produccion.id(), info, ayuda));
        raiz.setCenter(desplazable);
        raiz.setBottom(new VBox(8, txtObservacion, botones));
        Scene escena = new Scene(raiz, 1000, 600);
        Ui.aplicarEstilos(escena);
        setScene(escena);
        recalcular();
    }

    private static void encabezado(GridPane grilla, String texto, int columna) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("encabezado-grilla");
        grilla.add(etiqueta, columna, 0);
    }

    /** Diferencia = enviado − recibido: positivo falta, negativo sobra. */
    private void recalcular() {
        int enviado = 0;
        int recibido = 0;
        int conDiferencia = 0;
        for (Fila f : filas) {
            int llego = f.recibido().totalParcial();
            int dif = f.producto().enviado() - llego;
            f.diferencia().getStyleClass().removeAll("texto-alerta", "texto-ok");
            f.diferencia().getStyleClass().add(dif == 0 ? "texto-ok" : "texto-alerta");
            f.diferencia().setText(dif == 0 ? "0 ✓" : dif > 0 ? "Faltan " + dif : "Sobran " + (-dif));
            enviado += f.producto().enviado();
            recibido += llego;
            if (dif != 0) {
                conDiferencia++;
            }
        }
        lblResumen.setText(String.format("Envió %d  ·  Llegó %d  ·  %s", enviado, recibido, conDiferencia == 0
                ? "Coincide ✓"
                : conDiferencia + (conDiferencia == 1 ? " producto con diferencia" : " productos con diferencia")));
    }

    private void recibir() {
        List<ItemProduccion> items = new ArrayList<>();
        boolean hayDiferencia = false;
        for (Fila f : filas) {
            int llego = f.recibido().total();
            if (llego < 0) {
                Ui.mostrarError(this, "Revise la cantidad recibida de " + f.producto().nombreCompleto() + ".");
                return;
            }
            items.add(new ItemProduccion(f.producto().productoId(), llego));
            hayDiferencia |= llego != f.producto().enviado();
        }
        String observacion = txtObservacion.getText().trim();
        if (hayDiferencia && observacion.isEmpty()) {
            Ui.mostrarError(this, "Lo que llegó no coincide con lo que envió la fábrica. Escriba una observación.");
            txtObservacion.requestFocus();
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "Lo recibido se sumará al stock del almacén. Después no se podrá modificar.",
                ButtonType.YES, ButtonType.NO);
        confirmacion.initOwner(this);
        confirmacion.setHeaderText("¿Confirmar la recepción del envío #" + produccion.id() + "?"
                + (hayDiferencia ? " (con diferencia)" : ""));
        if (confirmacion.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        btnRecibir.setDisable(true);
        Ui.alCompletar(servicios.producciones().recibir(produccion.id(),
                        new RecepcionSolicitud(items, observacion.isEmpty() ? null : observacion)),
                detalle -> {
                    close();
                    alRecibir.accept(detalle);
                },
                mensaje -> {
                    btnRecibir.setDisable(false);
                    Ui.mostrarError(this, mensaje);
                });
    }
}
