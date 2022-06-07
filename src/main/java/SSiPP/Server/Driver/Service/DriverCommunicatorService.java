package SSiPP.Server.Driver.Service;

import SSiPP.Server.Driver.Driver;
import SSiPP.Server.Driver.util.Command;
import SSiPP.Server.Driver.util.ModuleReportChildren;
import SSiPP.Server.Driver.util.Status;
import SSiPP.Server.Driver.util.XMLUtil;
import SSiPP.Server.Server;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.*;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled Service to handle communication between server and Drivers
 * @author Lukas Andrea Bachler
 * @version 0.1
 */
public class DriverCommunicatorService extends ScheduledService<String> {
    /**
     * XML Document as currently known
     */
    private Document xml;
    /**
     * X-Path for executing xpath queries
     */
    private XPath xPath;
    /**
     * Managing server
     */
    private Server server;
    /**
     * Id of the running process
     */
    private String id;
    /**
     * Nodes that are currently running on hardware
     */
    private List<Node> currentNodes;
    /**
     * Should the currently running nodes be held
     */
    private boolean hold;
    /**
     * Should the currently running nodes be aborted
     */
    private boolean abort;
    /**
     * Should the currently running nodes be reset
     */
    private boolean reset;
    /**
     * Should the currently running nodes be restarted
     */
    private boolean restart;

    /**
     * Node counter for giving ids to the module_instances
     */
    private int nodeCounter;

    /**
     * Flag for if the Driver has finished
     */
    private boolean finished;

    /**
     * Format of time_started and time_finished
     */
    static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * Set the hold mode
     */
    public void hold() {
        hold = true;
    }

    /**
     * Set the abort mode
     */
    public void abort() {
        abort = true;
    }

    /**
     * Set the reset mode
     */
    public void reset() {
        reset = true;
    }

    /**
     * Set the restart mode
     */
    public void restart() {
        restart = true;
    }

    /**
     * Constructor for the Scheduled Service
     * @param server Parent Server handling this Service
     * @param xml XML Process to be handled
     */
    public DriverCommunicatorService(Server server, String xml) {
        System.out.println("Driver Communicator Service created for: " + xml);
        this.nodeCounter = 0;
        this.finished = false;
        try {
            this.xml = (DocumentBuilderFactory.newInstance()).newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            this.xPath = XPathFactory.newInstance().newXPath();
            setAllCommandsNothing();
            scale();
            this.server = server;
            this.id = findId();
            this.currentNodes = new ArrayList<>();
        } catch (SAXException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Task that is rerun in the scheduler
     * @return Result is the current XML correctly formatted for the GUI
     */
    @Override
    protected Task<String> createTask() {
        return new Task<String>() {
            @Override
            protected String call() {
                Platform.runLater(() -> {
                    try {
                        for (Node n : currentNodes)
                            updateFromDriver(n);

                        if (allCurrentRunningFinished()) {
                            setRedis();
                            startNextNode();
                            setRedis();
                        }

                        if (abort) {
                            abortCurrent();
                            setRedis();
                        }
                        else if (reset) {
                            resetCurrent();
                            setRedis();
                        }
                        else if (restart) {
                            restartCurrent();
                            setRedis();
                        }
                        else if (hold) {
                            holdCurrent();
                            setRedis();
                        }
                    } catch (RuntimeException e) {
                        System.out.println("Dcs call(): " + e.getMessage());
                    }
                });
                return getXmlStringLiteral();
            }
        };
    }

    /**
     * @return ID of loaded XML
     * @throws XPathExpressionException
     */
    private String findId() throws XPathExpressionException {
        XPathExpression expression = xPath.compile("/" + XMLUtil.TAG_PROCESS);
        return ((Node)expression.evaluate(xml, XPathConstants.NODE)).getAttributes()
                .getNamedItem(XMLUtil.ATTRIBUTE_ID.toString()).getNodeValue();
    }

    /**
     * @return Full XML Document in String format
     */
    private String getXmlString() {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer;
        try {
            transformer = transformerFactory.newTransformer();
            StringWriter sw = new StringWriter();
            StreamResult result = new StreamResult(sw);
            DOMSource source = new DOMSource(xml);
            transformer.transform(source, result);
            return result.getWriter().toString();
        } catch (TransformerConfigurationException e) {
            throw new RuntimeException(e);
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return Full XML Document in String format with Commands and Status in literal format
     */
    private String getXmlStringLiteral() {
        String result = getXmlString();
        for (Command command : Command.values()) {
            String commandString = ModuleReportChildren.COMMAND.toString();
            result = result.replace("<" + commandString + ">" + Command.getNum(command) + "</" + commandString + ">",
                    "<" + commandString + ">" + command.toString() + "</" + commandString + ">");
        }
        return result;
    }

    /**
     * Sets all Commands in the module_instance_report s to "do nothing"
     * @throws XPathExpressionException
     */
    private void setAllCommandsNothing() throws XPathExpressionException {
        XPathExpression expression = xPath.compile("//" + XMLUtil.TAG_MODULE_INSTANCE_REPORT + "/"
                + ModuleReportChildren.COMMAND);
        NodeList commands = (NodeList) expression.evaluate(xml, XPathConstants.NODESET);
        for (int i = 0; i < commands.getLength(); i++)
            commands.item(i).setTextContent(String.valueOf(Command.getNum(Command.CMD_NOTHING)));
    }

    /**
     * Aborts all currently running modules
     */
    private void abortCurrent() {
        for (Node n : currentNodes) {
            Node moduleInstanceReport = findNodeByName(n.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
            findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_ABORT)));
        }
        abort = false;
    }

    /**
     * Holds all currently running modules
     */
    private void holdCurrent() {
        for (Node n : currentNodes) {
            Node moduleInstanceReport = findNodeByName(n.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
            findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_HOLD)));
        }
    }

