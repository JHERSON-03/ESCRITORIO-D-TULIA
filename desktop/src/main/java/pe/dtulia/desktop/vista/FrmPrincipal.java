package pe.dtulia.desktop.vista;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import pe.dtulia.desktop.modelo.Empleado;
import pe.dtulia.desktop.modelo.Sesion;
import pe.dtulia.desktop.servicio.Servicios;

/** Ventana principal: pestañas según el cargo del empleado. */
public class FrmPrincipal extends Stage {

    public FrmPrincipal(Sesion sesion, Servicios servicios, Runnable alCerrarSesion) {
        Empleado empleado = sesion.empleado();

        FrmProductos frmProductos = new FrmProductos(servicios);
        FrmVentas frmVentas = new FrmVentas(servicios, frmProductos::recargar);

        Tab tabInventario = new Tab("Inventario", frmProductos);
        Tab tabVentas = new Tab("Ventas", frmVentas);
        tabVentas.setOnSelectionChanged(e -> {
            if (tabVentas.isSelected()) {
                frmVentas.cargarProductos();
            }
        });
        TabPane pestanas = new TabPane(tabInventario, tabVentas);

        if (empleado.gestionaInventario()) {
            FrmMovimientos frmMovimientos = new FrmMovimientos(servicios, empleado, frmProductos::recargar);
            Tab tabMovimientos = new Tab("Movimientos de stock", frmMovimientos);
            tabMovimientos.setOnSelectionChanged(e -> {
                if (tabMovimientos.isSelected()) {
                    frmMovimientos.cargarProductos();
                }
            });
            pestanas.getTabs().add(tabMovimientos);
        }
        pestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Label marca = new Label("D'Tulia");
        marca.getStyleClass().add("cabecera-marca");
        Label usuario = new Label(empleado.nombreCompleto() + "  ·  " + empleado.rol() + "  ·  Sede " + empleado.sede());
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Button btnSalir = new Button("Cerrar sesión");
        btnSalir.setOnAction(e -> {
            alCerrarSesion.run(); // abre el login antes de cerrar para que JavaFX no finalice
            close();
        });

        HBox cabecera = new HBox(16, marca, espacio, usuario, btnSalir);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        cabecera.getStyleClass().add("cabecera");

        BorderPane raiz = new BorderPane(pestanas);
        raiz.setTop(cabecera);

        Scene escena = new Scene(raiz, 1100, 680);
        Ui.aplicarEstilos(escena);
        setScene(escena);
        setTitle("D'Tulia · " + empleado.sede());
        setMinWidth(900);
        setMinHeight(560);
        setOnHidden(e -> frmProductos.detener());
    }
}
