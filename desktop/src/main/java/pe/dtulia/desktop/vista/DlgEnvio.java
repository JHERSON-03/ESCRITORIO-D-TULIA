package pe.dtulia.desktop.vista;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import pe.dtulia.desktop.modelo.EnvioSolicitud;
import pe.dtulia.desktop.modelo.ItemViaje;
import pe.dtulia.desktop.modelo.Producto;
import pe.dtulia.desktop.modelo.Sede;
import pe.dtulia.desktop.modelo.ViajeDetalle;
import pe.dtulia.desktop.servicio.Servicios;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Mercadería que sale del almacén hacia el jefe: la carga inicial de un viaje o lo que pide
 * durante el viaje. Cada producto se ingresa en cajas + unidades sueltas.
 */
class DlgEnvio extends Stage {

    /** Línea del envío; la cantidad siempre en unidades. */
    record Linea(Producto producto, int cantidad) {
    }

    private final Servicios servicios;
    private final Long ciudadSugerida;
    private final Function<EnvioSolicitud, CompletableFuture<ViajeDetalle>> registrar;
    private final Consumer<ViajeDetalle> alRegistrar;

    private final ComboBox<Sede> cboCiudad = new ComboBox<>();
    private final TextField txtReferencia = new TextField();
    private final ComboBox<Producto> cboProducto = new ComboBox<>();
    private final CampoCajas campoCajas = new CampoCajas();
    private final ObservableList<Linea> lineas = FXCollections.observableArrayList();
    private final TableView<Linea> tabla = new TableView<>(lineas);
    private final Label lblTotal = new Label();
    private final Button btnRegistrar;

    /**
     * @param ciudadSugerida ciudad elegida al abrir (la del último envío) o null
     * @param registrar      llamada a la API (abrir viaje o enviar al viaje)
     */
    DlgEnvio(Window duenio, Servicios servicios, String titulo, String textoBoton, Long ciudadSugerida,
             Function<EnvioSolicitud, CompletableFuture<ViajeDetalle>> registrar, Consumer<ViajeDetalle> alRegistrar) {
        this.servicios = servicios;
        this.ciudadSugerida = ciudadSugerida;
        this.registrar = registrar;
        this.alRegistrar = alRegistrar;
        this.btnRegistrar = new Button(textoBoton);

        initOwner(duenio);
        initModality(Modality.WINDOW_MODAL);
        setTitle("D'Tulia · " + titulo);
        BorderPane raiz = new BorderPane();
        raiz.getStyleClass().add("formulario");
        raiz.setTop(crearDatos(titulo));
        raiz.setCenter(crearDetalle());
        raiz.setBottom(crearPie());
        Scene escena = new Scene(raiz, 820, 580);
        Ui.aplicarEstilos(escena);
        setScene(escena);

        lineas.addListener((ListChangeListener<Linea>) c -> actualizarTotal());
        cboProducto.valueProperty().addListener((obs, antes, ahora) -> campoCajas.setProducto(ahora));
        actualizarTotal();
        cargarDatos();
    }

    private VBox crearDatos(String titulo) {
        cboCiudad.setPromptText("Cargando ciudades…");
        cboCiudad.setMaxWidth(Double.MAX_VALUE);
        txtReferencia.setPromptText("Agencia, guía, placa… (opcional)");
        txtReferencia.setMaxWidth(Double.MAX_VALUE);

        GridPane grilla = new GridPane();
        grilla.setHgap(10);
        grilla.setVgap(8);
        grilla.addRow(0, new Label("Ciudad"), cboCiudad, new Label("Referencia"), txtReferencia);
        ColumnConstraints etiqueta = new ColumnConstraints();
        ColumnConstraints campo = new ColumnConstraints(180, 240, Double.MAX_VALUE);
        campo.setHgrow(Priority.ALWAYS);
        grilla.getColumnConstraints().setAll(etiqueta, campo, etiqueta, campo);
        return Ui.seccion(titulo, grilla);
    }

    private VBox crearDetalle() {
        cboProducto.setPromptText("Seleccione un producto");
        cboProducto.setMaxWidth(Double.MAX_VALUE);
        cboProducto.setVisibleRowCount(14);
        cboProducto.setConverter(new StringConverter<>() {
            @Override
            public String toString(Producto p) {
                return p == null ? "" : String.format("%s · %s (almacén: %s)", p.tipoProducto(), p.nombreCompleto(),
                        Ui.cajas(p.stock(), p.unidadesCaja()));
            }

            @Override
            public Producto fromString(String texto) {
                return null;
            }
        });
        Button btnAgregar = new Button("Agregar");
        btnAgregar.setOnAction(e -> agregar());
        HBox filaCantidad = new HBox(10, campoCajas, btnAgregar);
        filaCantidad.setAlignment(Pos.CENTER_LEFT);

        tabla.getColumns().setAll(List.of(
                Ui.<Linea>columna("Producto", l -> l.producto().nombreCompleto(), 300, false),
                Ui.<Linea>columna("Tipo", l -> l.producto().tipoProducto(), 110, false),
                Ui.<Linea>columna("Unidades", l -> String.valueOf(l.cantidad()), 90, true),
                Ui.<Linea>columna("En cajas", l -> Ui.cajas(l.cantidad(), l.producto().unidadesCaja()), 140, false),
                Ui.<Linea>columna("Quedan en almacén", l -> String.valueOf(l.producto().stock() - l.cantidad()), 130, true)));
        tabla.setPlaceholder(new Label("Agregue los productos que salen del almacén"));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tabla, Priority.ALWAYS);

