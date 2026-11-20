package com.hishamwahba.weatherapp;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks the latest GitHub Release for a newer signed APK. */
public final class UpdateChecker {
    private static final String TAG = "WeatherApp";
    private static final String RELEASES_URL =
            "https://api.github.com/repos/HISHAM-WAHBA/Weather-app/releases/latest";
    static final String APK_NAME = "weather app.apk";

    public interface Callback {
        void onResult(Release release);
    }

    public static final class Release {
        public final String version;
        public final String downloadUrl;
        public final String notes;

        Release(String version, String downloadUrl, String notes) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.notes = notes;
        }
    }

    private UpdateChecker() {
    }

    public static void check(Context context, Callback callback) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler main = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            Release found = null;
            try {
                String current = currentVersion(context);
                JSONObject json = new JSONObject(fetch(RELEASES_URL));
                String version = stripV(json.optString("tag_name", ""));
                if (!version.isEmpty() && isNewer(version, current)) {
                    String url = findApkUrl(json.optJSONArray("assets"));
                    if (url != null && !url.isEmpty()) {
                        found = new Release(version, url, json.optString("body", ""));
                    }
                }
            } catch (Throwable t) {
                // No network / rate-limited / bad JSON: just skip the check.
                Log.w(TAG, "Update check skipped", t);
            }
            final Release result = found;
            main.post(() -> callback.onResult(result));
        });
        executor.shutdown();
    }

    private static String currentVersion(Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
        return info.versionName == null ? "0" : info.versionName;
    }

    private static String fetch(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "WeatherApp-Android");
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status);
            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) text.append(line).append('\n');
            }
            return text.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static String stripV(String tag) {
        String value = tag == null ? "" : tag.trim();
        if (value.startsWith("v") || value.startsWith("V")) value = value.substring(1);
        return value.trim();
    }

    private static String findApkUrl(JSONArray assets) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            // gh release upload turns "weather app.apk" into "weather.app.apk",
            // while the label keeps the original name — accept either.
            if (isApkName(asset.optString("name")) || isApkName(asset.optString("label"))) {
                return asset.optString("browser_download_url");
            }
        }
        return null;
    }

    private static boolean isApkName(String value) {
        if (value == null) return false;
        String name = value.trim().toLowerCase();
        return name.equals(APK_NAME.toLowerCase()) || name.equals("weather.app.apk");
    }

    private static boolean isNewer(String candidate, String current) {
        return number(candidate) > number(current);
    }

    /** "1.2" -> 1002000, so numeric comparison works for major/minor/patch. */
    private static long number(String version) {
        String cleaned = version.replaceAll("[^0-9.]", "");
        String[] parts = cleaned.split("\\.");
        long value = 0;
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            if (parts[i].isEmpty()) continue;
            try {
                value = value * 1000 + Long.parseLong(parts[i]);
            } catch (NumberFormatException ignored) {
            }
        }
        return value;
    }
}
