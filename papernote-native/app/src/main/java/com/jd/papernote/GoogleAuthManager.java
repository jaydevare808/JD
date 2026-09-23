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
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.OAuthProvider;

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
        requestExplicitGoogleSignIn(activity, callback);
    }

    private void requestExplicitGoogleSignIn(Activity activity, Callback callback) {
        try {
            GetSignInWithGoogleOption option =
                    new GetSignInWithGoogleOption.Builder(webClientId)
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
                            // If Credential Manager cannot provide a Google credential
                            // on this device, use Firebase's hosted Google OAuth flow.
                            // This is a real sign-in path, not a fake/offline fallback,
                            // and works even when the device has no usable Google
                            // Credential Manager provider.
                            requestGoogleIdCredential(activity, true, callback, error);
                        }
                    }
            );
        } catch (RuntimeException e) {
            requestGoogleIdCredential(activity, true, callback, e);
        }
    }

    private void requestGoogleIdCredential(Activity activity,
                                           boolean authorizedOnly,
                                           Callback callback,
                                           Exception previousError) {
        try {
            GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(authorizedOnly)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
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
                                requestGoogleIdCredential(activity, false, callback, error);
                            } else {
                                startFirebaseGoogleOAuth(activity, callback, previousError != null ? previousError : error);
                            }
                        }
                    }
            );
        } catch (RuntimeException e) {
            if (authorizedOnly) {
                requestGoogleIdCredential(activity, false, callback, e);
            } else {
                startFirebaseGoogleOAuth(activity, callback, previousError != null ? previousError : e);
            }
        }
    }

    private void startFirebaseGoogleOAuth(Activity activity, Callback callback, Exception previousError) {
        try {
            OAuthProvider.Builder providerBuilder = OAuthProvider.newBuilder("google.com", auth);
            providerBuilder.setScopes(java.util.Arrays.asList("email", "profile"));
            providerBuilder.addCustomParameter("prompt", "select_account");

            auth.startActivityForSignInWithProvider(activity, providerBuilder.build())
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            callback.onSuccess(user);
                        } else {
                            callback.onError("Google sign-in completed but Firebase returned no user.");
                        }
                    })
                    .addOnFailureListener(error -> {
                        String message = humanizeOAuthError(error);
                        if (message == null || message.trim().isEmpty()) {
                            message = humanizeCredentialError(previousError);
                        }
                        callback.onError(message);
                    });
        } catch (RuntimeException e) {
            callback.onError("Google sign-in could not start. Please check your internet connection and try again: "
                    + safeMessage(e));
        }
    }

    /**
     * Completes a Firebase-hosted Google OAuth flow if Android recreated the
     * Activity while Chrome/Custom Tab was open.
     */
    public void consumePendingSignIn(Callback callback) {
        if (auth == null) return;
        com.google.android.gms.tasks.Task<AuthResult> pending = auth.getPendingAuthResult();
        if (pending == null) return;

        pending.addOnSuccessListener(authResult -> {
            FirebaseUser user = authResult.getUser();
            if (user != null) callback.onSuccess(user);
            else callback.onError("Google sign-in completed but Firebase returned no user.");
        }).addOnFailureListener(error -> callback.onError(humanizeOAuthError(error)));
    }

    private String humanizeOAuthError(Exception error) {
        String message = safeMessage(error);
        String lower = message.toLowerCase();
        if (lower.contains("cancel")) return "Google sign-in was cancelled.";
        if (lower.contains("network") || lower.contains("timeout")) {
            return "Google sign-in needs an internet connection. Please try again.";
        }
        if (lower.contains("operation-not-allowed") || lower.contains("provider is disabled")) {
            return "Google sign-in is disabled in Firebase Authentication. Enable the Google provider and try again.";
        }
        return "Google sign-in failed: " + message;
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
