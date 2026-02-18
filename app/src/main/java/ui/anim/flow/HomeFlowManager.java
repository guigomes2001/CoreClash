package ui.anim.flow;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

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
            if (selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP) {
                cb.onConfirmOnlinePvp();
                return;
            }
            closeModeModal();
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
        binding.btnModeLocalLobby.setVisibility(View.GONE);
        applyModeCardArts();
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

        binding.modeOverlay.animate().alpha(1f).setDuration(180).start();
        binding.modeCard.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    public void closeModeModal() {
        binding.modeOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> {
                    binding.modeOverlay.setVisibility(View.GONE);
                })
                .start();
    }

    public void updateModeButtonStyles() {
        styleModeButton(
                binding.btnModeOffline,
                selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT,
                binding.badgeModeOffline
        );
        styleModeButton(
                binding.btnModeOnline,
                selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP,
                binding.badgeModeOnline
        );
        styleModeButton(
                binding.btnModeLocalPassPlay,
                selectedMatchKind == enums.DomainMatchKind.LOCAL_PASS_PLAY,
                binding.badgeModeLocalPassPlay
        );
    }

    private void styleModeButton(@NonNull android.widget.Button button, boolean selected, @NonNull View badge) {
        button.setTextColor(selected ? 0xFF04131F : 0xFFEAF2FF);
        button.setBackgroundResource(selected ? R.drawable.bg_mode_card_selected : R.drawable.bg_mode_card);
        button.setAlpha(selected ? 1f : 0.96f);
        button.setElevation(selected ? 10f : 0f);
        button.setScaleX(selected ? 1.01f : 1f);
        button.setScaleY(selected ? 1.01f : 1f);

        if (selected) {
            badge.setVisibility(View.VISIBLE);
            badge.setAlpha(0f);
            badge.setScaleX(0.88f);
            badge.setScaleY(0.88f);
            badge.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(170).start();

            ObjectAnimator glow = ObjectAnimator.ofFloat(button, View.ALPHA, 0.84f, 1f);
            glow.setDuration(640);
            glow.setRepeatCount(1);
            glow.setRepeatMode(ObjectAnimator.REVERSE);
            glow.start();
        } else {
            badge.animate().cancel();
            badge.setVisibility(View.GONE);
            badge.setAlpha(1f);
        }
    }


    private void applyModeCardArts() {
        applyModeArt(binding.imgModeOfflinePreview, "mode_offline_card");
        applyModeArt(binding.imgModeOnlinePreview, "mode_online_card");
        applyModeArt(binding.imgModeLocalPassPlayPreview, "mode_local_card");
    }

    private void applyModeArt(@NonNull ImageView imageView, @NonNull String assetName) {
        int drawableId = binding.getRoot().getResources().getIdentifier(
                assetName,
                "drawable",
                binding.getRoot().getContext().getPackageName()
        );

        if (drawableId == 0) {
            return;
        }

        try {
            Bitmap source = BitmapFactory.decodeResource(binding.getRoot().getResources(), drawableId);
            if (source == null) {
                return;
            }
            Bitmap processed = removeWhiteBackground(source);
            imageView.setImageBitmap(processed);
        } catch (Throwable ignored) {
            // keep fallback drawable configured in XML
        }
    }

    @NonNull
    private Bitmap removeWhiteBackground(@NonNull Bitmap source) {
        Bitmap mutable = source.copy(Bitmap.Config.ARGB_8888, true);
        if (mutable == null) {
            return source;
        }

        int width = mutable.getWidth();
        int height = mutable.getHeight();
        int[] pixels = new int[width * height];
        mutable.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int alpha = (color >>> 24) & 0xFF;
            if (alpha == 0) {
                continue;
            }

            int red = (color >>> 16) & 0xFF;
            int green = (color >>> 8) & 0xFF;
            int blue = color & 0xFF;

            int max = Math.max(red, Math.max(green, blue));
            int min = Math.min(red, Math.min(green, blue));
            int spread = max - min;

            if (red >= 245 && green >= 245 && blue >= 245) {
                pixels[i] = 0x00000000;
                continue;
            }

            if (min >= 225 && spread <= 16) {
                int fade = Math.max(0, Math.min(255, (245 - min) * 14));
                int newAlpha = Math.min(alpha, fade);
                pixels[i] = (newAlpha << 24) | (red << 16) | (green << 8) | blue;
            }
        }

        mutable.setPixels(pixels, 0, width, 0, 0, width, height);
        return mutable;
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
