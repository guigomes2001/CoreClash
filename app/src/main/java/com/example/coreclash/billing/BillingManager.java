package com.example.coreclash.billing;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.NullUtil;

public class BillingManager implements PurchasesUpdatedListener {

    public interface PurchaseListener {
        void onCoinsGranted(int amount);
        void onRankedPassGranted();
        default void onPremiumEntitlementChanged(boolean premiumOwned) { }
    }

    public interface RestoreListener {
        void onRestoreCompleted(int restoredPurchasesCount);
    }

    public interface PremiumRestoreListener {
        void onPremiumRestored(boolean active);
    }

    public static final String PRODUCT_CORECLASH_SMALL = "coreclash_pack_small";
    public static final String PRODUCT_CORECLASH_PRO = "coreclash_pack_pro";
    public static final String PRODUCT_RANKED_PASS = "coreclash_ranked_pass";
    public static final String PRODUCT_BATTLE_PASS_PREMIUM = "coreclash_battle_pass_premium";

    private static final String PREFS_NAME = "billing_prefs";
    private static final String TOKEN_PREFIX = "ack_";

    private final Map<String, ProductDetails> productDetailsById = new HashMap<>();

    private BillingClient billingClient;
    private PurchaseListener purchaseListener;
    private SharedPreferences prefs;
    private Context context;

    public void start(@NonNull Activity activity, @NonNull PurchaseListener listener) {
        this.purchaseListener = listener;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        this.context = activity.getApplicationContext();

        billingClient = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProducts();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
            }
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(buildInApp(PRODUCT_CORECLASH_SMALL));
        products.add(buildInApp(PRODUCT_CORECLASH_PRO));
        products.add(buildInApp(PRODUCT_RANKED_PASS));
        products.add(buildInApp(PRODUCT_BATTLE_PASS_PREMIUM));

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, detailsList) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || NullUtil.isNull(detailsList)) {
                return;
            }

            productDetailsById.clear();
            for (ProductDetails details : detailsList) {
                productDetailsById.put(details.getProductId(), details);
            }
        });
    }

    private QueryProductDetailsParams.Product buildInApp(String productId) {
        return QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
    }

    public boolean launchProductPurchase(@NonNull Activity activity, @NonNull String productId) {
        if (NullUtil.isNull(billingClient) || !productDetailsById.containsKey(productId)) {
            return false;
        }

        ProductDetails details = productDetailsById.get(productId);
        if (NullUtil.isNull(details)) {
            return false;
        }

        List<BillingFlowParams.ProductDetailsParams> products = new ArrayList<>();
        products.add(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build());

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(products)
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        return result.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    public boolean launchPremiumBattlePassPurchase(@NonNull Activity activity) {
        return launchProductPurchase(activity, PRODUCT_BATTLE_PASS_PREMIUM);
    }

    public void restorePremiumPass(@NonNull PremiumRestoreListener listener) {
        if (NullUtil.isNull(billingClient)) {
            listener.onPremiumRestored(false);
            return;
        }
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || NullUtil.isNull(purchases)) {
                listener.onPremiumRestored(false);
                return;
            }
            boolean hasPremium = false;
            for (Purchase purchase : purchases) {
                if (purchase.getProducts().contains(PRODUCT_BATTLE_PASS_PREMIUM)
                        && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    hasPremium = true;
                    processPurchase(purchase);
                }
            }
            listener.onPremiumRestored(hasPremium);
        });
    }

    public void restorePurchases(@NonNull RestoreListener listener) {
        if (NullUtil.isNull(billingClient)) {
            listener.onRestoreCompleted(0);
            return;
        }

        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || NullUtil.isNull(purchases)) {
                listener.onRestoreCompleted(0);
                return;
            }

            int restored = 0;
            for (Purchase purchase : purchases) {
                restored += processPurchase(purchase);
            }
            listener.onRestoreCompleted(restored);
        });
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || NullUtil.isNull(purchases)) {
            return;
        }

        for (Purchase purchase : purchases) {
            processPurchase(purchase);
        }
    }

    private int processPurchase(@NonNull Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            return 0;
        }

        String token = purchase.getPurchaseToken();
        if (isTokenProcessed(token)) {
            return 0;
        }

        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(token)
                    .build();
            billingClient.acknowledgePurchase(params, result -> { });
        }

        int granted = 0;
        boolean grantedPass = false;
        boolean premiumOwned = false;
        List<String> products = purchase.getProducts();
        for (String productId : products) {
            if (PRODUCT_RANKED_PASS.equals(productId)) {
                grantedPass = true;
                continue;
            }
            if (PRODUCT_BATTLE_PASS_PREMIUM.equals(productId)) {
                premiumOwned = true;
                continue;
            }
            granted += coresForProduct(productId);
        }

        if (granted <= 0 && !grantedPass && !premiumOwned) {
            granted = 500;
        }

        markTokenProcessed(token);

        if (!NullUtil.isNull(purchaseListener)) {
            if (granted > 0) purchaseListener.onCoinsGranted(granted);
            if (grantedPass) purchaseListener.onRankedPassGranted();
            if (premiumOwned) purchaseListener.onPremiumEntitlementChanged(true);
        }
        if (premiumOwned) {
            syncPremiumEntitlementFirestore(true);
        }
        return 1;
    }

    private void syncPremiumEntitlementFirestore(boolean premiumOwned) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("premiumOwned", premiumOwned);
        payload.put("updatedAt", System.currentTimeMillis());
        FirebaseFirestore.getInstance().collection("battlePassEntitlements")
                .document(uid)
                .set(payload, SetOptions.merge());
    }

    private int coresForProduct(@NonNull String productId) {
        return switch (productId) {
            case PRODUCT_CORECLASH_SMALL -> 500;
            case PRODUCT_CORECLASH_PRO -> 1200;
            default -> 0;
        };
    }

    private boolean isTokenProcessed(@NonNull String token) {
        return !NullUtil.isNull(prefs) && prefs.getBoolean(TOKEN_PREFIX + token, false);
    }

    private void markTokenProcessed(@NonNull String token) {
        if (NullUtil.isNull(prefs)) return;
        prefs.edit().putBoolean(TOKEN_PREFIX + token, true).apply();
    }
}
