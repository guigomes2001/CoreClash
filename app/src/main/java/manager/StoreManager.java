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
            Toast.makeText(activity, "Créditos injetados!", Toast.LENGTH_SHORT).show();
        });
    }

    public void openStore() {
        refreshStoreUI();
        playStoreTransition(() -> {
            binding.storeOverlay.setVisibility(View.VISIBLE);
            binding.storeOverlay.setAlpha(0f);
            binding.storeScreen.setTranslationY(60f);
            binding.storeOverlay.animate().alpha(1f).setDuration(200).start();
            binding.storeScreen.animate().translationY(0f).setDuration(250).start();
        });
    }

    public void closeStore() {
        binding.storeScreen.animate().translationY(40f).setDuration(160).start();
        binding.storeOverlay.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction(() -> binding.storeOverlay.setVisibility(View.GONE))
                .start();
    }

    private void refreshStoreUI() {
        if (profile == null) return;
        binding.txtStoreCoinsFull.setText("Core Coins: " + profile.coins);

        updateButtonState(binding.btnThemeRoyal, "ROYAL", "180");
        updateButtonState(binding.btnThemeVoid, "VOID", "220");
        updateButtonState(binding.btnStyleRune, "RUNE", "140");
        updateButtonState(binding.btnStyleFuture, "FUTURE", "160");
    }

    private void updateButtonState(android.widget.Button btn, String id, String price) {
        boolean owns = profile.ownedThemes.contains(id) || profile.ownedSymbolStyles.contains(id);
        if (owns) {
            boolean isEquipped = id.equals(profile.equippedTheme) || id.equals(profile.equippedSymbolStyle);
            btn.setText(isEquipped ? "EQUIPADO" : "EQUIPAR");
            btn.setAlpha(isEquipped ? 0.5f : 1.0f);
        } else {
            btn.setText(price);
            btn.setAlpha(1.0f);
        }
    }

    private void buyOrEquipTheme(String themeId, int price) {
        if (profile.ownsTheme(themeId)) {
            profile.equippedTheme = themeId;
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedThemes.add(themeId);
            profile.equippedTheme = themeId;
            Toast.makeText(activity, "Tema adquirido!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "Saldo insuficiente", Toast.LENGTH_SHORT).show();
            return;
        }
        finalizePurchase();
    }

    private void buyOrEquipStyle(String styleId, int price) {
        if (profile.ownsSymbolStyle(styleId)) {
            profile.equippedSymbolStyle = styleId;
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedSymbolStyles.add(styleId);
            profile.equippedSymbolStyle = styleId;
            Toast.makeText(activity, "Estilo desbloqueado!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, "Saldo insuficiente", Toast.LENGTH_SHORT).show();
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
        if (profile == null) return;

        binding.gameBoardView.setTheme(profile.equippedTheme);
        binding.gameBoardView.setSymbolStyle(profile.equippedSymbolStyle);

        if (binding.victoryLineView != null) {
            binding.victoryLineView.setTheme(profile.equippedTheme);
        }
    }

    private void playStoreTransition(Runnable onEnd) {
        binding.storeTransitionOverlay.setVisibility(View.VISIBLE);
        binding.storeTransitionOverlay.setAlpha(0f);
        binding.txtCurtainTop.setTranslationX(-300f);
        binding.txtCurtainMiddle.setTranslationX(300f);
        binding.txtCurtainBottom.setTranslationX(-300f);

        binding.storeTransitionOverlay.animate().alpha(1f).setDuration(150).start();
        binding.txtCurtainTop.animate().translationX(0f).setDuration(300).start();
        binding.txtCurtainMiddle.animate().translationX(0f).setDuration(350).start();
        binding.txtCurtainBottom.animate().translationX(0f).setDuration(400).start();

        binding.getRoot().postDelayed(() -> {
            if (onEnd != null) onEnd.run();
            binding.storeTransitionOverlay.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> binding.storeTransitionOverlay.setVisibility(View.GONE))
                    .start();
        }, 450);
    }
}