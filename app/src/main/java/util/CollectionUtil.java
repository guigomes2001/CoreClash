package util;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class CollectionUtil {

    private CollectionUtil() {
    }

    public static boolean isNullOrEmpty(Collection<?> values) {
        return NullUtil.isNull(values) || values.isEmpty();
    }

    public static boolean isNullOrEmpty(Map<?, ?> values) {
        return NullUtil.isNull(values) || values.isEmpty();
    }

    public static <T> List<T> safeList(List<T> values) {
        return NullUtil.isNull(values) ? Collections.emptyList() : values;
    }
}
