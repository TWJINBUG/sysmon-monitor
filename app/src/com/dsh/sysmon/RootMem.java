package com.dsh.sysmon;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 各应用内存占用（PSS）。普通应用读不到别的进程，所以有两条路：
 *
 * 1. 模块方式（推荐，零授权）：配套 KernelSU 模块里的 service.sh 以 root 身份每 3 秒扫
 *    /proc/<pid>/smaps_rollup，结果写进本应用私有目录的 root_mem.txt；App 只读文件，
 *    全程不弹 su 授权。
 * 2. 兜底方式：没装模块时自己 fork 一个 su 采样循环（会弹一次 root 授权）。
 */
public final class RootMem {

    public static final class AppMem {
        public String pkg = "";
        public String label = "";
        public Drawable icon;
        public long pssKb;
        public int procs;
    }

    public interface Listener {
        void onUpdate(List<AppMem> list, long totalKb, int appCount);

        void onError(String msg);
    }

    /** 兜底用的 su 采样循环脚本 */
    private static final String SCRIPT =
            "while :; do\n" +
            "  for d in /proc/[0-9]*; do\n" +
            "    [ -r \"$d/smaps_rollup\" ] || continue\n" +
            "    name=$(tr '\\0' ' ' < \"$d/cmdline\" 2>/dev/null)\n" +
            "    [ -z \"$name\" ] && continue\n" +
            "    pss=$(awk '/^Pss:/{print $2; exit}' \"$d/smaps_rollup\" 2>/dev/null)\n" +
            "    [ -n \"$pss\" ] && echo \"${d#/proc/}|$pss|$name\"\n" +
            "  done\n" +
            "  echo \"@END\"\n" +
            "  sleep 3\n" +
            "done\n";

    private static final String FILE_NAME = "root_mem.txt";

    private static Process proc;
    private static Thread worker;
    private static volatile boolean running;
    private static volatile boolean fileMode;
    private static Listener listener;
    private static Handler handler;
    private static Context app;

    private static final Map<String, Drawable> iconCache = new HashMap<>();
    private static final Map<String, String> labelCache = new HashMap<>();

    private RootMem() {}

    /** true：数据来自 KernelSU 模块写入的文件，没有用 su */
    public static boolean fromModule() {
        return fileMode;
    }

    public static boolean isRunning() {
        return running && worker != null && worker.isAlive();
    }

    /** 启动采样；结果回调在主线程 */
    public static void start(Context c, Handler h, Listener l) {
        app = c.getApplicationContext();
        handler = h;
        listener = l;
        if (isRunning()) return;
        running = true;
        fileMode = false;
        worker = new Thread(RootMem::work, "memworker");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stop() {
        running = false;
        Process p = proc;
        proc = null;
        if (p != null) {
            try {
                p.destroy();
            } catch (Exception ignored) {}
        }
        worker = null;
    }

    private static File provided() {
        return new File(app.getFilesDir(), FILE_NAME);
    }

    private static boolean fresh(File f) {
        return f.exists() && f.length() > 0
                && System.currentTimeMillis() - f.lastModified() < 15000;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }

    private static void work() {
        // 先给模块服务 5 秒时间写文件；有模块就完全不碰 su
        for (int i = 0; i < 10 && running; i++) {
            if (fresh(provided())) break;
            sleep(500);
        }

        boolean suFailed = false;
        boolean toldWait = false;

        while (running) {
            File f = provided();
            if (fresh(f)) {
                fileMode = true;
                try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                    Map<String, AppMem> agg = parse(r);
                    if (!agg.isEmpty()) deliver(agg);
                } catch (Exception ignored) {}
                sleep(3000);
                continue;
            }

            if (!suFailed && !fileMode) {
                if (startSu()) {
                    suLoop();
                    if (running) {
                        suFailed = true;
                        post(() -> listener.onError("su 采样已退出，未装模块时需要重新授权"));
                    }
                } else {
                    suFailed = true;
                }
                continue;
            }

            if (!toldWait) {
                toldWait = true;
                post(() -> listener.onError("等待模块采集服务写入数据…（模块需开机后生效）"));
            }
            sleep(5000);
        }
    }

