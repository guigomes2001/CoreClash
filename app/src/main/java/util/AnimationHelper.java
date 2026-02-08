package util;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;

public class AnimationHelper {

    public static void pulse(View v) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                .withEndAction(() -> v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()).start()).start();
    }

    public static void spin(View v) {
        v.animate().rotationBy(360f).setDuration(500)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public static void shakeView(View v) {
        TranslateAnimation shake = new TranslateAnimation(0, 15, 0, 0);
        shake.setDuration(400);
        shake.setInterpolator(new CycleInterpolator(4));
        v.startAnimation(shake);
    }
}