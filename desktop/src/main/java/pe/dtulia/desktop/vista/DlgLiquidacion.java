package pe.dtulia.desktop.vista;

import javafx.beans.binding.Bindings;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
import pe.dtulia.desktop.modelo.Envio;
import pe.dtulia.desktop.modelo.LiquidacionSolicitud;
import pe.dtulia.desktop.modelo.RetornoViaje;
import pe.dtulia.desktop.modelo.Sede;
import pe.dtulia.desktop.modelo.VentaViaje;
import pe.dtulia.desktop.modelo.ViajeDetalle;
import pe.dtulia.desktop.modelo.ViajeProducto;
import pe.dtulia.desktop.modelo.ViajeResumen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Cierre del viaje con lo que el jefe anotó en su hoja: vendido en cada ciudad, lo que regresó y la
 * merma. La diferencia (llevó − vendió − regresó − merma) se calcula mientras se escribe; si no es 0
 * el viaje se cierra igual, pero con una observación obligatoria.
 */
class DlgLiquidacion extends Stage {

    /** Campos de una fila de producto. Los vendidos se guardan por ciudad para no perderlos al agregar otra. */
    private static final class Fila {
        final ViajeProducto producto;
        final Map<Long, Spinner<Integer>> vendidos = new HashMap<>();
        final Spinner<Integer> regreso = spinner();
        final Spinner<Integer> merma = spinner();
        final TextField motivo = new TextField();
        final Label diferencia = Ui.insignia("", "neutro");

        Fila(ViajeProducto producto) {
            this.producto = producto;
            motivo.setPromptText("Rotura, regalo…");
            motivo.setPrefWidth(150);
        }
    }

    private final ViajeResumen viaje;
    private final Function<LiquidacionSolicitud, CompletableFuture<ViajeDetalle>> liquidar;
    private final Consumer<ViajeDetalle> alLiquidar;

    private final List<Fila> filas = new ArrayList<>();
    private final Map<Long, Sede> ciudades = new LinkedHashMap<>(); // columnas "Vendió en …"
    private final GridPane grilla = new GridPane();
    private final ComboBox<Sede> cboOtraCiudad = new ComboBox<>();
    private final TextArea txtObservacion = new TextArea();
    private final Label lblResumen = new Label();
    private final Label lblCaracteres = new Label();
    private final Label lblLlevado = new Label("0");
    private final Label lblVendido = new Label("0");
    private final Label lblRegreso = new Label("0");
    private final Label lblMerma = new Label("0");
    private final Label lblDiferencia = new Label("0");
    private HBox tarjetaDiferencia;
    private final Button btnLiquidar = Ui.boton("Liquidar viaje", "mdi2c-clipboard-check-outline", "boton-primario");
    private final ProgressIndicator progreso = new ProgressIndicator();

    /**
     * @param destinos ciudades que se pueden agregar como columna (las que no son el almacén)
     */
    DlgLiquidacion(Window duenio, ViajeDetalle detalle, List<Sede> destinos,
                   Function<LiquidacionSolicitud, CompletableFuture<ViajeDetalle>> liquidar,
                   Consumer<ViajeDetalle> alLiquidar) {
        this.viaje = detalle.viaje();
        this.liquidar = liquidar;
        this.alLiquidar = alLiquidar;

        // Columnas iniciales: las ciudades a las que se envió mercadería, en orden de envío
        for (Envio envio : detalle.envios()) {
            if (!envio.anulado()) {
                ciudades.putIfAbsent(envio.sedeId(), new Sede(envio.sedeId(), envio.sede(), null, false));
            }
        }
        detalle.productos().stream().filter(p -> p.llevado() > 0).map(Fila::new).forEach(filas::add);
        cboOtraCiudad.getItems().setAll(destinos);

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · Liquidar viaje #" + viaje.id());
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(crearCabecera());
        raiz.setCenter(crearGrilla());
        raiz.setBottom(crearPie());
        Scene escena = new Scene(raiz, 1080, 680);
        Ui.aplicarEstilos(escena);
        setScene(escena);

        // Esc cancela (pregunta si ya se escribió algo); Ctrl+Enter liquida
        Ui.cerrarConEsc(this, this::hayDatos);
        Ui.atajo(raiz, new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN), () -> {
            if (!btnLiquidar.isDisabled()) {
                liquidar();
            }
        });

