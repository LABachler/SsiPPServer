package SSiPP.Server;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public class AddDriverController {
    @FXML
    public TextField tfPath;
    @FXML
    public TextField tfDriverType;
    @FXML
    public Button btnCancel;
    @FXML
    public Button btnSave1;
    @FXML
    public Button btnAddExecutable;

    @FXML
    public void handleBtnAddExecutableOnMouseReleased(MouseEvent mouseEvent) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Executable");
        File f = fc.showOpenDialog(new Stage());
        if (f != null)
            tfPath.setText(f.getAbsolutePath());
    }

    @FXML
    public void handleBtnCancelOnMouseReleased(MouseEvent mouseEvent) {
        ((Stage)btnCancel.getScene().getWindow()).close();
    }

    @FXML
    public void handleBtnSaveOnMouseReleased(MouseEvent mouseEvent) {
    }
}
