package pe.dtulia.desktop;

import javafx.application.Application;
import javafx.stage.Stage;
import pe.dtulia.desktop.config.Configuracion;
import pe.dtulia.desktop.modelo.Sesion;
import pe.dtulia.desktop.servicio.ApiClient;
import pe.dtulia.desktop.servicio.AuthServicio;
import pe.dtulia.desktop.servicio.CatalogoServicio;
import pe.dtulia.desktop.servicio.InventarioServicio;
import pe.dtulia.desktop.servicio.ProductoServicio;
import pe.dtulia.desktop.servicio.Servicios;
import pe.dtulia.desktop.servicio.VentaServicio;
import pe.dtulia.desktop.servicio.ViajeServicio;
import pe.dtulia.desktop.vista.FrmLogin;
import pe.dtulia.desktop.vista.FrmPrincipal;

public class App extends Application {

    private ApiClient api;
    private AuthServicio authServicio;
    private Servicios servicios;

    @Override
    public void start(Stage escenarioInicial) {
        api = new ApiClient(Configuracion.urlApi());
        authServicio = new AuthServicio(api);
        servicios = new Servicios(
                new CatalogoServicio(api),
                new ProductoServicio(api),
                new VentaServicio(api),
                new InventarioServicio(api),
                new ViajeServicio(api));
        mostrarLogin();
    }

    private void mostrarLogin() {
        api.setToken(null);
        new FrmLogin(authServicio, this::mostrarPrincipal).show();
    }

    private void mostrarPrincipal(Sesion sesion) {
        new FrmPrincipal(sesion, servicios, this::mostrarLogin).show();
    }
}
