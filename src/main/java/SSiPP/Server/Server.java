package SSiPP.Server;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Server {
    private final ObservableList<Driver> drivers;

    public Server() {
        drivers = FXCollections.observableArrayList();
    }

    public void addDriver(String type, String path) {
        drivers.add(new Driver(type, path));
        System.out.println(drivers.get(drivers.size() - 1));
    }

    public ObservableList<Driver> getDrivers() {
        return drivers;
    }
}
