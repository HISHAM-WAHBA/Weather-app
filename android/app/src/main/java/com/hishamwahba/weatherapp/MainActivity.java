package com.hishamwahba.weatherapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
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
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Thin native shell around the existing web app.
 * The UI stays 100% HTML/CSS/JS — this class only wires up Android features
 * (permissions, back button, offline fallback, notifications, ads) around a WebView.
 */
public class MainActivity extends Activity {
    private static final String TAG = "WeatherApp";
    // The live Vercel site: every change pushed to the repo appears here automatically.
    private static final String REMOTE_URL = "https://weather-app-sooty-phi-31.vercel.app/";
    // Local copy of the same files, used only when there is no connection.
    private static final String LOCAL_URL = "file:///android_asset/www/index.html";
    private static final int LOCATION_REQUEST_CODE = 1001;
    private static final int NOTIFICATION_REQUEST_CODE = 1002;

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

    // In-app update download (sideload flavor only).
    private boolean updateDialogShown;
    private long updateDownloadId = -1;
    private BroadcastReceiver updateDownloadReceiver;

    // AdMob (official test IDs until real ones are supplied via CI secrets).
    private AdView adView;
    private InterstitialAd interstitialAd;
    private boolean adsInitialized;
    private boolean interstitialShown;

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
            if (BuildConfig.ENABLE_ADS) initAds();

            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);       // the weather app is JS-driven
            settings.setDomStorageEnabled(true);       // localStorage remembers the chosen language
            settings.setGeolocationEnabled(true);      // browser geolocation -> Android location
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setSupportZoom(false);
            settings.setBuiltInZoomControls(false);
            settings.setDisplayZoomControls(false);
            // Always fetch the live Vercel files so website edits show up at once.
            settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            webView.setBackgroundColor(0xFF080D1C);
            webView.setVerticalScrollBarEnabled(false);
            webView.setHorizontalScrollBarEnabled(false);
            // Optional bridge: the website ignores it, the app stores the last city.
            webView.addJavascriptInterface(new WeatherBridge(this), "WeatherBridge");

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
            // Play builds must not prompt users to install an APK from outside the store.
            if (BuildConfig.ENABLE_SELF_UPDATE) checkForUpdate();
            // Re-arm the 7 AM / 10 PM job if it was enabled on a previous session.
            NotificationScheduler.scheduleNext(this);
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
        mainHandler.postDelayed(this::dismissSplash, 4500);
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

    /** App-only tweaks: remove footer, put contacts + notification bell in the navbar. */
    private void injectAppSkin(WebView view) {
        if (view == null || appSkinJs == null || appSkinJs.isEmpty()) return;
        try {
            view.evaluateJavascript(
                    "window.APP_VERSION=\"" + BuildConfig.VERSION_NAME + "\";", null);
            view.evaluateJavascript(appSkinJs, null);
        } catch (Throwable t) {
            Log.e(TAG, "App skin injection failed", t);
        }
    }

    // ================= Notifications (called from WeatherBridge) =================

    boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    void setNotificationsEnabledFromWeb(boolean enabled) {
        if (!enabled) {
            Prefs.setNotificationsEnabled(this, false);
            NotificationScheduler.cancel(this);
            Toast.makeText(this, "الإشعارات متوقفة", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasNotificationPermission()) {
            try {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_REQUEST_CODE);
            } catch (Throwable t) {
                Log.e(TAG, "Notification permission request failed", t);
            }
            return;
        }
        Prefs.setNotificationsEnabled(this, true);
        NotificationScheduler.scheduleNext(this);
        Toast.makeText(this, "الإشعارات مفعّلة · 7 صباحاً و10 مساءً", Toast.LENGTH_SHORT).show();
    }

    /** Called automatically whenever the web page shows a city's weather. */
    void onCityLoaded() {
        if (!BuildConfig.ENABLE_ADS) return;
        // Slight delay so the interstitial never covers the splash/first paint.
        mainHandler.postDelayed(() -> {
            if (isFinishing() || interstitialShown || interstitialAd == null) return;
            interstitialShown = true;
            try {
                interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdDismissedFullScreenContent() {
                        interstitialAd = null;
                        loadInterstitial();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError error) {
                        interstitialAd = null;
                        loadInterstitial();
                    }
                });
                interstitialAd.show(this);
            } catch (Throwable t) {
                Log.w(TAG, "Could not show interstitial", t);
                interstitialAd = null;
            }
        }, 9000);
    }

    // ================= AdMob =================

    private void initAds() {
        try {
            ConsentRequestParameters params = new ConsentRequestParameters.Builder()
                    .setTagForUnderAgeOfConsent(false)
                    .build();
            UserMessagingPlatform.getConsentInformation(this).requestConsentInfoUpdate(
                    this,
                    params,
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            this, error -> initializeMobileAds()),
                    error -> initializeMobileAds());
            // Never block ads forever if the consent backend is unreachable.
            mainHandler.postDelayed(this::initializeMobileAds, 5000);
        } catch (Throwable t) {
            Log.w(TAG, "Consent flow failed", t);
            initializeMobileAds();
        }
    }

    private void initializeMobileAds() {
        if (adsInitialized) return;
        adsInitialized = true;
        try {
            MobileAds.initialize(this, status -> runOnUiThread(() -> {
                loadBanner();
                loadInterstitial();
            }));
        } catch (Throwable t) {
            Log.w(TAG, "AdMob init failed", t);
        }
    }

    private void loadBanner() {
        try {
            adView = findViewById(R.id.adView);
            if (adView == null) return;
            adView.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
            adView.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    if (adView != null) adView.setVisibility(View.VISIBLE);
                }
            });
            adView.loadAd(new AdRequest.Builder().build());
        } catch (Throwable t) {
            Log.w(TAG, "Banner load failed", t);
        }
    }

    private void loadInterstitial() {
        try {
            InterstitialAd.load(this, BuildConfig.ADMOB_INTERSTITIAL_ID,
                    new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override
                        public void onAdLoaded(InterstitialAd ad) {
                            interstitialAd = ad;
                        }

                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            interstitialAd = null;
                        }
                    });
        } catch (Throwable t) {
            Log.w(TAG, "Interstitial load failed", t);
        }
    }

    // ================= Update / navigation / fallback =================

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
                            (dialog, which) -> downloadUpdate(release))
                    .setNegativeButton(R.string.update_later, null)
                    .show();
        });
    }

    /**
     * Downloads the release APK inside the app via DownloadManager, then opens the
     * system package installer. No REQUEST_INSTALL_PACKAGES permission is needed.
     */
    private void downloadUpdate(UpdateChecker.Release release) {
        try {
            String fileName = "weather-app-v" + release.version + ".apk";
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) {
                openReleaseInBrowser(release.downloadUrl);
                return;
            }

            // Remove old downloaded APKs to keep storage clean.
            try {
                java.io.File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (dir != null && dir.listFiles() != null) {
                    for (java.io.File f : dir.listFiles()) {
                        if (f.getName().startsWith("weather-app-v") && f.getName().endsWith(".apk")) f.delete();
                    }
                }
            } catch (Throwable ignored) {
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.downloadUrl));
            request.setMimeType("application/vnd.android.package-archive");
            request.setTitle(getString(R.string.app_name) + " v" + release.version);
            request.setDescription(getString(R.string.update_downloading));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, android.os.Environment.DIRECTORY_DOWNLOADS, fileName);
            updateDownloadId = dm.enqueue(request);

            registerUpdateDownloadReceiver();
            Toast.makeText(this, R.string.update_downloading, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Log.e(TAG, "In-app download failed, falling back to browser", t);
            openReleaseInBrowser(release.downloadUrl);
        }
    }

    private void registerUpdateDownloadReceiver() {
        if (updateDownloadReceiver != null) return;
        updateDownloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != updateDownloadId) return;
                installDownloadedUpdate();
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateDownloadReceiver, filter);
        }
    }

    private void installDownloadedUpdate() {
        try {
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) return;
            Uri uri = dm.getUriForDownloadedFile(updateDownloadId);
            if (uri == null) {
                Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(install);
        } catch (Throwable t) {
            Log.e(TAG, "Could not launch installer", t);
            Toast.makeText(this, R.string.update_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** Browser fallback if in-app download is unavailable. */
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

        if (requestCode == NOTIFICATION_REQUEST_CODE) {
            boolean granted = grantResults != null && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                Prefs.setNotificationsEnabled(this, true);
                NotificationScheduler.scheduleNext(this);
                Toast.makeText(this, "الإشعارات مفعّلة · 7 صباحاً و10 مساءً", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "الموافقة على الإشعارات مطلوبة", Toast.LENGTH_LONG).show();
            }
            return;
        }

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
    protected void onDestroy() {
        if (updateDownloadReceiver != null) {
            try { unregisterReceiver(updateDownloadReceiver); } catch (Throwable ignored) {}
            updateDownloadReceiver = null;
        }
        super.onDestroy();
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
