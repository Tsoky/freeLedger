package com.freeledger.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class IgnoreReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int id = intent.getIntExtra(DetectionNotifier.EXTRA_NOTIFICATION_ID, -1);
        if (id >= 0) DetectionNotifier.cancel(context, id);
    }
}
