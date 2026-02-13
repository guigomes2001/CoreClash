package enums;

public enum DomainActionType {

    MOVE("MOVE"),
    TRIANGLE("TRIANGLE"),
    SQUARE("SQUARE");

    private final String value;

    DomainActionType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
