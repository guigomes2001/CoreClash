package enums;

import androidx.annotation.Nullable;

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

    @Nullable
    public static DomainActionType fromValue(String value) {
        for (DomainActionType type : values()) {
            if (type.getValue().equals(value)) {
                return type;
            }
        }
        return null;
    }
}
