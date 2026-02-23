package com.example.coreclash.battlepass.anim;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

public final class BattlePassAnimator {

    private BattlePassAnimator() {
    }

    public static void playScreenEnter(@NonNull View root) {
        root.setAlpha(0f);
        root.setTranslationY(28f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(root, View.TRANSLATION_Y, 28f, 0f)
        );
        set.setDuration(320);
        set.setInterpolator(new DecelerateInterpolator());
        set.start();
    }

    public static void playClaim(@NonNull View view) {
        AnimatorSet set = new AnimatorSet();
        set.playSequentially(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.06f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.06f, 1f)
        );
        set.setDuration(260);
        set.start();
    }

    public static void playLevelUp(@NonNull View badge) {
        ObjectAnimator pulse = ObjectAnimator.ofFloat(badge, View.ROTATION, -2f, 2f, -1f, 1f, 0f);
        pulse.setDuration(380);
        pulse.start();
    }
}
