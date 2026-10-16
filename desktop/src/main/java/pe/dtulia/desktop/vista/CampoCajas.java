package pe.dtulia.desktop.vista;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.HBox;
import pe.dtulia.desktop.modelo.Producto;

/**
 * Cantidad como "Cajas [2] + Unidades [6] = 30 unidades". El jefe pide a veces cajas y a veces
 * unidades sueltas; el stock siempre se guarda en unidades.
 */
class CampoCajas extends HBox {

    private final Spinner<Integer> spnCajas = new Spinner<>(0, 10_000, 0);
    private final Spinner<Integer> spnUnidades = new Spinner<>(0, 100_000, 0);
    private final Label lblPorCaja = new Label();
    private final Label lblTotal = new Label();
    private int porCaja = 1;
    private Runnable alCambiar = () -> {
    };

    CampoCajas() {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        spnCajas.setEditable(true);
        spnCajas.setPrefWidth(80);
        spnUnidades.setEditable(true);
        spnUnidades.setPrefWidth(90);
        lblPorCaja.getStyleClass().add("estado");
        lblTotal.getStyleClass().add("cliente");
        // Recalcula mientras se escribe, no solo al perder el foco
        spnCajas.getEditor().textProperty().addListener((obs, antes, ahora) -> actualizar());
        spnUnidades.getEditor().textProperty().addListener((obs, antes, ahora) -> actualizar());
        getChildren().addAll(new Label("Cajas"), spnCajas, lblPorCaja, new Label("+ Unidades"), spnUnidades, lblTotal);
        setProducto(null);
    }

    /** Las unidades por caja dependen de la presentación del producto elegido. */
    void setProducto(Producto producto) {
        setPorCaja(producto == null ? 0 : producto.unidadesCaja());
    }

    /** porCaja 0 = sin producto elegido (solo unidades). */
    void setPorCaja(int unidadesPorCaja) {
        porCaja = Math.max(1, unidadesPorCaja);
        lblPorCaja.setText(unidadesPorCaja <= 0 ? "" : "(×" + porCaja + ")");
        spnCajas.setDisable(unidadesPorCaja <= 0);
        actualizar();
    }

    /** Muestra un total en unidades repartido en cajas completas y sueltas. */
    void setUnidades(int total) {
        spnCajas.getValueFactory().setValue(porCaja > 1 ? total / porCaja : 0);
        spnUnidades.getValueFactory().setValue(porCaja > 1 ? total % porCaja : total);
        actualizar();
    }

    /** Se ejecuta mientras se escribe (para recalcular diferencias). */
    void setAlCambiar(Runnable accion) {
        alCambiar = accion;
    }

    /** Se ejecuta al presionar Enter en las cajas o en las unidades (por ejemplo, para agregar). */
    void setAlConfirmar(Runnable accion) {
        spnCajas.getEditor().setOnAction(e -> accion.run());
        spnUnidades.getEditor().setOnAction(e -> accion.run());
    }

    /** Total para cálculos en vivo: texto vacío o inválido cuenta como 0. */
    int totalParcial() {
        return Math.max(0, leer(spnCajas)) * porCaja + Math.max(0, leer(spnUnidades));
    }

    /** Total en unidades, o -1 si algún número no es válido. */
    int total() {
        int cajas = Ui.leerEntero(spnCajas);
        int unidades = Ui.leerEntero(spnUnidades);
        return cajas < 0 || unidades < 0 ? -1 : cajas * porCaja + unidades;
    }

    void limpiar() {
        spnCajas.getValueFactory().setValue(0);
        spnUnidades.getValueFactory().setValue(0);
        actualizar();
    }

    private void actualizar() {
        int cajas = leer(spnCajas);
        int unidades = leer(spnUnidades);
        lblTotal.setText(cajas < 0 || unidades < 0 ? "= ?" : "= " + (cajas * porCaja + unidades) + " unidades");
        if (alCambiar != null) {
            alCambiar.run();
        }
    }

    /** Lectura sin confirmar el valor en el Spinner (se usa mientras el usuario escribe). */
    private static int leer(Spinner<Integer> spinner) {
        try {
            int valor = Integer.parseInt(spinner.getEditor().getText().trim());
            return valor < 0 ? -1 : valor;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
