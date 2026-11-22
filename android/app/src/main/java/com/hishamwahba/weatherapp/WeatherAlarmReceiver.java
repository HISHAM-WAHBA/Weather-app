package com.hishamwahba.weatherapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Wakes up at 7 AM / 10 PM, fetches the weather, then arms the next alarm. */
public class WeatherAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "WeatherApp";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                WeatherWorker.refreshAndNotify(context.getApplicationContext());
            } catch (Throwable t) {
                Log.w(TAG, "Weather alarm failed", t);
            } finally {
                try {
                    NotificationScheduler.scheduleNext(context.getApplicationContext());
                } catch (Throwable ignored) {
                }
                executor.shutdown();
                result.finish();
            }
        });
    }
}
