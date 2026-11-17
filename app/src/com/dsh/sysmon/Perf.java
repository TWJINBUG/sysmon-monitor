package com.dsh.sysmon;

import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * CPU / 内存使用率采样。
 *
 * CPU 有三条路径，按可靠性依次降级：
 *   1. /proc/stat 前后差值（原生机型可读，最准）；
 *   2. /sys/devices/system/cpu/cpuN/cpuidle/stateX/time 空闲计时（Android 12+ 屏蔽 /proc/stat 后的替代，实测误差 <1%）；
 *   3. 各核当前频率占比（最后兜底，只作趋势参考）。
 */
public final class Perf {
    private Perf() {}

    /** 一次采样结果 */
    public static final class Usage {
        public float total = -1f;             // 总体使用率（%），首次采样为 -1
        public float[] core = new float[0];   // 每核使用率（离线核为 -1）
        public float[] freq = new float[0];   // 每核当前频率（GHz）
        public int cores = 0;
        public int online = 0;
        public String source = "";
    }

    /** /proc/meminfo 关键项，单位 KB */
    public static final class Mem {
        public long total, avail, cached, buffers, swapTotal, swapUsed;

        public long used() { return Math.max(0, total - avail); }
    }

    /** 独立采样器：自己保存上一帧计数，多个采样器互不干扰 */
    public static final class Sampler {
        private long[][] lastStat;
        private long[] lastIdle;
        private long lastWall;
        private boolean statDead;

