module ssipp.server.ssipp_server {
    requires javafx.controls;
    requires javafx.fxml;
    requires redis.clients.jedis;
    requires java.xml;
    requires jdk.httpserver;

    exports SSiPP.Server;
    opens SSiPP.Server to javafx.fxml;
    exports SSiPP.Server.Controllers;
    opens SSiPP.Server.Controllers to javafx.fxml;
    exports SSiPP.Server.Driver;
    opens SSiPP.Server.Driver to javafx.fxml;
    exports SSiPP.Server.Driver.Service;
    opens SSiPP.Server.Driver.Service to javafx.fxml;
    exports SSiPP.Server.Driver.util;
    opens SSiPP.Server.Driver.util to javafx.fxml;
}