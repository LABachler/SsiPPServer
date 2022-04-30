module ssipp.server.ssipp_server {
    requires javafx.controls;
    requires javafx.fxml;
    requires redis.clients.jedis;

    exports SSiPP.Server;
    opens SSiPP.Server to javafx.fxml;
}