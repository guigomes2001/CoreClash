package enums;

public enum DomainSymbols {

    X("X"),
    O("O");

    DomainSymbols(String value) {
        this.value = value;
    }

    private String value;

    public String getValue() {
        return value;
    }
}
