package util;

import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public class AnimationHelper {

    public static void pulse(View v) {
        v.animate().cancel();
        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90)
                .withEndAction(() -> v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(130)
                        .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                                .setInterpolator(new DecelerateInterpolator()).start()).start()).start();
    }

    public static void spin(View v) {
        v.animate().cancel();
        v.animate().rotationBy(360f).setDuration(520)
                .setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    public static void shakeButton(View v) {
        v.animate().cancel();
        v.animate().translationX(10f).setDuration(40)
                .withEndAction(() -> v.animate().translationX(-8f).setDuration(40)
                        .withEndAction(() -> v.animate().translationX(0f).setDuration(35).start())).start();
    }
}