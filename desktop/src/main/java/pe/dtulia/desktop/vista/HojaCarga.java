package pe.dtulia.desktop.vista;

import javafx.geometry.Insets;
import javafx.print.PageLayout;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Window;
import pe.dtulia.desktop.modelo.Envio;
import pe.dtulia.desktop.modelo.VentaCiudad;
import pe.dtulia.desktop.modelo.ViajeDetalle;
import pe.dtulia.desktop.modelo.ViajeProducto;
import pe.dtulia.desktop.modelo.ViajeResumen;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hoja impresa para el jefe. Con el viaje abierto lista lo que lleva y deja columnas en blanco para
 * anotar a mano lo vendido en cada ciudad, lo que regresa y la merma. Con el viaje liquidado imprime
 * los valores declarados. En Windows se puede elegir "Microsoft Print to PDF" para guardarla.
 */
final class HojaCarga {

    private static final DateTimeFormatter IMPRESO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String CELDA = "-fx-border-color: #444; -fx-border-width: 0 1 1 0; -fx-padding: 4 5;";
    private static final String ENCABEZADO = CELDA + "-fx-font-weight: bold; -fx-background-color: #e6e6e6;";

    private HojaCarga() {
    }

    static void imprimir(Window duenio, ViajeDetalle detalle) {
        PrinterJob trabajo = PrinterJob.createPrinterJob();
        if (trabajo == null) {
            Ui.mostrarError(duenio, "No hay impresoras instaladas en este equipo.");
            return;
        }
        if (!trabajo.showPrintDialog(duenio)) {
            return; // el usuario canceló
        }
        PageLayout pagina = trabajo.getJobSettings().getPageLayout();
        VBox hoja = construir(detalle, pagina.getPrintableWidth());

        // Fuera de pantalla: se aplica CSS y se calcula el tamaño antes de imprimir
        new Scene(new Group(hoja));
        hoja.applyCss();
        hoja.layout();
        double alto = hoja.prefHeight(pagina.getPrintableWidth());
        double escala = Math.min(1, pagina.getPrintableHeight() / alto);
        if (escala < 1) {
            hoja.getTransforms().add(new Scale(escala, escala));
        }

        if (trabajo.printPage(pagina, hoja)) {
            trabajo.endJob();
        } else {
            trabajo.cancelJob();
            Ui.mostrarError(duenio, "No se pudo imprimir la hoja del viaje.");
        }
    }

    private static VBox construir(ViajeDetalle detalle, double ancho) {
        ViajeResumen viaje = detalle.viaje();
        boolean abierto = viaje.abierto();

        Label titulo = new Label(abierto ? "D'TULIA · HOJA DE CARGA · VIAJE #" + viaje.id()
                : "D'TULIA · LIQUIDACIÓN DEL VIAJE #" + viaje.id());
        titulo.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label datos = new Label(String.format("Salida: %s   ·   Ciudades: %s   ·   %s", FrmViajes.fecha(viaje.fechaSalida()),
                viaje.ciudades() == null ? "—" : viaje.ciudades(),
                abierto ? viaje.dias() + " días fuera" : "Liquidado el " + FrmViajes.fecha(viaje.fechaLiquidacion())));
        Label impreso = new Label("Impreso: " + LocalDateTime.now().format(IMPRESO));
        impreso.setStyle("-fx-text-fill: #555;");

        VBox envios = new VBox(2);
        for (Envio envio : detalle.envios()) {
            if (!envio.anulado()) {
                int unidades = envio.items().stream().mapToInt(i -> i.cantidad()).sum();
                envios.getChildren().add(new Label(String.format("• %s  %s → %s  ·  %d unidades%s",
                        envio.numero() == 1 ? "Carga inicial" : "Envío " + envio.numero(),
                        FrmViajes.fecha(envio.fecha()), envio.sede(), unidades,
                        envio.referencia() == null ? "" : "  ·  " + envio.referencia())));
            }
        }

        VBox hoja = new VBox(8, titulo, datos, impreso, envios, tabla(detalle, abierto));
        if (!abierto && viaje.observacion() != null) {
            hoja.getChildren().add(new Label("Observación: " + viaje.observacion()));
        }
        hoja.getChildren().add(firmas());
        hoja.setPrefWidth(ancho);
        hoja.setMaxWidth(ancho);
        hoja.setPadding(new Insets(4));
        hoja.setStyle("-fx-background-color: white; -fx-font-family: 'Segoe UI'; -fx-font-size: 10px;");
        return hoja;
    }

