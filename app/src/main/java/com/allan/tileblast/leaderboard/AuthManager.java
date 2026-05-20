package com.allan.tileblast.leaderboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.Random;

/**
 * Manages Firebase Authentication for TileBlast.
 *
 * Lifecycle:
 *   - On first launch, signs in anonymously and generates a "PlayerNNNN" display name.
 *   - Players can later link a Google account, which preserves scores under the same UID.
 *   - Auth state (userId, displayName, isAnonymous) is persisted in SharedPreferences.
 */
public class AuthManager {

    private static final String TAG = "AuthManager";

    private static final String PREFS_NAME = "tile_blast_auth";
    private static final String KEY_USER_ID = "firebase_user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_IS_ANONYMOUS = "is_anonymous";

    @SuppressWarnings("StaticFieldLeak")
    private static AuthManager instance;

    private final Context appContext;
    private final SharedPreferences prefs;
    private final FirebaseAuth firebaseAuth;
    private final Random random;

    @Nullable private GoogleSignInClient googleSignInClient;
    @Nullable private String userId;
    @Nullable private String displayName;
    private boolean isAnonymous;

    public interface AuthCallback {
        void onSuccess();
        void onFailure(String errorMessage);
    }

    private AuthManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.random = new Random();

        // Restore persisted state
        this.userId = prefs.getString(KEY_USER_ID, null);
        this.displayName = prefs.getString(KEY_DISPLAY_NAME, null);
        this.isAnonymous = prefs.getBoolean(KEY_IS_ANONYMOUS, true);

