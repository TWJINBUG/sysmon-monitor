package com.dsh.sysmon;

import android.app.AppOpsManager;
import android.content.Context;
import android.os.Process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AppUsage {
    private AppUsage() {}

    public static boolean hasAccess(Context c) {
        try {
            AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    public static String foreground(Context c) {
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents ev = usm.queryEvents(now - 30000, now);
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String pkg = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    pkg = e.getPackageName();
                }
            }
            return pkg;
        } catch (Exception e) {
            return null;
        }
    }

    public static final class Event {
        public long t;
        public String pkg;
    }

    /** 区间内的前台切换事件（用于时间轴图标） */
    public static List<Event> events(Context c, long start, long end) {
        List<Event> out = new ArrayList<>();
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            String self = c.getPackageName();
            String launcher = null;
            try {
                android.content.Intent home = new android.content.Intent(android.content.Intent.ACTION_MAIN);
                home.addCategory(android.content.Intent.CATEGORY_HOME);
                android.content.pm.ResolveInfo ri = c.getPackageManager().resolveActivity(home, 0);
                if (ri != null && ri.activityInfo != null) launcher = ri.activityInfo.packageName;
            } catch (Exception ignored) {}
            android.app.usage.UsageEvents ev = usm.queryEvents(start, end);
            android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
            String last = "";
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                if (e.getEventType() != android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) continue;
                String p = e.getPackageName();
                if (p == null || p.equals(last)) continue;
                if (p.equals(self) || p.equals(launcher) || p.equals("com.android.systemui")) continue;
                if (c.getPackageManager().getLaunchIntentForPackage(p) == null) continue;
                last = p;
                Event x = new Event();
                x.t = e.getTimeStamp();
                x.pkg = p;
                out.add(x);
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 每个应用当天前台时长（毫秒） */
    public static Map<String, Long> usageForDay(Context c, long start, long end) {
        Map<String, Long> out = new HashMap<>();
        try {
            android.app.usage.UsageStatsManager usm =
                    (android.app.usage.UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            Map<String, android.app.usage.UsageStats> map = usm.queryAndAggregateUsageStats(start, end);
            for (Map.Entry<String, android.app.usage.UsageStats> en : map.entrySet()) {
                long t = en.getValue().getTotalTimeInForeground();
                if (t > 10000) out.put(en.getKey(), t);
            }
        } catch (Exception ignored) {}
        return out;
    }
}
