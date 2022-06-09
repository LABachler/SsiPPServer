package SSiPP.Server;

import SSiPP.Server.Driver.Driver;
import SSiPP.Server.Driver.Service.DriverCommunicatorService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Duration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import redis.clients.jedis.Jedis;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Server responsible for handling API and Drivers
 */
public class Server {
    /**
     * SSiPP root directory
     */
    static final String SSIPP_DIR_PATH = System.getProperty("user.home") + File.separator + ".ssipp";
    /**
     * process templates directory
     */
    static final String PROCESS_TEMPLATE_PATH = SSIPP_DIR_PATH + File.separator + "process_templates";
    /**
     * processes directory
     */
    static final String PROCESSES_PATH = SSIPP_DIR_PATH + File.separator + "processes";
    /**
     * module instances directory
     */
    static final String MODULE_INSTANCES_PATH = SSIPP_DIR_PATH + File.separator + "module_instances";
    /**
     * driver xml information file
     */

    static final String MODULES_PATH = SSIPP_DIR_PATH + File.separator + "modules";
    static final String DRIVERS_PATH = SSIPP_DIR_PATH + File.separator + "drivers.xml";
    /**
     * driver template
     */
    static final String DRIVERS_TEMPLATE = "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>" +
            "<drivers></drivers>";
    /**
     * module report xml template
     */
    static final String MODULE_REPORT_PATH = SSIPP_DIR_PATH + File.separator + "module_report.xml";
    /**
     * module report template content
     */
    static final String MODULE_REPORT_TEMPLATE_CONTENT = "" +
            "<module_instance_report>" +
            "<time_started></time_started>" +
            "<time_finished></time_finished>" +
            "<STATUS/>" +
            "<COMMAND/>" +
            "<MESSAGE/>"+
            "<ERROR/>" +
            "<E_MSG/>" +
            "</module_instance_report>";
    /**
     * List of known drivers
     */
    private final ObservableList<Driver> drivers;
    /**
     * Redis connection
     */
    private final Jedis redis;
    private File workingDir;
    /**
     * document builder for parsing xml
     */
    private DocumentBuilder documentBuilder;
    /**
     * x path needed for creating x path expressions
     */
    private XPath xPath;

    static final int SERVER_PORT = 7000;

    public Server() {
        drivers = FXCollections.observableArrayList();
        redis = new Jedis();
        checkAndCreateFilesAndDirectories();
        try {
            documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        xPath = XPathFactory.newInstance().newXPath();
        loadDriversFromFileSystem();

        /**
         * http server on which the api is running
         */
        HttpServer apiServer = null;
        try {
            apiServer = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        API processesApi = new API(apiServer, this);
        apiServer = processesApi.getServer();
        apiServer.setExecutor(null);
        apiServer.start();
    }


    private void checkAndCreateFilesAndDirectories(){
        workingDir = new File(SSIPP_DIR_PATH);
        if (!workingDir.exists()) {
            workingDir.mkdir();
        }
        File processTemplates = new File(PROCESS_TEMPLATE_PATH);
        if (!processTemplates.exists()) {
            processTemplates.mkdir();
        }
        File processes = new File(PROCESSES_PATH);
        if (!processes.exists()) {
            processes.mkdir();
        }
        File moduleInstances = new File(MODULE_INSTANCES_PATH);
        if (!moduleInstances.exists()) {
            moduleInstances.mkdir();
        }
        File moduleReport = new File(MODULE_REPORT_PATH);
        if (!moduleReport.exists()) {
            try {
                moduleReport.createNewFile();
                FileWriter fw = new FileWriter(moduleReport);
                fw.write(MODULE_REPORT_TEMPLATE_CONTENT);
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        File drivers = new File(DRIVERS_PATH);
        if (!drivers.exists()) {
            try {
                drivers.createNewFile();
                FileWriter fw = new FileWriter(drivers);
                fw.write(DRIVERS_TEMPLATE);
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadDriversFromFileSystem() {
        try {
            File f = new File(DRIVERS_PATH);
            Document drivers = documentBuilder.parse(f);
            NodeList nodeList = (NodeList) (xPath.compile("//drivers/driver")).evaluate(drivers, XPathConstants.NODESET);
            for (int iDriversList = 0; iDriversList < nodeList.getLength(); iDriversList++) {
                NodeList childList = nodeList.item(iDriversList).getChildNodes();
                String type = "", path = "";
                for (int iChildList = 0; iChildList < childList.getLength(); iChildList++) {
                    if (childList.item(iChildList).getNodeName().equals("type"))
                        type = childList.item(iChildList).getChildNodes().item(0).getNodeValue();
                    else if (childList.item(iChildList).getNodeName().equals("path"))
                        path = childList.item(iChildList).getChildNodes().item(0).getNodeValue();
                }
                if (type != null && path != null)
                    this.drivers.add(new Driver(type, path));
            }
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public void addDriver(String type, String path) {
        drivers.add(new Driver(type, path));
    }

    public ObservableList<Driver> getDrivers() {
        return drivers;
    }

    private void writeToRedis(int processId, String string) {
        redis.set("ssipp_" + processId, string);
    }

    private String readFromRedis(int processId) {
        return redis.get("ssipp_" + processId);
    }

    public void saveInfoToFileSystem() {
        saveDriverInfoToFileSystem();
    }

    private void saveDriverInfoToFileSystem() {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(DRIVERS_TEMPLATE.getBytes(StandardCharsets.UTF_8));
            Document driversDoc = documentBuilder.parse(inputStream);
            Node root = (Node)xPath.compile("//drivers").evaluate(driversDoc, XPathConstants.NODE);
            drivers.stream().forEach(driver -> {
                Node toAppend = driversDoc.createElement("driver");

                Node type = driversDoc.createElement("type");
                type.setTextContent(driver.getType());
                Node path = driversDoc.createElement("path");
                path.setTextContent(driver.getPath());

                toAppend.appendChild(type);
                toAppend.appendChild(path);

                root.appendChild(toAppend);
            });
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            FileWriter fw = new FileWriter(new File(DRIVERS_PATH));
            StreamResult result = new StreamResult(fw);
            DOMSource source = new DOMSource(driversDoc);
            transformer.transform(source, result);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    public Jedis getRedis() {
        return redis;
    }

}