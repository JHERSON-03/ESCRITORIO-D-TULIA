package pe.dtulia.desktop.vista;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pe.dtulia.desktop.config.Configuracion;
import pe.dtulia.desktop.modelo.Empleado;
import pe.dtulia.desktop.modelo.Sesion;
import pe.dtulia.desktop.servicio.Servicios;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Ventana principal: pestañas según el cargo. Las administradoras (almacén y fábrica) ven todo;
 * el almacenero solo consulta Inventario y Kardex.
 */
public class FrmPrincipal extends Stage {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final PseudoClass DESCONECTADO = PseudoClass.getPseudoClass("desconectado");

    public FrmPrincipal(Sesion sesion, Servicios servicios, Runnable alCerrarSesion) {
        Empleado empleado = sesion.empleado();
        boolean administrador = empleado.esAdministrador();

        FrmProductos frmProductos = new FrmProductos(servicios, empleado);
        TabPane pestanas = new TabPane(Ui.pestana("Inventario", "mdi2a-archive-outline", frmProductos));

        if (administrador) {
            FrmVentas frmVentas = new FrmVentas(servicios, frmProductos::recargar);
            Tab tabVentas = Ui.pestana("Ventas", "mdi2c-cash-register", frmVentas);
            tabVentas.setOnSelectionChanged(e -> {
                if (tabVentas.isSelected()) {
                    frmVentas.cargarProductos();
                }
            });
            pestanas.getTabs().add(tabVentas);
        }

        FrmMovimientos frmMovimientos = new FrmMovimientos(servicios, empleado, frmProductos::recargar);
        Tab tabMovimientos = Ui.pestana(administrador ? "Movimientos de stock" : "Kardex", "mdi2s-swap-horizontal",
                frmMovimientos);
        tabMovimientos.setOnSelectionChanged(e -> {
            if (tabMovimientos.isSelected()) {
                frmMovimientos.cargarProductos();
            }
        });
        pestanas.getTabs().add(tabMovimientos);

        FrmProduccion frmProduccion = null;
        if (administrador) {
            FrmViajes frmViajes = new FrmViajes(servicios, empleado, frmProductos::recargar);
            Tab tabViajes = Ui.pestana("Viajes", "mdi2t-truck-outline", frmViajes);
            tabViajes.setOnSelectionChanged(e -> {
                if (tabViajes.isSelected()) {
                    frmViajes.recargar();
                }
            });
            pestanas.getTabs().add(tabViajes);

            // La insignia roja avisa cuántos envíos de la fábrica falta recibir
            Label insignia = new Label();
            insignia.getStyleClass().add("insignia-pestana");
            insignia.setVisible(false);
            insignia.managedProperty().bind(insignia.visibleProperty());
            Tab tabProduccion = Ui.pestana("Producción", "mdi2f-factory", null);
            ((HBox) tabProduccion.getGraphic()).getChildren().add(insignia);
            frmProduccion = new FrmProduccion(servicios, frmProductos::recargar, enCamino -> {
                insignia.setText(String.valueOf(enCamino));
                insignia.setVisible(enCamino > 0);
            });
            FrmProduccion produccion = frmProduccion;
            tabProduccion.setContent(produccion);
            tabProduccion.setOnSelectionChanged(e -> {
                if (tabProduccion.isSelected()) {
                    produccion.recargar();
                }
            });
            pestanas.getTabs().add(tabProduccion);
        }
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        FrmProduccion produccionAbierta = frmProduccion;

        Button btnSalir = Ui.boton("Cerrar sesión", "mdi2l-logout", "boton-cabecera");
        btnSalir.setOnAction(e -> {
            alCerrarSesion.run(); // abre el login antes de cerrar para que JavaFX no finalice
            close();
        });

        BorderPane raiz = new BorderPane(pestanas);
        raiz.setTop(cabecera(empleado, btnSalir));
        raiz.setBottom(barraEstado(empleado));

        Scene escena = new Scene(raiz, 1100, 680);
        Ui.aplicarEstilos(escena);
        setScene(escena);
        setTitle("D'Tulia · Almacén " + empleado.sede());
        setMinWidth(900);
        setMinHeight(560);
        setOnHidden(e -> {
            frmProductos.detener();
            if (produccionAbierta != null) {
                produccionAbierta.detener();
            }
        });
    }


