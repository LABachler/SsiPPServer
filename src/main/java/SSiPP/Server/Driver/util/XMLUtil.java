package SSiPP.Server.Driver.util;

public enum XMLUtil {
    TAG_PROCESS("process"),
    TAG_MODULE_INSTANCE_REPORT("module_instance_report"),
    TAG_PARALLEL("parallel"),
    TAG_MODULE_INSTANCE("module_instance"),
    TAG_PARAM("param"),
    ATTRIBUTE_ID("id"),
    ATTRIBUTE_DRIVER("driver"),
    ATTRIBUTE_NAME("name"),
    ATTRIBUTE_DATABLOCK("datablock_name"),
    ATTRIBUTE_SCALE("scale"),
    QUANTITY("quantity");

    private final String text;
    XMLUtil(final String text){
        this.text = text;
    }
    @Override
    public String toString(){
        return text;
    }
}
