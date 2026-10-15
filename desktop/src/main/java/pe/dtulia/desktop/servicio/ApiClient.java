package pe.dtulia.desktop.servicio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Cliente HTTP de la API FastAPI: java.net.http.HttpClient (nativo del JDK)
 * para las peticiones asíncronas y Jackson para convertir JSON ⇄ records.
 */
public class ApiClient {

    private static final Duration TIEMPO_RESPUESTA = Duration.ofSeconds(15);

    private final String urlBase;
    private final HttpClient http;
    private final ObjectMapper json;
    private volatile String token;

    public ApiClient(String urlBase) {
        this.urlBase = urlBase;
        this.http = HttpClient.newBuilder()
                // Uvicorn no acepta el "Upgrade: h2c" que HttpClient envía por defecto (descarta el cuerpo)
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.json = JsonMapper.builder()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE) // precioVenta ⇄ precio_venta
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .addModule(new JavaTimeModule())
                .build();
    }

    public void setToken(String token) {
        this.token = token;
    }

    public <T> CompletableFuture<T> get(String ruta, Class<T> tipo) {
        return enviar(solicitud(ruta).GET().build(), json.constructType(tipo));
    }

    public <T> CompletableFuture<T> get(String ruta, TypeReference<T> tipo) {
        return enviar(solicitud(ruta).GET().build(), json.constructType(tipo));
    }

    public <T> CompletableFuture<T> post(String ruta, Object cuerpo, Class<T> tipo) {
        return conCuerpo("POST", ruta, cuerpo, tipo);
    }

    public <T> CompletableFuture<T> patch(String ruta, Object cuerpo, Class<T> tipo) {
        return conCuerpo("PATCH", ruta, cuerpo, tipo);
    }

    private <T> CompletableFuture<T> conCuerpo(String metodo, String ruta, Object cuerpo, Class<T> tipo) {
        String cuerpoJson;
        try {
            cuerpoJson = json.writeValueAsString(cuerpo);
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
        HttpRequest peticion = solicitud(ruta)
                .header("Content-Type", "application/json")
                .method(metodo, HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8))
                .build();
        return enviar(peticion, json.constructType(tipo));
    }

    private HttpRequest.Builder solicitud(String ruta) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(urlBase + ruta))
                .timeout(TIEMPO_RESPUESTA)
                .header("Accept", "application/json");
        String tokenActual = token;
        if (tokenActual != null) {
            builder.header("Authorization", "Bearer " + tokenActual);
        }
        return builder;
    }

    private <T> CompletableFuture<T> enviar(HttpRequest peticion, JavaType tipo) {
        return http.sendAsync(peticion, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(respuesta -> convertir(respuesta, tipo));
    }

    private <T> T convertir(HttpResponse<String> respuesta, JavaType tipo) {
        int estado = respuesta.statusCode();
        if (estado < 200 || estado >= 300) {
            throw new ApiException(estado, extraerDetalle(respuesta));
        }
        if (respuesta.body() == null || respuesta.body().isBlank()) {
            return null;
        }
        try {
            return json.readValue(respuesta.body(), tipo);
        } catch (JsonProcessingException e) {
            throw new ApiException(estado, "Respuesta inválida del servidor: " + e.getOriginalMessage());
        }
    }

    /** FastAPI responde {"detail": "texto"} o {"detail": [{"msg": ...}, ...]} en errores de validación. */
    private String extraerDetalle(HttpResponse<String> respuesta) {
        try {
            JsonNode detalle = json.readTree(respuesta.body()).path("detail");
            if (detalle.isTextual()) {
                return detalle.asText();
            }
            if (detalle.isArray()) {
                List<String> mensajes = new ArrayList<>();
                detalle.forEach(error -> mensajes.add(error.path("msg").asText()));
                return String.join("\n", mensajes);
            }
        } catch (JsonProcessingException ignorado) {
            // cuerpo no JSON: se usa el mensaje genérico
        }
        return "Error HTTP " + respuesta.statusCode();
    }
}