    private static HBox cabecera(Empleado empleado, Button btnSalir) {
        StackPane logo = new StackPane(Ui.icono("mdi2f-fruit-grapes-outline"));
        logo.getStyleClass().add("cabecera-logo");
        Label marca = new Label("D'Tulia");
        marca.getStyleClass().add("cabecera-marca");
        Label subtitulo = new Label("Control de inventario y ventas");
        subtitulo.getStyleClass().add("cabecera-sub");
        HBox marcaYSub = new HBox(10, marca, subtitulo);
        marcaYSub.setAlignment(Pos.BASELINE_LEFT);

        Label nombre = new Label(empleado.nombreCompleto());
        nombre.getStyleClass().add("cabecera-usuario");
        Label rol = new Label(empleado.rol());
        rol.getStyleClass().add("insignia-rol");
        Label sede = new Label("Almacén " + empleado.sede());
        sede.getStyleClass().add("cabecera-sede");
        HBox usuario = new HBox(8, nombre, separador("·", "cabecera-separador"), rol, separador("·", "cabecera-separador"), sede);
        usuario.setAlignment(Pos.CENTER_RIGHT);

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox cabecera = new HBox(12, logo, marcaYSub, espacio, usuario, btnSalir);
        HBox.setMargin(btnSalir, new Insets(0, 0, 0, 8));
        cabecera.setAlignment(Pos.CENTER_LEFT);
        cabecera.setFillHeight(false);
        cabecera.getStyleClass().add("cabecera");
        return cabecera;
    }

    /** Pie: servidor (verde si responde, rojo si no), usuario y hora de la última respuesta correcta. */
    private static HBox barraEstado(Empleado empleado) {
        // Los bindings escuchan a Ui de forma débil: al cerrar sesión la ventana se libera
        Region punto = new Region();
        punto.getStyleClass().add("punto-conexion");
        Label servidor = new Label();
        servidor.getStyleClass().add("texto-conexion");
        servidor.textProperty().bind(Bindings.when(Ui.sinConexionProperty())
                .then("Sin conexión con " + Configuracion.urlApi())
                .otherwise("Conectado a " + Configuracion.urlApi()));
        BooleanBinding caido = Bindings.createBooleanBinding(() -> Ui.sinConexionProperty().get(),
                Ui.sinConexionProperty());
        caido.addListener((obs, antes, ahora) -> {
            punto.pseudoClassStateChanged(DESCONECTADO, ahora);
            servidor.pseudoClassStateChanged(DESCONECTADO, ahora);
        });
        punto.getProperties().put("caido", caido); // la ventana mantiene vivo el binding
        punto.pseudoClassStateChanged(DESCONECTADO, caido.get());
        servidor.pseudoClassStateChanged(DESCONECTADO, caido.get());
        HBox conexion = new HBox(7, punto, servidor);
        conexion.setAlignment(Pos.CENTER_LEFT);

        Label usuario = new Label(empleado.nombreCompleto() + " · " + empleado.rol(), Ui.icono("mdi2a-account-outline"));

        Label actualizado = new Label("", Ui.icono("mdi2c-clock-outline"));
        actualizado.textProperty().bind(Bindings.createStringBinding(() -> {
            LocalTime hora = Ui.ultimaRespuestaProperty().get();
            return "Última actualización: " + (hora == null ? "—" : HORA.format(hora));
        }, Ui.ultimaRespuestaProperty()));

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox barra = new HBox(12, conexion, separador("|", "separador-estado"), usuario, espacio, actualizado);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.getStyleClass().add("barra-estado");
        return barra;
    }

    private static Label separador(String texto, String clase) {
        Label separador = new Label(texto);
        separador.getStyleClass().add(clase);
        return separador;
    }
}
