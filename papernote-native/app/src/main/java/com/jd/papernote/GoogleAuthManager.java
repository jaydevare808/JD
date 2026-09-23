package com.jd.papernote;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * PaperNote Google authentication backed by Firebase Authentication.
 *
 * Firebase is initialized automatically by FirebaseInitProvider from the
 * standard google-services Gradle plugin setup. The Web OAuth client ID is
 * generated as R.string.default_web_client_id.
 */
public final class GoogleAuthManager {
    public interface Callback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    private static volatile GoogleAuthManager instance;

    private final Context appContext;
    private CredentialManager credentialManager;
    private FirebaseAuth auth;
    private String webClientId;
    private boolean configured;
    private String configurationMessage;

    private GoogleAuthManager(Context context) {
        appContext = context.getApplicationContext();
        initialize();
    }

    public static GoogleAuthManager get(Context context) {
        if (instance == null) {
            synchronized (GoogleAuthManager.class) {
                if (instance == null) {
                    instance = new GoogleAuthManager(context);
                }
            }
        }
        return instance;
    }

    private void initialize() {
        try {
            FirebaseApp app = FirebaseApp.getInstance();
            auth = FirebaseAuth.getInstance(app);
            credentialManager = CredentialManager.create(appContext);
            webClientId = appContext.getString(R.string.default_web_client_id);

            configured = !TextUtils.isEmpty(webClientId)
                    && webClientId.endsWith(".apps.googleusercontent.com");

            configurationMessage = configured
                    ? null
                    : "Google sign-in configuration is incomplete. Check google-services.json and rebuild.";
        } catch (Exception e) {
            configured = false;
            configurationMessage =
                    "Firebase could not initialize. Make sure google-services.json is in the app module and Google Sign-In is enabled in Firebase.";
        }
    }

    public boolean isConfigured() {
        return configured && auth != null && credentialManager != null;
    }

    public String getConfigurationMessage() {
        return configurationMessage;
    }

    public FirebaseUser getCurrentUser() {
        return auth == null ? null : auth.getCurrentUser();
    }

    public void signIn(Activity activity, Callback callback) {
        if (!isConfigured()) {
            callback.onError(configurationMessage == null
                    ? "Google sign-in is unavailable."
                    : configurationMessage);
            return;
        }
        requestCredential(activity, true, callback);
    }

    private void requestCredential(Activity activity, boolean authorizedOnly, Callback callback) {
        try {
            GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(authorizedOnly)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(!authorizedOnly)
                    .setNonce(generateNonce())
                    .build();

            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build();

            credentialManager.getCredentialAsync(
                    activity,
                    request,
                    null,
                    ContextCompat.getMainExecutor(activity),
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse response) {
                            handleCredential(response.getCredential(), callback);
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException error) {
                            if (authorizedOnly) {
                                // First-time sign-in: allow the account chooser to show all
                                // eligible Google accounts.
                                requestCredential(activity, false, callback);
                            } else {
                                callback.onError(humanizeCredentialError(error));
                            }
                        }
                    }
            );
        } catch (RuntimeException e) {
            callback.onError("Google sign-in could not start: " + safeMessage(e));
        }
    }

    private void handleCredential(Credential credential, Callback callback) {
        if (!(credential instanceof CustomCredential)) {
            callback.onError("Google returned an unsupported credential.");
            return;
        }

        CustomCredential customCredential = (CustomCredential) credential;
        if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
            callback.onError("Google returned an unsupported credential type.");
            return;
        }

        try {
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());

            AuthCredential firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);

            auth.signInWithCredential(firebaseCredential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && auth.getCurrentUser() != null) {
                            callback.onSuccess(auth.getCurrentUser());
                            return;
                        }

                        Exception error = task.getException();
                        callback.onError(error == null
                                ? "Google account authentication failed."
                                : humanizeAuthError(error));
                    });
        } catch (RuntimeException e) {
            callback.onError("Google returned an invalid sign-in response: " + safeMessage(e));
        }
    }

    public void signOut(Activity activity) {
        if (auth != null) {
            auth.signOut();
        }

        if (credentialManager == null) return;

        credentialManager.clearCredentialStateAsync(
                new androidx.credentials.ClearCredentialStateRequest(),
                null,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                    @Override public void onResult(Void result) {}
                    @Override public void onError(@NonNull androidx.credentials.exceptions.ClearCredentialException error) {}
                }
        );
    }

    private String humanizeCredentialError(Exception error) {
        String message = safeMessage(error);
        String lower = message.toLowerCase();
        if (lower.contains("cancel")) return "Google sign-in was cancelled.";
        if (lower.contains("no credential") || lower.contains("not found")) {
            return "No Google account credential was available. Please select an account and try again.";
        }
        return "Google sign-in could not be completed: " + message;
    }

    private String humanizeAuthError(Exception error) {
        String message = safeMessage(error);
        if (message.contains("12500")) {
            return "Google sign-in error 12500. Check the Firebase support email and SHA-1 certificate.";
        }
        if (message.contains("10") || message.toLowerCase().contains("developer error")) {
            return "Google sign-in configuration error. Check the package name and SHA-1 certificate in Firebase.";
        }
        return "Firebase authentication failed: " + message;
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return (message == null || message.trim().isEmpty())
                ? "Please try again."
                : message.trim();
    }

    private static String generateNonce() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