        construirGrilla();
    }

    /** true si ya se escribió alguna cantidad, motivo u observación. */
    private boolean hayDatos() {
        return !txtObservacion.getText().isBlank() || filas.stream().anyMatch(f -> leer(f.regreso) > 0
                || leer(f.merma) > 0 || !f.motivo.getText().isBlank()
                || f.vendidos.values().stream().anyMatch(s -> leer(s) > 0));
    }

    private VBox crearCabecera() {
        Label info = new Label(String.format("Salió el %s  ·  %d días  ·  Llevó %d unidades en %d %s",
                FrmViajes.fecha(viaje.fechaSalida()), viaje.dias(), viaje.llevado(), viaje.envios(),
                viaje.envios() == 1 ? "envío" : "envíos"));
        info.getStyleClass().add("total-cajas");
        Label ayuda = new Label("Copie lo que el jefe anotó en su hoja. Si vendió en una ciudad a la que no se le "
                + "envió nada, agréguela como columna.", Ui.icono("mdi2i-information-outline"));
        ayuda.getStyleClass().add("ayuda");
        ayuda.setWrapText(true);

        cboOtraCiudad.setPromptText("Otra ciudad");
        Button btnAgregar = Ui.boton("Agregar ciudad", "mdi2m-map-marker-outline");
        btnAgregar.setOnAction(e -> {
            Sede ciudad = cboOtraCiudad.getValue();
            if (ciudad != null && ciudades.putIfAbsent(ciudad.id(), ciudad) == null) {
                construirGrilla();
            }
        });
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox fila = new HBox(10, info, espacio, cboOtraCiudad, btnAgregar);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox cabecera = Ui.seccion("Liquidación del viaje #" + viaje.id(), "mdi2c-clipboard-check-outline", fila, ayuda);
        BorderPane.setMargin(cabecera, new Insets(0, 0, 10, 0));
        return cabecera;
    }

    private ScrollPane crearGrilla() {
        grilla.setHgap(10);
        grilla.setVgap(6);
        grilla.setPadding(new Insets(10));
        grilla.getStyleClass().add("grilla-liquidacion");
        ScrollPane desplazable = new ScrollPane(grilla);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("seccion");
        return desplazable;
    }

    private VBox crearPie() {
        // Resumen en vivo: lo que salió frente a lo declarado
        tarjetaDiferencia = Ui.tarjetaResumen("mdi2s-scale-balance", "Diferencia", lblDiferencia, "exito");
        HBox tarjetas = new HBox(10,
                Ui.tarjetaResumen("mdi2t-truck-outline", "Llevó", lblLlevado, "vino"),
                Ui.tarjetaResumen("mdi2c-cash-multiple", "Vendió", lblVendido, "exito"),
                Ui.tarjetaResumen("mdi2s-store-outline", "Regresó al almacén", lblRegreso, "info"),
                Ui.tarjetaResumen("mdi2m-minus-circle-outline", "Merma", lblMerma, "aviso"),
                tarjetaDiferencia);

        txtObservacion.setPromptText("Qué pasó con lo que falta o sobra");
        txtObservacion.setPrefRowCount(2);
        txtObservacion.setWrapText(true);
        txtObservacion.setTextFormatter(new TextFormatter<String>(
                cambio -> cambio.getControlNewText().length() <= 300 ? cambio : null));
        Label lblObservacion = new Label("Observación (obligatoria si la diferencia no es 0)");
        lblObservacion.getStyleClass().add("etiqueta-filtro");
        lblCaracteres.getStyleClass().add("estado");
        lblCaracteres.textProperty().bind(Bindings.createStringBinding(
                () -> txtObservacion.getLength() + "/300 caracteres", txtObservacion.textProperty()));
        Region espacioObs = new Region();
        HBox.setHgrow(espacioObs, Priority.ALWAYS);
        HBox encabezadoObs = new HBox(lblObservacion, espacioObs, lblCaracteres);
        lblResumen.getStyleClass().add("total-cajas");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setTooltip(new Tooltip("Esc"));
        btnCancelar.setOnAction(e -> close());
        btnLiquidar.setTooltip(new Tooltip("Ctrl+Enter"));
        btnLiquidar.setOnAction(e -> liquidar());
        progreso.setPrefSize(16, 16);
        progreso.setMaxSize(16, 16);
        Label aviso = new Label("Lo que regresó vuelve al stock del almacén al liquidar.", Ui.icono("mdi2i-information-outline"));
        aviso.getStyleClass().add("ayuda");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox botones = new HBox(12, aviso, espacio, lblResumen, btnCancelar, btnLiquidar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox pie = new VBox(8, tarjetas, encabezadoObs, txtObservacion, botones);
        pie.setPadding(new Insets(10, 0, 0, 0));
        return pie;
    }

    /** Se reconstruye al agregar una ciudad; los Spinner se reutilizan y conservan lo escrito. */
    private void construirGrilla() {
        grilla.getChildren().clear();
        int col = 0;
        encabezado("Producto", col++);
        encabezado("Llevó", col++);
        for (Sede ciudad : ciudades.values()) {
            encabezado("Vendió\n" + ciudad.nombre(), col++);
        }
        encabezado("Regresó", col++);
        encabezado("Merma", col++);
        encabezado("Motivo de la merma", col++);
        encabezado("Diferencia", col);

        int fila = 1;
        for (Fila f : filas) {
            col = 0;
            Label nombre = new Label(f.producto.nombreCompleto());
            nombre.setMinWidth(200);
            grilla.add(nombre, col++, fila);
            Label llevado = new Label(f.producto.llevado() + "  (" + Ui.cajas(f.producto.llevado(),
                    f.producto.unidadesCaja()) + ")");
            grilla.add(llevado, col++, fila);
            for (Sede ciudad : ciudades.values()) {
                grilla.add(f.vendidos.computeIfAbsent(ciudad.id(), id -> escuchar(spinner())), col++, fila);
            }
            grilla.add(escuchar(f.regreso), col++, fila);
            grilla.add(escuchar(f.merma), col++, fila);
            grilla.add(f.motivo, col++, fila);
            grilla.add(f.diferencia, col, fila);
            GridPane.setHalignment(f.diferencia, HPos.LEFT);
            fila++;
        }
        if (filas.isEmpty()) {
            grilla.add(new Label("Este viaje no tiene productos llevados (todos los envíos están anulados)."), 0, 1);
        }
        recalcular();
    }

    private void encabezado(String texto, int columna) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("encabezado-grilla");
        grilla.add(etiqueta, columna, 0);
    }

    private static Spinner<Integer> spinner() {
        Spinner<Integer> spinner = new Spinner<>(0, 100_000, 0);
        spinner.setEditable(true);
        spinner.setPrefWidth(85);
        return spinner;
    }

    /** Recalcula la diferencia mientras se escribe (una sola vez por Spinner). */
    private Spinner<Integer> escuchar(Spinner<Integer> spinner) {
        if (spinner.getProperties().putIfAbsent("escuchado", true) == null) {
            spinner.getEditor().textProperty().addListener((obs, antes, ahora) -> recalcular());
        }
        return spinner;
    }

    private void recalcular() {
        int llevado = 0;
        int vendido = 0;
        int regreso = 0;
        int merma = 0;
        int conDiferencia = 0;
        for (Fila f : filas) {
            int vendidoFila = f.vendidos.entrySet().stream()
                    .filter(e -> ciudades.containsKey(e.getKey()))
                    .mapToInt(e -> leer(e.getValue())).sum();
            int dif = f.producto.llevado() - vendidoFila - leer(f.regreso) - leer(f.merma);
            f.diferencia.getStyleClass().removeAll("insignia-neutro", "insignia-exito", "insignia-peligro");
            f.diferencia.getStyleClass().add(dif == 0 ? "insignia-exito" : "insignia-peligro");
            f.diferencia.setText(dif == 0 ? "0 ✓" : dif > 0 ? "Faltan " + dif : "Sobran " + (-dif));
            llevado += f.producto.llevado();
            vendido += vendidoFila;
            regreso += leer(f.regreso);
            merma += leer(f.merma);
            if (dif != 0) {
                conDiferencia++;
            }
        }
        lblResumen.setText(conDiferencia == 0 ? "Cuadra ✓"
                : conDiferencia + (conDiferencia == 1 ? " producto con diferencia" : " productos con diferencia"));
        int diferencia = llevado - vendido - regreso - merma;
        lblLlevado.setText(String.valueOf(llevado));
        lblVendido.setText(String.valueOf(vendido));
        lblRegreso.setText(String.valueOf(regreso));
        lblMerma.setText(String.valueOf(merma));
        lblDiferencia.setText(diferencia == 0 ? "0 ✓" : diferencia > 0 ? "Faltan " + diferencia : "Sobran " + (-diferencia));
        if (tarjetaDiferencia != null) {
            tarjetaDiferencia.getStyleClass().removeAll("tono-exito", "tono-peligro");
            tarjetaDiferencia.getStyleClass().add(conDiferencia == 0 ? "tono-exito" : "tono-peligro");
        }
    }

    private void liquidar() {
        List<VentaViaje> ventas = new ArrayList<>();
        List<RetornoViaje> retornos = new ArrayList<>();
        boolean hayDiferencia = false;
        for (Fila f : filas) {
            int vendidoFila = 0;
            for (Sede ciudad : ciudades.values()) {
                int cantidad = Ui.leerEntero(f.vendidos.get(ciudad.id()));
                if (cantidad < 0) {
                    Ui.mostrarError(this, "Revise lo vendido en " + ciudad.nombre() + " de " + f.producto.nombreCompleto() + ".");
                    return;
                }
                if (cantidad > 0) {
                    ventas.add(new VentaViaje(f.producto.productoId(), ciudad.id(), cantidad));
                }
                vendidoFila += cantidad;
            }
            int regreso = Ui.leerEntero(f.regreso);
            int merma = Ui.leerEntero(f.merma);
            if (regreso < 0 || merma < 0) {
                Ui.mostrarError(this, "Revise lo que regresó y la merma de " + f.producto.nombreCompleto() + ".");
                return;
            }
            String motivo = f.motivo.getText().trim();
            if (merma > 0 && motivo.isEmpty()) {
                Ui.mostrarError(this, "Indique el motivo de la merma de " + f.producto.nombreCompleto() + ".");
                return;
            }
            if (regreso + merma > 0) {
                retornos.add(new RetornoViaje(f.producto.productoId(), regreso, merma, motivo.isEmpty() ? null : motivo));
            }
            hayDiferencia |= f.producto.llevado() != vendidoFila + regreso + merma;
        }
        String observacion = txtObservacion.getText().trim();
        if (hayDiferencia && observacion.isEmpty()) {
            Ui.mostrarError(this, "Lo declarado no cuadra con lo que se llevó. Escriba una observación para cerrar "
                    + "el viaje con diferencia.");
            txtObservacion.requestFocus();
            return;
        }

        if (!Ui.confirmar(this, "¿Liquidar el viaje #" + viaje.id() + "?" + (hayDiferencia ? " (con diferencia)" : ""),
                "Lo que regresó volverá al stock del almacén y el viaje quedará cerrado: después no se podrá modificar.",
                "Liquidar viaje")) {
            return;
        }

        ocupado(true);
        Ui.alCompletar(liquidar.apply(new LiquidacionSolicitud(ventas, retornos, observacion.isEmpty() ? null : observacion)),
                detalle -> {
                    close();
                    alLiquidar.accept(detalle);
                },
                mensaje -> {
                    ocupado(false);
                    Ui.mostrarError(this, mensaje);
                });
    }

    private void ocupado(boolean ocupado) {
        btnLiquidar.setDisable(ocupado);
        btnLiquidar.setText(ocupado ? "Liquidando…" : "Liquidar viaje");
        btnLiquidar.setGraphic(ocupado ? progreso : Ui.icono("mdi2c-clipboard-check-outline"));
    }

    /** Lectura tolerante para el cálculo en vivo (texto vacío o inválido = 0). */
    private static int leer(Spinner<Integer> spinner) {
        try {
            return Math.max(0, Integer.parseInt(spinner.getEditor().getText().trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
