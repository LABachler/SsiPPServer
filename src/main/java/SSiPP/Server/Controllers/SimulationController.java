package SSiPP.Server.Controllers;

import SSiPP.Server.Driver.Service.DriverCommunicatorService;
import SSiPP.Server.Server;
import javafx.event.ActionEvent;
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

    private DriverCommunicatorService driverCommunicatorService;

    public void initServer(Server server) {
        if (this.server != null)
            return;
        this.server = server;
    }

    @FXML
    public void handleBtnSimBasicOnRelease(MouseEvent mouseEvent) {
        driverCommunicatorService = new DriverCommunicatorService(
                server, taProcess.getText());
        driverCommunicatorService.setPeriod(Duration.millis(250));
        driverCommunicatorService.start();
    }

    public void handleBtnPrintScheduledStateOnAction(ActionEvent actionEvent) {
        System.out.println("Driver Communicator Service Sim State:" + driverCommunicatorService.getState());
        System.out.println("Driver Communicator Service Sim Period:" + driverCommunicatorService.getPeriod());
        System.out.println("Driver Communicator Service Sim cumulative Period:" + driverCommunicatorService.getCumulativePeriod());
        System.out.println("Driver Communicator Service Sim backoff:" + driverCommunicatorService.getBackoffStrategy());
        System.out.println("Driver Communicator Service Sim failures:" + driverCommunicatorService.getCurrentFailureCount());
        System.out.println("Driver Communicator Service Sim restart on failure:" + driverCommunicatorService.getRestartOnFailure());
    }
}
