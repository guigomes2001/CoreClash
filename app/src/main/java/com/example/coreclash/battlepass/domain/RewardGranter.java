package com.example.coreclash.battlepass.domain;

import androidx.annotation.NonNull;

import com.example.coreclash.battlepass.model.BpState;
import com.example.coreclash.battlepass.model.Reward;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardGranter {

    public interface ClaimCallback {
        void onClaimed(int convertedCurrency);
        void onError(Exception error);
    }

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public void claimRewardTransactional(@NonNull BpState state,
                                         int level,
                                         boolean premiumTrack,
                                         @NonNull Reward reward,
                                         @NonNull ClaimCallback callback) {
        String uid = auth.getCurrentUser() == null ? "" : auth.getCurrentUser().getUid();
        if (uid.isBlank()) {
            callback.onError(new IllegalStateException("Authenticated user required"));
            return;
        }

        String rewardClaimId = state.seasonId + ":" + level + ":" + (premiumTrack ? "premium" : "free");
        DocumentReference stateRef = firestore.collection("battlePassStates").document(uid + "_" + state.seasonId);
        DocumentReference inventoryRef = firestore.collection("inventories").document(uid);

        firestore.runTransaction((Transaction.Function<Integer>) transaction -> {
            var stateSnap = transaction.get(stateRef);
            List<String> claimed = (List<String>) stateSnap.get("claimedRewardIds");
            if (claimed != null && claimed.contains(rewardClaimId)) {
                throw new IllegalStateException("Reward already claimed");
            }

            int convertedCurrency = 0;
            if ("cosmetic".equals(reward.type) && userOwnsCosmetic(transaction, inventoryRef, reward.itemId)) {
                convertedCurrency = Math.max(20, reward.amount);
                applyCurrency(transaction, inventoryRef, convertedCurrency);
            } else {
                applyReward(transaction, inventoryRef, reward);
            }

            transaction.update(stateRef, "claimedRewardIds", FieldValue.arrayUnion(rewardClaimId));
            return convertedCurrency;
        }).addOnSuccessListener(callback::onClaimed)
                .addOnFailureListener(callback::onError);
    }

    private boolean userOwnsCosmetic(Transaction transaction, DocumentReference inventoryRef, String itemId) throws FirebaseFirestoreException {
        var inv = transaction.get(inventoryRef);
        List<String> cosmetics = (List<String>) inv.get("cosmeticsOwned");
        return cosmetics != null && cosmetics.contains(itemId);
    }

    private void applyCurrency(Transaction transaction, DocumentReference inventoryRef, int amount) {
        transaction.set(inventoryRef, Map.of("coins", FieldValue.increment(amount)), com.google.firebase.firestore.SetOptions.merge());
    }

    private void applyReward(Transaction transaction, DocumentReference inventoryRef, Reward reward) {
        Map<String, Object> map = new HashMap<>();
        switch (reward.type) {
            case "currency" -> map.put("coins", FieldValue.increment(reward.amount));
            case "token" -> map.put("tokens", FieldValue.increment(reward.amount));
            case "cosmetic" -> map.put("cosmeticsOwned", FieldValue.arrayUnion(reward.itemId));
            default -> map.put("coins", FieldValue.increment(Math.max(1, reward.amount)));
        }
        transaction.set(inventoryRef, map, com.google.firebase.firestore.SetOptions.merge());
    }
}
