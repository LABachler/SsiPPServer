package SSiPP.Server.Driver.Service;

import SSiPP.Server.Driver.Driver;
import SSiPP.Server.Driver.util.Command;
import SSiPP.Server.Driver.util.ModuleReportChildren;
import SSiPP.Server.Driver.util.Status;
import SSiPP.Server.Server;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import org.w3c.dom.Document;
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
import java.util.stream.Collectors;

public class DriverCommunicatorService extends ScheduledService<String> {
    private Document xml;
    private XPath xPath;
    private Server server;
    private String id;
    private List<Node> currentNodes;
    private boolean hold;
    private boolean abort;
    private boolean reset;
    private boolean restart;

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public void hold() {
        hold = true;
    }

    public void abort() {
        abort = true;
    }

    public void reset() {
        reset = true;
    }

    public void restart() {
        restart = true;
    }

    public DriverCommunicatorService(Server server, String xml) {
        System.out.println("Driver Communicator Service created for: " + xml);
        try {
            this.xml = (DocumentBuilderFactory.newInstance()).newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            setAllCommandsNothing();
            this.xPath = XPathFactory.newInstance().newXPath();
            this.server = server;
            this.id = findId();
            this.currentNodes = new ArrayList<>();
            String driverType = findDriverType();
            List<Driver> drivers = this.server.getDrivers().stream()
                    .filter(driver -> driver.getType().compareToIgnoreCase(driverType) == 0).collect(Collectors.toList());
            if (!drivers.isEmpty()) {
                server.getJedis().set("ssipp_" + id, getXmlString());
                drivers.get(0).start(Integer.valueOf(id));
            }
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

    @Override
    protected Task<String> createTask() {
        return new Task<String>() {
            @Override
            protected String call() throws Exception {
                Document driverXml = (DocumentBuilderFactory.newInstance()).newDocumentBuilder()
                        .parse(new InputSource(new StringReader(server.getJedis().get("ssipp_" + id))));
                updateDocFromDriver(driverXml);
                if (allCurrentRunningFinished())
                    startNextNode();
                if (abort)
                    abortCurrent();
                else if (reset)
                    resetCurrent();
                else if (restart)
                    restartCurrent();
                else if (hold)
                    holdCurrent();
                System.out.println("Driver Communicator Service run! xml: " + getXmlString());
                return getXmlStringLiteral();
            }
        };
    }

    private String findId() throws XPathExpressionException {
        XPathExpression expression = xPath.compile("/process");
        return ((Node)expression.evaluate(xml, XPathConstants.NODE)).getAttributes().getNamedItem("id").getNodeValue();
    }

    private String findDriverType() throws XPathExpressionException {
        XPathExpression expression = xPath.compile("/process");
        return ((Node)expression.evaluate(xml, XPathConstants.NODE)).getAttributes().getNamedItem("driver_type").getNodeValue();
    }

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

    private String getXmlStringLiteral() {
        String result = getXmlString();
        for (Command command : Command.values()) {
            String commandString = ModuleReportChildren.COMMAND.toString();
            result = result.replace("<" + commandString + ">" + Command.getNum(command) + "</" + commandString + ">",
                    "<" + commandString + ">" + command.toString() + "</" + commandString + ">");
        }
        return result;
    }

    private void setAllCommandsNothing() throws XPathExpressionException {
        XPathExpression expression = xPath.compile("//module_instance_report/" + ModuleReportChildren.COMMAND.toString());
        NodeList commands = (NodeList) expression.evaluate(xml, XPathConstants.NODE);
        for (int i = 0; i < commands.getLength(); i++)
            commands.item(i).setTextContent(String.valueOf(Command.getNum(Command.CMD_NOTHING)));
    }

    private void abortCurrent() {
        for (Node n : currentNodes)
            findNodeByName(n.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_ABORT)));
    }

