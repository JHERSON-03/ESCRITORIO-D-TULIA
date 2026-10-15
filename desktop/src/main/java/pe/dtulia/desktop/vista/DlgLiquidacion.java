package pe.dtulia.desktop.vista;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
        final Label diferencia = new Label();

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
    private final Button btnLiquidar = new Button("Liquidar viaje");

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
        Scene escena = new Scene(raiz, 1080, 640);
        Ui.aplicarEstilos(escena);
        setScene(escena);

        construirGrilla();
    }

    private VBox crearCabecera() {
        Label info = new Label(String.format("Salió el %s  ·  %d días  ·  Llevó %d unidades en %d %s",
                FrmViajes.fecha(viaje.fechaSalida()), viaje.dias(), viaje.llevado(), viaje.envios(),
                viaje.envios() == 1 ? "envío" : "envíos"));
        Label ayuda = new Label("Copie lo que el jefe anotó en su hoja. Si vendió en una ciudad a la que no se le "
                + "envió nada, agréguela como columna.");
        ayuda.getStyleClass().add("estado");
        ayuda.setWrapText(true);

        cboOtraCiudad.setPromptText("Otra ciudad");
        Button btnAgregar = new Button("Agregar ciudad");
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
        VBox cabecera = Ui.seccion("Liquidación del viaje #" + viaje.id(), fila, ayuda);
        VBox.setMargin(cabecera, new Insets(0, 0, 10, 0));
        return cabecera;
    }

    private ScrollPane crearGrilla() {
        grilla.setHgap(10);
        grilla.setVgap(6);
        grilla.setPadding(new Insets(10));
        ScrollPane desplazable = new ScrollPane(grilla);
        desplazable.setFitToWidth(true);
        desplazable.getStyleClass().add("seccion");
        return desplazable;
    }

    private VBox crearPie() {
        txtObservacion.setPromptText("Observación (obligatoria si la diferencia no es 0): qué pasó con lo que falta o sobra");
        txtObservacion.setPrefRowCount(2);
        txtObservacion.setWrapText(true);
        txtObservacion.setTextFormatter(new TextFormatter<String>(
                cambio -> cambio.getControlNewText().length() <= 300 ? cambio : null));
        lblResumen.getStyleClass().add("cliente");

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(e -> close());
        btnLiquidar.getStyleClass().add("boton-primario");
        btnLiquidar.setOnAction(e -> liquidar());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox botones = new HBox(12, lblResumen, espacio, btnCancelar, btnLiquidar);
        botones.setAlignment(Pos.CENTER_RIGHT);

        VBox pie = new VBox(8, txtObservacion, botones);
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
            f.diferencia.getStyleClass().removeAll("texto-alerta", "texto-ok");
            f.diferencia.getStyleClass().add(dif == 0 ? "texto-ok" : "texto-alerta");
            f.diferencia.setText(dif == 0 ? "0 ✓" : dif > 0 ? "Faltan " + dif : "Sobran " + (-dif));
            llevado += f.producto.llevado();
            vendido += vendidoFila;
            regreso += leer(f.regreso);
            merma += leer(f.merma);
            if (dif != 0) {
                conDiferencia++;
            }
        }
        lblResumen.setText(String.format("Llevó %d  ·  Vendió %d  ·  Regresó %d  ·  Merma %d  ·  %s", llevado,
                vendido, regreso, merma, conDiferencia == 0 ? "Cuadra ✓"
                        : conDiferencia + (conDiferencia == 1 ? " producto con diferencia" : " productos con diferencia")));
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

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "Lo que regresó volverá al stock del almacén y el viaje quedará cerrado: después no se podrá modificar.",
                ButtonType.YES, ButtonType.NO);
        confirmacion.initOwner(this);
        confirmacion.setHeaderText("¿Liquidar el viaje #" + viaje.id() + "?" + (hayDiferencia ? " (con diferencia)" : ""));
        if (confirmacion.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        btnLiquidar.setDisable(true);
        Ui.alCompletar(liquidar.apply(new LiquidacionSolicitud(ventas, retornos, observacion.isEmpty() ? null : observacion)),
                detalle -> {
                    close();
                    alLiquidar.accept(detalle);
                },
                mensaje -> {
                    btnLiquidar.setDisable(false);
                    Ui.mostrarError(this, mensaje);
                });
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
