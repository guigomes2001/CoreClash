package com.example.coreclash.billing;

import android.app.Activity;

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

import java.util.ArrayList;
import java.util.List;
import util.NullUtil;

public class BillingManager implements PurchasesUpdatedListener {

    public interface CoinsListener {
        void onCoinsGranted(int amount);
    }

    private static final String PRODUCT_COINS_SMALL = "coins_pack_small";

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
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // reconnect lazily when player opens the store again
            }
        });
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

    public boolean launchCoinsPackPurchase(@NonNull Activity activity) {
        if (NullUtil.isNull(billingClient) || NullUtil.isNull(coinsPackDetails)) {
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
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || NullUtil.isNull(purchases)) {
            return;
        }

        for (Purchase purchase : purchases) {
            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                if (!purchase.isAcknowledged()) {
                    AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();
                    billingClient.acknowledgePurchase(params, result -> {
                    });
                }
                if (!NullUtil.isNull(coinsListener)) {
                    coinsListener.onCoinsGranted(500);
                }
            }
        }
    }
}
