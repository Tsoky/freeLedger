package com.freeledger.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.util.Locale;

public final class DetectionNotifier {
    private DetectionNotifier() {}

    // Channel importance is immutable after creation.  Use a versioned id so
    // devices where the old channel was accidentally muted can recover.
    public static final String CHANNEL_ID = "payment_detected_v2";
    public static final String EXTRA_NOTIFICATION_ID = "notification_id";

    public static void show(Context context, PaymentParser.Detection d) {
        try {
            if (Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) return;

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || (Build.VERSION.SDK_INT >= 24 && !nm.areNotificationsEnabled())) {
                android.util.Log.w("FreeLedgerNotify", "notifications disabled");
                return;
            }

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "支付检测",
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("检测到疑似支付后，提醒你确认是否记账");
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
        }

        int id = 3000 + Math.abs(d.signature.hashCode() % 100000);

        Intent open = new Intent(context, QuickEntryActivity.class);
        open.putExtra("detected", true);
        open.putExtra("amount", d.amount);
        open.putExtra("type", d.type);
        open.putExtra("sourcePackage", d.sourcePackage);
        open.putExtra("sourceApp", d.sourceApp);
        open.putExtra("rawText", d.rawText);
        open.putExtra(EXTRA_NOTIFICATION_ID, id);
        open.setData(Uri.parse("freeledger://detected/" + id + "/" + System.currentTimeMillis()));

        PendingIntent openPi = PendingIntent.getActivity(
                context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent ignore = new Intent(context, IgnoreReceiver.class);
        ignore.putExtra(EXTRA_NOTIFICATION_ID, id);
        PendingIntent ignorePi = PendingIntent.getBroadcast(
                context, id + 1, ignore,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String amount = String.format(Locale.CHINA, "¥%.2f", d.amount);
        String title = "检测到一笔可能的支出 " + amount;
        String body = "点一下确认并记账";

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        b.setSmallIcon(R.drawable.ic_wallet)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(
                        body + "\n来源：" + d.sourceApp
                ))
                .setAutoCancel(true)
                .setContentIntent(openPi)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_HIGH)
                .addAction(android.R.drawable.ic_input_add, "记一笔", openPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "忽略", ignorePi);

            nm.notify(id, b.build());
        } catch (SecurityException e) {
            android.util.Log.e("FreeLedgerNotify", "notification permission denied", e);
        }
    }

    public static void cancel(Context context, int id) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(id);
    }
}
