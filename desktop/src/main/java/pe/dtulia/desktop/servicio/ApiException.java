package pe.dtulia.desktop.servicio;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Error devuelto por la API (el mensaje es el "detail" que envía FastAPI). */
public class ApiException extends RuntimeException {

    private final int estado;

    public ApiException(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public int getEstado() {
        return estado;
    }

    /** Convierte cualquier fallo de una petición en un mensaje apto para el usuario. */
    public static String mensajeDe(Throwable error) {
        Throwable causa = error;
        while ((causa instanceof CompletionException || causa instanceof ExecutionException)
                && causa.getCause() != null) {
            causa = causa.getCause();
        }
        if (causa instanceof ApiException) {
            return causa.getMessage();
        }
        if (causa instanceof ConnectException) {
            return "No se pudo conectar con el servidor de D'Tulia. Verifique que la API esté en ejecución.";
        }
        if (causa instanceof HttpTimeoutException) {
            return "El servidor tardó demasiado en responder.";
        }
        return "Error inesperado: " + causa.getMessage();
    }
}
