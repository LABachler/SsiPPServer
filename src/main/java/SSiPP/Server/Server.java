package SSiPP.Server;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import redis.clients.jedis.Jedis;

public class Server {
    private final ObservableList<Driver> drivers;
    private final Jedis jedis;

    public Server() {
        drivers = FXCollections.observableArrayList();
        jedis = new Jedis();
    }

    public void addDriver(String type, String path) {
        drivers.add(new Driver(type, path));
        System.out.println(drivers.get(drivers.size() - 1));
    }

    public ObservableList<Driver> getDrivers() {
        return drivers;
    }

    private void writeToRedis(int processId, String string) {
        jedis.set("ssipp_" + processId, string);
    }

    private String readFromRedis(int processId) {
        return jedis.get("ssipp_" + processId);
    }
}
