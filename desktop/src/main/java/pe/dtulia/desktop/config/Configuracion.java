package pe.dtulia.desktop.config;

public final class Configuracion {

    private static final String URL_POR_DEFECTO = "http://127.0.0.1:8000";

    private Configuracion() {
    }

    /** Prioridad: -Ddtulia.api.url=... → variable DTULIA_API_URL → localhost. */
    public static String urlApi() {
        String url = System.getProperty("dtulia.api.url",
                System.getenv().getOrDefault("DTULIA_API_URL", URL_POR_DEFECTO));
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
