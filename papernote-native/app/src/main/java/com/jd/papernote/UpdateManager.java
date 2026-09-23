package com.jd.papernote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UpdateManager {
    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/jaydevare808/JD/releases/latest";
    private static final String APK_NAME = "PaperNote.apk";
    private static final long CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private UpdateManager() {}

    public static void check(Activity activity, boolean manual) {
        long lastCheck = activity.getPreferences(Activity.MODE_PRIVATE)
                .getLong("papernote_last_update_check", 0L);
        long now = System.currentTimeMillis();

        if (!manual && now - lastCheck < CHECK_INTERVAL_MS) return;

        activity.getPreferences(Activity.MODE_PRIVATE)
                .edit()
                .putLong("papernote_last_update_check", now)
                .apply();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                JSONObject release = getJson(LATEST_RELEASE);
                String tag = release.optString("tag_name", "");
                int remote = versionCode(tag);
                int local = versionCode(BuildConfig.VERSION_NAME);

                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                long apkSize = 0L;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        if (APK_NAME.equals(asset.optString("name"))) {
                            apkUrl = asset.optString("browser_download_url", null);
                            apkSize = asset.optLong("size", 0L);
                            break;
                        }
                    }
                }

                if (remote > local && apkUrl != null) {
                    String finalApkUrl = apkUrl;
                    long finalApkSize = apkSize;
                    activity.runOnUiThread(() ->
                            showUpdateDialog(activity, tag, release.optString("body", ""), finalApkUrl, finalApkSize));
                } else if (manual) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "PaperNote is up to date.", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception error) {
                if (manual) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "Could not check for updates.", Toast.LENGTH_SHORT).show());
                }
            } finally {
                executor.shutdown();
            }
        });
    }

    private static void showUpdateDialog(Activity activity, String tag, String notes, String apkUrl, long size) {
        String message = "A newer PaperNote version is available.\n\n" +
                "Version: " + tag + "\n" +
                "Download: " + formatSize(size) + "\n\n" +
                (notes == null || notes.trim().isEmpty()
                        ? "Includes handwriting and stability improvements."
                        : notes.trim());

        new AlertDialog.Builder(activity)
                .setTitle("PaperNote update")
                .setMessage(message)
                .setNegativeButton("Later", null)
                .setPositiveButton("Download & install", (dialog, which) ->
                        downloadAndInstall(activity, apkUrl))
                .show();
    }

    private static void downloadAndInstall(Activity activity, String apkUrl) {
        Toast.makeText(activity, "Downloading PaperNote update…", Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File temp = new File(activity.getCacheDir(), "PaperNote-update.apk.part");
            File apk = new File(activity.getCacheDir(), "PaperNote-update.apk");

            try {
                download(apkUrl, temp);
                if (apk.exists() && !apk.delete()) throw new Exception("Could not replace old update");
                if (!temp.renameTo(apk)) throw new Exception("Could not finalize update");

                activity.runOnUiThread(() -> installApk(activity, apk));
            } catch (Exception error) {
                if (temp.exists()) temp.delete();
                activity.runOnUiThread(() ->
                        Toast.makeText(activity, "Update download failed.", Toast.LENGTH_LONG).show());
            } finally {
                executor.shutdown();
            }
        });
    }

    private static void installApk(Activity activity, File apk) {
        if (Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Allow PaperNote to install updates")
                    .setMessage("Android requires you to explicitly allow this app to install its downloaded APK update.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open settings", (dialog, which) -> {
                        Intent settings = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + activity.getPackageName())
                        );
                        activity.startActivity(settings);
                    })
                    .show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apk
        );

        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
    }

    private static JSONObject getJson(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "PaperNote/" + BuildConfig.VERSION_NAME);

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                return new JSONObject(out.toString(java.nio.charset.StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void download(String urlString, File destination) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "PaperNote/" + BuildConfig.VERSION_NAME);

        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP " + connection.getResponseCode());
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }
        } finally {
            connection.disconnect();
        }
    }

    private static int versionCode(String value) {
        if (value == null) return 0;
        String clean = value.trim().replaceFirst("^[vV]", "");
        String[] parts = clean.split("\\\\.");
        int major = parts.length > 0 ? parse(parts[0]) : 0;
        int minor = parts.length > 1 ? parse(parts[1]) : 0;
        int patch = parts.length > 2 ? parse(parts[2]) : 0;
        return major * 1_000_000 + minor * 1_000 + patch;
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "unknown";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }
}
