package manager;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.coreclash.R;
import com.example.coreclash.billing.BillingManager;
import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;

import util.NullUtil;

public class StoreManager {

    public interface Callbacks {
        void onHeaderShouldRefresh();
    }

    private final Context context;
    private final android.os.Handler handler;
    private final ActivityMainBinding binding;
    private final PlayerProfile profile;
    private final ProfileManager profileManager;
    private final BoardManager board;
    private final Callbacks cb;
    private final BillingManager billingManager = new BillingManager();

    public StoreManager(
            @NonNull Context context,
            @NonNull android.os.Handler handler,
            @NonNull ActivityMainBinding binding,
            @NonNull PlayerProfile profile,
            @NonNull ProfileManager profileManager,
            @NonNull BoardManager board,
            @NonNull Callbacks callbacks
    ) {
        this.context = context;
        this.handler = handler;
        this.binding = binding;
        this.profile = profile;
        this.profileManager = profileManager;
        this.board = board;
        this.cb = callbacks;

        if (context instanceof Activity activity) {
            billingManager.start(activity, amount -> {
                profile.coins += amount;
                finalizePurchase();
                Toast.makeText(context, context.getString(R.string.toast_coins_added), Toast.LENGTH_SHORT).show();
            });
        }

        setupActions();
    }

    private void setupActions() {
        binding.btnStoreClose.setOnClickListener(v -> closeStore());

        binding.btnThemeRoyal.setOnClickListener(v -> buyOrEquipTheme("ROYAL", 180));
        binding.btnThemeVoid.setOnClickListener(v -> buyOrEquipTheme("VOID", 220));

        binding.btnStyleRune.setOnClickListener(v -> buyOrEquipStyle("RUNE", 320));
        binding.btnStyleFuture.setOnClickListener(v -> buyOrEquipStyle("FUTURE", 180));
        binding.btnStyleNeon.setOnClickListener(v -> buyOrEquipStyle("NEON", 210));
        binding.btnStyleSamurai.setOnClickListener(v -> buyOrEquipStyle("SAMURAI", 240));

        binding.btnBuyCoins.setOnClickListener(v -> buyCoreclashSmall());
        binding.btnBuyCoinsPro.setOnClickListener(v -> buyCoreclashPro());
        binding.btnRestorePurchases.setOnClickListener(v -> restorePurchases());
    }

    private void buyCoreclashSmall() {
        if (context instanceof Activity activity && billingManager.launchProductPurchase(activity, BillingManager.PRODUCT_CORECLASH_SMALL)) {
            return;
        }

        Toast.makeText(context, context.getString(R.string.toast_store_billing_unavailable), Toast.LENGTH_SHORT).show();
    }

    private void buyCoreclashPro() {
        if (context instanceof Activity activity && billingManager.launchProductPurchase(activity, BillingManager.PRODUCT_CORECLASH_PRO)) {
            return;
        }

        Toast.makeText(context, context.getString(R.string.toast_store_billing_unavailable), Toast.LENGTH_SHORT).show();
    }

    private void restorePurchases() {
        billingManager.restorePurchases(restoredCount -> {
            if (restoredCount > 0) {
                Toast.makeText(context, context.getString(R.string.toast_restore_success, restoredCount), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getString(R.string.toast_restore_empty), Toast.LENGTH_SHORT).show();
            }
            refreshStoreUI();
        });
    }

    public void openStore() {
        openStore(false);
    }

    public void openStore(boolean focusCoreclashShop) {
        refreshStoreUI();
        playStoreTransition(() -> {
            binding.storeOverlay.setVisibility(View.VISIBLE);
            binding.storeOverlay.setAlpha(0f);
            binding.storeScreen.setTranslationY(40f);
            binding.storeOverlay.animate().alpha(1f).setDuration(200).start();
            binding.storeScreen.animate().translationY(0f).setDuration(240).start();

            if (focusCoreclashShop) {
                binding.storeScreen.post(() -> {
                    int targetY = Math.max(0, binding.sectionCoreclashShop.getTop() - 20);
                    binding.storeScreen.smoothScrollTo(0, targetY);
                });
            }
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
        binding.txtStoreCoinsFull.setText(context.getString(R.string.store_coins_format, profile.coins));
        binding.txtHomeWallet.setText(context.getString(R.string.store_coins_format, profile.coins));
    }

    private void buyOrEquipTheme(String themeId, int price) {
        if (profile.ownsTheme(themeId)) {
            profile.equippedTheme = themeId;
            Toast.makeText(context, context.getString(R.string.toast_theme_equipped), Toast.LENGTH_SHORT).show();
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedThemes.add(themeId);
            profile.equippedTheme = themeId;
            Toast.makeText(context, context.getString(R.string.toast_theme_bought), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, context.getString(R.string.toast_insufficient_coins), Toast.LENGTH_SHORT).show();
            return;
        }

        finalizePurchase();
    }

    private void buyOrEquipStyle(String styleId, int price) {
        if (profile.ownsSymbolStyle(styleId)) {
            profile.equippedSymbolStyle = styleId;
            Toast.makeText(context, context.getString(R.string.toast_style_equipped), Toast.LENGTH_SHORT).show();
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedSymbolStyles.add(styleId);
            profile.equippedSymbolStyle = styleId;
            Toast.makeText(context, context.getString(R.string.toast_style_bought), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, context.getString(R.string.toast_insufficient_coins), Toast.LENGTH_SHORT).show();
            return;
        }

        finalizePurchase();
    }

    private void finalizePurchase() {
        profileManager.persistProfile();
        refreshStoreUI();
        applyEquippedCosmetics();
        if (!NullUtil.isNull(cb)) cb.onHeaderShouldRefresh();
    }

    public void applyEquippedCosmetics() {
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

        handler.postDelayed(() -> {
            if (!NullUtil.isNull(onEnd)) onEnd.run();
            binding.storeTransitionOverlay.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction(() -> binding.storeTransitionOverlay.setVisibility(View.GONE))
                    .start();
        }, 360);
    }
}
