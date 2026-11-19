package com.hishamwahba.weatherapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
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
        } catch (Throwable t) {
            Log.e(TAG, "Startup failed", t);
            showFatalError(t);
        }
    }

    /** Displays the reason for a crash so it can be reported without logcat. */
    private void showFatalError(Throwable t) {
        try {
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
        ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
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