    private static boolean startSu() {
        File script = new File(app.getFilesDir(), "memscan.sh");
        try (FileWriter w = new FileWriter(script, false)) {
            w.write(SCRIPT);
        } catch (Exception e) {
            post(() -> listener.onError("写入采样脚本失败：" + e.getMessage()));
            return false;
        }

        // KernelSU / Magisk 把 su 藏起来了，按名字解析会 ENOENT，必须试绝对路径
        String[] candidates = {"/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su", "su"};
        String last = null;
        for (String bin : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(bin, "-c", "sh " + script.getAbsolutePath());
                pb.redirectErrorStream(true);
                proc = pb.start();
                return true;
            } catch (Exception e) {
                last = e.getMessage();
            }
        }
        final String msg = last == null ? "未知错误" : last;
        post(() -> listener.onError("没有可用的 su，也没检测到模块：装模块后无需授权"));
        return false;
    }

    /** 阻塞读 su 子进程输出，每批 @END 交付一次 */
    private static void suLoop() {
        long lastLine = System.currentTimeMillis();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            while (running) {
                Map<String, AppMem> agg = parse(r);
                if (agg.isEmpty()) break;
                lastLine = System.currentTimeMillis();
                deliver(agg);
            }
        } catch (Exception e) {
            if (running) post(() -> listener.onError("读取失败：" + e.getMessage()));
        }
        if (running && System.currentTimeMillis() - lastLine > 20000) {
            post(() -> listener.onError("等了 20 秒没有数据，Root 授权可能被拒绝"));
        }
    }

    private static Map<String, AppMem> parse(BufferedReader r) {
        Map<String, AppMem> agg = new HashMap<>();
        try {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("@")) {
                    if (line.startsWith("@END")) break;
                    continue;
                }
                String[] p = line.split("\\|", 3);
                if (p.length < 3) continue;
                long pss;
                try {
                    pss = Long.parseLong(p[1].trim());
                } catch (Exception e) {
                    continue;
                }
                String name = p[2].trim();
                if (name.isEmpty() || pss <= 0) continue;
                String pkg = name.split(" ")[0].split(":")[0];
                if (pkg.isEmpty() || pkg.startsWith("[")) continue;
                AppMem a = agg.get(pkg);
                if (a == null) {
                    a = new AppMem();
                    a.pkg = pkg;
                    agg.put(pkg, a);
                }
                a.pssKb += pss;
                a.procs++;
            }
        } catch (Exception ignored) {}
        return agg;
    }

    private static void deliver(Map<String, AppMem> agg) {
        if (agg.isEmpty()) return;
        PackageManager pm = app.getPackageManager();
        final List<AppMem> out = new ArrayList<>();
        for (AppMem a : agg.values()) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(a.pkg, 0);
                a.label = labelCache.get(a.pkg);
                if (a.label == null) {
                    a.label = pm.getApplicationLabel(ai).toString();
                    labelCache.put(a.pkg, a.label);
                }
                a.icon = iconCache.get(a.pkg);
                if (a.icon == null) {
                    a.icon = pm.getApplicationIcon(ai);
                    if (a.icon != null) iconCache.put(a.pkg, a.icon);
                }
            } catch (Exception e) {
                continue; // 不是已安装应用（残留进程等）
            }
            out.add(a);
        }
        if (out.isEmpty()) return;
        Collections.sort(out, (x, y) -> Long.compare(y.pssKb, x.pssKb));

        final long total = Perf.mem().total;
        final int count = agg.size();
        if (out.size() > 12) out.subList(12, out.size()).clear();
        post(() -> listener.onUpdate(out, total, count));
    }

    private static void post(Runnable r) {
        Handler h = handler;
        if (h != null) h.post(r);
    }

    static String gb(long kb) {
        return String.format(Locale.US, "%.2f GB", kb / 1024.0 / 1024.0);
    }
}
