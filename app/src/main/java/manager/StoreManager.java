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
            billingManager.start(activity, new BillingManager.PurchaseListener() {
                @Override
                public void onCoinsGranted(int amount) {
                    profile.coins += amount;
                    finalizePurchase();
                    Toast.makeText(context, context.getString(R.string.toast_coins_added), Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onRankedPassGranted() {
                    profile.rankedPassActive = true;
                    finalizePurchase();
                    Toast.makeText(context, context.getString(R.string.toast_ranked_pass_activated), Toast.LENGTH_SHORT).show();
                }
            });
        }

        setupActions();
    }

    private void setupActions() {
        binding.btnStoreOverlayClose.setOnClickListener(v -> closeStore());

        binding.btnThemeRoyal.setOnClickListener(v -> buyOrEquipTheme("ROYAL", 180));
        binding.btnThemeVoid.setOnClickListener(v -> buyOrEquipTheme("VOID", 220));

        binding.btnStyleRune.setOnClickListener(v -> buyOrEquipStyle("RUNE", 320));
        binding.btnStyleFuture.setOnClickListener(v -> buyOrEquipStyle("FUTURE", 180));
        binding.btnStyleNeon.setOnClickListener(v -> buyOrEquipStyle("NEON", 210));
        binding.btnStyleSamurai.setOnClickListener(v -> buyOrEquipStyle("SAMURAI", 240));

        binding.btnBuyCoins.setOnClickListener(v -> buyCoreclashSmall());
        binding.btnBuyCoinsPro.setOnClickListener(v -> buyCoreclashPro());
        binding.btnRestorePurchases.setOnClickListener(v -> restorePurchases());
        binding.btnBuyRankedPass.setOnClickListener(v -> buyRankedPass());
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



    private void buyRankedPass() {
        if (profile.rankedPassActive) {
            Toast.makeText(context, context.getString(R.string.store_ranked_pass_active), Toast.LENGTH_SHORT).show();
            return;
        }

        if (context instanceof Activity activity
                && billingManager.launchProductPurchase(activity, BillingManager.PRODUCT_RANKED_PASS)) {
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
        binding.btnBuyRankedPass.setText(profile.rankedPassActive
                ? context.getString(R.string.store_ranked_pass_active)
                : context.getString(R.string.store_ranked_pass_price));
        refreshOwnershipCards();
    }

    private void buyOrEquipTheme(String themeId, int price) {
        if (profile.ownsTheme(themeId)) {
            if (themeId.equals(profile.equippedSymbolStyle)) {
                profile.equippedTheme = "ARENA";
                profile.equippedSymbolStyle = "CLASSIC";
                Toast.makeText(context, context.getString(R.string.toast_style_default_equipped), Toast.LENGTH_SHORT).show();
            } else {
                profile.equippedTheme = themeId;
                profile.equippedSymbolStyle = themeId;
                if (!profile.ownedSymbolStyles.contains(themeId)) {
                    profile.ownedSymbolStyles.add(themeId);
                }
                Toast.makeText(context, context.getString(R.string.toast_theme_equipped), Toast.LENGTH_SHORT).show();
            }
        } else if (profile.coins >= price) {
            profile.coins -= price;
            profile.ownedThemes.add(themeId);
            if (!profile.ownedSymbolStyles.contains(themeId)) {
                profile.ownedSymbolStyles.add(themeId);
            }
            profile.equippedTheme = themeId;
            profile.equippedSymbolStyle = themeId;
            Toast.makeText(context, context.getString(R.string.toast_theme_bought), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, context.getString(R.string.toast_insufficient_coins), Toast.LENGTH_SHORT).show();
            return;
        }

        finalizePurchase();
    }

    private void buyOrEquipStyle(String styleId, int price) {
        if (profile.ownsSymbolStyle(styleId)) {
            if (styleId.equals(profile.equippedSymbolStyle)) {
                profile.equippedSymbolStyle = "CLASSIC";
                Toast.makeText(context, context.getString(R.string.toast_style_default_equipped), Toast.LENGTH_SHORT).show();
            } else {
                profile.equippedSymbolStyle = styleId;
                Toast.makeText(context, context.getString(R.string.toast_style_equipped), Toast.LENGTH_SHORT).show();
            }
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


    private void refreshOwnershipCards() {
        bindCardState(profile.ownsSymbolStyle("ROYAL") || profile.ownsTheme("ROYAL"), "ROYAL".equals(profile.equippedSymbolStyle),
                binding.badgeThemeRoyal, binding.btnThemeRoyal, binding.cardThemeRoyal,
                context.getString(R.string.store_theme_royal_price));

        bindCardState(profile.ownsSymbolStyle("VOID") || profile.ownsTheme("VOID"), "VOID".equals(profile.equippedSymbolStyle),
                binding.badgeThemeVoid, binding.btnThemeVoid, binding.cardThemeVoid,
                context.getString(R.string.store_theme_void_price));

        bindCardState(profile.ownsSymbolStyle("RUNE"), "RUNE".equals(profile.equippedSymbolStyle),
                binding.badgeStyleRune, binding.btnStyleRune, binding.cardStyleRune,
                context.getString(R.string.store_style_rune_price));

        bindCardState(profile.ownsSymbolStyle("FUTURE"), "FUTURE".equals(profile.equippedSymbolStyle),
                binding.badgeStyleFuture, binding.btnStyleFuture, binding.cardStyleFuture,
                context.getString(R.string.store_style_future_price));

        bindCardState(profile.ownsSymbolStyle("NEON"), "NEON".equals(profile.equippedSymbolStyle),
                binding.badgeStyleNeon, binding.btnStyleNeon, binding.cardStyleNeon,
                context.getString(R.string.store_style_neon_price));

        bindCardState(profile.ownsSymbolStyle("SAMURAI"), "SAMURAI".equals(profile.equippedSymbolStyle),
                binding.badgeStyleSamurai, binding.btnStyleSamurai, binding.cardStyleSamurai,
                context.getString(R.string.store_style_samurai_price));
    }

    private void bindCardState(boolean owned, boolean equipped, View badge, android.widget.Button button, View card, String priceText) {
        badge.setVisibility(owned ? View.VISIBLE : View.GONE);
        button.setText(owned
                ? context.getString(equipped ? R.string.store_btn_unequip : R.string.store_btn_equip)
                : priceText);
        button.setBackgroundResource(equipped ? R.drawable.bg_store_item_equipped : R.drawable.bg_store_item);
        button.setTextColor(android.graphics.Color.parseColor(equipped ? "#D8EEFF" : "#FFFFFF"));
        button.setAlpha(equipped ? 0.78f : 1f);
        card.setBackgroundResource(equipped ? R.drawable.bg_store_clash : R.drawable.bg_store_section);
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

    public void playCurtainTransition(Runnable onEnd) {
        playStoreTransition(onEnd);
    }

    private void playStoreTransition(Runnable onEnd) {
        binding.storeTransitionOverlay.setVisibility(View.VISIBLE);
        binding.storeTransitionOverlay.setAlpha(0f);

        binding.txtCurtainTop.setTranslationX(-260f);
        binding.txtCurtainMiddle.setTranslationX(260f);
        binding.txtCurtainBottom.setTranslationX(-260f);
        binding.txtCurtainTop.setAlpha(0f);
        binding.txtCurtainMiddle.setAlpha(0f);
        binding.txtCurtainBottom.setAlpha(0f);
        binding.txtCurtainTop.setScaleX(0.94f);
        binding.txtCurtainMiddle.setScaleX(0.94f);
        binding.txtCurtainBottom.setScaleX(0.94f);

        binding.storeTransitionOverlay.animate().alpha(1f).setDuration(130).start();
        binding.txtCurtainTop.animate().translationX(0f).alpha(1f).scaleX(1f).setDuration(250).start();
        binding.txtCurtainMiddle.animate().translationX(0f).alpha(1f).scaleX(1f).setDuration(290).start();
        binding.txtCurtainBottom.animate().translationX(0f).alpha(1f).scaleX(1f).setDuration(330).start();

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