        VBox detalle = Ui.seccion("Productos", cboProducto, filaCantidad, tabla);
        VBox.setVgrow(detalle, Priority.ALWAYS);
        VBox contenedor = new VBox(detalle);
        contenedor.setPadding(new Insets(10, 0, 10, 0));
        return contenedor;
    }

    private HBox crearPie() {
        Button btnQuitar = new Button("Quitar seleccionado");
        btnQuitar.setOnAction(e -> {
            Linea linea = tabla.getSelectionModel().getSelectedItem();
            if (linea != null) {
                lineas.remove(linea);
            }
        });
        Button btnCancelar = new Button("Cancelar");
        btnCancelar.setOnAction(e -> close());
        btnRegistrar.getStyleClass().add("boton-primario");
        btnRegistrar.setOnAction(e -> registrar());
        lblTotal.getStyleClass().add("cliente");

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(12, btnQuitar, espacio, lblTotal, btnCancelar, btnRegistrar);
        pie.setAlignment(Pos.CENTER_RIGHT);
        return pie;
    }

    private void cargarDatos() {
        Ui.alCompletar(servicios.catalogos().sedes(),
                sedes -> {
                    List<Sede> ciudades = sedes.stream().filter(s -> !s.esAlmacen()).toList();
                    cboCiudad.getItems().setAll(ciudades);
                    cboCiudad.setPromptText("Seleccione la ciudad");
                    ciudades.stream().filter(s -> ciudadSugerida != null && s.id() == ciudadSugerida).findFirst()
                            .ifPresent(cboCiudad::setValue);
                },
                mensaje -> Ui.mostrarError(this, mensaje));
        // Solo lo que hay en el almacén puede salir
        Ui.alCompletar(servicios.productos().listar(),
                lista -> cboProducto.getItems().setAll(lista.stream()
                        .filter(p -> p.stock() > 0)
                        .sorted(Comparator.comparing(Producto::tipoProducto).thenComparing(Producto::nombre)
                                .thenComparingLong(Producto::presentacionId))
                        .toList()),
                mensaje -> Ui.mostrarError(this, mensaje));
    }

    private void agregar() {
        Producto producto = cboProducto.getValue();
        if (producto == null) {
            Ui.mostrarError(this, "Seleccione un producto.");
            return;
        }
        int cantidad = campoCajas.total();
        if (cantidad < 1) {
            Ui.mostrarError(this, "Ingrese las cajas y/o unidades (en total, mayor a 0).");
            return;
        }

        int indice = -1;
        for (int i = 0; i < lineas.size(); i++) {
            if (lineas.get(i).producto().id() == producto.id()) {
                indice = i;
            }
        }
        int total = cantidad + (indice >= 0 ? lineas.get(indice).cantidad() : 0);
        if (total > producto.stock()) {
            Ui.mostrarError(this, String.format("En el almacén solo hay %d unidades (%s) de %s.", producto.stock(),
                    Ui.cajas(producto.stock(), producto.unidadesCaja()), producto.nombreCompleto()));
            return;
        }
        if (indice >= 0) {
            lineas.set(indice, new Linea(producto, total));
        } else {
            lineas.add(new Linea(producto, total));
        }
        cboProducto.getSelectionModel().clearSelection();
        campoCajas.limpiar();
    }

    private void registrar() {
        Sede ciudad = cboCiudad.getValue();
        if (ciudad == null) {
            Ui.mostrarError(this, "Seleccione la ciudad a la que va la mercadería.");
            return;
        }
        if (lineas.isEmpty()) {
            Ui.mostrarError(this, "Agregue al menos un producto.");
            return;
        }
        String referencia = txtReferencia.getText().trim();
        EnvioSolicitud solicitud = new EnvioSolicitud(ciudad.id(), referencia.isEmpty() ? null : referencia,
                lineas.stream().map(l -> new ItemViaje(l.producto().id(), l.cantidad())).toList());

        btnRegistrar.setDisable(true);
        Ui.alCompletar(registrar.apply(solicitud),
                detalle -> {
                    close();
                    alRegistrar.accept(detalle);
                },
                mensaje -> {
                    btnRegistrar.setDisable(false);
                    Ui.mostrarError(this, mensaje);
                });
    }

    private void actualizarTotal() {
        int unidades = lineas.stream().mapToInt(Linea::cantidad).sum();
        lblTotal.setText(lineas.size() + (lineas.size() == 1 ? " producto" : " productos") + "  ·  " + unidades + " unidades");
    }
}
