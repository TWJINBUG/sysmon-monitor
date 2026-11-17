package com.dsh.sysmon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

public class MonitorService extends Service {

    private static final int NOTIF_ID = 1001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Perf.Sampler sampler = new Perf.Sampler();
    private final Perf.Sampler notifSampler = new Perf.Sampler();
    private float lastCpu = -1f;
    private float lastMem = -1f;

    private final Runnable history = new Runnable() {
        @Override public void run() {
            record();
            handler.postDelayed(this, 300000);
        }
    };

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        sampler.sample(); // 采集一帧基线，1.5 秒后的首条记录才有真实差值
        startForeground(NOTIF_ID, buildNotification());
        handler.post(loop);
        handler.postDelayed(history, 1500); // 先让采样器有一帧基线，首条记录才有真实值
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void record() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            boolean interactive = pm != null && pm.isInteractive();
            HistoryStore.append(this, Stats.batteryPct(this), Stats.batteryTemp(this), interactive, Stats.charging(this), AppUsage.foreground(this));

            Perf.Usage u = sampler.sample();
            Perf.Mem m = Perf.mem();
            lastCpu = u.total;
            lastMem = m.total > 0 ? m.used() * 100f / m.total : -1f;
            if (u.total >= 0 && lastMem >= 0) PerfStore.append(this, u.total, lastMem);
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(loop);
        super.onDestroy();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("monitor", "设备监控", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        float ct = Stats.cpuTemp();
        float bt = Stats.batteryTemp(this);
        long[] m = Stats.mem();
        String text = "CPU " + (lastCpu >= 0 ? String.format(java.util.Locale.US, "%.0f%%", lastCpu) : "--")
                + (ct > 0 ? String.format(java.util.Locale.US, " %.1f℃", ct) : "")
                + " · 内存 " + (lastMem >= 0 ? String.format(java.util.Locale.US, "%.0f%%", lastMem) : "--")
                + " " + Stats.gb(m[0]) + "/" + Stats.gb(m[1]) + "GB"
                + " · 电池 " + (bt > 0 ? String.format(java.util.Locale.US, "%.1f", bt) : "--") + "℃";

        PendingIntent pi = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, "monitor")
                : new Notification.Builder(this);
        b.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("设备监控")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        return b.build();
    }

    private void refresh() {
        Perf.Usage u = notifSampler.sample();
        Perf.Mem m = Perf.mem();
        if (u.total >= 0) lastCpu = u.total;
        if (m.total > 0) lastMem = m.used() * 100f / m.total;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification());
        updateWidgets();
    }

    private void updateWidgets() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName cn = new ComponentName(this, SysMonWidget.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        for (int id : ids) SysMonWidget.updateViews(this, mgr, id);
    }
}