    /**
     * Resets all currently running modules
     */
    private void resetCurrent() {
        for (Node n : currentNodes) {
            Node moduleInstanceReport = findNodeByName(n.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
            findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_RESET)));
        }
    }

    /**
     * Restarts all currently running modules
     */
    private void restartCurrent() {
        for (Node n : currentNodes) {
            Node moduleInstanceReport = findNodeByName(n.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
            findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_RESTART)));
        }
    }

    /**
     * Checks if all modules marked as currently running have finished
     * @return true, if all currently running have STATE: STAT_COMPLETE, false otherwise
     */
    private boolean allCurrentRunningFinished() {
        for (Node n : currentNodes) {
            Node moduleInstanceReport = findNodeByName(n.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
            if (findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.TIME_FINISHED.toString()).getTextContent()
                    .isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Checks if all Children of a given node have finished
     * @param node node for which children are to be checked
     * @return true, if all children have finished, false otherwise
     */
    private boolean allNodeChildrenFinished(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node n = node.getChildNodes().item(i);
            if (n.getNodeName().compareTo(XMLUtil.TAG_PARALLEL.toString()) == 0 && !allNodeChildrenFinished(n))
                return false;
            if (n.getNodeName().compareTo(XMLUtil.TAG_MODULE_INSTANCE.toString()) == 0 &&
                    (findNodeByName(findNodeByName(n.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString()).getChildNodes(),
                    ModuleReportChildren.TIME_FINISHED.toString()).getTextContent()
                            .isEmpty()))
                return false;
        }
        return true;
    }

    /**
     * Checks which node or nodes are to be run next and starts those
     * Stops the Service if reached the end of the process
     */
    private void startNextNode() {
        if (!allCurrentRunningFinished())
            return;

        Node parent;
        if (currentNodes.size() > 0)
            parent = currentNodes.get(0).getParentNode();
        else {
            try {
                XPathExpression expression = xPath.compile("/" + XMLUtil.TAG_PROCESS);
                parent = (Node) expression.evaluate(xml, XPathConstants.NODE);
            } catch (XPathExpressionException e) {
                throw new RuntimeException(e);
            }
        }
        currentNodes.clear();

        if (parent.getNodeName().compareTo(XMLUtil.TAG_PROCESS.toString()) == 0 && allNodeChildrenFinished(parent)) {
            this.finished = true;
            this.cancel();
        }
        else if (!allNodeChildrenFinished(parent)) {
            startNextChild(parent);
        } else if (allNodeChildrenFinished(parent)) {
            do {
                parent = parent.getParentNode();
            } while (allNodeChildrenFinished(parent) && parent.getNodeName().compareTo(XMLUtil.TAG_PROCESS.toString()) != 0);
            if (parent.getNodeName().compareTo(XMLUtil.TAG_PROCESS.toString()) == 0 &&allNodeChildrenFinished(parent)) {
                this.cancel();
                this.finished = true;
            }
            else
                startNextChild(parent);
        }
    }

    /**
     * Starts next child of a given parent node
     * @param parent of children which are to be started
     */
    private void startNextChild(Node parent) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            Node cur = parent.getChildNodes().item(i);
            Node moduleInstanceReport = null;
            if (cur.getNodeName().compareTo(XMLUtil.TAG_PARALLEL.toString()) == 0 &&
                !allNodeChildrenFinished(cur)) {
                startParallel(cur);
                return;
            }
            else
                moduleInstanceReport = findNodeByName(cur.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
            if (moduleInstanceReport != null) {
                String status = findNodeByName(moduleInstanceReport.getChildNodes(),ModuleReportChildren.STATUS.toString())
                        .getTextContent();
                if (status.compareTo(Status.STAT_IDLE.toString()) == 0 || status.compareTo("") == 0) {
                    startNode(parent.getChildNodes().item(i));
                    return;
                } else if (status.compareTo(Status.STAT_COMPLETE.toString()) != 0)
                    return;
            }
        }
    }

    /**
     * Starts the given node
     * @param node to be started
     */
    private void startNode(Node node) {
        if (node == null)
            return;

        if (node.getNodeName().compareTo(XMLUtil.TAG_PARALLEL.toString()) == 0) {
            startParallel(node);
            return;
        }

        if (node.getNodeName().compareTo(XMLUtil.TAG_MODULE_INSTANCE.toString()) != 0)
            return;

        String driverType = node.getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_DRIVER.toString()).getNodeValue();


        for (Driver driver : server.getDrivers()) {
            if (driver.getType().compareTo(driverType) == 0) {
                ((Element)node).setAttribute(XMLUtil.ATTRIBUTE_ID.toString(), String.valueOf(nodeCounter));

                Node moduleInstanceReport = findNodeByName(node.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
                findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.TIME_STARTED.toString())
                        .setTextContent(dateTimeFormatter.format(LocalDateTime.now()));
                findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                        .setTextContent(String.valueOf(Command.getNum(Command.CMD_START)));
                currentNodes.add(node);
                driver.start(id + "_" + nodeCounter++);
                return;
            }
        }
        Node moduleInstanceReport = findNodeByName(node.getChildNodes(), XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString());
        findNodeByName(moduleInstanceReport.getChildNodes(), XMLUtil.TAG_ERROR.toString())
                .setTextContent("ERROR");
        findNodeByName(moduleInstanceReport.getChildNodes(), XMLUtil.TAG_ERRORMESSAGE.toString())
                .setTextContent("No corresponding driver found for: " + driverType);
        throw new RuntimeException("No corresponding driver found for: " + driverType);
    }

    /**
     * Starts a parallel node
     * @param node parallel for which all children should be started simultaneously
     */
    private void startParallel(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++)
            startNode(node.getChildNodes().item(i));
    }

    /**
     * Updates a single module instance from driver side
     * @param destination node into which values are to be inserted
     * @param source node from which values are to be taken
     */
    private void updateModuleInstanceFromDriver(Node destination, Node source) {
        if (source.getNodeName().compareTo(XMLUtil.TAG_PARALLEL.toString()) == 0) {
            updateParallelFromDriver(destination, source);
            return;
        }
        for (int i = 0; i < source.getChildNodes().getLength(); i++) {
            Node sourceNode = source.getChildNodes().item(i);
            if (sourceNode.getNodeName().compareTo(XMLUtil.TAG_MODULE_INSTANCE_REPORT.toString()) == 0)
                updateModuleInstanceReportFromDriver(findNodeByName(destination.getChildNodes(), sourceNode.getNodeName()),
                        sourceNode);
            else if (sourceNode.getNodeName().compareTo(ModuleReportChildren.REPORT.toString()) == 0)
                updateNodeValue(findNodeByName(destination.getChildNodes(), sourceNode.getNodeName(),
                        sourceNode.getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_NAME.toString()).getNodeValue()), sourceNode);
        }
    }

    /**
     * Updates a parallel node from driver side
     * @param destination parallel node into which values are to be inserted
     * @param source parallel node from which values are to be taken
     */
    private void updateParallelFromDriver(Node destination, Node source) {
        for (int i = 0; i < destination.getChildNodes().getLength() && i < source.getChildNodes().getLength(); i++) {
            if (source.getNodeName().compareTo(XMLUtil.TAG_PARALLEL.toString()) == 0)
                updateParallelFromDriver(destination.getChildNodes().item(i), source.getChildNodes().item(i));
            else
                updateModuleInstanceFromDriver(findModuleInstanceById(xml,
                        source.getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_ID.toString()).getNodeValue()),
                        source.getChildNodes().item(i));
        }
    }

    /**
     * Updates a module instance report from driver side
     * @param destinationModuleReport module instance report node into which values are to be inserted
     * @param sourceModuleReport module instance report node from which values are to be taken
     */
    private void updateModuleInstanceReportFromDriver(Node destinationModuleReport, Node sourceModuleReport) {
        for (int i = 0; i < sourceModuleReport.getChildNodes().getLength(); i++) {
            Node source = sourceModuleReport.getChildNodes().item(i);
            String sourceNodeName = source.getNodeName();
            Node destination = findNodeByName(destinationModuleReport.getChildNodes(), sourceNodeName);
            if (sourceNodeName.compareTo(ModuleReportChildren.MESSAGE.toString()) == 0
                    || sourceNodeName.compareTo(ModuleReportChildren.ERROR_MESSAGE.toString()) == 0
                    || sourceNodeName.compareTo(ModuleReportChildren.ERROR.toString()) == 0
                    || (sourceNodeName.compareTo(ModuleReportChildren.COMMAND.toString()) == 0
                            && destination.getTextContent().compareTo(String.valueOf(Command.getNum(Command.CMD_RESET))) != 0))
                updateNodeValue(destination, source);
            else if (sourceNodeName.compareTo(ModuleReportChildren.STATUS.toString()) == 0) {
                if (!source.getTextContent().isEmpty()) {
                    if (Integer.valueOf(source.getTextContent()) == Status.getNum(Status.STAT_COMPLETE)
                            && destination.getTextContent().compareTo(Status.STAT_COMPLETE.toString()) != 0) {
                        LocalDateTime now = LocalDateTime.now();
                        findNodeByName(destinationModuleReport.getChildNodes(), ModuleReportChildren.TIME_FINISHED.toString())
                                .setTextContent(dateTimeFormatter.format(now));
                        findNodeByName(destinationModuleReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                                .setTextContent(String.valueOf(Command.getNum(Command.CMD_RESET)));
                    }
                    destination.setTextContent(Status.values()[Integer.valueOf(source.getTextContent())].toString());
                }
            }
        }
    }

    /**
     * Updates the text contents of the destination node from the source node
     * @param destination
     * @param source
     */
    private void updateNodeValue(Node destination, Node source) {
        if (source != null && destination != null && !source.getTextContent().isEmpty())
            destination.setTextContent(source.getTextContent());
    }

    /**
     * Finds a node in a node list
     * @param source list to be searched through
     * @param nameToMatch name that should be matched
     * @return matching node, or null if none was found
     */
    private Node findNodeByName(NodeList source, String nameToMatch) {
        return findNodeByName(source, nameToMatch, "");
    }

    /**
     * Finds a node in a node list
     * @param source list to be searched
     * @param nameToMatch name that should be matched
     * @param attributeNameToMatch attribute name that has to be matched additionally
     * @return matching node, or null if none was found
     */
    private Node findNodeByName(NodeList source, String nameToMatch, String attributeNameToMatch) {
        for (int i = 0; i < source.getLength(); i++) {
            if (source.item(i).getNodeName().compareTo(nameToMatch) == 0)
                if (attributeNameToMatch.length() == 0
                        || source.item(i).getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_NAME.toString())
                        .getNodeValue().compareTo(attributeNameToMatch) == 0)
                    return source.item(i);
        }
        return null;
    }

    /**
     * Fetches a module instance in the source by the given id
     * @param source
     * @param id
     * @return
     */
    private Node findModuleInstanceById(Document source, String id) {
        try {
            XPathExpression expression = xPath.compile("//" + XMLUtil.TAG_MODULE_INSTANCE +
                    "[@" + XMLUtil.ATTRIBUTE_ID + "='" + id + "']");
            return (Node) expression.evaluate(source, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Fetches Node information from Redis and updates the destination node
     * @param destination
     */
    private void updateFromDriver(Node destination) {
        try {
            String moduleInstanceId = destination.getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_ID.toString()).getNodeValue();
            Document driverXml = (DocumentBuilderFactory.newInstance()).newDocumentBuilder()
                    .parse(new InputSource(new StringReader(server.getRedis().get("ssipp_" + id + "_" + moduleInstanceId))));
            updateModuleInstanceFromDriver(destination, findModuleInstanceById(driverXml, moduleInstanceId));
        } catch (SAXException e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        } catch (ParserConfigurationException e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes all currently running nodes to redis.
     */
    private void setRedis() {
        for (Node n : currentNodes) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer;
            try {
                transformer = transformerFactory.newTransformer();
                StringWriter sw = new StringWriter();
                StreamResult result = new StreamResult(sw);
                DOMSource source = new DOMSource(n);
                transformer.transform(source, result);
                String moduleInstanceId = id + "_" + n.getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_ID.toString()).getNodeValue();
                server.getRedis().set("ssipp_" + moduleInstanceId, result.getWriter().toString());
            } catch (TransformerConfigurationException e) {
                throw new RuntimeException(e);
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Scales all parameters according to the scale attribute in the process
     */
    private void scale() {
        try {
            XPathExpression expression = xPath.compile("/" + XMLUtil.TAG_PROCESS);
            Node process = (Node) expression.evaluate(xml, XPathConstants.NODE);
            String scale = process.getAttributes().getNamedItem(XMLUtil.ATTRIBUTE_SCALE.toString()).getNodeValue();

            if (scale.length() == 0)
                return;

            float scaleValue = Float.valueOf(scale);

            expression = xPath.compile("//" + XMLUtil.TAG_PARAM + "[@" + XMLUtil.ATTRIBUTE_NAME + "='" +
                    XMLUtil.QUANTITY + "']");
            NodeList nodeList = (NodeList) expression.evaluate(xml, XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node n = nodeList.item(i);
                n.setTextContent(String.valueOf(Float.valueOf(n.getTextContent()) * scaleValue));
            }
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return -1 if id is invalid, else id converted to int
     */
    public int getId() {
        if (this.id.isEmpty())
            return -1;
        try {
            return Integer.valueOf(this.id);
        } catch (NumberFormatException e) {
            return -1;
        }

    }

    /**
     * @return whether process has finished or not
     */
    public boolean isFinished() {
        return finished;
    }
}
