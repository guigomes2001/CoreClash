package manager;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import ui.style.HudIdentityStyle;

import util.AnimationHelper;

public class TurnHudManager {

    public interface OnTurnTimeout {
        void onTimeout(boolean xTurnStarted);
    }

    private final View hudRoot;
    private final TextView nameX;
    private final TextView nameO;
    private final ProgressBar barX;
    private final ProgressBar barO;

    private final long durationMs;
    private final OnTurnTimeout callback;

    private ValueAnimator timerAnimator;
    private ValueAnimator pulseAnimator;

    private Boolean lastTurnX = null;
    private boolean cancelled = false;

    private float pulseBaseScale = 1f;
    private float pulseAmp = 0f;

    private final HudIdentityStyle identityStyle = HudIdentityStyle.defaults();

    public TurnHudManager(
            @NonNull View hudRoot,
            @NonNull TextView nameX,
            @NonNull TextView nameO,
            @NonNull ProgressBar barX,
            @NonNull ProgressBar barO,
            long durationMs,
            @NonNull OnTurnTimeout callback
    ) {
        this.hudRoot = hudRoot;
        this.nameX = nameX;
        this.nameO = nameO;
        this.barX = barX;
        this.barO = barO;
        this.durationMs = durationMs;
        this.callback = callback;

        resetBars();
        stopPulse();
    }

    public void render(@NonNull String playerX, @NonNull String playerO, boolean running, boolean xTurn, boolean iAmX) {
        nameX.setText(buildNameLabel(playerX, "X", iAmX));
        nameO.setText(buildNameLabel(playerO, "O", !iAmX));

        if (!running) {
            stopAll();
            styleName(nameX, false, iAmX);
            styleName(nameO, false, !iAmX);
            return;
        }

        styleName(nameX, xTurn, iAmX);
        styleName(nameO, !xTurn, !iAmX);

        if (lastTurnX == null || lastTurnX != xTurn) {
            startTurn(xTurn);
            lastTurnX = xTurn;
        }
    }

    @NonNull
    private CharSequence buildNameLabel(@NonNull String playerName, @NonNull String symbol, boolean isMe) {
        String prefix = isMe ? identityStyle.localPipePrefix() : "";
        String label = prefix + playerName + identityStyle.nameSymbolSeparator() + symbol;

        SpannableString span = new SpannableString(label);

        int symbolStart = label.length() - symbol.length();
        int symbolEnd = label.length();

        int color = "X".equals(symbol) ? identityStyle.xSymbolColor() : identityStyle.oSymbolColor();

        span.setSpan(new ForegroundColorSpan(color), symbolStart, symbolEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new RelativeSizeSpan(identityStyle.symbolRelativeSize()), symbolStart, symbolEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), symbolStart, symbolEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        if (isMe && !prefix.isEmpty()) {
            span.setSpan(new ForegroundColorSpan(identityStyle.localMarkerColor()), 0, prefix.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        return span;
    }

    public void stopAll() {
        cancelTimer();
        stopPulse();
        resetBars();
        lastTurnX = null;

        barX.setScaleY(1f);
        barO.setScaleY(1f);
        barX.setAlpha(1f);
        barO.setAlpha(1f);
    }

    private void startTurn(boolean xTurn) {
        cancelTimer();
        stopPulse();

        final ProgressBar active = xTurn ? barX : barO;
        final ProgressBar inactive = xTurn ? barO : barX;

        inactive.setProgress(0);
        active.setProgress(100);

        pulseBaseScale = 1.0f;
        pulseAmp = 0.0f;
        startPulse(active);

        cancelled = false;

        timerAnimator = ValueAnimator.ofInt(100, 0);
        timerAnimator.setDuration(durationMs);
        timerAnimator.setInterpolator(new LinearInterpolator());

        timerAnimator.addUpdateListener(a -> {
            int value = (int) a.getAnimatedValue();
            active.setProgress(value);
            updatePulseIntensity(value);
        });

        timerAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;

                timeoutEffect(active);
                callback.onTimeout(xTurn);
            }
        });

        active.setScaleY(1f);
        active.setAlpha(1f);
        inactive.setScaleY(1f);
        inactive.setAlpha(0.85f);

        timerAnimator.start();
    }

    private void cancelTimer() {
        if (timerAnimator != null) {
            timerAnimator.cancel();
            timerAnimator = null;
        }
    }

    private void resetBars() {
        barX.setProgress(0);
        barO.setProgress(0);
    }

    private void styleName(TextView tv, boolean active, boolean isMe) {
        tv.setAlpha(active ? 1f : (isMe ? 0.92f : 0.75f));
        tv.animate()
                .scaleX(active ? 1.04f : (isMe ? 1.02f : 1f))
                .scaleY(active ? 1.04f : (isMe ? 1.02f : 1f))
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void startPulse(@NonNull ProgressBar target) {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(760);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.REVERSE);
        pulseAnimator.setInterpolator(new DecelerateInterpolator());

        pulseAnimator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            float scale = pulseBaseScale + (pulseAmp * t);
            target.setScaleY(scale);

            float alpha = 0.92f + (0.08f * t);
            target.setAlpha(alpha);
        });

        pulseAnimator.start();
    }

    private void stopPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
    }

    private void updatePulseIntensity(int value) {
        if (value >= 65) {
            pulseBaseScale = 1.0f;
            pulseAmp = 0.015f;
            return;
        }
        if (value >= 35) {
            pulseBaseScale = 1.0f;
            pulseAmp = 0.035f;
            return;
        }
        if (value >= 15) {
            pulseBaseScale = 1.0f;
            pulseAmp = 0.06f;
            return;
        }
        pulseBaseScale = 1.0f;
        pulseAmp = 0.09f;
    }

    private void timeoutEffect(@NonNull ProgressBar bar) {
        bar.animate().cancel();
        hudRoot.animate().cancel();

        bar.animate()
                .scaleY(1.22f)
                .alpha(1f)
                .setDuration(120)
                .withEndAction(() -> bar.animate()
                        .scaleY(1f)
                        .alpha(0.85f)
                        .setDuration(220)
                        .setInterpolator(new DecelerateInterpolator())
                        .start())
                .start();

        AnimationHelper.shakeButton(hudRoot);
    }
}