        public Usage sample() {
            int cores = coreCount();
            Usage u = new Usage();
            u.cores = cores;
            u.freq = freqs(cores);

            long wall = SystemClock.elapsedRealtime();
            long dt = lastWall > 0 ? wall - lastWall : 0;

            long[][] stat = null;
            if (!statDead) {
                stat = stat(cores);
                if (stat[0] == null) statDead = true;
            }

            long[] idle = idleUs(cores);
            boolean[] online = online(cores);
            for (boolean b : online) if (b) u.online++;

            float[] usage = new float[cores];
            for (int i = 0; i < cores; i++) usage[i] = -1f;

            if (stat != null && stat[0] != null && lastStat != null && dt > 0) {
                for (int i = 1; i <= cores && i < stat.length; i++) {
                    usage[i - 1] = ratio(stat[i], lastStat[i]);
                }
                u.total = ratio(stat[0], lastStat[0]);
                u.source = "/proc/stat";

            } else if (idle != null && lastIdle != null && dt >= 200) {
                double sum = 0;
                int n = 0;
                for (int i = 0; i < cores; i++) {
                    if (!online[i] || idle[i] < 0 || lastIdle[i] < 0) continue;
                    long d = idle[i] - lastIdle[i];
                    if (d < 0) d = 0;
                    float busy = 100f - (float) (d * 100.0 / (dt * 1000.0));
                    usage[i] = clamp(busy);
                    sum += usage[i];
                    n++;
                }
                if (n > 0) {
                    u.total = (float) (sum / n);
                    u.source = "cpuidle 空闲计时";
                }

            } else if (dt >= 200) {
                double sum = 0;
                int n = 0;
                for (int i = 0; i < cores; i++) {
                    float lo = minFreq(i), hi = maxFreq(i);
                    if (!online[i] || u.freq[i] <= 0 || hi <= lo) continue;
                    usage[i] = clamp((u.freq[i] - lo) * 100f / (hi - lo));
                    sum += usage[i];
                    n++;
                }
                if (n > 0) {
                    u.total = (float) (sum / n);
                    u.source = "频率估算";
                }
            }

            u.core = usage;
            if (stat != null) lastStat = stat;
            lastIdle = idle;
            lastWall = wall;
            return u;
        }
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(100f, v)); }

    // ---------------- /proc/stat ----------------

    private static long[] parseStat(String[] p) {
        long busy = 0, idle = 0;
        try {
            for (int i = 1; i < p.length && i <= 8; i++) {
                long n = Long.parseLong(p[i]);
                if (i == 4 || i == 5) idle += n;   // idle + iowait
                else busy += n;                    // user nice system irq softirq steal
            }
        } catch (Exception ignored) {}
        return new long[]{busy, busy + idle};
    }

    private static long[][] stat(int cores) {
        long[][] out = new long[cores + 1][];
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/stat"))) {
            String l;
            while ((l = r.readLine()) != null) {
                if (!l.startsWith("cpu")) break;
                String[] p = l.trim().split("\\s+");
                int idx;
                if (p[0].equals("cpu")) {
                    idx = 0;
                } else {
                    try {
                        idx = Integer.parseInt(p[0].substring(3)) + 1;
                    } catch (Exception e) {
                        continue;
                    }
                    if (idx >= out.length) continue;
                }
                out[idx] = parseStat(p);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static float ratio(long[] a, long[] b) {
        if (a == null || b == null) return -1f;
        long dt = a[1] - b[1], db = a[0] - b[0];
        if (dt <= 0) return -1f;
        return clamp(db * 100f / dt);
    }

    // ---------------- cpuidle ----------------

    private static File[][] idleFiles;

    private static File[][] idleFiles(int cores) {
        if (idleFiles != null && idleFiles.length == cores) return idleFiles;
        File[][] out = new File[cores][];
        for (int i = 0; i < cores; i++) {
            File dir = new File("/sys/devices/system/cpu/cpu" + i + "/cpuidle");
            File[] list = dir.listFiles();
            if (list == null) {
                out[i] = new File[0];
                continue;
            }
            int n = 0;
            for (File f : list) if (f.getName().startsWith("state")) n++;
            File[] times = new File[n];
            int k = 0;
            for (File f : list) {
                if (!f.getName().startsWith("state")) continue;
                times[k++] = new File(f, "time");
            }
            out[i] = times;
        }
        idleFiles = out;
        return out;
    }

    /** 每核累计空闲时间（微秒），读不到为 -1 */
    private static long[] idleUs(int cores) {
        File[][] files = idleFiles(cores);
        long[] out = new long[cores];
        boolean any = false;
        for (int i = 0; i < cores; i++) {
            long sum = 0;
            boolean ok = false;
            for (File f : files[i]) {
                String s = read(f);
                if (s == null) continue;
                try {
                    sum += Long.parseLong(s.trim());
                    ok = true;
                } catch (Exception ignored) {}
            }
            out[i] = ok ? sum : -1;
            if (ok) any = true;
        }
        return any ? out : null;
    }

    // ---------------- 其他 ----------------

    private static int cachedCores;
    private static float[] minCache, maxCache;

    public static int coreCount() {
        if (cachedCores > 0) return cachedCores;
        int n = 0;
        File[] list = new File("/sys/devices/system/cpu").listFiles();
        if (list != null) {
            for (File f : list) {
                String name = f.getName();
                if (!name.startsWith("cpu") || name.equals("cpufreq") || name.equals("cpuidle")) continue;
                try {
                    Integer.parseInt(name.substring(3));
                    n++;
                } catch (Exception ignored) {}
            }
        }
        if (n <= 0) n = Runtime.getRuntime().availableProcessors();
        cachedCores = Math.max(1, n);
        return cachedCores;
    }

    public static float[] freqs(int cores) {
        float[] out = new float[cores];
        for (int i = 0; i < cores; i++) {
            String s = read("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (s == null) continue;
            try {
                out[i] = Integer.parseInt(s.trim()) / 1000000f;
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static float minFreq(int core) {
        if (minCache == null) bounds();
        return minCache != null && core < minCache.length ? minCache[core] : 0f;
    }

    private static float maxFreq(int core) {
        if (maxCache == null) bounds();
        return maxCache != null && core < maxCache.length ? maxCache[core] : 0f;
    }

    private static void bounds() {
        int cores = coreCount();
        float[] lo = new float[cores], hi = new float[cores];
        for (int i = 0; i < cores; i++) {
            lo[i] = freqOf(i, "cpuinfo_min_freq");
            hi[i] = freqOf(i, "cpuinfo_max_freq");
        }
        minCache = lo;
        maxCache = hi;
    }

    private static float freqOf(int core, String name) {
        String s = read("/sys/devices/system/cpu/cpu" + core + "/cpufreq/" + name);
        if (s == null) return 0f;
        try {
            return Integer.parseInt(s.trim()) / 1000000f;
        } catch (Exception e) {
            return 0f;
        }
    }

    /** 所有核的最小/最大频率（GHz），返回 {lo, hi} */
    public static float[] freqBounds() {
        int cores = coreCount();
        float lo = 0, hi = 0;
        for (int i = 0; i < cores; i++) {
            float a = minFreq(i), b = maxFreq(i);
            if (a > 0 && (lo == 0 || a < lo)) lo = a;
            if (b > hi) hi = b;
        }
        return new float[]{lo, hi};
    }

    /** 每核是否在线 */
    public static boolean[] online(int cores) {
        boolean[] out = new boolean[cores];
        for (int i = 0; i < cores; i++) {
            String s = read("/sys/devices/system/cpu/cpu" + i + "/online");
            out[i] = s == null || "1".equals(s.trim()) || !new File("/sys/devices/system/cpu/cpu" + i).exists();
            if (s == null && new File("/sys/devices/system/cpu/cpu" + i).exists()) out[i] = true;
        }
        return out;
    }

    public static Mem mem() {
        Mem m = new Mem();
        long swapFree = 0;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String l;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf(':');
                if (i < 0) continue;
                String k = l.substring(0, i);
                long v = kb(l.substring(i + 1));
                if (k.equals("MemTotal")) m.total = v;
                else if (k.equals("MemAvailable")) m.avail = v;
                else if (k.equals("Cached")) m.cached = v;
                else if (k.equals("Buffers")) m.buffers = v;
                else if (k.equals("SwapTotal")) m.swapTotal = v;
                else if (k.equals("SwapFree")) swapFree = v;
            }
        } catch (Exception ignored) {}
        m.swapUsed = Math.max(0, m.swapTotal - swapFree);
        if (m.avail <= 0) m.avail = Math.max(0, m.total - m.cached - m.buffers);
        return m;
    }

    private static long kb(String s) {
        try {
            return Long.parseLong(s.trim().split("\\s+")[0]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String read(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            return r.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private static String read(String path) {
        return read(new File(path));
    }
}
