package com.example.coreclash.billing;

import android.app.Activity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.NullUtil;

public class BillingManager implements PurchasesUpdatedListener {

    public interface PurchaseListener {
        void onCoinsGranted(int amount);
        void onRankedPassGranted();
    }

    public interface RestoreListener {
        void onRestoreCompleted(int restoredPurchasesCount);
    }

    public static final String PRODUCT_CORECLASH_SMALL = "coreclash_pack_small";
    public static final String PRODUCT_CORECLASH_PRO = "coreclash_pack_pro";
    public static final String PRODUCT_RANKED_PASS = "coreclash_ranked_pass";

    private static final String PREFS_NAME = "billing_prefs";
    private static final String TOKEN_PREFIX = "ack_";

    private final Map<String, ProductDetails> productDetailsById = new HashMap<>();

    private BillingClient billingClient;
    private PurchaseListener purchaseListener;
    private SharedPreferences prefs;

    public void start(@NonNull Activity activity, @NonNull PurchaseListener listener) {
        this.purchaseListener = listener;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);

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
                // reconnect lazily
            }
        });
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_CORECLASH_SMALL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_CORECLASH_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_RANKED_PASS)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());

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
        List<String> products = purchase.getProducts();
        for (String productId : products) {
            if (PRODUCT_RANKED_PASS.equals(productId)) {
                grantedPass = true;
                continue;
            }
            granted += coresForProduct(productId);
        }

        if (granted <= 0 && !grantedPass) {
            granted = 500;
        }

        markTokenProcessed(token);

        if (!NullUtil.isNull(purchaseListener)) {
            if (granted > 0) purchaseListener.onCoinsGranted(granted);
            if (grantedPass) purchaseListener.onRankedPassGranted();
        }
        return 1;
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
