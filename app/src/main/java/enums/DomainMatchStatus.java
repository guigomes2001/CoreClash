package enums;

public enum DomainMatchStatus {

    MATCHED("MATCHED"),
    WAITING("WAITING"),
    PLAYING("PLAYING"),
    ENDED("ENDED"),
    ABANDONED("ABANDONED"),
    END_ABANDONMENT("END_ABANDONMENT");

    DomainMatchStatus(String value) {
        this.value = value;
    }

    private final String value;

    public String getValue() {
        return value;
    }
}