    private static GridPane tabla(ViajeDetalle detalle, boolean abierto) {
        // Columnas de venta: ciudades de los envíos (y de lo vendido, si ya se liquidó)
        Map<Long, String> ciudades = new LinkedHashMap<>();
        detalle.envios().stream().filter(e -> !e.anulado()).forEach(e -> ciudades.putIfAbsent(e.sedeId(), e.sede()));
        detalle.ventas().forEach(v -> ciudades.putIfAbsent(v.sedeId(), v.sede()));

        List<String> encabezados = new ArrayList<>(List.of("Producto", "Llevó", "En cajas"));
        ciudades.values().forEach(c -> encabezados.add("Vendió\n" + c));
        if (abierto) {
            encabezados.add("Vendió en\n__________"); // por si pasa por otra ciudad
        }
        encabezados.add("Regresa");
        encabezados.add("Merma /\nregalo");

        GridPane grilla = new GridPane();
        grilla.setStyle("-fx-border-color: #444; -fx-border-width: 1 0 0 1;");
        for (int c = 0; c < encabezados.size(); c++) {
            grilla.add(celda(encabezados.get(c), ENCABEZADO), c, 0);
        }
        int fila = 1;
        for (ViajeProducto p : detalle.productos()) {
            if (p.llevado() == 0) {
                continue;
            }
            List<String> valores = new ArrayList<>(List.of(p.nombreCompleto(), String.valueOf(p.llevado()),
                    Ui.cajas(p.llevado(), p.unidadesCaja())));
            for (Long sedeId : ciudades.keySet()) {
                valores.add(abierto ? "" : String.valueOf(vendido(detalle.ventas(), sedeId, p.productoId())));
            }
            if (abierto) {
                valores.add("");
            }
            valores.add(abierto ? "" : String.valueOf(p.devuelto()));
            valores.add(abierto ? "" : p.merma() + (p.motivoMerma() == null ? "" : " (" + p.motivoMerma() + ")"));
            for (int c = 0; c < valores.size(); c++) {
                grilla.add(celda(valores.get(c), CELDA + "-fx-min-height: 24px;"), c, fila);
            }
            fila++;
        }

        // El producto ocupa más; el resto se reparte en partes iguales
        ColumnConstraints producto = new ColumnConstraints();
        producto.setPercentWidth(30);
        grilla.getColumnConstraints().add(producto);
        double resto = 70.0 / (encabezados.size() - 1);
        for (int c = 1; c < encabezados.size(); c++) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(resto);
            grilla.getColumnConstraints().add(columna);
        }
        return grilla;
    }

    private static int vendido(List<VentaCiudad> ventas, long sedeId, long productoId) {
        return ventas.stream().filter(v -> v.sedeId() == sedeId && v.productoId() == productoId)
                .mapToInt(VentaCiudad::cantidad).sum();
    }

    private static Label celda(String texto, String estilo) {
        Label etiqueta = new Label(texto);
        etiqueta.setStyle(estilo);
        etiqueta.setWrapText(true);
        etiqueta.setMaxWidth(Double.MAX_VALUE);
        etiqueta.setMaxHeight(Double.MAX_VALUE);
        GridPane.setHgrow(etiqueta, Priority.ALWAYS);
        GridPane.setFillWidth(etiqueta, true);
        GridPane.setFillHeight(etiqueta, true);
        return etiqueta;
    }

    private static HBox firmas() {
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox firmas = new HBox(new Label("Entregó (almacén): ______________________"), espacio,
                new Label("Recibió (jefe): ______________________"));
        firmas.setPadding(new Insets(28, 0, 0, 0));
        return firmas;
    }
}
