package pe.dtulia.desktop.vista;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TableColumn;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import pe.dtulia.desktop.servicio.ApiException;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

/** Utilidades comunes de la capa de presentación. */
final class Ui {

    private static final Locale PERU = Locale.forLanguageTag("es-PE");
    private static final String HOJA_ESTILOS = Ui.class.getResource("/pe/dtulia/desktop/estilos.css").toExternalForm();

    /** Hora de la última respuesta correcta del servidor (la muestra la barra de estado). */
    private static final ReadOnlyObjectWrapper<LocalTime> ULTIMA_RESPUESTA = new ReadOnlyObjectWrapper<>();
    /** true cuando la última petición no llegó al servidor (API apagada, red caída o sin respuesta). */
    private static final ReadOnlyBooleanWrapper SIN_CONEXION = new ReadOnlyBooleanWrapper();
    /** Toasts visibles por ventana, para apilarlos sin que se tapen. */
    private static final Map<Window, List<Popup>> TOASTS = new WeakHashMap<>();

    private Ui() {
    }

    /** Ejecuta los callbacks en el hilo de JavaFX cuando termina la petición HTTP. */
    static <T> void alCompletar(CompletableFuture<T> futuro, Consumer<T> exito, Consumer<String> error) {
        futuro.whenComplete((valor, falla) -> Platform.runLater(() -> {
            registrarConexion(falla);
            if (falla == null) {
                exito.accept(valor);
            } else {
                error.accept(ApiException.mensajeDe(falla));
            }
        }));
    }

    static ReadOnlyObjectProperty<LocalTime> ultimaRespuestaProperty() {
        return ULTIMA_RESPUESTA.getReadOnlyProperty();
    }

    static ReadOnlyBooleanProperty sinConexionProperty() {
        return SIN_CONEXION.getReadOnlyProperty();
    }

    /** Un error de la API (400, 409…) significa que el servidor respondió; solo IOException es falta de conexión. */
    private static void registrarConexion(Throwable falla) {
        Throwable causa = falla;
        while ((causa instanceof CompletionException || causa instanceof ExecutionException) && causa.getCause() != null) {
            causa = causa.getCause();
        }
        if (causa == null) {
            ULTIMA_RESPUESTA.set(LocalTime.now());
        }
        SIN_CONEXION.set(causa instanceof IOException);
    }

    static void aplicarEstilos(Scene escena) {
        escena.getStylesheets().add(HOJA_ESTILOS);
    }

    static void aplicarEstilos(Dialog<?> dialogo) {
        dialogo.getDialogPane().getStylesheets().add(HOJA_ESTILOS);
    }

    /** Ícono de Material Design Icons; el literal es el de Ikonli, p. ej. "mdi2t-truck-outline". */
    static FontIcon icono(String literal) {
        return new FontIcon(literal);
    }

    static FontIcon icono(String literal, int tamanio) {
        FontIcon icono = new FontIcon(literal);
        icono.setIconSize(tamanio);
        return icono;
    }

    /** Botón con ícono a la izquierda y clases de estilo opcionales ("boton-primario", "boton-peligro"…). */
    static Button boton(String texto, String icono, String... clases) {
        Button boton = new Button(texto, icono == null ? null : icono(icono));
        boton.getStyleClass().addAll(clases);
        return boton;
    }

    // ---------------------------------------------------------------- pestañas

