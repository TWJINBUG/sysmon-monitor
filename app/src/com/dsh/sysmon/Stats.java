package com.dsh.sysmon;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public final class Stats {
    private Stats() {}

    private static String read(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String s = r.readLine();
            return s == null ? null : s.trim();
        } catch (Exception e) {
            return null;
        }
    }

    public static float cpuTemp() {
        File dir = new File("/sys/class/thermal");
        File[] zones = dir.listFiles();
        float max = -1f;
        if (zones == null) return max;
        for (File z : zones) {
            if (!z.getName().startsWith("thermal_zone")) continue;
            String type = read(z.getPath() + "/type");
            if (type == null || type.contains("trip")) continue;
            if (!(type.startsWith("cpu-") || type.startsWith("cpullc") || type.startsWith("cpuss"))) continue;
            float v = parseTemp(read(z.getPath() + "/temp"));
            if (v > max) max = v;
        }
        return max;
    }

    public static float boardTemp() {
        File dir = new File("/sys/class/thermal");
        File[] zones = dir.listFiles();
        if (zones == null) return -1f;
        float v1 = -1f, v2 = -1f;
        for (File z : zones) {
            String type = read(z.getPath() + "/type");
            if (type == null) continue;
            if (type.equals("board_temp")) v1 = parseTemp(read(z.getPath() + "/temp"));
            if (type.equals("shell_back")) v2 = parseTemp(read(z.getPath() + "/temp"));
        }
        return v1 > 0 ? v1 : v2;
    }

    private static float parseTemp(String s) {
        if (s == null) return -1f;
        try { return Float.parseFloat(s) / 1000f; } catch (Exception e) { return -1f; }
    }

    private static Intent battery(Context c) {
        return c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    public static float batteryTemp(Context c) {
        Intent i = battery(c);
        if (i == null) return -1f;
        return i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f;
    }

    public static int batteryPct(Context c) {
        Intent i = battery(c);
        if (i == null) return -1;
        int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale > 0 ? lvl * 100 / scale : lvl;
    }

    public static boolean charging(Context c) {
        Intent i = battery(c);
        if (i == null) return false;
        int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
    }

    public static long[] mem() {
        long total = 0, avail = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("MemTotal:")) total = kb(line);
                else if (line.startsWith("MemAvailable:")) avail = kb(line);
            }
        } catch (Exception ignored) {}
        return new long[]{Math.max(0, total - avail), total};
    }

    private static long kb(String line) {
        try {
            String[] p = line.trim().split("\\s+");
            return Long.parseLong(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 返回 {已用 KB, 总共 KB} */
    public static long[] storage(Context c) {
        try {
            StatFs fs = new StatFs(c.getFilesDir().getAbsolutePath());
            long total = fs.getTotalBytes() / 1024L;
            long avail = fs.getAvailableBytes() / 1024L;
            return new long[]{total - avail, total};
        } catch (Exception e) {
            return new long[]{0, 0};
        }
    }

    public static String uptime() {
        long s = SystemClock.elapsedRealtime() / 1000L;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        return (d > 0 ? d + "天" : "") + h + "时" + m + "分";
    }

    public static String gb(long kb) {
        if (kb <= 0) return "--";
        return String.format(java.util.Locale.US, "%.1f", kb / 1024.0 / 1024.0);
    }

    public static String freqs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            String f = read("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (i > 0) sb.append(" / ");
            if (f == null) { sb.append("--"); continue; }
            try {
                sb.append(String.format(java.util.Locale.US, "%.2f", Integer.parseInt(f) / 1000000f));
            } catch (Exception e) {
                sb.append("--");
            }
        }
        return sb.toString();
    }
}
