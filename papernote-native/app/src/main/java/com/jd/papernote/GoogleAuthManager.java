package com.jd.papernote;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class GoogleAuthManager {
    public interface Callback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    private static volatile GoogleAuthManager instance;

    private final Context appContext;
    private final Executor callbackExecutor = Executors.newSingleThreadExecutor();
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
                if (instance == null) instance = new GoogleAuthManager(context);
            }
        }
        return instance;
    }

    private void initialize() {
        try {
            FirebaseApp app;
            if (!FirebaseApp.getApps(appContext).isEmpty()) {
                app = FirebaseApp.getInstance();
            } else {
                String json = readAsset("google-services.json");
                JSONObject root = new JSONObject(json);

                JSONObject projectInfo = root.getJSONObject("project_info");
                JSONArray clients = root.getJSONArray("client");
                JSONObject client = clients.getJSONObject(0);
                JSONObject clientInfo = client.getJSONObject("client_info");
                String applicationId = clientInfo.getString("mobilesdk_app_id");
                String apiKey = client.getJSONArray("api_key").getJSONObject(0).getString("current_key");

                String projectId = projectInfo.getString("project_id");
                String senderId = projectInfo.optString("project_number", null);
                String storageBucket = projectInfo.optString("storage_bucket", null);

                webClientId = findWebClientId(root);

                if (isPlaceholder(applicationId) || isPlaceholder(apiKey)
                        || isPlaceholder(projectId) || TextUtils.isEmpty(webClientId)
                        || isPlaceholder(webClientId)) {
                    configured = false;
                    configurationMessage =
                            "Google sign-in is prepared but your Firebase project configuration is not connected yet.";
                    return;
                }

                FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                        .setApplicationId(applicationId)
                        .setApiKey(apiKey)
                        .setProjectId(projectId);

                if (!TextUtils.isEmpty(senderId)) builder.setGcmSenderId(senderId);
                if (!TextUtils.isEmpty(storageBucket)) builder.setStorageBucket(storageBucket);

                app = FirebaseApp.initializeApp(appContext, builder.build());
            }

            if (app == null) {
                configured = false;
                configurationMessage = "Firebase could not be initialized.";
                return;
            }

            auth = FirebaseAuth.getInstance(app);
            credentialManager = CredentialManager.create(appContext);
            configured = !TextUtils.isEmpty(webClientId);
            configurationMessage = configured ? null
                    : "The Firebase web OAuth client ID is missing.";
        } catch (Exception e) {
            configured = false;
            configurationMessage =
                    "Google sign-in is not configured yet. Upload your Firebase google-services.json file to finish setup.";
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

    public void signIn(Context activityContext, Callback callback) {
        if (!isConfigured()) {
            callback.onError(getConfigurationMessage() == null
                    ? "Google sign-in is unavailable."
                    : getConfigurationMessage());
            return;
        }

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .setNonce(generateNonce())
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                activityContext,
                request,
                null,
                activityContext.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        handleCredential(result.getCredential(), callback);
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // No previously authorized account. Retry with all Google accounts so
                        // first-time sign-up is supported as documented by Google/Firebase.
                        GetGoogleIdOption signupOption = new GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .setNonce(generateNonce())
                                .build();

                        GetCredentialRequest signupRequest = new GetCredentialRequest.Builder()
                                .addCredentialOption(signupOption)
                                .build();

                        credentialManager.getCredentialAsync(
                                activityContext,
                                signupRequest,
                                null,
                                activityContext.getMainExecutor(),
                                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                                    @Override
                                    public void onResult(GetCredentialResponse result) {
                                        handleCredential(result.getCredential(), callback);
                                    }

                                    @Override
                                    public void onError(@NonNull GetCredentialException retryError) {
                                        callback.onError(humanizeCredentialError(retryError));
                                    }
                                }
                        );
                    }
                }
        );
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
                        } else {
                            Exception error = task.getException();
                            callback.onError(error == null
                                    ? "Google sign-in failed."
                                    : humanizeAuthError(error));
                        }
                    });
        } catch (RuntimeException e) {
            callback.onError("Google returned an invalid sign-in response. Please try again.");
        }
    }

    public void signOut(Context context) {
        if (auth != null) auth.signOut();

        if (credentialManager != null) {
            credentialManager.clearCredentialStateAsync(
                    new androidx.credentials.ClearCredentialStateRequest(),
                    null,
                    context.getMainExecutor(),
                    new CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                        @Override
                        public void onResult(Void result) {
                        }

                        @Override
                        public void onError(@NonNull androidx.credentials.exceptions.ClearCredentialException e) {
                        }
                    }
            );
        }
    }

    private String humanizeCredentialError(Exception error) {
        String value = error.getMessage();
        if (value == null || value.trim().isEmpty()) return "Google sign-in was cancelled or could not be completed.";
        return "Google sign-in could not be completed. Please try again.";
    }

    private String humanizeAuthError(Exception error) {
        String value = error.getMessage();
        if (value == null || value.trim().isEmpty()) return "Account authentication failed.";
        return "Account authentication failed. Please check your Google/Firebase setup.";
    }

    private String readAsset(String name) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (InputStream input = appContext.getAssets().open(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private String findWebClientId(JSONObject root) throws Exception {
        JSONArray clients = root.getJSONArray("client");
        for (int i = 0; i < clients.length(); i++) {
            JSONObject client = clients.getJSONObject(i);
            JSONArray oauth = client.optJSONArray("oauth_client");
            if (oauth == null) continue;

            for (int j = 0; j < oauth.length(); j++) {
                JSONObject item = oauth.getJSONObject(j);
                if (item.optInt("client_type", -1) == 3) {
                    String id = item.optString("client_id", "");
                    if (!TextUtils.isEmpty(id)) return id;
                }
            }
        }
        return "";
    }

    private boolean isPlaceholder(String value) {
        if (TextUtils.isEmpty(value)) return true;
        String lower = value.toLowerCase();
        return lower.contains("change_me")
                || lower.contains("replace")
                || lower.contains("your_")
                || lower.contains("placeholder");
    }

    private static String generateNonce() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
