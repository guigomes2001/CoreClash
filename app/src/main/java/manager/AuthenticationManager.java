package manager;

import android.content.Intent;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import com.example.coreclash.MainActivity;
import com.example.coreclash.R;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

import java.util.Objects;

public class AuthenticationManager {

    private final MainActivity activity;
    private final FirebaseAuth firebaseAuth;
    private final GoogleSignInClient googleSignInClient;

    public interface AuthCallback {
        void onGoogleLinked(String displayName);
        void onFailure(String message);
    }

    public AuthenticationManager(MainActivity activity) {
        this.activity = activity;
        this.firebaseAuth = FirebaseAuth.getInstance();

        String webClientId = activity.getString(R.string.default_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        this.googleSignInClient = GoogleSignIn.getClient(activity, gso);
    }

    public void startGoogleSignIn(ActivityResultLauncher<Intent> launcher) {
        launcher.launch(googleSignInClient.getSignInIntent());
    }

    public void handleSignInResult(Intent data, AuthCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null) {
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                linkWithGoogle(credential, callback);
            }
        } catch (ApiException e) {
            Log.e("AUTH", "Google Sign-In failed", e);
            callback.onFailure("Erro ao conectar com Google");
        }
    }

    private void linkWithGoogle(AuthCredential credential, AuthCallback callback) {
        var user = firebaseAuth.getCurrentUser();
        if (user != null) {
            user.linkWithCredential(credential)
                    .addOnSuccessListener(authResult -> {
                        String name = Objects.requireNonNull(authResult.getUser()).getDisplayName();
                        callback.onGoogleLinked(name);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("AUTH", "Link failed", e);
                        callback.onFailure("Falha ao vincular: Conta já em uso.");
                    });
        }
    }
}