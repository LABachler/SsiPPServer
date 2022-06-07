package SSiPP.Server.Driver.util;

/**
 * Status translations of the plc
 */
public enum Status {
    STAT_NONE("STAT_NONE"),
    STAT_IDLE("STAT_IDLE"),
    STAT_COMPLETE("STAT_COMPLETE"),
    STAT_RUNNING("STAT_RUNNING"),
    STAT_ABORTING("STAT_ABORTING"),
    STAT_ABORTED("STAT_ABORTED"),
    STAT_HOLDING("STAT_HOLDING"),
    STAT_HELD("STAT_HELD"),
    STAT_RESTARTING("STAT_RESTARTING");

    private String text;

    Status(final String text){
        this.text = text;
    }

    /**
     * @param status
     * @return Logical number of the given status
     */
    public static int getNum(Status status) {
        switch (status) {
            case STAT_NONE:
                return 0;
            case STAT_IDLE:
                return 1;
            case STAT_COMPLETE:
                return 2;
            case STAT_RUNNING:
                return 3;
            case STAT_ABORTING:
                return 4;
            case STAT_ABORTED:
                return 5;
            case STAT_HOLDING:
                return 6;
            case STAT_HELD:
                return 7;
            case STAT_RESTARTING:
                return 8;
            default:
                return -1;
        }
    }

    @Override
    public String toString(){
        return text;
    }
}