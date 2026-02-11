package ui.anim;

import android.os.Handler;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

public class MatchIntroAnimator {

    public interface Callbacks {
        @NonNull String getPlayerName();
        @NonNull String getOpponentName();
        @NonNull String getModeLabel();
        void setArenaUiVisible(boolean visible);
        void onIntroFinished();
    }

    private final ActivityMainBinding binding;
    private final Handler handler;
    private final Callbacks cb;

    private final Runnable breakRunnable = this::playVersusBreakAnimation;

    public MatchIntroAnimator(@NonNull ActivityMainBinding binding,
                              @NonNull Handler handler,
                              @NonNull Callbacks callbacks) {
        this.binding = binding;
        this.handler = handler;
        this.cb = callbacks;
    }

    public void startFromHome() {
        cancelPending();

        binding.homeOverlay.animate()
                .alpha(0f)
                .setDuration(460)
                .withEndAction(() -> {
                    binding.homeOverlay.setVisibility(View.GONE);
                    showVersusOverlayInternal();
                })
                .start();
    }

    public void startRematch() {
        cancelPending();
        binding.homeOverlay.setVisibility(View.GONE);
        showVersusOverlayInternal();
    }

    public void cancel() {
        cancelPending();
        cancelAnimations();
    }

    private void showVersusOverlayInternal() {
        resetVersusUiState();

        binding.versusOverlay.setVisibility(View.VISIBLE);
        binding.versusOverlay.setAlpha(1f);

        cb.setArenaUiVisible(true);

        binding.txtVersusX.setText(cb.getPlayerName());
        binding.txtVersusO.setText(cb.getOpponentName());

        binding.txtVersusMode.setText(cb.getModeLabel());
        binding.txtVersusCenter.setText(binding.getRoot().getContext().getString(R.string.versus_battle_title));

        binding.txtVersusMode.setAlpha(0f);
        binding.viewVersusStripeTop.setAlpha(0f);
        binding.viewVersusStripeBottom.setAlpha(0f);

        binding.txtBreakX.setAlpha(0f);
        binding.txtBreakO.setAlpha(0f);
        binding.txtBreakX.setTranslationX(0f);
        binding.txtBreakO.setTranslationX(0f);

        binding.txtVersusMode.animate().alpha(1f).setDuration(220).start();
        binding.viewVersusStripeTop.animate().alpha(1f).setDuration(220).start();
        binding.viewVersusStripeBottom.animate().alpha(1f).setDuration(220).start();
        binding.lottieVersusTransition.playAnimation();

        handler.postDelayed(breakRunnable, 2500);
    }

    private void playVersusBreakAnimation() {
        if (binding.versusOverlay.getVisibility() != View.VISIBLE) return;

        binding.txtBreakX.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        binding.txtBreakO.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();

        float dist = binding.getRoot().getWidth() * 0.65f;

        binding.txtBreakX.animate()
                .translationX(-dist)
                .alpha(0f)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();

        binding.txtBreakO.animate()
                .translationX(dist)
                .alpha(0f)
                .setDuration(380)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .start();

        binding.versusBandRoot.animate()
                .alpha(0f)
                .setDuration(260)
                .start();

        binding.versusDim.animate()
                .alpha(0f)
                .setDuration(260)
                .withEndAction(() -> {
                    binding.versusOverlay.setVisibility(View.GONE);
                    binding.lottieVersusTransition.cancelAnimation();
                    cb.onIntroFinished();
                })
                .start();
    }

    private void resetVersusUiState() {
        binding.versusOverlay.setVisibility(View.VISIBLE);
        binding.versusOverlay.setAlpha(1f);

        binding.versusBandRoot.setAlpha(1f);
        binding.versusDim.setAlpha(1f);
        binding.lottieVersusTransition.cancelAnimation();
        binding.lottieVersusTransition.setProgress(0f);

        binding.txtVersusMode.setAlpha(1f);
        binding.viewVersusStripeTop.setAlpha(1f);
        binding.viewVersusStripeBottom.setAlpha(1f);

        binding.txtBreakX.animate().cancel();
        binding.txtBreakO.animate().cancel();
        binding.txtBreakX.setAlpha(0f);
        binding.txtBreakO.setAlpha(0f);
        binding.txtBreakX.setScaleX(0.6f);
        binding.txtBreakX.setScaleY(0.6f);
        binding.txtBreakO.setScaleX(0.6f);
        binding.txtBreakO.setScaleY(0.6f);
        binding.txtBreakX.setTranslationX(0f);
        binding.txtBreakO.setTranslationX(0f);

        binding.versusBandRoot.animate().cancel();
        binding.versusDim.animate().cancel();
        binding.txtVersusMode.animate().cancel();
        binding.viewVersusStripeTop.animate().cancel();
        binding.viewVersusStripeBottom.animate().cancel();
        binding.lottieVersusTransition.cancelAnimation();
    }

    private void cancelPending() {
        handler.removeCallbacks(breakRunnable);
    }

    private void cancelAnimations() {
        binding.homeOverlay.animate().cancel();

        binding.txtBreakX.animate().cancel();
        binding.txtBreakO.animate().cancel();
        binding.versusBandRoot.animate().cancel();
        binding.versusDim.animate().cancel();
        binding.txtVersusMode.animate().cancel();
        binding.viewVersusStripeTop.animate().cancel();
        binding.viewVersusStripeBottom.animate().cancel();
        binding.lottieVersusTransition.cancelAnimation();
    }
}

