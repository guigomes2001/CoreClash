package ui.anim.flow;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import util.FontAwesomeIconFactory;
import util.SafeClickUtil;

public class HomeFlowManager {

    public interface Callbacks {
        void onQuickPlayClicked();
        void onPlayOnlineClicked();
        void onStoreClicked();
        void onSettingsClicked();
        void onGoogleLoginFromSettingsClicked();
        void onConfirmOfflineVsBot();
        void onConfirmOnlinePvp();
        void onConfirmLocalPassPlay();
        void onConfirmLocalLobby();
        void onModeChanged(@NonNull enums.DomainMatchKind selected);
    }

    private final ActivityMainBinding binding;
    private final Callbacks cb;

    private enums.DomainMatchKind selectedMatchKind = enums.DomainMatchKind.OFFLINE_BOT;

    public HomeFlowManager(@NonNull ActivityMainBinding binding, @NonNull Callbacks callbacks) {
        this.binding = binding;
        this.cb = callbacks;
    }

    public void bind() {
        configureHomeMenuTiles();
        configureModeOverlayButtons();
        setupSpringInteractions();

        SafeClickUtil.setSafeClick(binding.btnPlay, 420, v -> openModeModal());
        SafeClickUtil.setSafeClick(binding.btnStore, 420, v -> cb.onStoreClicked());
        SafeClickUtil.setSafeClick(binding.btnSettings, 320, v -> cb.onSettingsClicked());

        SafeClickUtil.setSafeClick(binding.btnModeOffline, 220, v -> {
            selectedMatchKind = enums.DomainMatchKind.OFFLINE_BOT;
            updateModeButtonStyles();
            cb.onModeChanged(selectedMatchKind);
        });

        SafeClickUtil.setSafeClick(binding.btnModeOnline, 220, v -> {
            selectedMatchKind = enums.DomainMatchKind.ONLINE_PVP;
            updateModeButtonStyles();
            cb.onModeChanged(selectedMatchKind);
        });

        SafeClickUtil.setSafeClick(binding.btnModeLocalPassPlay, 220, v -> {
            selectedMatchKind = enums.DomainMatchKind.LOCAL_PASS_PLAY;
            updateModeButtonStyles();
            cb.onModeChanged(selectedMatchKind);
        });

        SafeClickUtil.setSafeClick(binding.btnModeCancel, 280, v -> closeModeModal());
        SafeClickUtil.setSafeClick(binding.btnModeConfirm, 280, v -> {
            closeModeModal();

            if (selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP) {
                cb.onConfirmOnlinePvp();
                return;
            }
            if (selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT) {
                cb.onConfirmOfflineVsBot();
                return;
            }
            if (selectedMatchKind == enums.DomainMatchKind.LOCAL_PASS_PLAY) {
                cb.onConfirmLocalPassPlay();
                return;
            }
            cb.onConfirmLocalPassPlay();
        });

        binding.modeOverlay.setOnClickListener(v -> {});

        SafeClickUtil.setSafeClick(binding.btnGoogleLoginSettings, 320, v -> cb.onGoogleLoginFromSettingsClicked());

        updateModeButtonStyles();
    }

    private void configureHomeMenuTiles() {
        binding.btnPlay.setText(R.string.btn_play);
        binding.btnStore.setText(R.string.btn_store);

        int iconColor = 0xFFDDEBFF;
        FontAwesomeIconFactory.applyTopIcon(binding.btnPlay, binding.getRoot().getContext().getString(R.string.fa_gamepad), 16, iconColor, 6);
        FontAwesomeIconFactory.applyTopIcon(binding.btnStore, binding.getRoot().getContext().getString(R.string.fa_store), 16, iconColor, 6);

        binding.btnOnline.setVisibility(View.GONE);
        binding.btnArena.setVisibility(View.GONE);
    }

    private void configureModeOverlayButtons() {
        int iconColor = 0xFFEAF2FF;
        FontAwesomeIconFactory.applyStartIcon(binding.btnModeOnline, binding.getRoot().getContext().getString(R.string.fa_bolt), 14, iconColor, 10);
        FontAwesomeIconFactory.applyStartIcon(binding.btnModeOffline, binding.getRoot().getContext().getString(R.string.fa_gamepad), 14, iconColor, 10);
        FontAwesomeIconFactory.applyStartIcon(binding.btnModeLocalPassPlay, binding.getRoot().getContext().getString(R.string.fa_users), 14, iconColor, 10);
        binding.btnModeLocalLobby.setVisibility(View.GONE);
    }

