module ssipp.server.ssipp_server {
    requires javafx.controls;
    requires javafx.fxml;
    requires redis.clients.jedis;
    requires java.xml;

    exports SSiPP.Server;
    opens SSiPP.Server to javafx.fxml;
    exports SSiPP.Server.Controllers;
    opens SSiPP.Server.Controllers to javafx.fxml;
}