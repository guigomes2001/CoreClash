package ui.anim.flow;

import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.coreclash.databinding.ActivityMainBinding;

public class HomeFlowManager {

    public interface Callbacks {
        void onPlayOnlineClicked();
        void onStoreClicked();
        void onSettingsClicked();
        void onGoogleLoginFromSettingsClicked();
        void onConfirmOfflineVsBot();
        void onConfirmOnlinePvp();
        void onConfirmLocalMultiplayer();
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
        binding.btnPlay.setOnClickListener(v -> openModeModal());
        binding.btnOnline.setOnClickListener(v -> cb.onPlayOnlineClicked());

        binding.btnStore.setOnClickListener(v -> cb.onStoreClicked());
        binding.btnSettings.setOnClickListener(v -> cb.onSettingsClicked());

        binding.btnModeOffline.setOnClickListener(v -> {
            selectedMatchKind = enums.DomainMatchKind.OFFLINE_BOT;
            updateModeButtonStyles();
            cb.onModeChanged(selectedMatchKind);
        });

        binding.btnModeOnline.setOnClickListener(v -> {
            selectedMatchKind = enums.DomainMatchKind.ONLINE_PVP;
            updateModeButtonStyles();
            cb.onModeChanged(selectedMatchKind);
        });

        binding.btnModeCancel.setOnClickListener(v -> closeModeModal());
        binding.btnModeConfirm.setOnClickListener(v -> {
            closeModeModal();

            if (selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP) {
                cb.onConfirmOnlinePvp();
            } else if (selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT) {
                cb.onConfirmOfflineVsBot();
            } else {
                cb.onConfirmLocalMultiplayer();
            }
        });

        binding.modeOverlay.setOnClickListener(v -> closeModeModal());

        binding.btnGoogleLoginSettings.setOnClickListener(v -> cb.onGoogleLoginFromSettingsClicked());

        updateModeButtonStyles();
    }

    public void setSelectedMatchKind(@NonNull enums.DomainMatchKind kind) {
        this.selectedMatchKind = kind;
        updateModeButtonStyles();
    }

    @NonNull
    public enums.DomainMatchKind getSelectedMatchKind() {
        return selectedMatchKind;
    }

    public void openModeModal() {
        updateModeButtonStyles();
        binding.modeOverlay.setVisibility(View.VISIBLE);
        binding.modeOverlay.setAlpha(0f);
        binding.modeCard.setScaleX(0.9f);
        binding.modeCard.setScaleY(0.9f);

        binding.modeOverlay.animate().alpha(1f).setDuration(180).start();
        binding.modeCard.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
    }

    public void closeModeModal() {
        binding.modeOverlay.animate()
                .alpha(0f)
                .setDuration(150)
                .withEndAction(() -> binding.modeOverlay.setVisibility(View.GONE))
                .start();
    }

    public void updateModeButtonStyles() {
        boolean offlineSelected = (selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT);

        int selectedBg = 0xFF22D3EE;
        int selectedText = 0xFF082F49;
        int defaultBg = 0xFF312E81;
        int defaultText = 0xFFE0E7FF;

        binding.btnModeOffline.setBackgroundTintList(ColorStateList.valueOf(offlineSelected ? selectedBg : defaultBg));
        binding.btnModeOffline.setTextColor(offlineSelected ? selectedText : defaultText);

        binding.btnModeOnline.setBackgroundTintList(ColorStateList.valueOf(offlineSelected ? defaultBg : selectedBg));
        binding.btnModeOnline.setTextColor(offlineSelected ? defaultText : selectedText);
    }

    public void showWaitingOpponentUi() {
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);

        binding.btnPlay.setEnabled(false);
        binding.btnOnline.setEnabled(false);
    }

    public void restoreMenuButtons() {
        binding.btnPlay.setEnabled(true);
        binding.btnOnline.setEnabled(true);
    }
}

