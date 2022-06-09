package SSiPP.Server;

import SSiPP.Server.Driver.Service.DriverCommunicatorService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.util.Duration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class API {
    private XPath xPath = XPathFactory.newInstance().newXPath();
    private HttpServer server;
    private ArrayList<DriverCommunicatorService> dcs = new ArrayList<>();
    private Server Server;

    public API(HttpServer server, Server Server) {
        this.server = api(server);
        this.Server = Server;
    }

    public HttpServer getServer() {
        return server;
    }

    public String getDcsLastValue(int id) throws Exception {
        for(int i = 0; i < dcs.size(); i++)
        {
            if(dcs.get(i).getId() == id){
                if(!dcs.get(i).isFinished()) {
                    try {
                        return (dcs.get(i).getLastValue() == null ? "-" : dcs.get(i).getLastValue());
                    } catch (Exception e) {
                        System.out.println("getRunningProcess: " + e.getMessage());
                    }
                    return "-";
                }else{
                    saveFinishedProcess(dcs.get(i).getLastValue());
                    dcs.remove(i);
                    return "DONE";
                }
            }

        }
        return null;
    }

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
            try{
                exchangeData(exchange,"GET","get_running_process");
            } catch(Exception e) {
                e.printStackTrace();
            }
        }));

        server.createContext("/SSiPP/currently_running_processes", (exchange ->
        {
            try{
                exchangeData(exchange,"GET","currently_running_processes");
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
                    case "running_process" -> startOrStopProcess(dataString);
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
                    case "currently_running_processes" -> getRunningProcesses();
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

    private String getRunningProcesses(){
        StringBuilder runningProcesses = new StringBuilder("<runningProcesses>");

        for(int i = 0; i < dcs.size(); i++){
            runningProcesses.append("<process_id>"+ dcs.get(i).getId()+"</process_id>");
        }
        runningProcesses.append("</runningProcesses>");
        return runningProcesses.toString();
    }

    private void startOrStopProcess(String xmlData) throws Exception {
        String[] files = new File(SSiPP.Server.Server.PROCESS_TEMPLATE_PATH).list();
        Document doc = loadXMLFromString(xmlData);
        String id = parseAttribute(doc, "//startProcess/@id");
        String scale = parseAttribute(doc,"//startProcess/@scale");
        if(scale.isEmpty()){
            for(int i = 0; i < dcs.size(); i++){
                if(dcs.get(i).getId() == Integer.parseInt(id)) {
                    saveFinishedProcess(dcs.get(i).getLastValue());
                    dcs.get(i).abort();
                    dcs.remove(i);
                    return;
                }
            }
        }
        else {
            StringBuilder processTemplate = new StringBuilder();
            for (int i = 0; i < files.length; i++) {
                if (files[i].contains(id)) {
                    File myObj = new File(SSiPP.Server.Server.PROCESS_TEMPLATE_PATH + File.separator + files[i]);
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
            finalProcess = finalProcess.replaceFirst("[\\\r\\\n]+", "");

            String finalProcessXML = finalProcess;
            Platform.runLater(() -> {
                dcs.add(new DriverCommunicatorService(Server, finalProcessXML));
                dcs.get(dcs.size() - 1).setPeriod(Duration.millis(250));
                dcs.get(dcs.size() - 1).start();
            });
        }
    }

    private void saveFinishedProcess(String finishedProcess) throws Exception {
        Document finishedProcessDoc = loadXMLFromString(finishedProcess);
        NodeList nodes = (NodeList) xPath.evaluate("//process/@id", finishedProcessDoc, XPathConstants.NODESET);
        Node idNode = nodes.item(0);
        idNode.setNodeValue(String.valueOf(findStartedProcessId()));
        String name = parseAttribute(finishedProcessDoc, "//process/@name");
        String process = getStringFromDoc(finishedProcessDoc);
        process = process.replace("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>", "");
        process = process.replaceFirst("[\\\r\\\n]+","");
        createFile(process, SSiPP.Server.Server.PROCESSES_PATH + File.separator + findStartedProcessId() +"_" + name + ".txt");

    }
    private int findStartedProcessId(){
        String[] files = new File(SSiPP.Server.Server.PROCESSES_PATH).list();
        int i = 0;
        for(i = 0; i < files.length; i++){
            if(!files[i].contains(String.valueOf(i))){
                return i;
            }
        }
        return i;
    }
    private String getProcessTemplates() throws IOException {
        String[] files = new File(SSiPP.Server.Server.PROCESS_TEMPLATE_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<processes>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(SSiPP.Server.Server.PROCESS_TEMPLATE_PATH + File.separator + files[i]);
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
        String[] files = new File(SSiPP.Server.Server.MODULE_INSTANCES_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<module_instances>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(SSiPP.Server.Server.MODULE_INSTANCES_PATH + File.separator + files[i]);
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
        String[] files = new File(SSiPP.Server.Server.MODULES_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<modules>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(SSiPP.Server.Server.MODULES_PATH + File.separator + files[i]);
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
        String[] files = new File(SSiPP.Server.Server.PROCESSES_PATH).list();
        StringBuilder processTemplates = new StringBuilder("<processes>");
        for(int i = 0; i< files.length; i++) {
            File myObj = new File(SSiPP.Server.Server.PROCESSES_PATH + File.separator + files[i]);
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
        createFile(data,SSiPP.Server.Server.PROCESS_TEMPLATE_PATH + File.separator + fileID + "_" + fileName+".txt");
    }
    private void addModuleInstance(String data) throws Exception {
        Document doc = loadXMLFromString(data);
        String fileName = parseAttribute( doc, "//module_instance/@datablock_name");
        createFile(data,SSiPP.Server.Server.MODULE_INSTANCES_PATH+ File.separator + fileName+".txt");
    }
    private void addModule(String data) throws Exception {
        Document doc = loadXMLFromString(data);
        String fileName = parseAttribute( doc, "//module/@name");
        createFile(data,SSiPP.Server.Server.MODULES_PATH + File.separator + fileName+".txt");
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

}
