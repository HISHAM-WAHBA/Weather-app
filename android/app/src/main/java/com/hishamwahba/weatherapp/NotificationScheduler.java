package com.hishamwahba.weatherapp;

import android.content.Context;
import android.util.Log;

import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Schedules the next weather notification at 7:00 AM or 10:00 PM (device time).
 * After each notification the worker calls scheduleNext() again, so the cycle
 * keeps going. WorkManager also restores pending work after a reboot.
 */
final class NotificationScheduler {
    private static final String TAG = "WeatherApp";
    private static final String UNIQUE_WORK = "daily_weather_notification";
    private static final int MORNING_HOUR = 7;
    private static final int NIGHT_HOUR = 22;

    private NotificationScheduler() {
    }

    static void scheduleNext(Context context) {
        try {
            WorkManager manager = WorkManager.getInstance(context.getApplicationContext());
            if (!Prefs.notificationsEnabled(context)) {
                manager.cancelUniqueWork(UNIQUE_WORK);
                return;
            }
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WeatherWorker.class)
                    .setInitialDelay(delayToNextSlot(), TimeUnit.MILLISECONDS)
                    .build();
            manager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request);
        } catch (Throwable t) {
            Log.w(TAG, "Could not schedule weather notification", t);
        }
    }

    static void cancel(Context context) {
        try {
            WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(UNIQUE_WORK);
        } catch (Throwable t) {
            Log.w(TAG, "Could not cancel weather notification", t);
        }
    }

    /** Milliseconds from now to the next 7:00 or 22:00, always in the future. */
    private static long delayToNextSlot() {
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
        return Math.max(next.getTimeInMillis() - now, TimeUnit.MINUTES.toMillis(1));
    }
}
