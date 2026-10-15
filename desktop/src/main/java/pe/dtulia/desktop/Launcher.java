package pe.dtulia.desktop;

import javafx.application.Application;

/** Punto de entrada que no extiende Application (permite ejecutar el JAR sin módulos). */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
