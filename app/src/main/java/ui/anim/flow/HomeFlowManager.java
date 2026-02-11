package ui.anim.flow;

import android.content.res.ColorStateList;
import android.view.View;

import androidx.annotation.NonNull;

import com.example.coreclash.R;
import com.example.coreclash.databinding.ActivityMainBinding;

import util.FontAwesomeIconFactory;

public class HomeFlowManager {

    public interface Callbacks {
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

        binding.btnPlay.setOnClickListener(v -> openModeModal());
        binding.btnOnline.setOnClickListener(v -> cb.onPlayOnlineClicked());

        binding.btnStore.setOnClickListener(v -> cb.onStoreClicked());
        binding.btnArena.setOnClickListener(v -> openModeModal());
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

        binding.btnModeLocalPassPlay.setOnClickListener(v -> {
            selectedMatchKind = enums.DomainMatchKind.LOCAL_PASS_PLAY;
            updateModeButtonStyles();
            cb.onModeChanged(selectedMatchKind);
        });

        binding.btnModeLocalLobby.setOnClickListener(v -> {
            selectedMatchKind = enums.DomainMatchKind.LOCAL_LOBBY;
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
            } else if (selectedMatchKind == enums.DomainMatchKind.LOCAL_PASS_PLAY) {
                cb.onConfirmLocalPassPlay();
            } else {
                cb.onConfirmLocalLobby();
            }
        });

        binding.modeOverlay.setOnClickListener(v -> closeModeModal());

        binding.btnGoogleLoginSettings.setOnClickListener(v -> cb.onGoogleLoginFromSettingsClicked());

        updateModeButtonStyles();
    }

    private void configureHomeMenuTiles() {
        binding.btnPlay.setText(R.string.btn_play);
        binding.btnOnline.setText(R.string.mode_online_world);
        binding.btnStore.setText(R.string.btn_store);
        binding.btnArena.setText(R.string.btn_modes);

        int iconColor = 0xFFDDEBFF;
        FontAwesomeIconFactory.applyTopIcon(binding.btnPlay, binding.getRoot().getContext().getString(R.string.fa_gamepad), 16, iconColor, 6);
        FontAwesomeIconFactory.applyTopIcon(binding.btnOnline, binding.getRoot().getContext().getString(R.string.fa_bolt), 16, iconColor, 6);
        FontAwesomeIconFactory.applyTopIcon(binding.btnStore, binding.getRoot().getContext().getString(R.string.fa_store), 16, iconColor, 6);
        FontAwesomeIconFactory.applyTopIcon(binding.btnArena, binding.getRoot().getContext().getString(R.string.fa_users), 16, iconColor, 6);
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
        int selectedBg = 0xFF22D3EE;
        int selectedText = 0xFF082F49;
        int defaultBg = 0xFF312E81;
        int defaultText = 0xFFE0E7FF;

        styleModeButton(binding.btnModeOnline, selectedMatchKind == enums.DomainMatchKind.ONLINE_PVP, selectedBg, selectedText, defaultBg, defaultText);
        styleModeButton(binding.btnModeOffline, selectedMatchKind == enums.DomainMatchKind.OFFLINE_BOT, selectedBg, selectedText, defaultBg, defaultText);
        styleModeButton(binding.btnModeLocalPassPlay, selectedMatchKind == enums.DomainMatchKind.LOCAL_PASS_PLAY, selectedBg, selectedText, defaultBg, defaultText);
        styleModeButton(binding.btnModeLocalLobby, selectedMatchKind == enums.DomainMatchKind.LOCAL_LOBBY, selectedBg, selectedText, defaultBg, defaultText);
    }

    private void styleModeButton(@NonNull android.widget.Button button,
                                 boolean selected,
                                 int selectedBg,
                                 int selectedText,
                                 int defaultBg,
                                 int defaultText) {
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? selectedBg : defaultBg));
        button.setTextColor(selected ? selectedText : defaultText);
    }

    public void showWaitingOpponentUi() {
        binding.homeOverlay.setVisibility(View.VISIBLE);
        binding.homeOverlay.setAlpha(1f);

        binding.btnPlay.setEnabled(false);
        binding.btnOnline.setEnabled(false);
        binding.btnArena.setEnabled(false);
    }

    public void restoreMenuButtons() {
        binding.btnPlay.setEnabled(true);
        binding.btnOnline.setEnabled(true);
        binding.btnArena.setEnabled(true);
    }
}
