package manager;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.coreclash.R;
import com.example.coreclash.data.FirebaseProfileRepository;
import com.example.coreclash.data.LocalProfileRepository;
import com.example.coreclash.data.ProfileRepository;
import com.example.coreclash.databinding.ActivityMainBinding;
import com.example.coreclash.model.PlayerProfile;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.Objects;

import util.NullUtil;

public class PlayerServicesManager {

    public interface UiRunner {
        void runOnUi(@NonNull Runnable r);
    }

    public interface Callbacks {
        void onProfileReady(@NonNull PlayerProfile profile);
        void onStoreReady(@NonNull StoreManager storeManager);
        void onRender();
    }

    // ✅ separa os contexts
    private final Context appContext;
    private final Context uiContext;

    private final Handler handler;
    private final UiRunner ui;
    private final ActivityMainBinding binding;
    private final BoardManager board;
    private final Callbacks cb;

    private ProfileManager profileManager;
    private StoreManager storeManager;
    private PlayerProfile currentProfile;

    public PlayerServicesManager(
            @NonNull Context context,
            @NonNull UiRunner uiRunner,
            @NonNull ActivityMainBinding binding,
            @NonNull BoardManager board,
            @NonNull Handler handler,
            @NonNull Callbacks callbacks
    ) {
        this.uiContext = context;
        this.appContext = context.getApplicationContext();

        this.ui = uiRunner;
        this.binding = binding;
        this.board = board;
        this.handler = handler;
        this.cb = callbacks;
    }

    public void start() {
        profileManager = new ProfileManager();
        profileManager.localProfileRepository = new LocalProfileRepository(appContext);

        FirebaseAuth auth = FirebaseAuth.getInstance();

        auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    String firebaseUid = Objects.requireNonNull(authResult.getUser()).getUid();

                    profileManager.profileRepository = new FirebaseProfileRepository();
                    profileManager.profileRepository.loadOrCreateProfile(new ProfileRepository.Callback() {
                        @Override public void onSuccess(@NonNull PlayerProfile profile) {
                            profile.uid = firebaseUid;
                            onProfileLoaded(profile);
                        }

                        @Override public void onError(@NonNull String error) {
                            loadLocalFallback();
                        }
                    });
                })
                .addOnFailureListener(e -> loadLocalFallback());
    }

    public void onDestroy() {

    }

    public void updateDisplayNameAndPersist(@NonNull String displayName) {
        if (NullUtil.isNull(currentProfile)) {
            return;
        }

        currentProfile.displayName = displayName;

        try {
            if (!NullUtil.isNull(profileManager)) {
                profileManager.setCurrentProfile(currentProfile);
                profileManager.persistProfile();
            }
            if (!NullUtil.isNull(profileManager) && !NullUtil.isNull(profileManager.localProfileRepository)) {
                profileManager.localProfileRepository.saveProfile(currentProfile);
            }

            var user = FirebaseAuth.getInstance().getCurrentUser();
            if (!NullUtil.isNull(user)) {
                UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build();
                user.updateProfile(req);
            }
        } catch (Exception ignored) {}
    }

    private void onProfileLoaded(@NonNull PlayerProfile profile) {
        currentProfile = profile;

        try {
            profileManager.setCurrentProfile(profile);
            if (!NullUtil.isNull(profileManager.localProfileRepository)) {
                profileManager.localProfileRepository.saveProfile(profile);
            }
        } catch (Exception ignored) {}

        storeManager = new StoreManager(
                uiContext,
                handler,
                binding,
                profile,
                profileManager,
                board,
                () -> ui.runOnUi(cb::onRender)
        );

        ui.runOnUi(() -> {
            try {
                storeManager.applyEquippedCosmetics();
            } catch (Exception ignored) {}

            cb.onProfileReady(profile);
            cb.onStoreReady(storeManager);
            cb.onRender();
        });
    }

    private void loadLocalFallback() {
        var localRepo = new LocalProfileRepository(appContext);

        localRepo.loadOrCreateProfile(new ProfileRepository.Callback() {
            @Override public void onSuccess(@NonNull PlayerProfile profile) {
                currentProfile = profile;

                ui.runOnUi(() -> {
                    cb.onProfileReady(profile);
                    cb.onRender();
                    Toast.makeText(uiContext, uiContext.getString(R.string.toast_offline_loaded), Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onError(@NonNull String error) {
                Log.e("PlayerServicesManager", "Fallback local failed: " + error);
                currentProfile = PlayerProfile.createDefault("temp_" + System.currentTimeMillis());
                ui.runOnUi(cb::onRender);
            }
        });
    }
}
