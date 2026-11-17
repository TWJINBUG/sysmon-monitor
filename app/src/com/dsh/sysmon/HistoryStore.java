package com.dsh.sysmon;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class HistoryStore {
    private HistoryStore() {}

    public static File file(Context c) {
        return new File(c.getFilesDir(), "battery_history.csv");
    }

    public static void append(Context c, int cap, float temp, boolean interactive, boolean charging, String pkg) {
        try {
            File f = file(c);
            if (f.length() > 3 * 1024 * 1024) trim(f);
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(System.currentTimeMillis() + "," + cap + ","
                        + String.format(java.util.Locale.US, "%.1f", temp) + ","
                        + (interactive ? 1 : 0) + "," + (charging ? 1 : 0) + ","
                        + (pkg == null ? "" : pkg) + "\n");
            }
        } catch (Exception ignored) {}
    }

    private static void trim(File f) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) lines.add(l);
        }
        try (FileWriter w = new FileWriter(f, false)) {
            for (int i = Math.max(0, lines.size() - 5000); i < lines.size(); i++) {
                w.write(lines.get(i));
                w.write("\n");
            }
        }
    }

    public static List<Sample> read(Context c) {
        List<Sample> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = r.readLine()) != null) {
                String[] p = l.split(",", -1);
                if (p.length < 5) continue;
                try {
                    Sample s = new Sample();
                    s.t = Long.parseLong(p[0]);
                    s.cap = Integer.parseInt(p[1]);
                    s.temp = Float.parseFloat(p[2]);
                    s.interactive = p[3].equals("1");
                    s.charging = p[4].equals("1");
                    s.pkg = p.length > 5 ? p[5] : "";
                    out.add(s);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** 删除某一天的记录 */
    public static void deleteDay(Context c, long dayStart, long dayEnd) {
        try {
            File f = file(c);
            if (!f.exists()) return;
            List<String> keep = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String l;
                while ((l = r.readLine()) != null) {
                    String[] p = l.split(",", -1);
                    if (p.length < 1) continue;
                    try {
                        long t = Long.parseLong(p[0]);
                        if (t >= dayStart && t < dayEnd) continue;
                    } catch (Exception ignored) {}
                    keep.add(l);
                }
            }
            try (FileWriter w = new FileWriter(f, false)) {
                for (String l : keep) {
                    w.write(l);
                    w.write("\n");
                }
            }
        } catch (Exception ignored) {}
    }

    public static final class Sample {
        public long t;
        public int cap;
        public float temp;
        public boolean interactive;
        public boolean charging;
        public String pkg = "";
    }
}
