package enums;

public enum DomainSymmetries {

    X("X"),
    O("O");

    DomainSymmetries(String value) {
        this.value = value;
    }

    private final String value;

    public String getValue() {
        return value;
    }
}
