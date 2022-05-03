package SSiPP.Server;

import javafx.beans.property.SimpleStringProperty;

public class Driver {
    private final SimpleStringProperty typeProperty;
    private final SimpleStringProperty pathProperty;

    public Driver(String type, String path) {
        typeProperty = new SimpleStringProperty(type);
        pathProperty = new SimpleStringProperty(path);
    }

    public String getType() {
        return typeProperty.get();
    }

    public SimpleStringProperty typeProperty() {
        return typeProperty;
    }

    public void setType(String type) {
        this.typeProperty.set(type);
    }

    public String getPath() {
        return pathProperty.get();
    }

    public SimpleStringProperty pathProperty() {
        return pathProperty;
    }

    public void setPath(String path) {
        this.pathProperty.set(path);
    }

    @Override
    public String toString() {
        return typeProperty.get() + ": " + pathProperty.get();
    }
}
