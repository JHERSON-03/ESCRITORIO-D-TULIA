package pe.dtulia.desktop.vista;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import pe.dtulia.desktop.servicio.ApiException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Utilidades comunes de la capa de presentación. */
final class Ui {

    private static final Locale PERU = Locale.forLanguageTag("es-PE");

    private Ui() {
    }

    /** Ejecuta los callbacks en el hilo de JavaFX cuando termina la petición HTTP. */
    static <T> void alCompletar(CompletableFuture<T> futuro, Consumer<T> exito, Consumer<String> error) {
        futuro.whenComplete((valor, falla) -> Platform.runLater(() -> {
            if (falla == null) {
                exito.accept(valor);
            } else {
                error.accept(ApiException.mensajeDe(falla));
            }
        }));
    }

    static void aplicarEstilos(Scene escena) {
        escena.getStylesheets().add(Ui.class.getResource("/pe/dtulia/desktop/estilos.css").toExternalForm());
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

    /** Recuadro blanco con título usado por los formularios. */
    static VBox seccion(String titulo, Node... contenido) {
        Label etiqueta = new Label(titulo);
        etiqueta.getStyleClass().add("titulo-seccion");
        VBox caja = new VBox(10, etiqueta);
        caja.getChildren().addAll(contenido);
        caja.getStyleClass().add("seccion");
        return caja;
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
        if (duenio != null) {
            alerta.initOwner(duenio);
        }
        alerta.setTitle("D'Tulia");
        alerta.setHeaderText(titulo);
        alerta.setContentText(mensaje);
        alerta.showAndWait();
    }
}
