package util;

import java.util.Collection;

public class NullUtil {

    private NullUtil() {
    }

    public static boolean isNull(Object value) {
        return value == null;
    }

    public static boolean isNull(Number number) {
        return number == null;
    }

    public static boolean isNullOrEmpty(String value) {
        return isNull(value) || value.trim().isEmpty();
    }

    public static boolean isNullOrEmpty(Object value) {
        return isNull(value);
    }

    public static <T> boolean isNullOrEmpty(Collection<T> collection) {
        return isNull(collection) || collection.isEmpty();
    }

    public static boolean isNullOrEmpty(Number number) {
        return isNull(number) || !(number.doubleValue() > 0);
    }

    public static boolean isNullOrEmpty(Object[] array) {
        return isNull(array) || array.length == 0;
    }

    public static boolean isNullOrEmptyOrZero(String value) {
        return isNullOrEmpty(value) || value.equals("0") || value.equals("00");
    }

    public static boolean isNullOrEmptyOrValorZero(String value) {
        return isNullOrEmpty(value) || value.equals("0") || value.equals("0,00") || value.equals("0.00");
    }

    public static boolean isNullOrEmptyOrZero(Number value) {
        return isNullOrEmpty(value) || value.intValue() == 0;
    }
}
