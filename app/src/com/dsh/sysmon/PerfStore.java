package com.dsh.sysmon;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/** CPU / 内存使用率历史，每 5 分钟一条，与电池记录分开存 */
public final class PerfStore {
    private PerfStore() {}

    public static File file(Context c) {
        return new File(c.getFilesDir(), "perf_history.csv");
    }

    public static void append(Context c, float cpu, float memPct) {
        try {
            File f = file(c);
            if (f.length() > 1024 * 1024) trim(f);
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(System.currentTimeMillis() + ","
                        + String.format(java.util.Locale.US, "%.0f", cpu) + ","
                        + String.format(java.util.Locale.US, "%.0f", memPct) + "\n");
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
            for (int i = Math.max(0, lines.size() - 3000); i < lines.size(); i++) {
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
                if (p.length < 3) continue;
                try {
                    Sample s = new Sample();
                    s.t = Long.parseLong(p[0]);
                    s.cpu = Float.parseFloat(p[1]);
                    s.mem = Float.parseFloat(p[2]);
                    out.add(s);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static final class Sample {
        public long t;
        public float cpu;
        public float mem;
    }
}
