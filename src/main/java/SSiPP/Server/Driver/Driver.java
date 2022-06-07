package SSiPP.Server.Driver;

import javafx.beans.property.SimpleStringProperty;

import java.io.IOException;

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

    /**
     * Checks extension and starts the corresponding program with id as parameter
     * @param id argument with which executable gets started
     */
    public void start(String id) {
        boolean isWindows = System.getProperty("os.name")
                   .toLowerCase().startsWith("windows");

        String path = getPath();

        if (path.endsWith(".exe")) {

            ProcessBuilder builder = new ProcessBuilder();

            if (isWindows) {
                builder.command(path, id);
            } else {
                //need wine support
                throw new UnsupportedOperationException("exe not supported for non windows systems");
            }
            try {
                Process process = builder.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if (path.endsWith(".js")) {

            ProcessBuilder builder = new ProcessBuilder();
            builder.command("node", path, id);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            /*try {
                Process process = builder.start();
            }   catch (IOException e) {
                e.printStackTrace();
            }*/
        }


    }











    @Override
    public String toString() {
        return typeProperty.get() + ": " + pathProperty.get();
    }
}
