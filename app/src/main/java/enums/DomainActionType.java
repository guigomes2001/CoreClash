package enums;

public enum DomainActionType {

    MOVE("MOVE", 0),
    TRIANGLE("TRIANGLE", 1),
    SQUARE("SQUARE", 2);

    private final String value;
    private final int number;

    DomainActionType(String value, int number) {
        this.value = value;
        this.number = number;
    }

    public String getValue() {
        return value;
    }

    public int getNumber() {
        return number;
    }
}
