package pe.dtulia.desktop.servicio;

import pe.dtulia.desktop.modelo.Credenciales;
import pe.dtulia.desktop.modelo.Sesion;

import java.util.concurrent.CompletableFuture;

public class AuthServicio {

    private final ApiClient api;

    public AuthServicio(ApiClient api) {
        this.api = api;
    }

    /** POST /auth/login. Si es exitoso, el token queda guardado para las siguientes peticiones. */
    public CompletableFuture<Sesion> iniciarSesion(String documento, String password) {
        return api.post("/auth/login", new Credenciales(documento, password), Sesion.class)
                .thenApply(sesion -> {
                    api.setToken(sesion.accessToken());
                    return sesion;
                });
    }
}
