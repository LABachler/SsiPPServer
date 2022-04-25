module ssipp.server.ssipp_server {
    requires javafx.controls;
    requires javafx.fxml;

    exports SSiPP.Server;
    opens SSiPP.Server to javafx.fxml;
}