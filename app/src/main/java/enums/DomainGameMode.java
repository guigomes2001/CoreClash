package enums;

public enum DomainGameMode {
    CASUAL("CASUAL"),
    RANKED("RANKED"),
    ONLINE("ONLINE"),
    BOT("BOT"),
    LOCAL_PASS_PLAY("LOCAL_PASS_PLAY");
    private final String value;

    DomainGameMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
