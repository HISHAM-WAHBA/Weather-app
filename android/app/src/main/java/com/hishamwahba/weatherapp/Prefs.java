package com.hishamwahba.weatherapp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small local store for the "last searched city" and the notification switch.
 * Nothing is uploaded anywhere — this data never leaves the device.
 */
final class Prefs {
    private static final String FILE = "weather_prefs";

    static final String KEY_CITY = "last_city_name";
    static final String KEY_COUNTRY = "last_city_country";
    static final String KEY_LAT = "last_lat";
    static final String KEY_LON = "last_lon";
    static final String KEY_LANG = "last_lang";
    static final String KEY_NOTIFICATIONS = "notifications_enabled";

    private Prefs() {
    }

    private static SharedPreferences get(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean notificationsEnabled(Context context) {
        return get(context).getBoolean(KEY_NOTIFICATIONS, false);
    }

    static void setNotificationsEnabled(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    static void saveCity(Context context, String name, String country,
                         double latitude, double longitude, String language) {
        get(context).edit()
                .putString(KEY_CITY, name == null ? "" : name)
                .putString(KEY_COUNTRY, country == null ? "" : country)
                .putString(KEY_LAT, String.valueOf(latitude))
                .putString(KEY_LON, String.valueOf(longitude))
                .putString(KEY_LANG, language == null ? "en" : language)
                .apply();
    }

    static String city(Context context) {
        return get(context).getString(KEY_CITY, "");
    }

    static String country(Context context) {
        return get(context).getString(KEY_COUNTRY, "");
    }

    static String language(Context context) {
        return get(context).getString(KEY_LANG, "en");
    }

    static double latitude(Context context) {
        return parse(get(context).getString(KEY_LAT, ""));
    }

    static double longitude(Context context) {
        return parse(get(context).getString(KEY_LON, ""));
    }

    static boolean hasCity(Context context) {
        double lat = latitude(context);
        double lon = longitude(context);
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    private static double parse(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Throwable t) {
            return Double.NaN;
        }
    }
}
