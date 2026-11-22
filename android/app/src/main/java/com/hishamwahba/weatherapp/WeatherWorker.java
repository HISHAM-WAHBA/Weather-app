package com.hishamwahba.weatherapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Fetches the weather for the last searched city and posts one notification. */
public class WeatherWorker extends Worker {
    private static final String TAG = "WeatherApp";
    private static final String CHANNEL_ID = "weather_daily";
    private static final int NOTIFICATION_ID = 1001;

    public WeatherWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        try {
            if (!Prefs.notificationsEnabled(context) || !Prefs.hasCity(context)) {
                return Result.success();
            }

            String url = "https://api.open-meteo.com/v1/forecast?latitude="
                    + Prefs.latitude(context) + "&longitude=" + Prefs.longitude(context)
                    + "&current=temperature_2m,weather_code&timezone=auto";
            JSONObject json = new JSONObject(fetch(url));
            JSONObject current = json.optJSONObject("current");
            if (current == null) return Result.success();

            double temp = current.optDouble("temperature_2m", Double.NaN);
            int code = current.optInt("weather_code", -1);
            boolean arabic = "ar".equals(Prefs.language(context));
            String city = Prefs.city(context);
            String title = city.isEmpty()
                    ? (arabic ? "تطبيق الطقس" : "Weather")
                    : (arabic ? "الطقس في " + city : "Weather in " + city);
            String tempText = Double.isNaN(temp) ? "--°" : Math.round(temp) + "°";
            String body = tempText + " · " + conditionText(code, arabic);

            postNotification(context, title, body);
            return Result.success();
        } catch (Throwable t) {
            // A failed fetch must not retry endlessly — the next scheduled slot
            // will try again with fresh data.
            Log.w(TAG, "Weather notification fetch failed", t);
            return Result.success();
        } finally {
            NotificationScheduler.scheduleNext(context);
        }
    }

    private static String fetch(String urlString) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
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

    private void postNotification(Context context, String title, String body) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !manager.areNotificationsEnabled()) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Weather updates", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Daily weather at 7 AM and 10 PM");
            manager.createNotificationChannel(channel);
        }

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(
                context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_stat_weather)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build();

        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (Throwable t) {
            Log.w(TAG, "Could not post notification", t);
        }
    }

    private static String conditionText(int code, boolean arabic) {
        if (arabic) {
            if (code == 0) return "صافٍ";
            if (code == 1) return "صافٍ غالباً";
            if (code == 2) return "غائم جزئياً";
            if (code == 3) return "غائم";
            if (code == 45 || code == 48) return "ضباب";
            if (code >= 51 && code <= 57) return "رذاذ";
            if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "مطر";
            if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "ثلج";
            if (code >= 95) return "عاصفة رعدية";
            return "غير معروف";
        }
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "Rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorm";
        return "Unknown";
    }
}
