package pe.dtulia.desktop.vista;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pe.dtulia.desktop.config.Configuracion;
import pe.dtulia.desktop.modelo.Sede;
import pe.dtulia.desktop.modelo.Sesion;
import pe.dtulia.desktop.servicio.AuthServicio;
import pe.dtulia.desktop.servicio.CatalogoServicio;

import java.util.function.Consumer;

/** Autenticación del personal (documento + contraseña) y elección de la sede de trabajo. */
public class FrmLogin extends Stage {

    private final AuthServicio authServicio;
    private final CatalogoServicio catalogoServicio;
    private final Consumer<Sesion> alIngresar;

    private final TextField txtDocumento = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final ComboBox<Sede> cboSede = new ComboBox<>();
    private final Button btnIngresar = new Button("Ingresar");
    private final Label lblMensaje = new Label();

    public FrmLogin(AuthServicio authServicio, CatalogoServicio catalogoServicio, Consumer<Sesion> alIngresar) {
        this.authServicio = authServicio;
        this.catalogoServicio = catalogoServicio;
        this.alIngresar = alIngresar;

        Scene escena = new Scene(construir(), 380, 500);
        Ui.aplicarEstilos(escena);
        setScene(escena);
        setTitle("D'Tulia · Iniciar sesión");
        setResizable(false);
        cargarSedes();
    }

    private Parent construir() {
        Label marca = new Label("D'Tulia");
        marca.getStyleClass().add("marca");
        Label subtitulo = new Label("Control de inventario y ventas");
        subtitulo.getStyleClass().add("subtitulo");

        txtDocumento.setPromptText("DNI del empleado");
        txtPassword.setPromptText("Contraseña");
        cboSede.setPromptText("Cargando sedes…");
        cboSede.setMaxWidth(Double.MAX_VALUE);

        btnIngresar.getStyleClass().add("boton-primario");
        btnIngresar.setDefaultButton(true);
        btnIngresar.setMaxWidth(Double.MAX_VALUE);
        btnIngresar.setOnAction(e -> ingresar());

        lblMensaje.getStyleClass().add("mensaje-error");
        lblMensaje.setWrapText(true);

        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        Label servidor = new Label("Servidor: " + Configuracion.urlApi());
        servidor.getStyleClass().add("estado");

        VBox caja = new VBox(8,
                marca, subtitulo, new Region(),
                new Label("Documento"), txtDocumento,
                new Label("Contraseña"), txtPassword,
                new Label("Sede"), cboSede,
                btnIngresar, lblMensaje, espacio, servidor);
        caja.setPadding(new Insets(32, 32, 20, 32));
        caja.getStyleClass().add("login");
        return caja;
    }

    private void cargarSedes() {
        Ui.alCompletar(catalogoServicio.sedes(),
                sedes -> {
                    cboSede.getItems().setAll(sedes);
                    cboSede.setPromptText("Seleccione su sede");
                    if (sedes.size() == 1) {
                        cboSede.getSelectionModel().selectFirst();
                    }
                },
                mensaje -> {
                    cboSede.setPromptText("Sin conexión");
                    lblMensaje.setText(mensaje);
                });
    }

    private void ingresar() {
        if (cboSede.getItems().isEmpty()) {
            lblMensaje.setText("");
            cargarSedes(); // reintento si la API no respondía al abrir
            return;
        }
        String documento = txtDocumento.getText().trim();
        Sede sede = cboSede.getValue();
        if (documento.isEmpty() || txtPassword.getText().isEmpty() || sede == null) {
            lblMensaje.setText("Ingrese su documento, contraseña y sede.");
            return;
        }

        bloquear(true);
        lblMensaje.setText("");
        Ui.alCompletar(authServicio.iniciarSesion(documento, txtPassword.getText(), sede.id()),
                sesion -> {
                    alIngresar.accept(sesion); // se abre la ventana principal antes de cerrar esta
                    close();
                },
                mensaje -> {
                    bloquear(false);
                    lblMensaje.setText(mensaje);
                    txtPassword.clear();
                    txtPassword.requestFocus();
                });
    }

    private void bloquear(boolean ocupado) {
        txtDocumento.setDisable(ocupado);
        txtPassword.setDisable(ocupado);
        cboSede.setDisable(ocupado);
        btnIngresar.setDisable(ocupado);
        btnIngresar.setText(ocupado ? "Validando…" : "Ingresar");
    }
}
