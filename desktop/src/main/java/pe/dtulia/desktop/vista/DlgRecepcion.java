package pe.dtulia.desktop.vista;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
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
    private final Label lblDeclarado = new Label();
    private final Label lblContado = new Label();
    private final Label lblDescuadre = new Label();
    private final HBox tarjetaDescuadre;
    private final Button btnRecibir = Ui.boton("Confirmar recepción y subir stock", "mdi2i-inbox-arrow-down-outline",
            "boton-primario");
    private final ProgressIndicator progreso = new ProgressIndicator();

    DlgRecepcion(Window duenio, Servicios servicios, ProduccionDetalle detalle, Consumer<ProduccionDetalle> alRecibir) {
        this.servicios = servicios;
        this.produccion = detalle.produccion();
        this.alRecibir = alRecibir;

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · Recibir envío de producción #" + produccion.id());

        Label info = new Label(String.format("Enviado el %s por %s%s", FrmViajes.fecha(produccion.fechaEnvio()),
                produccion.enviadoPor(), produccion.referencia() == null ? "" : "  ·  " + produccion.referencia()));
        info.getStyleClass().add("total-cajas");
        Label ayuda = new Label("Cuente lo que llegó. Si coincide con lo enviado, solo confirme; si no, corrija "
                + "las cantidades y escriba qué pasó. Al confirmar, lo recibido entra al stock (ENTRADA_PRODUCCION).",
                Ui.icono("mdi2i-information-outline"));
        ayuda.getStyleClass().add("ayuda");
        ayuda.setWrapText(true);

        GridPane grilla = new GridPane();
        grilla.setHgap(14);
        grilla.setVgap(8);
        grilla.setPadding(new Insets(10));
        grilla.getStyleClass().add("grilla-liquidacion");
        encabezado(grilla, "Producto", 0);
        encabezado(grilla, "Envió la fábrica", 1);
        encabezado(grilla, "Llegó al almacén", 2);
        encabezado(grilla, "Diferencia", 3);
        int fila = 1;
        for (ProduccionProducto p : detalle.productos()) {
            CampoCajas recibido = new CampoCajas();
            recibido.setPorCaja(p.unidadesCaja());
            recibido.setUnidades(p.enviado());
            Fila f = new Fila(p, recibido, Ui.insignia("", "neutro"));
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

        // Resumen en vivo: lo declarado por la fábrica frente a lo contado
        tarjetaDescuadre = Ui.tarjetaResumen("mdi2s-scale-balance", "Descuadre", lblDescuadre, "exito");
        HBox tarjetas = new HBox(10,
                Ui.tarjetaResumen("mdi2f-factory", "Declarado por la fábrica", lblDeclarado, "vino"),
                Ui.tarjetaResumen("mdi2c-counter", "Contado en el almacén", lblContado, "info"),
                tarjetaDescuadre);

        txtObservacion.setPromptText("Qué pasó con lo que falta o sobra");
        txtObservacion.setPrefRowCount(2);
        txtObservacion.setWrapText(true);
        txtObservacion.setTextFormatter(new TextFormatter<String>(
                cambio -> cambio.getControlNewText().length() <= 300 ? cambio : null));
        Label lblObservacion = new Label("Observación (obligatoria si lo que llegó no coincide con lo enviado)");
        lblObservacion.getStyleClass().add("etiqueta-filtro");
        Label lblCaracteres = new Label();
        lblCaracteres.getStyleClass().add("estado");
        lblCaracteres.textProperty().bind(Bindings.createStringBinding(
                () -> txtObservacion.getLength() + "/300 caracteres", txtObservacion.textProperty()));
        Region espacioObs = new Region();
        HBox.setHgrow(espacioObs, Priority.ALWAYS);
        HBox encabezadoObs = new HBox(lblObservacion, espacioObs, lblCaracteres);

        lblResumen.getStyleClass().add("total-cajas");
        Button btnRestablecer = Ui.boton("Restablecer valores", "mdi2r-restore");
        btnRestablecer.setTooltip(new Tooltip("Vuelve a poner en cada producto lo que envió la fábrica"));
        btnRestablecer.setOnAction(e -> filas.forEach(f -> f.recibido().setUnidades(f.producto().enviado())));
        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setTooltip(new Tooltip("Esc"));
        btnCancelar.setOnAction(e -> close());
        btnRecibir.setTooltip(new Tooltip("Ctrl+Enter"));
        btnRecibir.setOnAction(e -> recibir());
        progreso.setPrefSize(16, 16);
        progreso.setMaxSize(16, 16);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox botones = new HBox(12, btnRestablecer, lblResumen, espacio, btnCancelar, btnRecibir);
        botones.setAlignment(Pos.CENTER_RIGHT);

        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(Ui.seccion("Recibir envío de producción #" + produccion.id(), "mdi2i-inbox-arrow-down-outline",
                info, ayuda));
        raiz.setCenter(desplazable);
        raiz.setBottom(new VBox(8, tarjetas, encabezadoObs, txtObservacion, botones));
        Scene escena = new Scene(raiz, 1060, 660);
        Ui.aplicarEstilos(escena);
        setScene(escena);

        // Esc cancela (pregunta si ya se corrigió algo); Ctrl+Enter confirma
        Ui.cerrarConEsc(this, this::hayCambios);
        Ui.atajo(raiz, new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!btnRecibir.isDisabled()) {
                recibir();
            }
        });
        recalcular();
    }

    /** true si se cambió alguna cantidad precargada o se escribió una observación. */
    private boolean hayCambios() {
        return !txtObservacion.getText().isBlank()
                || filas.stream().anyMatch(f -> f.recibido().totalParcial() != f.producto().enviado());
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
            f.diferencia().getStyleClass().removeAll("insignia-neutro", "insignia-exito", "insignia-peligro");
            f.diferencia().getStyleClass().add(dif == 0 ? "insignia-exito" : "insignia-peligro");
            f.diferencia().setText(dif == 0 ? "0 ✓" : dif > 0 ? "Faltan " + dif : "Sobran " + (-dif));
            enviado += f.producto().enviado();
            recibido += llego;
            if (dif != 0) {
                conDiferencia++;
            }
        }
        lblResumen.setText(conDiferencia == 0 ? "Coincide ✓"
                : conDiferencia + (conDiferencia == 1 ? " producto con diferencia" : " productos con diferencia"));
        int descuadre = enviado - recibido;
        lblDeclarado.setText(enviado + " u.");
        lblContado.setText(recibido + " u.");
        lblDescuadre.setText(descuadre == 0 ? "0 ✓" : descuadre > 0 ? "Faltan " + descuadre : "Sobran " + (-descuadre));
        if (tarjetaDescuadre != null) {
            tarjetaDescuadre.getStyleClass().removeAll("tono-exito", "tono-peligro");
            tarjetaDescuadre.getStyleClass().add(conDiferencia == 0 ? "tono-exito" : "tono-peligro");
        }
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

        if (!Ui.confirmar(this, "¿Confirmar la recepción del envío #" + produccion.id() + "?"
                        + (hayDiferencia ? " (con diferencia)" : ""),
                "Lo recibido se sumará al stock del almacén. Después no se podrá modificar.",
                "Confirmar recepción")) {
            return;
        }

        ocupado(true);
        Ui.alCompletar(servicios.producciones().recibir(produccion.id(),
                        new RecepcionSolicitud(items, observacion.isEmpty() ? null : observacion)),
                detalle -> {
                    close();
                    alRecibir.accept(detalle);
                },
                mensaje -> {
                    ocupado(false);
                    Ui.mostrarError(this, mensaje);
                });
    }

    private void ocupado(boolean ocupado) {
        btnRecibir.setDisable(ocupado);
        btnRecibir.setText(ocupado ? "Confirmando…" : "Confirmar recepción y subir stock");
        btnRecibir.setGraphic(ocupado ? progreso : Ui.icono("mdi2i-inbox-arrow-down-outline"));
    }
}
