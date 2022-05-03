package SSiPP.Server;

import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.IOException;

public class ServerGUIApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        startupGui(stage);
    }

    public static void main(String[] args) {
        launch();
    }

    private void startupGui(Stage stage) throws IOException{
        FXMLLoader fxmlLoader = new FXMLLoader(ServerGUIApp.class.getResource("main-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 600, 400);
        stage.setOnCloseRequest(windowEvent -> ((MainController)fxmlLoader.getController()).handleDefaultCloseOperation());
        stage.setTitle("SSiPP Driver");
        stage.setScene(scene);
        stage.show();
    }
}