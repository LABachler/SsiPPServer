package SSiPP.Server.Driver.util;

public enum Command {
    CMD_NOTHING("CMD_NOTHING"),
    CMD_START("CMD_START"),
    CMD_ABORT("CMD_ABORT"),
    CMD_HOLD("CMD_HOLD"),
    CMD_RESTART("CMD_RESTART"),
    CMD_RESET("CMD_RESET");

    private String text;

    Command(final String text){
        this.text = text;
    }

    public static int getNum(Command cmd) {
        switch (cmd) {
            case CMD_NOTHING:
                return 0;
            case CMD_START:
                return 1;
            case CMD_ABORT:
                return 2;
            case CMD_HOLD:
                return 3;
            case CMD_RESTART:
                return 4;
            case CMD_RESET:
                return 5;
            default:
                return -1;
        }
    }

    @Override
    public String toString(){
        return text;
    }
}