    private void holdCurrent() {
        for (Node n : currentNodes)
            findNodeByName(n.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_HOLD)));
    }

    private void resetCurrent() {
        for (Node n : currentNodes)
            findNodeByName(n.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_RESET)));
    }

    private void restartCurrent() {
        for (Node n : currentNodes)
            findNodeByName(n.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                    .setTextContent(String.valueOf(Command.getNum(Command.CMD_RESTART)));
    }

    private boolean allCurrentRunningFinished() {
        for (Node n : currentNodes) {
            if (findNodeByName(n.getChildNodes(), ModuleReportChildren.STATUS.toString()).getTextContent()
                    .compareTo(Status.STAT_COMPLETE.toString()) != 0)
                return false;
        }
        return true;
    }

    private boolean allNodeChildrenFinished(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++) {
            Node n = node.getChildNodes().item(i);
            if (n.getNodeName().compareTo("parallel") == 0 && !allNodeChildrenFinished(n))
                return false;
            if (findNodeByName(findNodeByName(n.getChildNodes(), "module_instance_report").getChildNodes(),
                    ModuleReportChildren.STATUS.toString()).getTextContent()
                                .compareTo(Status.STAT_COMPLETE.toString()) != 0)
                return false;
        }
        return true;
    }

    private void startNextNode() {
        if (!allCurrentRunningFinished())
            return;

        Node parent = currentNodes.get(0).getParentNode().getParentNode();
        currentNodes.clear();

        if (parent.getNodeName().compareTo("process") == 0 && allNodeChildrenFinished(parent))
            System.out.println("STOP ME");
        else if (!allNodeChildrenFinished(parent)) {
            startNextChild(parent);
        } else if (allNodeChildrenFinished(parent)) {
            do {
                parent = parent.getParentNode();
            } while (allNodeChildrenFinished(parent) && parent.getNodeName().compareTo("process") != 0);
            if (parent.getNodeName().compareTo("process") == 0)
                System.out.println("STOP ME");
            else
                startNextChild(parent);
        }
    }

    private void startNextChild(Node parent) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            String status = findNodeByName(
                    findNodeByName(parent.getChildNodes().item(i).getChildNodes(), "module_instance_report")
                            .getChildNodes(),ModuleReportChildren.STATUS.toString())
                            .getTextContent();
            if (status.compareTo(Status.STAT_IDLE.toString()) == 0) {
                startNode(parent.getChildNodes().item(i));
                return;
            } else if (status.compareTo(Status.STAT_COMPLETE.toString()) != 0)
                return;
        }
    }

    private void startNode(Node node) {
        if (node == null)
            return;

        if (node.getNodeName().compareTo("parallel") == 0) {
            startParallel(node);
            return;
        }

        Node moduleInstanceReport = findNodeByName(node.getChildNodes(), "module_instance_report");
        findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.TIME_STARTED.toString())
                .setTextContent(dateTimeFormatter.format(LocalDateTime.now()));
        findNodeByName(moduleInstanceReport.getChildNodes(), ModuleReportChildren.COMMAND.toString())
                .setTextContent(String.valueOf(Command.getNum(Command.CMD_START)));
        currentNodes.add(moduleInstanceReport);
    }

    private void startParallel(Node node) {
        for (int i = 0; i < node.getChildNodes().getLength(); i++)
            startNode(node.getChildNodes().item(i));
    }

    private void updateDocFromDriver(Document source) {
        try {
            XPathExpression rootExpression = xPath.compile("/process");
            Node rootDest = (Node) rootExpression.evaluate(xml, XPathConstants.NODE);
            Node rootSource = (Node) rootExpression.evaluate(source, XPathConstants.NODE);
            for (int i = 0; i < rootDest.getChildNodes().getLength() && i < rootSource.getChildNodes().getLength(); i++)
                updateModuleInstanceFromDriver(rootDest.getChildNodes().item(i), rootSource.getChildNodes().item(i));
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateModuleInstanceFromDriver(Node destination, Node source) {
        if (source.getNodeName().compareTo("parallel") == 0) {
            updateParallelFromDriver(destination, source);
            return;
        }
        for (int i = 0; i < source.getChildNodes().getLength(); i++) {
            Node sourceNode = source.getChildNodes().item(i);
            if (sourceNode.getNodeName().compareTo("module_instance_report") == 0)
                updateModuleInstanceReportFromDriver(findNodeByName(destination.getChildNodes(), sourceNode.getNodeName()),
                        sourceNode);
            else if (sourceNode.getNodeName().compareTo(ModuleReportChildren.REPORT.toString()) == 0)
                updateNodeValue(findNodeByName(destination.getChildNodes(), sourceNode.getNodeName(),
                        sourceNode.getAttributes().getNamedItem("name").getNodeValue()), sourceNode);
        }
    }

    private void updateParallelFromDriver(Node destination, Node source) {
        for (int i = 0; i < destination.getChildNodes().getLength() && i < source.getChildNodes().getLength(); i++) {
            if (source.getNodeName().compareTo("parallel") == 0)
                updateParallelFromDriver(destination.getChildNodes().item(i), source.getChildNodes().item(i));
            else
                updateModuleInstanceFromDriver(findModuleInstanceByDatablockName(destination.getChildNodes(),
                        source.getAttributes().getNamedItem("datablock_name").getNodeValue()),
                        source.getChildNodes().item(i));
        }
    }

    private void updateModuleInstanceReportFromDriver(Node destinationModuleReport, Node sourceModuleReport) {
        for (int i = 0; i < sourceModuleReport.getChildNodes().getLength(); i++) {
            Node source = sourceModuleReport.getChildNodes().item(i);
            String sourceNodeName = source.getNodeName();
            if (sourceNodeName.compareTo(ModuleReportChildren.MESSAGE.toString()) == 0
                    || sourceNodeName.compareTo(ModuleReportChildren.ERROR_MESSAGE.toString()) == 0
                    || sourceNodeName.compareTo(ModuleReportChildren.ERROR.toString()) == 0
                    || sourceNodeName.compareTo(ModuleReportChildren.COMMAND.toString()) == 0)
                updateNodeValue(findNodeByName(destinationModuleReport.getChildNodes(), sourceNodeName), source);
            else if (sourceNodeName.compareTo(ModuleReportChildren.STATUS.toString()) == 0) {
                Node destination = findNodeByName(destinationModuleReport.getChildNodes(), sourceNodeName);
                if (Integer.valueOf(source.getTextContent()) == Status.getNum(Status.STAT_COMPLETE)
                        && destination.getTextContent().compareTo(Status.STAT_COMPLETE.toString()) != 0) {
                    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                    LocalDateTime now = LocalDateTime.now();
                    findNodeByName(destinationModuleReport.getChildNodes(), ModuleReportChildren.TIME_FINISHED.toString())
                            .setTextContent(dateTimeFormatter.format(now));
                }
                destination.setTextContent(Status.values()[Integer.valueOf(source.getTextContent())].toString());
            }
        }
    }

    private void updateNodeValue(Node destination, Node source) {
        if (source != null && destination != null && source.getTextContent() != "")
            destination.setTextContent(source.getTextContent());
    }

    private Node findNodeByName(NodeList source, String nameToMatch) {
        return findNodeByName(source, nameToMatch, "");
    }

    private Node findNodeByName(NodeList source, String nameToMatch, String attributeNameToMatch) {
        for (int i = 0; i < source.getLength(); i++) {
            if (source.item(i).getNodeName().compareTo(nameToMatch) == 0)
                if (attributeNameToMatch.length() == 0
                        || source.item(i).getAttributes().getNamedItem("name").getNodeValue().compareTo(attributeNameToMatch) == 0)
                    return source.item(i);
        }
        return null;
    }

    private Node findModuleInstanceByDatablockName(NodeList source, String dataBlockName) {
        for (int i = 0; i < source.getLength(); i++)
            if (source.item(i).getAttributes().getNamedItem("datablock_name").getNodeValue()
                    .compareTo(dataBlockName) == 0)
                return source.item(i);
        return null;
    }
}
