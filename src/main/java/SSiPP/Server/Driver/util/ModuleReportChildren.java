package SSiPP.Server.Driver.util;

public enum ModuleReportChildren {
    TIME_STARTED("time_started"),
    TIME_FINISHED("time_finished"),
    STATUS("STATUS"),
    COMMAND("COMMAND"),
    MESSAGE("MSG"),
    ERROR("ERROR"),
    ERROR_MESSAGE("E_MSG"),
    REPORT("REPORT");

    private final String text;
    ModuleReportChildren(final String text){
        this.text = text;
    }
    @Override
    public String toString(){
        return text;
    }
}
