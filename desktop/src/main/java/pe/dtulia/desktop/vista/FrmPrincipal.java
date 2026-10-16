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

/**
 * Ventana principal: pestañas según el cargo. Las administradoras (almacén y fábrica) ven todo;
 * el almacenero solo consulta Inventario y Kardex.
 */
public class FrmPrincipal extends Stage {

    public FrmPrincipal(Sesion sesion, Servicios servicios, Runnable alCerrarSesion) {
        Empleado empleado = sesion.empleado();
        boolean administrador = empleado.esAdministrador();

        FrmProductos frmProductos = new FrmProductos(servicios, empleado);
        TabPane pestanas = new TabPane(new Tab("Inventario", frmProductos));

        if (administrador) {
            FrmVentas frmVentas = new FrmVentas(servicios, frmProductos::recargar);
            Tab tabVentas = new Tab("Ventas", frmVentas);
            tabVentas.setOnSelectionChanged(e -> {
                if (tabVentas.isSelected()) {
                    frmVentas.cargarProductos();
                }
            });
            pestanas.getTabs().add(tabVentas);
        }

        FrmMovimientos frmMovimientos = new FrmMovimientos(servicios, empleado, frmProductos::recargar);
        Tab tabMovimientos = new Tab(administrador ? "Movimientos de stock" : "Kardex", frmMovimientos);
        tabMovimientos.setOnSelectionChanged(e -> {
            if (tabMovimientos.isSelected()) {
                frmMovimientos.cargarProductos();
            }
        });
        pestanas.getTabs().add(tabMovimientos);

        FrmProduccion frmProduccion = null;
        if (administrador) {
            FrmViajes frmViajes = new FrmViajes(servicios, empleado, frmProductos::recargar);
            Tab tabViajes = new Tab("Viajes", frmViajes);
            tabViajes.setOnSelectionChanged(e -> {
                if (tabViajes.isSelected()) {
                    frmViajes.recargar();
                }
            });
            pestanas.getTabs().add(tabViajes);

            Tab tabProduccion = new Tab("Producción");
            // El título avisa cuántos envíos de la fábrica falta recibir
            frmProduccion = new FrmProduccion(servicios, frmProductos::recargar,
                    enCamino -> tabProduccion.setText(enCamino == 0 ? "Producción" : "Producción (" + enCamino + ")"));
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

        Label marca = new Label("D'Tulia");
        marca.getStyleClass().add("cabecera-marca");
        Label usuario = new Label(empleado.nombreCompleto() + "  ·  " + empleado.rol() + "  ·  Almacén " + empleado.sede());
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
}
