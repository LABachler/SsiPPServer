package SSiPP.Server;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController {
    @FXML
    public TableColumn tcDriver;
    @FXML
    public TableColumn tcPath;
    @FXML
    public Button btnSimulation;
    @FXML
    public Button btnAddDriver;
    @FXML
    public Button btnDeleteDriver;
    @FXML
    public TableView tvDrivers;

    @FXML
    public void handleBtnSimulationOnMouseReleased(MouseEvent mouseEvent) {
        openSimulationDialog();
    }

    @FXML
    public void handleBtnAddDriverOnMouseReleased(MouseEvent mouseEvent) {
        openAddDriverDialog();
    }

    @FXML
    public void handleBtnDeleteDriverOnMouseReleased(MouseEvent mouseEvent) {
        tvDrivers.getColumns().removeAll(tvDrivers.getSelectionModel().getSelectedItems());
    }

    private void openSimulationDialog() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("simulation-view.fxml"));
        openChildDialog(fxmlLoader);
    }

    private void openAddDriverDialog() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("addDriver-view.fxml"));
        openChildDialog(fxmlLoader);
    }

    private void openChildDialog(FXMLLoader fxmlLoader) {
        try {
            Parent root1 = fxmlLoader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.DECORATED);
            stage.setTitle("Simulation");
            stage.setScene(new Scene(root1));
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}