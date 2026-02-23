package com.example.coreclash.battlepass.data;

import androidx.annotation.NonNull;

import com.example.coreclash.battlepass.model.Mission;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class MissionsRepository {

    private final FirebaseFirestore firestore = FirebaseFirestore.getInstance();

    public void fetchMissions(@NonNull String seasonId,
                              @NonNull OnSuccessListener<List<Mission>> listener,
                              @NonNull OnFailureListener failureListener) {
        firestore.collection("battlePassSeasons")
                .document(seasonId)
                .collection("missions")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Mission> result = new ArrayList<>();
                    snapshot.getDocuments().forEach(doc -> result.add(Mission.fromMap(doc.getId(), doc.getData())));
                    listener.onSuccess(result);
                })
                .addOnFailureListener(failureListener);
    }
}