    private void setupSpringInteractions() {
        attachTileSpringInteraction(binding.btnPlay);
        attachTileSpringInteraction(binding.btnStore);

        attachTileSpringInteraction(binding.btnSettings);
        attachTileSpringInteraction(binding.btnProfile);
        attachTileSpringInteraction(binding.btnFriends);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachTileSpringInteraction(@NonNull View view) {
        view.setOnTouchListener((v, event) -> {
            if (!v.isEnabled()) return false;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                springTo(v, DynamicAnimation.SCALE_X, 0.94f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, SpringForce.STIFFNESS_MEDIUM);
                springTo(v, DynamicAnimation.SCALE_Y, 0.94f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, SpringForce.STIFFNESS_MEDIUM);
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                springTo(v, DynamicAnimation.SCALE_X, 1f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, SpringForce.STIFFNESS_LOW);
                springTo(v, DynamicAnimation.SCALE_Y, 1f, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY, SpringForce.STIFFNESS_LOW);
            }
            return false;
        });
    }

    private void springTo(@NonNull View view,
                          @NonNull DynamicAnimation.ViewProperty property,
                          float finalValue,
                          float dampingRatio,
                          float stiffness) {
        SpringAnimation spring = new SpringAnimation(view, property);
        SpringForce force = new SpringForce(finalValue);
        force.setDampingRatio(dampingRatio);
        force.setStiffness(stiffness);
        spring.setSpring(force);
        spring.start();
    }

    public void setSelectedMatchKind(@NonNull enums.DomainMatchKind kind) {
        this.selectedMatchKind = kind;
        updateModeButtonStyles();
    }

    public void openModeModal() {
        updateModeButtonStyles();
        binding.modeOverlay.setVisibility(View.VISIBLE);
        binding.modeOverlay.setAlpha(0f);
        binding.modeCard.setScaleX(0.97f);
        binding.modeCard.setScaleY(0.97f);
        binding.lottieModeOverlay.playAnimation();

        binding.modeOverlay.animate().alpha(1f).setDuration(180).start();
        binding.modeCard.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    public void closeModeModal() {
        binding.modeOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    binding.modeOverlay.setVisibility(View.GONE);
                    binding.lottieModeOverlay.pauseAnimation();
                })
                .start();
    }

    public void updateModeButtonStyles() {
        styleModeButton(binding.btnModeOffline, selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT);
        styleModeButton(binding.btnModeOnline, selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP);
        styleModeButton(binding.btnModeLocalPassPlay, selectedMatchKind == enums.DomainMatchKind.LOCAL_PASS_PLAY);
    }

    private void styleModeButton(@NonNull android.widget.Button button, boolean selected) {
        button.setTextColor(selected ? 0xFF04131F : 0xFFEAF2FF);
        button.setBackgroundResource(selected ? R.drawable.bg_button_primary : R.drawable.bg_button_secondary);
        button.setAlpha(selected ? 1f : 0.92f);
    }

    public void playHomeEntrance() {
        View[] revealViews = new View[]{
                binding.homeCard,
                binding.homeQuickActions,
                binding.btnPlay,
                binding.btnStore
        };

        for (View view : revealViews) {
            view.animate().cancel();
            view.setAlpha(0f);
            view.setTranslationY(38f);
            view.setScaleX(0.9f);
            view.setScaleY(0.9f);
        }

        AnimatorSet alphaTimeline = new AnimatorSet();
        alphaTimeline.playTogether(
                buildAlpha(binding.homeCard, 200L, 0L),
                buildAlpha(binding.homeQuickActions, 180L, 80L),
                buildAlpha(binding.btnPlay, 170L, 130L),
                buildAlpha(binding.btnStore, 170L, 200L)
        );
        alphaTimeline.start();

        startEntranceSpring(binding.homeCard, 0L, 0.78f);
        startEntranceSpring(binding.homeQuickActions, 70L, 0.82f);
        startEntranceSpring(binding.btnPlay, 120L, 0.86f);
        startEntranceSpring(binding.btnStore, 190L, 0.86f);
    }

    private void startEntranceSpring(@NonNull View view, long delayMs, float damping) {
        view.postDelayed(() -> {
            springTo(view, DynamicAnimation.TRANSLATION_Y, 0f, damping, SpringForce.STIFFNESS_LOW);
            springTo(view, DynamicAnimation.SCALE_X, 1f, damping, SpringForce.STIFFNESS_MEDIUM);
            springTo(view, DynamicAnimation.SCALE_Y, 1f, damping, SpringForce.STIFFNESS_MEDIUM);
        }, delayMs);
    }

    @NonNull
    private ObjectAnimator buildAlpha(@NonNull View view, long durationMs, long startDelayMs) {
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f);
        alpha.setDuration(durationMs);
        alpha.setStartDelay(startDelayMs);
        alpha.setInterpolator(new DecelerateInterpolator());
        return alpha;
    }

    public void showWaitingOpponentUi() {
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);

        binding.btnPlay.setEnabled(false);
        binding.btnStore.setEnabled(false);
        binding.btnSettings.setEnabled(false);
        binding.btnProfile.setEnabled(false);
        binding.btnFriends.setEnabled(false);
    }

    public void restoreMenuButtons() {
        binding.btnPlay.setEnabled(true);
        binding.btnStore.setEnabled(true);
        binding.btnSettings.setEnabled(true);
        binding.btnProfile.setEnabled(true);
        binding.btnFriends.setEnabled(true);
    }
}
