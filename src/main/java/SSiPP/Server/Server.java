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

    ArrayList<DriverCommunicatorService> dcs = new ArrayList<DriverCommunicatorService>();

    public String getDcsLastValue(int id) {
        for(int i = 0; i < dcs.size(); i++)
        {
            if(dcs.get(i).getId() == id){
                try{
                    return (dcs.get(i).getLastValue()== null ? "-" : dcs.get(i).getLastValue());
                }catch(Exception e){
                    System.out.println("getRunningProcess: " + e.getMessage());
                }
                return "-";
            }
        }
        return null;
    }

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

        apiServer = api(apiServer);
        apiServer.setExecutor(null);
        apiServer.start();
    }

    /**
     * creates contexts for URI
     * calls corresponding method depending on URI
     * on which the api call was made
     * @param server http server on which the api is running
     * @return server
     */
    public HttpServer api(HttpServer server) {

        server.createContext("/SSiPP/process_templates", (exchange ->
        {
            try {
                exchangeData(exchange, "GET", "process_templates");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/historical_processes", (exchange ->
        {
            try {
                exchangeData(exchange, "GET", "historical_processes");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/module_instances", (exchange ->
        {
            try {
                exchangeData(exchange, "GET", "module_instances");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/modules", (exchange ->
        {
            try {
                exchangeData(exchange, "GET", "modules");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/add_process_template", (exchange ->
        {
            try {
                exchangeData(exchange, "POST", "add_process_template");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/add_module_instance", (exchange ->
        {
            try {
                exchangeData(exchange, "POST", "add_module_instance");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/add_module", (exchange ->
        {
            try {
                exchangeData(exchange, "POST", "add_module");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/running_process", (exchange ->
        {
            try{
                exchangeData(exchange,"POST","running_process");
            } catch(Exception e) {
                e.printStackTrace();
            }
        }));
        server.createContext("/SSiPP/get_running_process", (exchange ->
        {
            System.out.println(exchange.getRequestURI().getRawQuery());
            try{
                exchangeData(exchange,"GET","get_running_process");
            } catch(Exception e) {
                e.printStackTrace();
            }
        }));
        return server;
    }

    /**
     * allows api call only from a trusted source (our web page)
     * if api call allowed it sends data read from files saved on a computer
     * or receives data and saves that data in a file system
     * @param exchange data to be sent or received
     * @param method GET or POST method for api call
     * @param URI api URI
     * @throws Exception :
     */
    private void exchangeData(HttpExchange exchange, String method, String URI) throws Exception {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:63343");

        if(method.equals("POST")) {

            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                InputStream data = exchange.getRequestBody();
                BufferedReader inputStream = new BufferedReader(new InputStreamReader(data));
                String dataString = inputStream.readLine();
                switch (URI) {
                    case "add_process_template" -> addProcessTemplate(dataString);
                    case "add_module_instance" -> addModuleInstance(dataString);
                    case "add_module" -> addModule(dataString);
                    case "running_process" -> startProcess(dataString);
                }
                data.close();

                exchange.sendResponseHeaders(200, dataString.length());
                OutputStream responseBody = exchange.getResponseBody();
                responseBody.write(dataString.getBytes());
                responseBody.close();
            } else {
                exchange.sendResponseHeaders(405, -1);// 405 Method Not Allowed
            }
        }else if(method.equals("GET")){
            if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if ("GET".equals(exchange.getRequestMethod())) {
                System.out.println(exchange.getRequestURI());
                String respText = switch (URI) {
                    case "process_templates" -> getProcessTemplates();
                    case "module_instances" -> getModuleInstances();
                    case "modules" -> getModules();
                    case "historical_processes" -> getHistoricalProcesses();
                    case "get_running_process" -> getDcsLastValue(Integer.parseInt(exchange.getRequestURI().toString().replaceAll("[\\D]", "")));
                    default -> "";
                };
                exchange.sendResponseHeaders(200, respText.getBytes().length);
                OutputStream output = exchange.getResponseBody();
                output.write(respText.getBytes());
                output.flush();
            }
            else {
                exchange.sendResponseHeaders(405, -1);// 405 Method Not Allowed
            }
        }
        exchange.close();
    }


    /*private String getRunningProcess(){
        try{
            return (getDcsValue()== null ? "-" : getDcs().getLastValue());
        }catch(Exception e){
            System.out.println("getRunningProcess: " + e.getMessage());
        }
        return "-";
    }*/
    private void startProcess(String xmlData) throws Exception {
        String[] files = new File(PROCESS_TEMPLATE_PATH).list();
        Document doc = loadXMLFromString(xmlData);
        String id = parseAttribute(doc, "//startProcess/@id");
        String scale = parseAttribute(doc,"//startProcess/@scale");
        StringBuilder processTemplate = new StringBuilder();
        for(int i = 0; i< files.length; i++) {
            if(files[i].contains(id)){
                File myObj = new File(PROCESS_TEMPLATE_PATH + File.separator + files[i]);
                Scanner myReader = new Scanner(myObj);
                while (myReader.hasNextLine()) {
                    String data = myReader.nextLine();
                    processTemplate.append(data);
                }
            }
        }
        Document startedProcessDoc = loadXMLFromString(processTemplate.toString());
        NodeList scaleNodes = (NodeList) xPath.evaluate("//process/@scale", startedProcessDoc, XPathConstants.NODESET);
        Node scaleNode = scaleNodes.item(0);
        scaleNode.setNodeValue(String.valueOf(scale));
        String finalProcess = getStringFromDoc(startedProcessDoc);
        finalProcess = finalProcess.replace("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>", "");
        finalProcess = finalProcess.replaceFirst("[\\\r\\\n]+","");
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xPath.evaluate("//process/@id", startedProcessDoc, XPathConstants.NODESET);
        String name = parseAttribute(startedProcessDoc, "//process/@name");
        Node idNode = nodes.item(0);
        idNode.setNodeValue(String.valueOf(findStartedProcessId()));
        String process = getStringFromDoc(startedProcessDoc);
        process = process.replace("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>", "");
        process = process.replaceFirst("[\\\r\\\n]+","");
        createFile(process, PROCESSES_PATH + File.separator + findStartedProcessId() +"_" + name + ".txt");


        String finalProcessXML = finalProcess;
        Platform.runLater(() -> {
            dcs.add(new DriverCommunicatorService(this, finalProcessXML));
            dcs.get(dcs.size() - 1).setPeriod(Duration.millis(250));
            dcs.get(dcs.size() - 1).start();
            //setDcs(new DriverCommunicatorService(this, finalProcess));
            //getDcs().setPeriod(Duration.millis(250));
            //getDcs().start();
        });
    }
    private int findStartedProcessId(){
        String[] files = new File(PROCESSES_PATH).list();
        int i = 0;
        for(i = 0; i < files.length; i++){
            if(!files[i].contains(String.valueOf(i))){
                return i;
            }
        }
        return i;
    }
    private String getProcessTemplates() throws IOException {
        String[] files = new File(PROCESS_TEMPLATE_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<processes>");
        for(int i = 0; i< files.length; i++) {
             File myObj = new File(PROCESS_TEMPLATE_PATH + File.separator + files[i]);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                processTemplates.append(data);
            }
            myReader.close();
        }
        processTemplates.append("</processes>");
        return processTemplates.toString();
    }
    private String getModuleInstances() throws FileNotFoundException {
        String[] files = new File(MODULE_INSTANCES_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<module_instances>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(MODULE_INSTANCES_PATH + File.separator + files[i]);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                processTemplates.append(data);
            }
            myReader.close();
        }
        processTemplates.append("</module_instances>");
        return processTemplates.toString();
    }
    private String getModules() throws FileNotFoundException {
        String[] files = new File(MODULES_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<modules>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(MODULES_PATH + File.separator + files[i]);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                processTemplates.append(data);
            }
            myReader.close();
        }
        processTemplates.append("</modules>");
        return processTemplates.toString();
    }
    private String getHistoricalProcesses() throws FileNotFoundException {
        String[] files = new File(PROCESSES_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<processes>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(PROCESSES_PATH + File.separator + files[i]);
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                String data = myReader.nextLine();
                processTemplates.append(data);
            }
            myReader.close();
        }
        processTemplates.append("</processes>");
        return processTemplates.toString();
    }
    private void addProcessTemplate(String data) throws Exception {
        Document doc = loadXMLFromString(data);
        String fileName = parseAttribute(doc, "//process/@name");
        String fileID = parseAttribute(doc,"//process/@id");
        createFile(data,PROCESS_TEMPLATE_PATH + File.separator + fileID + "_" + fileName+".txt");
    }
    private void addModuleInstance(String data) throws Exception {
        Document doc = loadXMLFromString(data);
        String fileName = parseAttribute( doc, "//module_instance/@datablock_name");
        createFile(data,MODULE_INSTANCES_PATH+ File.separator + fileName+".txt");
    }
    private void addModule(String data) throws Exception {
        Document doc = loadXMLFromString(data);
        String fileName = parseAttribute( doc, "//module/@name");
        createFile(data,MODULES_PATH + File.separator + fileName+".txt");
    }
    public static Document loadXMLFromString(String xml) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xml));
        return builder.parse(is);
    }
    public String getStringFromDoc(Document doc) throws TransformerException {

        DOMSource domSource = new DOMSource(doc);
        StringWriter writer = new StringWriter();
        StreamResult result = new StreamResult(writer);
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.transform(domSource, result);
        writer.flush();
        return writer.toString();
    }

    /**
     * @param doc xml document from which to parse
     * @param nodePath path to find the node
     * @return attribute value
     * @throws XPathExpressionException
     */
    private String parseAttribute(Document doc, String nodePath) throws XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xPath.evaluate(nodePath, doc, XPathConstants.NODESET);
        String value = "";
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            value = node.getTextContent();
        }
        return value;
    }
    private void createFile(String data, String path) throws IOException {
        File file = new File(path);
        file.createNewFile();
        FileWriter fw = new FileWriter(file);
        fw.write(data);
        fw.close();
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