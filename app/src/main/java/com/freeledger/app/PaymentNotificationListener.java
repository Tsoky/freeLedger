package com.freeledger.app;

import android.util.Log;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Handler;
import android.os.Looper;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class PaymentNotificationListener extends NotificationListenerService {
    private static final String TAG = "FreeLedgerNotify";
    static final String PREFS = "notification_listener_status";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable rebind = () -> {
        try { requestRebind(new android.content.ComponentName(this, PaymentNotificationListener.class)); }
        catch (Throwable t) { Log.e(TAG, "rebind failed", t); }
    };

    @Override public void onCreate() {
        super.onCreate();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("connected", false).putLong("created_time", System.currentTimeMillis()).apply();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "notification listener connected");
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                nm.createNotificationChannel(new NotificationChannel("listener_status", "支付监听状态", NotificationManager.IMPORTANCE_LOW));
            }
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, "listener_status") : new Notification.Builder(this);
            b.setSmallIcon(R.drawable.ic_wallet).setContentTitle("自由账本正在监听支付通知")
                    .setContentText("监听服务运行中").setOngoing(true).setCategory(Notification.CATEGORY_SERVICE);
            startForeground(9101, b.build());
        } catch (Throwable t) { Log.e(TAG, "failed to enter foreground", t); }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("connected", true).apply();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "notification listener disconnected; requesting rebind");
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("connected", false).apply();
        // Some OEMs disconnect notification listeners while reclaiming the
        // process. Ask the system to bind this service again automatically.
        try {
            handler.removeCallbacks(rebind);
            handler.postDelayed(rebind, 1500L);
        } catch (Throwable t) {
            Log.e(TAG, "failed to request listener rebind", t);
        }
    }

    @Override public void onDestroy() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean("connected", false).putLong("destroyed_time", System.currentTimeMillis()).apply();
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || sbn.getNotification() == null) return;
            Log.d(TAG, "notification posted from " + sbn.getPackageName());
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong("last_time", System.currentTimeMillis())
                    .putString("last_package", sbn.getPackageName()).apply();
            PaymentParser.Detection d = PaymentParser.parse(this, sbn);
            if (d == null) return;

            LedgerStore store = new LedgerStore(this);
            if (!store.markDetectionIfNew(d.signature, 90_000L)) return;
            Log.i(TAG, "payment detected from " + d.sourcePackage + " amount=" + d.amount);
            DetectionNotifier.show(this, d);
        } catch (Throwable t) {
            // A malformed vendor notification must not terminate the listener
            // process and silently disable all subsequent detections.
            Log.e(TAG, "failed to process notification", t);
        }
    }
}