    /** Pestaña con ícono; el título va en el gráfico para poder añadir una insignia a su derecha. */
    static Tab pestana(String titulo, String icono, Node contenido) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("titulo-pestana");
        HBox grafico = new HBox(7, icono(icono), etiqueta);
        grafico.setAlignment(Pos.CENTER_LEFT);
        Tab tab = new Tab(null, contenido);
        tab.setGraphic(grafico);
        return tab;
    }

    /** Cambia el título de una pestaña creada con {@link #pestana}. */
    static void tituloPestana(Tab tab, String titulo) {
        ((Label) ((HBox) tab.getGraphic()).getChildren().get(1)).setText(titulo);
    }

    // ---------------------------------------------------------------- insignias (badges)

    /** Píldora de estado; tono: "exito", "aviso", "peligro", "info", "neutro", "vino" o "morado". */
    static Label insignia(String texto, String tono) {
        Label insignia = new Label(texto);
        insignia.getStyleClass().addAll("insignia", "insignia-" + tono);
        insignia.setMinWidth(Region.USE_PREF_SIZE);
        return insignia;
    }

    /** Color de los estados de viajes, envíos y movimientos. */
    static String tonoEstado(String estado) {
        if (estado == null) {
            return "neutro";
        }
        return switch (estado.trim().toUpperCase(Locale.ROOT).replace('_', ' ')) {
            case "ABIERTO" -> "info";
            case "LIQUIDADO", "RECIBIDO" -> "exito";
            case "EN CAMINO" -> "aviso";
            case "ANULADO" -> "peligro";
            default -> "neutro";
        };
    }

    /** Color del tipo de producto (PISCO, VINO, MACERADO…). */
    static String tonoTipo(String tipo) {
        String t = tipo == null ? "" : tipo.toUpperCase(Locale.ROOT);
        if (t.contains("PISCO")) {
            return "vino";
        }
        if (t.contains("VINO")) {
            return "morado";
        }
        if (t.contains("MACERADO") || t.contains("LICOR")) {
            return "info";
        }
        return "neutro";
    }

    /** Columna cuyo valor se dibuja como insignia; tono recibe el texto y devuelve el color. */
    static <S> TableColumn<S, String> columnaInsignia(String titulo, Function<S, String> valor, double ancho,
                                                      Function<String, String> tono) {
        TableColumn<S, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                setText(null);
                setGraphic(vacio || texto == null || texto.isBlank() ? null : insignia(texto, tono.apply(texto)));
            }
        });
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        return columna;
    }

    // ---------------------------------------------------------------- tarjetas de resumen

    /** Tarjeta "ícono + título + valor" para la franja de resumen; el valor se actualiza desde la vista. */
    static HBox tarjetaResumen(String icono, String titulo, Label valor, String tono) {
        StackPane fondoIcono = new StackPane(icono(icono));
        fondoIcono.getStyleClass().add("tarjeta-resumen-icono");
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("tarjeta-resumen-titulo");
        valor.getStyleClass().add("tarjeta-resumen-valor");
        VBox textos = new VBox(1, etiqueta, valor);
        HBox tarjeta = new HBox(12, fondoIcono, textos);
        tarjeta.setAlignment(Pos.CENTER_LEFT);
        tarjeta.getStyleClass().addAll("tarjeta-resumen", "tono-" + tono);
        HBox.setHgrow(tarjeta, Priority.ALWAYS);
        tarjeta.setPrefWidth(150); // mismo ancho base: la franja reparte el espacio en partes iguales
        tarjeta.setMaxWidth(Double.MAX_VALUE);
        return tarjeta;
    }

    // ---------------------------------------------------------------- estados vacío y de carga

    /** Contenido para tablas o paneles sin datos: ícono grande, título y una indicación de qué hacer. */
    static VBox estadoVacio(String icono, String titulo, String detalle) {
        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("estado-vacio-titulo");
        VBox caja = new VBox(6, icono(icono), lblTitulo);
        if (detalle != null && !detalle.isBlank()) {
            Label lblDetalle = new Label(detalle);
            lblDetalle.setWrapText(true);
            lblDetalle.setMaxWidth(420);
            lblDetalle.getStyleClass().add("estado-vacio-detalle");
            caja.getChildren().add(lblDetalle);
        }
        caja.setAlignment(Pos.CENTER);
        caja.getStyleClass().add("estado-vacio");
        return caja;
    }

    /** Indicador mientras se consulta al servidor. */
    static VBox cargando(String texto) {
        ProgressIndicator progreso = new ProgressIndicator();
        progreso.setPrefSize(30, 30);
        progreso.setMaxSize(30, 30);
        VBox caja = new VBox(10, progreso, new Label(texto));
        caja.setAlignment(Pos.CENTER);
        caja.getStyleClass().add("estado-cargando");
        return caja;
    }

    // ---------------------------------------------------------------- notificaciones (toast)

    /** Aviso breve en la esquina inferior derecha que desaparece solo; no interrumpe como un diálogo. */
    static void toast(Node origen, String mensaje) {
        toast(origen, mensaje, false);
    }

    static void toast(Node origen, String mensaje, boolean error) {
        Window ventana = origen == null || origen.getScene() == null ? null : origen.getScene().getWindow();
        if (ventana == null || !ventana.isShowing()) {
            return;
        }
        Label texto = new Label(mensaje,
                icono(error ? "mdi2a-alert-circle-outline" : "mdi2c-check-circle-outline", 18));
        texto.setMaxWidth(520);
        HBox caja = new HBox(texto);
        caja.getStylesheets().add(HOJA_ESTILOS);
        caja.getStyleClass().addAll("root", "toast", error ? "toast-error" : "toast-exito");
        caja.setOpacity(0);

        Popup popup = new Popup();
        popup.getContent().add(caja);
        List<Popup> abiertos = TOASTS.computeIfAbsent(ventana, v -> new ArrayList<>());
        popup.show(ventana);
        // Se apilan hacia arriba sobre la barra de estado
        double alto = caja.getHeight();
        double y = ventana.getY() + ventana.getHeight() - 56 - alto
                - abiertos.stream().mapToDouble(p -> p.getHeight() + 8).sum();
        popup.setX(ventana.getX() + ventana.getWidth() - caja.getWidth() - 28);
        popup.setY(y);
        abiertos.add(popup);

        FadeTransition entrar = new FadeTransition(Duration.millis(160), caja);
        entrar.setToValue(1);
        PauseTransition esperar = new PauseTransition(Duration.seconds(error ? 6 : 4));
        FadeTransition salir = new FadeTransition(Duration.millis(300), caja);
        salir.setToValue(0);
        SequentialTransition secuencia = new SequentialTransition(entrar, esperar, salir);
        secuencia.setOnFinished(e -> {
            popup.hide();
            abiertos.remove(popup);
        });
        caja.setOnMouseClicked(e -> secuencia.jumpTo(secuencia.getTotalDuration()));
        secuencia.play();
    }

    // ---------------------------------------------------------------- atajos de teclado

    /**
     * Registra un atajo en la escena de la vista; solo actúa cuando la vista está a la vista
     * (las pestañas no seleccionadas siguen en la escena, pero ocultas).
     */
    static void atajo(Node vista, KeyCombination combinacion, Runnable accion) {
        EventHandler<KeyEvent> filtro = e -> {
            if (combinacion.match(e) && estaVisible(vista)) {
                e.consume();
                accion.run();
            }
        };
        vista.sceneProperty().addListener((obs, antes, ahora) -> {
            if (antes != null) {
                antes.removeEventFilter(KeyEvent.KEY_PRESSED, filtro);
            }
            if (ahora != null) {
                ahora.addEventFilter(KeyEvent.KEY_PRESSED, filtro);
            }
        });
        if (vista.getScene() != null) {
            vista.getScene().addEventFilter(KeyEvent.KEY_PRESSED, filtro);
        }
    }

    /** Esc cierra la ventana (diálogos propios hechos con Stage). */
    static void cerrarConEsc(Stage ventana) {
        cerrarConEsc(ventana, () -> false);
    }

    /**
     * Esc cierra la ventana; si {@code hayDatos} es true pide confirmar para no perder lo escrito.
     * Es un manejador (no un filtro): si un control usa Esc antes (p. ej. un editor), no se cierra.
     */
    static void cerrarConEsc(Stage ventana, BooleanSupplier hayDatos) {
        ventana.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                if (!hayDatos.getAsBoolean() || confirmar(ventana, "¿Cerrar sin guardar?",
                        "Se perderá lo que escribió en esta ventana.", "Cerrar sin guardar")) {
                    ventana.close();
                }
            }
        });
    }

    private static boolean estaVisible(Node nodo) {
        for (Node n = nodo; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- confirmación

    /** Pide confirmación antes de una acción que no se puede deshacer; true si el usuario acepta. */
    static boolean confirmar(Window duenio, String titulo, String mensaje, String textoAccion) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION);
        aplicarEstilos(alerta);
        if (duenio != null) {
            alerta.initOwner(duenio);
        }
        alerta.setTitle("D'Tulia");
        alerta.setHeaderText(titulo);
        alerta.setContentText(mensaje);
        alerta.setGraphic(icono("mdi2a-alert-outline", 32));
        alerta.getDialogPane().getStyleClass().add("dialogo-peligro");
        ButtonType aceptar = new ButtonType(textoAccion, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        alerta.getButtonTypes().setAll(cancelar, aceptar);
        Button btnAceptar = (Button) alerta.getDialogPane().lookupButton(aceptar);
        btnAceptar.getStyleClass().add("boton-peligro-solido");
        btnAceptar.setDefaultButton(false); // Enter no confirma por accidente: el foco queda en Cancelar
        Button btnCancelar = (Button) alerta.getDialogPane().lookupButton(cancelar);
        btnCancelar.setDefaultButton(true);
        return alerta.showAndWait().filter(aceptar::equals).isPresent();
    }

    static void mostrarError(Window duenio, String mensaje) {
        mostrar(Alert.AlertType.ERROR, duenio, "No se pudo completar la operación", mensaje);
    }

    static void mostrarInfo(Window duenio, String titulo, String mensaje) {
        mostrar(Alert.AlertType.INFORMATION, duenio, titulo, mensaje);
    }

    static String soles(BigDecimal monto) {
        return String.format(PERU, "S/ %,.2f", monto == null ? BigDecimal.ZERO : monto);
    }

    /** 30 unidades de 12 por caja → "2 cajas + 6 u."; 6 → "6 u."; 24 → "2 cajas". */
    static String cajas(int unidades, int porCaja) {
        if (porCaja <= 1 || unidades < porCaja) {
            return unidades + " u.";
        }
        int cajas = unidades / porCaja;
        int sueltas = unidades % porCaja;
        String texto = cajas + (cajas == 1 ? " caja" : " cajas");
        return sueltas == 0 ? texto : texto + " + " + sueltas + " u.";
    }

    /** Recuadro blanco con título usado por los formularios. */
    static VBox seccion(String titulo, Node... contenido) {
        return seccion(titulo, null, contenido);
    }

    /** Igual, con un ícono antes del título. */
    static VBox seccion(String titulo, String icono, Node... contenido) {
        Label etiqueta = new Label(titulo, icono == null ? null : icono(icono));
        etiqueta.getStyleClass().add("titulo-seccion");
        VBox caja = new VBox(10, etiqueta);
        caja.getChildren().addAll(contenido);
        caja.getStyleClass().add("seccion");
        return caja;
    }

    /**
     * Envuelve una columna de formulario: ocupa todo el alto disponible y solo muestra barra de
     * desplazamiento cuando la ventana es más baja que el contenido (pantallas de 768 px).
     */
    static ScrollPane desplazable(Region contenido) {
        ScrollPane panel = new ScrollPane(contenido);
        panel.setFitToWidth(true);
        panel.setFitToHeight(true);
        panel.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        panel.setMinWidth(contenido.getMinWidth());
        panel.getStyleClass().add("desplazable");
        return panel;
    }

    /** Columna de texto no ordenable. */
    static <S> TableColumn<S, String> columna(String titulo, Function<S, String> valor, double ancho, boolean numerica) {
        return columna(titulo, valor, ancho, numerica, fila -> null);
    }

    /** claseCss: clase de estilo de la celda según la fila ("texto-alerta", "texto-ok" o null). */
    static <S> TableColumn<S, String> columna(String titulo, Function<S, String> valor, double ancho, boolean numerica,
                                              Function<S, String> claseCss) {
        TableColumn<S, String> columna = new TableColumn<>(titulo);
        columna.setCellValueFactory(d -> new ReadOnlyObjectWrapper<>(valor.apply(d.getValue())));
        columna.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String texto, boolean vacio) {
                super.updateItem(texto, vacio);
                setText(vacio ? null : texto);
                getStyleClass().removeAll("texto-alerta", "texto-ok");
                S fila = vacio || getTableRow() == null ? null : getTableRow().getItem();
                String clase = fila == null ? null : claseCss.apply(fila);
                if (clase != null) {
                    getStyleClass().add(clase);
                }
            }
        });
        columna.setPrefWidth(ancho);
        if (ancho <= 100) {
            columna.setMinWidth(ancho); // números y fechas: que no se recorten a "12/10/20…"
        }
        columna.setSortable(false);
        if (numerica) {
            columna.getStyleClass().add("numero");
        }
        return columna;
    }

    /** Al pasar el mouse sobre la celda se ve el texto completo (nombres, referencias y notas largas). */
    static <S> TableColumn<S, String> conTextoCompleto(TableColumn<S, String> columna) {
        var fabrica = columna.getCellFactory();
        columna.setCellFactory(c -> {
            TableCell<S, String> celda = fabrica.call(c);
            celda.textProperty().addListener((obs, antes, texto) ->
                    celda.setTooltip(texto == null || texto.isBlank() ? null : new Tooltip(texto)));
            return celda;
        });
        return columna;
    }

    /**
     * El Spinner editable no confirma lo tecleado hasta perder el foco: se lee el editor.
     * Devuelve -1 si el texto no es un número válido dentro del rango del Spinner.
     */
    static int leerEntero(Spinner<Integer> spinner) {
        try {
            int valor = Integer.parseInt(spinner.getEditor().getText().trim());
            spinner.getValueFactory().setValue(valor);
            return spinner.getValue() == valor ? valor : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void mostrar(Alert.AlertType tipo, Window duenio, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo);
        aplicarEstilos(alerta);
        if (duenio != null) {
            alerta.initOwner(duenio);
        }
        alerta.setTitle("D'Tulia");
        alerta.setHeaderText(titulo);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
