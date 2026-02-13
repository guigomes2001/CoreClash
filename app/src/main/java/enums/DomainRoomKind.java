package enums;

public enum DomainRoomKind {

    AUTO_QUEUE("AUTO_QUEUE"),
    LOCAL_LOBBY("LOCAL_LOBBY");

    private final String value;

    DomainRoomKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
