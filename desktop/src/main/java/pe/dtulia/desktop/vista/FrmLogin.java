package pe.dtulia.desktop.vista;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import pe.dtulia.desktop.config.Configuracion;
import pe.dtulia.desktop.modelo.Sesion;
import pe.dtulia.desktop.servicio.AuthServicio;

import java.util.function.Consumer;

/** Autenticación del personal (documento + contraseña). Todos trabajan desde el almacén de Trujillo. */
public class FrmLogin extends Stage {

    private final AuthServicio authServicio;
    private final Consumer<Sesion> alIngresar;

    private final TextField txtDocumento = new TextField();
    private final PasswordField txtPassword = new PasswordField();
    private final Button btnIngresar = new Button("Ingresar");
    private final Label lblMensaje = new Label();

    public FrmLogin(AuthServicio authServicio, Consumer<Sesion> alIngresar) {
        this.authServicio = authServicio;
        this.alIngresar = alIngresar;

        Scene escena = new Scene(construir(), 380, 440);
        Ui.aplicarEstilos(escena);
        setScene(escena);
        setTitle("D'Tulia · Iniciar sesión");
        setResizable(false);
    }

    private Parent construir() {
        Label marca = new Label("D'Tulia");
        marca.getStyleClass().add("marca");
        Label subtitulo = new Label("Control de inventario y ventas");
        subtitulo.getStyleClass().add("subtitulo");

        txtDocumento.setPromptText("DNI del empleado");
        txtPassword.setPromptText("Contraseña");

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
                btnIngresar, lblMensaje, espacio, servidor);
        caja.setPadding(new Insets(32, 32, 20, 32));
        caja.getStyleClass().add("login");
        return caja;
    }

    private void ingresar() {
        String documento = txtDocumento.getText().trim();
        if (documento.isEmpty() || txtPassword.getText().isEmpty()) {
            lblMensaje.setText("Ingrese su documento y contraseña.");
            return;
        }

        bloquear(true);
        lblMensaje.setText("");
        Ui.alCompletar(authServicio.iniciarSesion(documento, txtPassword.getText()),
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
        btnIngresar.setDisable(ocupado);
        btnIngresar.setText(ocupado ? "Validando…" : "Ingresar");
    }
}