        // Sync with current Firebase user (handles app restarts where SDK already has session)
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current != null) {
            this.userId = current.getUid();
            this.isAnonymous = current.isAnonymous();
            if (current.getDisplayName() != null && !current.getDisplayName().isEmpty()
                    && !current.isAnonymous()) {
                this.displayName = current.getDisplayName();
            }
            persistAuthState();
        }
    }

    public static synchronized AuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new AuthManager(context);
        }
        return instance;
    }

    // ─── Anonymous sign-in ─────────────────────────────────────────────────

    public void signInAnonymously(@Nullable AuthCallback callback) {
        // If already signed in, no need to re-sign in
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current != null) {
            this.userId = current.getUid();
            this.isAnonymous = current.isAnonymous();
            if (this.displayName == null || this.displayName.isEmpty()) {
                this.displayName = generateAnonymousDisplayName();
            }
            persistAuthState();
            if (callback != null) callback.onSuccess();
            return;
        }

        firebaseAuth.signInAnonymously().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    this.userId = user.getUid();
                    this.isAnonymous = true;
                    if (this.displayName == null || this.displayName.isEmpty()) {
                        this.displayName = generateAnonymousDisplayName();
                    }
                    persistAuthState();
                    if (callback != null) callback.onSuccess();
                } else if (callback != null) {
                    callback.onFailure("Anonymous sign-in returned null user");
                }
            } else {
                String msg = task.getException() != null
                        ? task.getException().getMessage()
                        : "Unknown error";
                Log.w(TAG, "Anonymous sign-in failed: " + msg);
                if (callback != null) callback.onFailure(msg);
            }
        });
    }

    /**
     * Generates a display name in the format "Player" + 4 random digits (1000-9999).
     * Visibility is package-private to allow tests to verify the format.
     */
    String generateAnonymousDisplayName() {
        // 1000 + nextInt(9000) yields range [1000, 9999] — always 4 digits
        int suffix = 1000 + random.nextInt(9000);
        return "Player" + suffix;
    }

    private void persistAuthState() {
        SharedPreferences.Editor editor = prefs.edit();
        if (userId != null) editor.putString(KEY_USER_ID, userId);
        if (displayName != null) editor.putString(KEY_DISPLAY_NAME, displayName);
        editor.putBoolean(KEY_IS_ANONYMOUS, isAnonymous);
        editor.apply();
    }

    // ─── Google sign-in ────────────────────────────────────────────────────

    /**
     * Builds (or returns the cached) GoogleSignInClient. The web client ID is read
     * from string resources (default_web_client_id) which is auto-generated by the
     * google-services plugin from google-services.json.
     */
    private GoogleSignInClient getGoogleSignInClient(Activity activity) {
        if (googleSignInClient == null) {
            int resId = activity.getResources().getIdentifier(
                    "default_web_client_id", "string", activity.getPackageName());
            String webClientId = resId != 0 ? activity.getString(resId) : null;

            GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(
                    GoogleSignInOptions.DEFAULT_SIGN_IN);
            if (webClientId != null) {
                builder.requestIdToken(webClientId);
            }
            builder.requestEmail();
            googleSignInClient = GoogleSignIn.getClient(activity, builder.build());
        }
        return googleSignInClient;
    }

    public void signInWithGoogle(Activity activity, int requestCode) {
        GoogleSignInClient client = getGoogleSignInClient(activity);
        Intent signInIntent = client.getSignInIntent();
        activity.startActivityForResult(signInIntent, requestCode);
    }

    public void handleGoogleSignInResult(Intent data, @NonNull AuthCallback callback) {
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                callback.onFailure("Google account or ID token missing");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

            FirebaseUser current = firebaseAuth.getCurrentUser();
            if (current != null && current.isAnonymous()) {
                // Link anonymous account to Google credential
                linkGoogleAccount(credential, callback);
            } else {
                // Direct sign-in (or replace existing non-anonymous session)
                firebaseAuth.signInWithCredential(credential).addOnCompleteListener(t -> {
                    if (t.isSuccessful()) {
                        applyFirebaseUser(firebaseAuth.getCurrentUser(), account.getDisplayName());
                        callback.onSuccess();
                    } else {
                        String msg = t.getException() != null ? t.getException().getMessage() : "Sign-in failed";
                        callback.onFailure(msg);
                    }
                });
            }
        } catch (ApiException e) {
            Log.w(TAG, "Google sign-in failed", e);
            callback.onFailure("Google sign-in failed: code " + e.getStatusCode());
        }
    }

    public void linkGoogleAccount(AuthCredential credential, @NonNull AuthCallback callback) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            callback.onFailure("No active anonymous session to link");
            return;
        }
        current.linkWithCredential(credential).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                AuthResult result = task.getResult();
                FirebaseUser linked = result != null ? result.getUser() : firebaseAuth.getCurrentUser();
                String googleName = linked != null ? linked.getDisplayName() : null;
                applyFirebaseUser(linked, googleName);
                callback.onSuccess();
            } else {
                Exception ex = task.getException();
                if (ex instanceof FirebaseAuthUserCollisionException) {
                    // Google account already linked to a different anonymous account.
                    // Per Requirement 2.5, sign in to the existing account.
                    firebaseAuth.signInWithCredential(credential).addOnCompleteListener(t2 -> {
                        if (t2.isSuccessful()) {
                            AuthResult r2 = t2.getResult();
                            FirebaseUser u2 = r2 != null ? r2.getUser() : firebaseAuth.getCurrentUser();
                            String name = u2 != null ? u2.getDisplayName() : null;
                            applyFirebaseUser(u2, name);
                            callback.onSuccess();
                        } else {
                            String msg = t2.getException() != null
                                    ? t2.getException().getMessage()
                                    : "Sign-in to existing account failed";
                            callback.onFailure(msg);
                        }
                    });
                } else {
                    String msg = ex != null ? ex.getMessage() : "Account linking failed";
                    Log.w(TAG, "Link failed: " + msg);
                    callback.onFailure(msg);
                }
            }
        });
    }

    private void applyFirebaseUser(@Nullable FirebaseUser user, @Nullable String preferredName) {
        if (user == null) return;
        this.userId = user.getUid();
        this.isAnonymous = user.isAnonymous();
        if (preferredName != null && !preferredName.isEmpty()) {
            this.displayName = preferredName;
        } else if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            this.displayName = user.getDisplayName();
        }
        persistAuthState();
    }

    // ─── Sign-out / delete ─────────────────────────────────────────────────

    public void signOut() {
        firebaseAuth.signOut();
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
        // Clear local state — the next launch will trigger a fresh anonymous sign-in
        prefs.edit().clear().apply();
        userId = null;
        displayName = null;
        isAnonymous = true;
    }

    public void deleteAccount(@NonNull AuthCallback callback) {
        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current == null) {
            // Already gone — re-sign-in anonymously
            signInAnonymously(callback);
            return;
        }
        current.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                prefs.edit().clear().apply();
                userId = null;
                displayName = null;
                isAnonymous = true;
                // Per Requirement 11.3, sign in with a fresh anonymous account
                signInAnonymously(callback);
            } else {
                String msg = task.getException() != null
                        ? task.getException().getMessage()
                        : "Account deletion failed";
                callback.onFailure(msg);
            }
        });
    }

    // ─── State accessors ───────────────────────────────────────────────────

    public boolean isSignedIn() {
        return userId != null && firebaseAuth.getCurrentUser() != null;
    }

    public boolean isAnonymous() { return isAnonymous; }
    @Nullable public String getUserId() { return userId; }
    @Nullable public String getDisplayName() { return displayName; }

    /**
     * Update display name pemain. Persist ke SharedPreferences. Best-effort
     * juga update Firebase profile bila user signed in.
     * @return true bila valid (3-20 chars setelah trim).
     */
    public boolean updateDisplayName(String newName) {
        if (newName == null) return false;
        String trimmed = newName.trim();
        if (trimmed.length() < 3 || trimmed.length() > 20) return false;

        this.displayName = trimmed;
        persistAuthState();

        FirebaseUser current = firebaseAuth.getCurrentUser();
        if (current != null) {
            UserProfileChangeRequest req = new UserProfileChangeRequest.Builder()
                    .setDisplayName(trimmed).build();
            current.updateProfile(req);
        }
        return true;
    }
}
