package com.hishamwahba.weatherapp;

import android.util.Log;
import android.webkit.JavascriptInterface;

/**
 * Optional bridge exposed to the WebView as window.WeatherBridge.
 * On the Vercel website this object simply does not exist, so the same JS file
 * keeps working there as a no-op.
 */
public class WeatherBridge {
    private static final String TAG = "WeatherApp";
    private final MainActivity activity;

    public WeatherBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void saveLastCity(String name, String country, String latitude,
                             String longitude, String language) {
        try {
            double lat = Double.parseDouble(latitude);
            double lon = Double.parseDouble(longitude);
            Prefs.saveCity(activity, name, country, lat, lon, language);
            NotificationScheduler.scheduleNext(activity);
        } catch (Throwable t) {
            Log.w(TAG, "Could not store last city", t);
        }
        activity.onCityLoaded();
    }

    @JavascriptInterface
    public boolean getNotificationsEnabled() {
        return Prefs.notificationsEnabled(activity);
    }

    @JavascriptInterface
    public boolean hasNotificationPermission() {
        return activity.hasNotificationPermission();
    }

    @JavascriptInterface
    public void setNotificationsEnabled(final boolean enabled) {
        activity.runOnUiThread(() -> activity.setNotificationsEnabledFromWeb(enabled));
    }
}
