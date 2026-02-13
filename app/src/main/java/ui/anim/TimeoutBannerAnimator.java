package ui.anim;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.coreclash.databinding.ActivityMainBinding;
import util.NullUtil;

public class TimeoutBannerAnimator {

    private final ActivityMainBinding binding;

    private AnimatorSet timeoutBannerAnimX;
    private AnimatorSet timeoutBannerAnimO;

    public TimeoutBannerAnimator(@NonNull ActivityMainBinding binding) {
        this.binding = binding;
    }

    public void play(boolean xSide) {
        final FrameLayout track = xSide ? binding.turnHudTrackX : binding.turnHudTrackO;
        final TextView arrow1   = xSide ? binding.txtTimeoutArrow1X : binding.txtTimeoutArrow1O;
        final TextView arrow2   = xSide ? binding.txtTimeoutArrow2X : binding.txtTimeoutArrow2O;
        final TextView arrow3   = xSide ? binding.txtTimeoutArrow3X : binding.txtTimeoutArrow3O;
        final TextView label    = xSide ? binding.txtTimeoutX : binding.txtTimeoutO;

        stopAnimation(xSide);

        track.post(() -> {
            int trackWidth = track.getWidth();

            label.setVisibility(View.VISIBLE);

            if (label.getWidth() <= 0) {
                label.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
            }

            int labelWidth = label.getWidth() > 0 ? label.getWidth() : label.getMeasuredWidth();

            if (trackWidth <= 0) {
                track.measure(
                        View.MeasureSpec.makeMeasureSpec(track.getMeasuredWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(track.getMeasuredHeight(), View.MeasureSpec.AT_MOST)
                );
                trackWidth = track.getWidth() > 0 ? track.getWidth() : track.getMeasuredWidth();
            }

            if (trackWidth <= 0 || labelWidth <= 0) {
                label.setAlpha(1f);
                label.setTranslationX(0f);
                label.animate()
                        .alpha(0f)
                        .setStartDelay(650)
                        .setDuration(350)
                        .withEndAction(() -> resetElement(label, 0f))
                        .start();
                return;
            }

            float startX  = -labelWidth - 34f;
            float centerX = (trackWidth - labelWidth) / 2f;
            float endX    = trackWidth + 34f;

            float arrowStart  = startX + labelWidth + 8f;
            float arrowCenter = centerX + labelWidth + 10f;
            float arrowEnd    = endX + 22f;

            playArrow(arrow1, arrowStart,       arrowCenter,       arrowEnd,       300, 560, 140);
            playArrow(arrow2, arrowStart + 14f, arrowCenter + 14f, arrowEnd + 14f, 410, 550, 132);
            playArrow(arrow3, arrowStart + 28f, arrowCenter + 28f, arrowEnd + 28f, 520, 540, 126);

            label.setTranslationX(startX);
            label.setAlpha(0f);
            label.setScaleX(0.96f);

            ObjectAnimator alphaIn = ObjectAnimator.ofFloat(label, View.ALPHA, 0f, 1f);
            alphaIn.setStartDelay(60);
            alphaIn.setDuration(180);

            ObjectAnimator scaleIn = ObjectAnimator.ofFloat(label, View.SCALE_X, 0.96f, 1f);
            scaleIn.setStartDelay(60);
            scaleIn.setDuration(260);
            scaleIn.setInterpolator(new DecelerateInterpolator(1.3f));

            ObjectAnimator fastIn = ObjectAnimator.ofFloat(label, View.TRANSLATION_X, startX, centerX - 6f);
            fastIn.setDuration(520);
            fastIn.setInterpolator(new DecelerateInterpolator(1.42f));

            ObjectAnimator glide = ObjectAnimator.ofFloat(label, View.TRANSLATION_X, centerX - 6f, centerX + 18f);
            glide.setDuration(900);
            glide.setInterpolator(new LinearInterpolator());

            ObjectAnimator fastOut = ObjectAnimator.ofFloat(label, View.TRANSLATION_X, centerX + 18f, endX);
            fastOut.setDuration(470);
            fastOut.setInterpolator(new AccelerateInterpolator(1.72f));

            ObjectAnimator alphaOut = ObjectAnimator.ofFloat(label, View.ALPHA, 1f, 0f);
            alphaOut.setStartDelay(1420);
            alphaOut.setDuration(420);

            AnimatorSet labelMotion = new AnimatorSet();
            labelMotion.playSequentially(fastIn, glide, fastOut);

            AnimatorSet set = new AnimatorSet();
            set.playTogether(labelMotion, alphaIn, scaleIn, alphaOut);
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { resetElement(label, startX); }
                @Override public void onAnimationCancel(Animator animation) { resetElement(label, startX); }
            });
            set.start();

            if (xSide) timeoutBannerAnimX = set;
            else timeoutBannerAnimO = set;
        });
    }

    public void hide(boolean xSide) {
        stopAnimation(xSide);

        final TextView arrow1 = xSide ? binding.txtTimeoutArrow1X : binding.txtTimeoutArrow1O;
        final TextView arrow2 = xSide ? binding.txtTimeoutArrow2X : binding.txtTimeoutArrow2O;
        final TextView arrow3 = xSide ? binding.txtTimeoutArrow3X : binding.txtTimeoutArrow3O;
        final TextView label  = xSide ? binding.txtTimeoutX : binding.txtTimeoutO;

        arrow1.animate().cancel();
        arrow2.animate().cancel();
        arrow3.animate().cancel();
        label.animate().cancel();

        arrow1.setVisibility(View.INVISIBLE);
        arrow2.setVisibility(View.INVISIBLE);
        arrow3.setVisibility(View.INVISIBLE);
        label.setVisibility(View.INVISIBLE);

        arrow1.setAlpha(0f);
        arrow2.setAlpha(0f);
        arrow3.setAlpha(0f);
        label.setAlpha(0f);
    }

    public void cancel(boolean xSide) {
        stopAnimation(xSide);
    }

    private void stopAnimation(boolean xSide) {
        AnimatorSet set = xSide ? timeoutBannerAnimX : timeoutBannerAnimO;
        if (!NullUtil.isNull(set)) set.cancel();
        if (xSide) timeoutBannerAnimX = null;
        else timeoutBannerAnimO = null;
    }

    private void playArrow(@NonNull TextView arrow, float startX, float centerX, float endX,
                           long startDelay, long moveDuration, long fadeOutDuration) {
        resetElement(arrow, startX);
        arrow.setVisibility(View.VISIBLE);

        ObjectAnimator alphaIn = ObjectAnimator.ofFloat(arrow, View.ALPHA, 0f, 1f);
        alphaIn.setStartDelay(startDelay);
        alphaIn.setDuration(60);

        ObjectAnimator move = ObjectAnimator.ofFloat(arrow, View.TRANSLATION_X, startX, centerX, endX);
        move.setStartDelay(startDelay);
        move.setDuration(moveDuration);
        move.setInterpolator(new AccelerateInterpolator(1.8f));

        ObjectAnimator alphaOut = ObjectAnimator.ofFloat(arrow, View.ALPHA, 1f, 0f);
        alphaOut.setStartDelay(startDelay + Math.max(120L, moveDuration - 40L));
        alphaOut.setDuration(fadeOutDuration);

        alphaIn.start();
        move.start();
        alphaOut.start();
    }

    private void resetElement(@NonNull TextView view, float startX) {
        view.setTranslationX(startX);
        view.setAlpha(0f);
        view.setVisibility(View.INVISIBLE);
    }
}
