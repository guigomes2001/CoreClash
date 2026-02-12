package util;

import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;

public final class SafeClickUtil {

    public interface ClickAction {
        void onClick(@NonNull View view);
    }

    private static final int TAG_LAST_CLICK_TS = View.generateViewId();

    private SafeClickUtil() {}

    public static void setSafeClick(@NonNull View view, long minIntervalMs, @NonNull ClickAction action) {
        view.setOnClickListener(v -> {
            Object tag = v.getTag(TAG_LAST_CLICK_TS);
            long now = SystemClock.elapsedRealtime();
            long lastTs = tag instanceof Long ? (Long) tag : 0L;
            if (now - lastTs < Math.max(180L, minIntervalMs)) return;
            v.setTag(TAG_LAST_CLICK_TS, now);
            action.onClick(v);
        });
    }
}
