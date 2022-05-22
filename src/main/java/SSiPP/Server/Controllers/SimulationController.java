package SSiPP.Server.Controllers;

import SSiPP.Server.Driver.Service.DriverCommunicatorService;
import SSiPP.Server.Server;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public class SimulationController {
    @FXML
    public Button btnSimBasic;
    @FXML
    public TextArea taProcess;

    private Server server;

    public void initServer(Server server) {
        if (this.server != null)
            return;
        this.server = server;
    }

    @FXML
    public void handleBtnSimBasicOnRelease(MouseEvent mouseEvent) {
        DriverCommunicatorService driverCommunicatorService = new DriverCommunicatorService(
                server, taProcess.getText());
        driverCommunicatorService.setPeriod(Duration.millis(500));
        driverCommunicatorService.start();
    }
}
