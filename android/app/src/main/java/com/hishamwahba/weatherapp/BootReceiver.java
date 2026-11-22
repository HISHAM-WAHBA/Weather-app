package com.hishamwahba.weatherapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the daily weather alarm after the device restarts. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        NotificationScheduler.scheduleNext(context.getApplicationContext());
    }
}
