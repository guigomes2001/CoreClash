package util;

import android.os.Handler;

import androidx.annotation.NonNull;

public final class ThreadUtil {

    private ThreadUtil() {
    }

    public static void postDelayed(@NonNull Handler handler, @NonNull Runnable runnable, long delayMs) {
        handler.postDelayed(runnable, Math.max(0L, delayMs));
    }

    public static void removeCallback(@NonNull Handler handler, @NonNull Runnable runnable) {
        handler.removeCallbacks(runnable);
    }
}
