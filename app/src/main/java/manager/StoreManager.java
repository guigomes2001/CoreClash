package manager;

import android.view.View;
import android.widget.Toast;

import com.example.coreclash.MainActivity;
import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;

public class StoreManager {

    private final MainActivity activity;
    private final ActivityMainBinding binding;
    private final PlayerProfile profile;
    private final ProfileManager profileManager;
    private final BoardManager board;

    public StoreManager(MainActivity activity, ActivityMainBinding binding,
                        PlayerProfile profile, ProfileManager profileManager,
                        BoardManager board) {
        this.activity = activity;
        this.binding = binding;
        this.profile = profile;
        this.profileManager = profileManager;
        this.board = board;
        setupActions();
    }

    private void setupActions() {
        binding.btnStoreClose.setOnClickListener(v -> closeStore());

        binding.btnThemeRoyal.setOnClickListener(v -> buyOrEquipTheme("ROYAL", 180));
        binding.btnThemeVoid.setOnClickListener(v -> buyOrEquipTheme("VOID", 220));

        binding.btnStyleRune.setOnClickListener(v -> buyOrEquipStyle("RUNE", 140));
        binding.btnStyleFuture.setOnClickListener(v -> buyOrEquipStyle("FUTURE", 160));

        binding.btnBuyCoins.setOnClickListener(v -> {
            profile.coins += 500;
            profileManager.persistProfile();
            refreshStoreUI();
            Toast.makeText(activity, "Moedas adicionadas!", Toast.LENGTH_SHORT).show();
        });
    }

    public void openStore() {
        refreshStoreUI();
        playStoreTransition(() -> {
            binding.storeOverlay.setVisibility(View.VISIBLE);
            binding.storeOverlay.setAlpha(0f);
            binding.storeScreen.setTranslationY(40f);
            binding.storeOverlay.animate().alpha(1f).setDuration(200).start();
            binding.storeScreen.animate().translationY(0f).setDuration(240).start();
        });
    }

    public void closeStore() {
        binding.storeScreen.animate().translationY(30f).setDuration(160).start();
        binding.storeOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> binding.storeOverlay.setVisibility(View.GONE))
                .start();
    }

    private void refreshStoreUI() {
        if (profile != null) {
            binding.txtStoreCoinsFull.setText("Core Coins: " + profile.coins);
        }
    }

    private void buyOrEquipTheme(String themeId, int price) {
        if (profile.ownsTheme(themeId)) {
            profile.equippedTheme = themeId;
            Toast.makeText(activity, "Tema equipado", Toast.LENGTH_SHORT).show();
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedThemes.add(themeId);
            profile.equippedTheme = themeId;
            Toast.makeText(activity, "Tema comprado!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "Moedas insuficientes", Toast.LENGTH_SHORT).show();
            return;
        }

        finalizePurchase();
    }

    private void buyOrEquipStyle(String styleId, int price) {
        if (profile.ownsSymbolStyle(styleId)) {
            profile.equippedSymbolStyle = styleId;
            Toast.makeText(activity, "Estilo equipado", Toast.LENGTH_SHORT).show();
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedSymbolStyles.add(styleId);
            profile.equippedSymbolStyle = styleId;
            Toast.makeText(activity, "Estilo comprado!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "Moedas insuficientes", Toast.LENGTH_SHORT).show();
            return;
        }

        finalizePurchase();
    }

    private void finalizePurchase() {
        profileManager.persistProfile();
        refreshStoreUI();
        applyEquippedCosmetics();
        activity.updateHeaderStatus();
    }

    public void applyEquippedCosmetics() {
        if (profile == null || board == null) return;
        board.setBoardTheme(profile.equippedTheme);
        board.setSymbolStyle(profile.equippedSymbolStyle);
        board.resetBoard();
    }

    private void playStoreTransition(Runnable onEnd) {
        binding.storeTransitionOverlay.setVisibility(View.VISIBLE);
        binding.storeTransitionOverlay.setAlpha(0f);

        binding.txtCurtainTop.setTranslationX(-240f);
        binding.txtCurtainMiddle.setTranslationX(240f);
        binding.txtCurtainBottom.setTranslationX(-240f);

        binding.storeTransitionOverlay.animate().alpha(1f).setDuration(120).start();
        binding.txtCurtainTop.animate().translationX(0f).setDuration(240).start();
        binding.txtCurtainMiddle.animate().translationX(0f).setDuration(280).start();
        binding.txtCurtainBottom.animate().translationX(0f).setDuration(320).start();

        activity.handler.postDelayed(() -> {
            if (onEnd != null) onEnd.run();
            binding.storeTransitionOverlay.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction(() -> binding.storeTransitionOverlay.setVisibility(View.GONE))
                    .start();
        }, 360);
    }
}