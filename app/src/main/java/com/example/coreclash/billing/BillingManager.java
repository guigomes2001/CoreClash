package com.example.coreclash.billing;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.List;

public class BillingManager implements PurchasesUpdatedListener {

    public interface CoinsListener {
        void onCoinsGranted(int amount);
    }

    private static final String PRODUCT_COINS_SMALL = "coins_pack_small";
    private static final int COINS_SMALL_AMOUNT = 500;

    private BillingClient billingClient;
    private ProductDetails coinsPackDetails;
    private CoinsListener coinsListener;

    public void start(@NonNull Activity activity, @NonNull CoinsListener listener) {
        this.coinsListener = listener;
        billingClient = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryProducts();
                    restorePendingPurchases();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // reconnect lazily when player opens the store again
            }
        });
    }

    public boolean isReady() {
        return billingClient != null && billingClient.isReady() && coinsPackDetails != null;
    }

    private void queryProducts() {
        List<QueryProductDetailsParams.Product> products = new ArrayList<>();
        products.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_COINS_SMALL)
                .setProductType(BillingClient.ProductType.INAPP)
                .build());

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || productDetailsList.isEmpty()) {
                return;
            }
            coinsPackDetails = productDetailsList.get(0);
        });
    }

    private void restorePendingPurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                return;
            }
            for (Purchase purchase : purchases) {
                handlePurchase(purchase);
            }
        });
    }

    public boolean launchCoinsPackPurchase(@NonNull Activity activity) {
        if (billingClient == null || coinsPackDetails == null) {
            return false;
        }

        List<BillingFlowParams.ProductDetailsParams> products = new ArrayList<>();
        products.add(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(coinsPackDetails)
                .build());

        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(products)
                .build();

        BillingResult result = billingClient.launchBillingFlow(activity, flowParams);
        return result.getResponseCode() == BillingClient.BillingResponseCode.OK;
    }

    @Override
    public void onPurchasesUpdated(@NonNull BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || purchases == null) {
            return;
        }

        for (Purchase purchase : purchases) {
            handlePurchase(purchase);
        }
    }

    private void handlePurchase(@NonNull Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            return;
        }

        // Coin packs are consumables: consuming (instead of only acknowledging)
        // is what allows the same player to buy the pack again.
        ConsumeParams params = ConsumeParams.newBuilder()
                .setPurchaseToken(purchase.getPurchaseToken())
                .build();

        billingClient.consumeAsync(params, (result, token) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && coinsListener != null) {
                coinsListener.onCoinsGranted(COINS_SMALL_AMOUNT);
            }
        });
    }
}
