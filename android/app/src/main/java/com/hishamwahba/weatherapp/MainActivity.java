package com.hishamwahba.weatherapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Thin native shell around the existing web app.
 * The UI stays 100% HTML/CSS/JS — this class only wires up Android features
 * (permissions, back button, offline fallback) around a WebView.
 */
public class MainActivity extends Activity {
    private static final String TAG = "WeatherApp";
    // The live Vercel site: every change pushed to the repo appears here automatically.
    private static final String REMOTE_URL = "https://weather-app-sooty-phi-31.vercel.app/";
    // Local copy of the same files, used only when there is no connection.
    private static final String LOCAL_URL = "file:///android_asset/www/index.html";
    private static final int LOCATION_REQUEST_CODE = 1001;

    private WebView webView;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin;
    private boolean usedOfflineFallback = false;

    // App-only launch screen and visual skin (never sent to the website).
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private View splashOverlay;
    private View splashLogo;
    private View splashTitle;
    private long splashStartedAt;
    private boolean splashDismissScheduled;
    private String appSkinJs;

    // Update prompt. The browser/download manager handles the APK from here.
    private boolean updateDialogShown;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show unexpected crashes on screen instead of the system "Close app"
        // dialog, so problems can be diagnosed without a USB cable / logcat.
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception on " + thread.getName(), throwable);
            try {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    showFatalError(throwable);
                } else {
                    runOnUiThread(() -> showFatalError(throwable));
                }
            } catch (Throwable ignored) {
                // Never let the error screen itself crash the app.
            }
        });

        try {
            setContentView(R.layout.activity_main);
            webView = findViewById(R.id.webview);
            if (webView == null) {
                throw new IllegalStateException("WebView not found in activity_main.xml");
            }
            // Framework WebView state restoration crashes on some devices — start fresh.
            webView.setSaveEnabled(false);
            initSplash();
            appSkinJs = readAssetFile("app-skin.js");

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);       // the weather app is JS-driven
            settings.setDomStorageEnabled(true);       // localStorage remembers the chosen language
            settings.setGeolocationEnabled(true);      // browser geolocation -> Android location
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            webView.setBackgroundColor(0xFF080D1C);

            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return handleUrl(view, request.getUrl());
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    injectAppSkin(view);
                    dismissSplash();
                }

                @Override
                public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                    if (request != null && request.isForMainFrame()) loadFallback(view);
                }

                @Override
                public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                                WebResourceResponse errorResponse) {
                    if (request != null && request.isForMainFrame()
                            && errorResponse != null && errorResponse.getStatusCode() >= 500) {
                        loadFallback(view);
                    }
                }
            });

            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onGeolocationPermissionsShowPrompt(String origin,
                                                               GeolocationPermissions.Callback callback) {
                    if (callback == null) return;
                    if (hasLocationPermission()) {
                        callback.invoke(origin, true, false);
                        return;
                    }
                    // Only ask when the page actually requests location. Asking at
                    // startup as well used to race with this request and leave the
                    // website's geolocation call waiting forever.
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    try {
                        requestPermissions(new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        }, LOCATION_REQUEST_CODE);
                    } catch (Throwable t) {
                        Log.e(TAG, "Location permission request failed", t);
                        callback.invoke(origin, false, false);
                        pendingGeoCallback = null;
                        pendingGeoOrigin = null;
                    }
                }
            });

            loadStartUrl();
            checkForUpdate();
        } catch (Throwable t) {
            Log.e(TAG, "Startup failed", t);
            showFatalError(t);
        }
    }

    /** Displays the reason for a crash so it can be reported without logcat. */
    private void showFatalError(Throwable t) {
        try {
            splashOverlay = null;
            TextView view = new TextView(this);
            String report = Log.getStackTraceString(t);
            if (report == null || report.isEmpty()) report = String.valueOf(t);
            view.setText("Weather App error\n\n" + report
                    + "\n\nPlease send this message to the developer.");
            view.setTextColor(Color.rgb(226, 232, 240));
            view.setTextSize(11f);
            view.setGravity(Gravity.TOP | Gravity.START);
            view.setPadding(24, 40, 24, 24);
            view.setBackgroundColor(Color.rgb(8, 13, 28));
            setContentView(view);
        } catch (Throwable ignored) {
        }
    }

    /** Shows the logo briefly while the weather page is loading. */
    private void initSplash() {
        splashStartedAt = SystemClock.uptimeMillis();
        splashOverlay = findViewById(R.id.splashOverlay);
        splashLogo = findViewById(R.id.splashLogo);
        splashTitle = findViewById(R.id.splashTitle);
        if (splashLogo != null) {
            splashLogo.setAlpha(0f);
            splashLogo.setScaleX(0.86f);
            splashLogo.setScaleY(0.86f);
            splashLogo.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setStartDelay(100).setDuration(650).start();
        }
        if (splashTitle != null) {
            splashTitle.setAlpha(0f);
            splashTitle.setTranslationY(16f);
            splashTitle.animate().alpha(1f).translationY(0f)
                    .setStartDelay(280).setDuration(600).start();
        }
        // A blocked/slow page must never leave the splash on screen forever.
        mainHandler.postDelayed(() -> dismissSplash(), 4500);
    }

    /** Fades the splash out, respecting a short minimum display time. */
    private void dismissSplash() {
        if (splashOverlay == null || splashDismissScheduled) return;
        splashDismissScheduled = true;
        long remaining = Math.max(0, 1500 - (SystemClock.uptimeMillis() - splashStartedAt));
        mainHandler.postDelayed(() -> {
            if (splashOverlay == null) return;
            splashOverlay.animate().alpha(0f).setDuration(420).withEndAction(() -> {
                if (splashOverlay != null) {
                    splashOverlay.setVisibility(View.GONE);
                    splashOverlay = null;
                }
            }).start();
        }, remaining);
    }

    /** Reads a small JS file bundled with the app. */
    private String readAssetFile(String name) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(name), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line).append('\n');
        } catch (Throwable t) {
            Log.e(TAG, "Could not read asset: " + name, t);
            return "";
        }
        return text.toString();
    }

    /** App-only tweaks: remove footer, put contacts in the navbar. */
    private void injectAppSkin(WebView view) {
        if (view == null || appSkinJs == null || appSkinJs.isEmpty()) return;
        try {
            view.evaluateJavascript(appSkinJs, null);
        } catch (Throwable t) {
            Log.e(TAG, "App skin injection failed", t);
        }
    }

    /** Looks for a newer signed APK published as a GitHub Release. */
    private void checkForUpdate() {
        UpdateChecker.check(this, release -> {
            if (release == null || isFinishing() || updateDialogShown) return;
            updateDialogShown = true;
            String notes = release.notes == null ? "" : release.notes.trim();
            if (notes.length() > 160) notes = notes.substring(0, 160) + "…";
            String message = getString(R.string.update_message, release.version)
                    + (notes.isEmpty() ? "" : "\n\n" + notes);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_title)
                    .setMessage(message)
                    .setPositiveButton(R.string.update_now,
                            (dialog, which) -> openReleaseInBrowser(release.downloadUrl))
                    .setNegativeButton(R.string.update_later, null)
                    .show();
        });
    }

    /**
     * Let the browser/download manager handle the APK. This keeps the app free of
     * the install-package permission, which some phone security suites reject.
     */
    private void openReleaseInBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Throwable t) {
            Log.e(TAG, "Could not open release link", t);
        }
    }

    /** Keep site navigation inside the WebView; open external links in a browser. */
    private boolean handleUrl(WebView view, Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        if (scheme.equals("file") || ((scheme.equals("http") || scheme.equals("https")) && isOwnSite(uri))) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ignored) {
            return true;
        }
        return true;
    }

    private boolean isOwnSite(Uri uri) {
        String host = uri.getHost();
        return host != null && (host.endsWith("vercel.app") || host.endsWith("zya.me"));
    }

    private void loadStartUrl() {
        if (isOnline()) {
            webView.loadUrl(REMOTE_URL);
        } else {
            usedOfflineFallback = true;
            webView.loadUrl(LOCAL_URL);
        }
    }

    private void loadFallback(WebView view) {
        if (usedOfflineFallback || view == null) return;
        usedOfflineFallback = true;
        try {
            view.loadUrl(LOCAL_URL);
        } catch (Throwable t) {
            Log.e(TAG, "Offline fallback failed", t);
        }
    }

    private boolean isOnline() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (manager == null) return true;
            NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Throwable t) {
            // If the state cannot be read, try the live site anyway.
            Log.w(TAG, "Could not read network state", t);
            return true;
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST_CODE || pendingGeoCallback == null) return;
        boolean granted = grantResults != null && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        try {
            pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
        } catch (Throwable t) {
            Log.e(TAG, "Geolocation callback failed", t);
        }
        pendingGeoCallback = null;
        pendingGeoOrigin = null;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

}
