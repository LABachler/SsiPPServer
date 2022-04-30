package SSiPP.Server;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Server {
    private ObservableList<Driver> drivers;

    public Server() {
        drivers = FXCollections.observableArrayList();
    }

    public void addDriver(String type, String path) {
        drivers.add(new Driver(type, path));
        System.out.println(drivers.get(drivers.size() - 1));
    }

    public void removeDriver(String type) {
        drivers.remove(type);
    }

    public ObservableList<Driver> getDrivers() {
        return drivers;
    }
}
