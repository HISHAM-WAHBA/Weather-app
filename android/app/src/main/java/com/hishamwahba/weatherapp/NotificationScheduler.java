package com.hishamwahba.weatherapp;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Calendar;

/**
 * Schedules the next weather notification at 7:00 AM or 10:00 PM (device time).
 * Uses the framework AlarmManager — no background library needed. The alarm
 * re-arms itself after each notification and after a reboot.
 */
final class NotificationScheduler {
    private static final String TAG = "WeatherApp";
    private static final int REQUEST_CODE = 2001;
    private static final int MORNING_HOUR = 7;
    private static final int NIGHT_HOUR = 22;

    private NotificationScheduler() {
    }

    static void scheduleNext(Context context) {
        try {
            if (!Prefs.notificationsEnabled(context)) {
                cancel(context);
                return;
            }
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms == null) return;
            long when = nextSlotMillis();
            PendingIntent pending = pendingIntent(context);
            // setAndAllowWhileIdle is inexact but has no exact-alarm permission
            // requirement, so it is safe for Google Play and works in Doze.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pending);
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, when, pending);
            }
            Log.i(TAG, "Next weather notification scheduled");
        } catch (Throwable t) {
            Log.w(TAG, "Could not schedule weather notification", t);
        }
    }

    static void cancel(Context context) {
        try {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.cancel(pendingIntent(context));
        } catch (Throwable t) {
            Log.w(TAG, "Could not cancel weather notification", t);
        }
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(context, WeatherAlarmReceiver.class);
        intent.setAction("com.hishamwahba.weatherapp.DAILY_WEATHER");
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }

    /** Absolute time of the next 7:00 or 22:00, always in the future. */
    private static long nextSlotMillis() {
        long now = System.currentTimeMillis();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);

        if (next.getTimeInMillis() <= now) {
            next.set(Calendar.HOUR_OF_DAY, NIGHT_HOUR);
            if (next.getTimeInMillis() <= now) {
                next.add(Calendar.DAY_OF_YEAR, 1);
                next.set(Calendar.HOUR_OF_DAY, MORNING_HOUR);
            }
        }
        return next.getTimeInMillis();
    }
}
