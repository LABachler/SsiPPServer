package SSiPP.Server;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Callback;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

public class MainController implements Initializable {
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

    private Server server;

    public MainController() {
        server = new Server();
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        tcDriver.setCellValueFactory(
                new PropertyValueFactory<Driver, String>("typeProperty")
        );
        tcPath.setCellValueFactory(
                new PropertyValueFactory<Driver, String>("pathProperty")
        );

        tvDrivers.setItems(server.getDrivers());
    }

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
        server.getDrivers().removeAll(tvDrivers.getSelectionModel().getSelectedItems());
    }

    private void openSimulationDialog() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("simulation-view.fxml"));
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

    private void openAddDriverDialog() {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("addDriver-view.fxml"));
        try {
            Parent root = fxmlLoader.load();
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.DECORATED);
            stage.setTitle("Add Driver");
            stage.setScene(new Scene(root));
            ((AddDriverController)fxmlLoader.getController()).initServer(server);
            stage.show();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addDriver(String type, String path) {
        server.addDriver(type, path);
    }
}