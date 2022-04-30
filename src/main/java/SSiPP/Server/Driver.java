package SSiPP.Server;

import javafx.beans.property.SimpleStringProperty;

import java.io.File;

public class Driver {
    private SimpleStringProperty typeProperty;
    private SimpleStringProperty pathProperty;

    public Driver(String type, String path) {
        typeProperty = new SimpleStringProperty(type);
        pathProperty = new SimpleStringProperty(path);
    }

    public String getTypeProperty() {
        return typeProperty.get();
    }

    public SimpleStringProperty typePropertyProperty() {
        return typeProperty;
    }

    public void setTypeProperty(String typeProperty) {
        this.typeProperty.set(typeProperty);
    }

    public String getPathProperty() {
        return pathProperty.get();
    }

    public SimpleStringProperty pathPropertyProperty() {
        return pathProperty;
    }

    public void setPathProperty(String pathProperty) {
        this.pathProperty.set(pathProperty);
    }

    @Override
    public String toString() {
        return typeProperty.get() + ": " + pathProperty.get();
    }
}