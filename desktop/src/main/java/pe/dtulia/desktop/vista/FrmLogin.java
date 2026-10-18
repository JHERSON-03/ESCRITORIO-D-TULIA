package pe.dtulia.desktop.vista;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
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
    private final Button btnIngresar = new Button("Ingresar al sistema");
    private final FontIcon icoIngresar = Ui.icono("mdi2a-arrow-right");
    private final ProgressIndicator progreso = new ProgressIndicator();
    private final Label lblMensaje = new Label();
    private final HBox cajaError = new HBox(8);
    private final Label lblMayus = new Label("Bloq Mayús activado", Ui.icono("mdi2k-keyboard-caps"));

    public FrmLogin(AuthServicio authServicio, Consumer<Sesion> alIngresar) {
        this.authServicio = authServicio;
        this.alIngresar = alIngresar;

        Scene escena = new Scene(construir(), 480, 620);
        Ui.aplicarEstilos(escena);
        setScene(escena);
        setTitle("D'Tulia · Iniciar sesión");
        setResizable(false);
        setOnShown(e -> {
            actualizarMayus();
            txtDocumento.requestFocus();
        });
    }

    private Parent construir() {
        StackPane logo = new StackPane(Ui.icono("mdi2f-fruit-grapes-outline"));
        logo.getStyleClass().add("login-logo");
        Label marca = new Label("D'Tulia");
        marca.getStyleClass().add("marca");
        Label subtitulo = new Label("Control de inventario y ventas");
        subtitulo.getStyleClass().add("subtitulo");
        Region divisor = new Region();
        divisor.getStyleClass().add("login-divisor");
        VBox encabezado = new VBox(6, logo, marca, subtitulo, divisor);
        encabezado.setAlignment(Pos.CENTER);
        VBox.setMargin(divisor, new Insets(6, 0, 0, 0));

        txtDocumento.setPromptText("Ingrese su DNI");
        txtPassword.setPromptText("Contraseña");
        txtDocumento.getStyleClass().add("campo-login");
        txtPassword.getStyleClass().add("campo-login");
        Label lblDocumento = etiqueta("DNI del empleado");
        lblDocumento.setLabelFor(txtDocumento);
        Label lblPassword = etiqueta("Contraseña");
        lblPassword.setLabelFor(txtPassword);

        // Aviso de Bloq Mayús a la derecha de "Contraseña"
        lblMayus.getStyleClass().add("aviso-mayus");
        lblMayus.setVisible(false);
        Region empuje = new Region();
        HBox.setHgrow(empuje, Priority.ALWAYS);
        HBox filaPassword = new HBox(lblPassword, empuje, lblMayus);
        filaPassword.setAlignment(Pos.CENTER_LEFT);
        txtPassword.setOnKeyReleased(e -> actualizarMayus());
        txtPassword.focusedProperty().addListener((obs, antes, ahora) -> actualizarMayus());
        txtDocumento.setOnKeyReleased(e -> actualizarMayus());

        progreso.setPrefSize(16, 16);
        progreso.setMaxSize(16, 16);
        btnIngresar.setGraphic(icoIngresar);
        btnIngresar.setContentDisplay(ContentDisplay.RIGHT);
        btnIngresar.getStyleClass().addAll("boton-primario", "boton-grande");
        btnIngresar.setDefaultButton(true);
        btnIngresar.setMaxWidth(Double.MAX_VALUE);
        btnIngresar.setOnAction(e -> ingresar());

        lblMensaje.setWrapText(true);
        lblMensaje.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblMensaje, Priority.ALWAYS);
        cajaError.getChildren().addAll(Ui.icono("mdi2a-alert-circle-outline"), lblMensaje);
        cajaError.setAlignment(Pos.CENTER_LEFT);
        cajaError.getStyleClass().add("alerta-error");
        cajaError.visibleProperty().bind(lblMensaje.textProperty().isNotEmpty());
        cajaError.managedProperty().bind(cajaError.visibleProperty());

        Label servidor = new Label(Configuracion.urlApi(), Ui.icono("mdi2s-server-network"));
        servidor.getStyleClass().addAll("estado", "texto-mono");
        HBox pie = new HBox(servidor);
        pie.setAlignment(Pos.CENTER_RIGHT);
        pie.getStyleClass().add("login-pie");

        VBox formulario = new VBox(6,
                lblDocumento, txtDocumento,
                espacio(8), filaPassword, txtPassword,
                espacio(12), btnIngresar,
                espacio(4), cajaError);

        Region relleno = new Region();
        VBox.setVgrow(relleno, Priority.ALWAYS);
        VBox tarjeta = new VBox(20, encabezado, formulario, relleno, pie);
        tarjeta.getStyleClass().add("login-tarjeta");
        tarjeta.setMaxWidth(400);
        tarjeta.setMaxHeight(Region.USE_PREF_SIZE);
        tarjeta.setPrefHeight(540);

        StackPane fondo = new StackPane(tarjeta);
        fondo.getStyleClass().add("login-fondo");
        return fondo;
    }

    private static Label etiqueta(String texto) {
        Label etiqueta = new Label(texto);
        etiqueta.getStyleClass().add("etiqueta-campo");
        return etiqueta;
    }

    private static Region espacio(double alto) {
        Region espacio = new Region();
        espacio.setMinHeight(alto);
        return espacio;
    }

    private void actualizarMayus() {
        lblMayus.setVisible(Platform.isKeyLocked(KeyCode.CAPS).orElse(false));
    }

    private void ingresar() {
        String documento = txtDocumento.getText().trim();
        if (documento.isEmpty() || txtPassword.getText().isEmpty()) {
            lblMensaje.setText("Ingrese su documento y contraseña.");
            (documento.isEmpty() ? txtDocumento : txtPassword).requestFocus();
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
        btnIngresar.setText(ocupado ? "Validando…" : "Ingresar al sistema");
        btnIngresar.setGraphic(ocupado ? progreso : icoIngresar);
    }
}
