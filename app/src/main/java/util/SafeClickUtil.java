package util;

import android.os.SystemClock;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.coreclash.R;

public final class SafeClickUtil {

    public interface ClickAction {
        void onClick(@NonNull View view);
    }

    private SafeClickUtil() {}

    public static void setSafeClick(@NonNull View view, long minIntervalMs, @NonNull ClickAction action) {
        view.setOnClickListener(v -> {
            Object tag = v.getTag(R.id.tag_safe_click_last_ts);
            long now = SystemClock.elapsedRealtime();
            long lastTs = tag instanceof Long ? (Long) tag : 0L;
            if (now - lastTs < Math.max(180L, minIntervalMs)) return;
            v.setTag(R.id.tag_safe_click_last_ts, now);
            action.onClick(v);
        });
    }
}
