package SSiPP.Server.Driver.util;

public enum Status {
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

    public static int getNum(Status status) {
        switch (status) {
            case STAT_IDLE:
                return 0;
            case STAT_COMPLETE:
                return 1;
            case STAT_RUNNING:
                return 2;
            case STAT_ABORTING:
                return 3;
            case STAT_ABORTED:
                return 4;
            case STAT_HOLDING:
                return 5;
            case STAT_HELD:
                return 6;
            case STAT_RESTARTING:
                return 7;
            default:
                return -1;
        }
    }

    @Override
    public String toString(){
        return text;
    }
}