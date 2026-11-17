package com.dsh.sysmon;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = 0xFFF2F3F5;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF1C1C1E;
    private static final int SUB = 0xFF8E8E93;
    private static final int BLUE = 0xFF2E6FF2;
    private static final int PURPLE = 0xFFAF52DE;
    private static final int GREEN = 0xFF34C759;
    private static final int ORANGE = 0xFFFF9500;
    private static final int RED = 0xFFFF3B30;

    private static final int RING = 120; // 最近 120 秒

    private final Handler handler = new Handler(Looper.getMainLooper());

    // 概览页
    private TextView cpuTemp, tempTag, batTemp, batCap, batStatus, memTxt, freqTxt, upTxt, modelTxt, stoTxt, boardCardTxt;
    private LinearLayout swatchRow, scrimRow;

    // 页签
    private LinearLayout tabRow, tabHost;
    private ScrollView pageOverview, pagePerf;
    private int tab = 0;

    // 性能页
    private TextView cpuBig, cpuTag, cpuSub, memBig, memTag, memCached, memAvail, memSwap;
    private TextView cpuSrc, freqAvgTxt, freqRangeTxt, perfTempTxt, perfUpTxt, histAvg, histPeak;
    private BarView cpuBar, memBar;
    private LinearLayout memAppList;
    private TextView memAppHint, memAppToggle;
    private UsageChartView liveChart, histChart;
    private LinearLayout coreList;
    private final List<BarView> coreBars = new ArrayList<>();
    private final List<TextView> coreFreqs = new ArrayList<>();

    private final Perf.Sampler sampler = new Perf.Sampler();
    private final long[] ringT = new long[RING];
    private final float[] ringCpu = new float[RING];
    private final float[] ringMem = new float[RING];
    private int ringN, ringPos;
    private int ticks;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }

        Intent i = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.post(tick);
        if (tab == 1) startMemScan();
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        RootMem.stop();
    }

    private void update() {
        updateOverview();
        updatePerf();
        ticks++;
        if (ticks % 10 == 0) renderHistory();
    }

    private void updateOverview() {
        float ct = Stats.cpuTemp();
        if (ct > 0) {
            cpuTemp.setText(String.format(Locale.US, "%.1f", ct));
            tempTag.setText(ct >= 80 ? "偏高" : ct >= 65 ? "温热" : "正常");
            tempTag.setTextColor(ct >= 80 ? RED : ct >= 65 ? ORANGE : GREEN);
        }
        float bt = Stats.batteryTemp(this);
        batTemp.setText(bt > 0 ? String.format(Locale.US, "%.1f℃", bt) : "--℃");
        batCap.setText(Stats.batteryPct(this) + "%");
        batStatus.setText(Stats.charging(this) ? "充电中" : "放电中");

        long[] m = Stats.mem();
        memTxt.setText(Stats.gb(m[0]) + " / " + Stats.gb(m[1]) + " GB");
        freqTxt.setText("各核频率(GHz)：\n" + Stats.freqs());

        long[] sto = Stats.storage(this);
        stoTxt.setText(Stats.gb(sto[0]) + " / " + Stats.gb(sto[1]) + " GB");
        upTxt.setText(Stats.uptime());

        float board = Stats.boardTemp();
        boardCardTxt.setText(board > 0 ? String.format(Locale.US, "%.1f℃", board) : "--℃");

        modelTxt.setText(Build.BRAND + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
    }

    private void updatePerf() {
        if (cpuBig == null) return;

        Perf.Usage u = sampler.sample();
        Perf.Mem m = Perf.mem();
        float memPct = m.total > 0 ? m.used() * 100f / m.total : -1f;

        if (u.total >= 0) {
            cpuBig.setText(String.format(Locale.US, "%.0f", u.total));
            cpuBar.setSingle(u.total, BLUE);
            cpuTag.setText(loadTag(u.total));
            cpuTag.setTextColor(loadColor(u.total));
            pushRing(System.currentTimeMillis(), u.total, memPct);
            renderLive();
        }

        float sum = 0;
        int fc = 0;
        for (float f : u.freq) if (f > 0) { sum += f; fc++; }
        float ct = Stats.cpuTemp();
        StringBuilder sub = new StringBuilder(Perf.coreCount() + " 核");
        if (fc > 0) sub.append(String.format(Locale.US, " · %.2f GHz 平均", sum / fc));
        if (ct > 0) sub.append(String.format(Locale.US, " · %.1f℃", ct));
        cpuSub.setText(sub.toString());
        cpuSrc.setText(u.total >= 0 ? "统计来源：" + u.source : "正在采样…");

        for (int i = 0; i < coreBars.size(); i++) {
            float v = i < u.core.length ? u.core[i] : -1f;
            if (v >= 0) coreBars.get(i).setSingle(v, BLUE); else coreBars.get(i).reset();
            float f = i < u.freq.length ? u.freq[i] : 0;
            coreFreqs.get(i).setText(f > 0 ? String.format(Locale.US, "%.2f GHz", f) : "--");
        }

        if (m.total > 0) {
            memBig.setText(Stats.gb(m.used()) + " / " + Stats.gb(m.total) + " GB");
            memTag.setText(String.format(Locale.US, "%.0f%%", memPct));
            memBar.setSingle(memPct, PURPLE);
            memCached.setText(Stats.gb(m.cached + m.buffers) + " GB");
            memAvail.setText(Stats.gb(m.avail) + " GB");
            memSwap.setText(m.swapTotal > 0
                    ? Stats.gb(m.swapUsed) + " / " + Stats.gb(m.swapTotal) + " GB"
                    : "未启用");
        }

        freqAvgTxt.setText(fc > 0 ? String.format(Locale.US, "%.2f GHz", sum / fc) : "--");
        float[] b = Perf.freqBounds();
        freqRangeTxt.setText(b[1] > 0 ? String.format(Locale.US, "%.2f – %.2f GHz", b[0], b[1]) : "--");
        perfTempTxt.setText(ct > 0 ? String.format(Locale.US, "%.1f℃", ct) : "--℃");
        perfUpTxt.setText(Stats.uptime());
    }

    private String loadTag(float v) {
        return v >= 80 ? "满载" : v >= 55 ? "繁忙" : v >= 20 ? "正常" : "空闲";
    }

    private int loadColor(float v) {
        return v >= 80 ? RED : v >= 55 ? ORANGE : GREEN;
    }

    // ---------------- 各应用内存占用 ----------------

    private final RootMem.Listener memListener = new RootMem.Listener() {
        @Override public void onUpdate(List<RootMem.AppMem> list, long totalKb, int appCount) {
            renderApps(list, totalKb, appCount);
        }

        @Override public void onError(String msg) {
            if (memAppHint == null) return;
            memAppHint.setText(msg);
            memAppToggle.setVisibility(View.VISIBLE);
            memAppToggle.setText("重新授权  →");
        }
    };

    private void startMemScan() {
        if (memAppHint == null) return;
        memAppHint.setText("正在通过 Root 读取各进程 PSS…");
        memAppToggle.setVisibility(View.GONE);
        RootMem.start(this, handler, memListener);
    }

    private void renderApps(List<RootMem.AppMem> list, long totalKb, int appCount) {
        if (memAppList == null) return;
        memAppList.removeAllViews();

        long max = 0, sum = 0;
        for (RootMem.AppMem a : list) {
            max = Math.max(max, a.pssKb);
            sum += a.pssKb;
        }

        for (RootMem.AppMem a : list) memAppList.addView(appRow(a, max, totalKb));

        memAppHint.setText(String.format(Locale.US, "共 %d 个应用 · 已识别合计 %s（占 %s）· %s",
                appCount, RootMem.gb(sum),
                totalKb > 0 ? String.format(Locale.US, "%.0f%%", sum * 100.0 / totalKb) : "--",
                RootMem.fromModule() ? "模块服务每 3 秒刷新" : "su 实时读取"));
        memAppToggle.setVisibility(View.GONE);
    }

    private View appRow(RootMem.AppMem a, long max, long totalKb) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, dp(8), 0, dp(3));

        ImageView iv = new ImageView(this);
        if (a.icon != null) iv.setImageDrawable(a.icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
        ilp.rightMargin = dp(10);
        top.addView(iv, ilp);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(mid, mlp);
        mid.addView(text(a.label, 14, TEXT, false));
        TextView sub = text(a.procs > 1 ? RootMem.gb(a.pssKb) + " · " + a.procs + " 个进程"
                : RootMem.gb(a.pssKb), 11, SUB, false);
        mid.addView(sub);

        float pct = totalKb > 0 ? a.pssKb * 100f / totalKb : 0f;
        TextView value = text(String.format(Locale.US, "%.1f%%", pct), 13, TEXT, true);
        top.addView(value);
        wrap.addView(top);

        BarView bar = new BarView(this);
        bar.setSingle(max > 0 ? a.pssKb * 100f / max : 0f, BLUE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(6));
        blp.leftMargin = dp(38);
        blp.bottomMargin = dp(6);
        wrap.addView(bar, blp);

        return wrap;
    }

    private void pushRing(long t, float cpu, float mem) {
        if (cpu < 0) return;
        ringT[ringPos] = t;
        ringCpu[ringPos] = cpu;
        ringMem[ringPos] = mem;
        ringPos = (ringPos + 1) % RING;
        if (ringN < RING) ringN++;
    }

    private void renderLive() {
        if (liveChart == null) return;
        int n = Math.min(ringN, RING);
        long[] ts = new long[n];
        float[] a = new float[n], b = new float[n];
        for (int i = 0; i < n; i++) {
            int idx = (ringPos - n + i + RING) % RING;
            ts[i] = ringT[idx];
            a[i] = ringCpu[idx];
            b[i] = ringMem[idx];
        }
        liveChart.setSeries(ts, a, b, false);
    }

    private void renderHistory() {
        if (histChart == null) return;
        long start = startOfDay(System.currentTimeMillis());
        List<PerfStore.Sample> all = PerfStore.read(this);
        List<PerfStore.Sample> today = new ArrayList<>();
        for (PerfStore.Sample s : all) if (s.t >= start) today.add(s);

        int n = today.size();
        long[] ts = new long[n];
        float[] c = new float[n], mm = new float[n];
        float sumC = 0, sumM = 0, peakC = -1;
        for (int i = 0; i < n; i++) {
            PerfStore.Sample s = today.get(i);
            ts[i] = s.t;
            c[i] = s.cpu;
            mm[i] = s.mem;
            sumC += s.cpu;
            sumM += s.mem;
            if (s.cpu > peakC) peakC = s.cpu;
        }
        histChart.setSeries(ts, c, mm, true);
        if (n > 0) {
            histAvg.setText(String.format(Locale.US, "CPU 均 %.0f%% · 内存均 %.0f%%", sumC / n, sumM / n));
            histPeak.setText(String.format(Locale.US, "CPU 峰 %.0f%% · 共 %d 个采样", peakC, n));
        } else {
            histAvg.setText("今天还没有记录，服务每 5 分钟采样一次");
            histPeak.setText("");
        }
    }

    private long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(22));
        c.setBackground(g);
        c.setPadding(dp(20), dp(18), dp(20), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private View row(String k, TextView v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(9), 0, dp(9));
        TextView key = text(k, 14, SUB, false);
        r.addView(key, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(v);
        return r;
    }

    private void applyPreset(int idx) {
        WidgetPrefs.setPreset(this, idx);
        refreshWidgets();
        buildSwatches();
        Toast.makeText(this, "组件背景：" + Theme.NAMES[idx], Toast.LENGTH_SHORT).show();
    }

    private void refreshWidgets() {
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        ComponentName cn = new ComponentName(this, SysMonWidget.class);
        for (int id : mgr.getAppWidgetIds(cn)) SysMonWidget.updateViews(this, mgr, id);
    }

    private void buildSwatches() {
        if (swatchRow == null) return;
        swatchRow.removeAllViews();
        int current = WidgetPrefs.preset(this);

        for (int i = 0; i < Theme.PRESETS.length; i++) {
            final int idx = i;
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{Theme.PRESETS[i][0], Theme.PRESETS[i][1]});
            g.setShape(GradientDrawable.OVAL);
            g.setStroke(dp(2), idx == current ? BLUE : 0x22000000);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(36));
            lp.rightMargin = dp(12);
            dot.setLayoutParams(lp);
            dot.setOnClickListener(v -> applyPreset(idx));
            swatchRow.addView(dot);
        }

        swatchRow.addView(chip("透·白字", Theme.TRANSPARENT_WHITE, current == Theme.TRANSPARENT_WHITE));
        swatchRow.addView(chip("透·黑字", Theme.TRANSPARENT_DARK, current == Theme.TRANSPARENT_DARK));
        swatchRow.addView(chip("液态玻璃", Theme.GLASS, current == Theme.GLASS));
        swatchRow.addView(chip("白玻璃", Theme.GLASS_WHITE, current == Theme.GLASS_WHITE));
        swatchRow.addView(chip("选择图片", Theme.CUSTOM_IMAGE, current == Theme.CUSTOM_IMAGE));
    }

    private View chip(String label, int idx, boolean selected) {
        TextView t = text(label, 12, selected ? BLUE : 0xFF4A4A4F, true);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(selected ? 0x142E6FF2 : 0xFFF2F3F5);
        g.setStroke(dp(1), selected ? BLUE : 0x22000000);
        t.setBackground(g);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> {
            if (idx == Theme.CUSTOM_IMAGE) pickImage();
            else applyPreset(idx);
        });
        return t;
    }

    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, 7);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 7 && res == RESULT_OK && data != null && data.getData() != null) {
            Intent crop = new Intent(this, CropActivity.class);
            crop.putExtra("uri", data.getData().toString());
            startActivityForResult(crop, 8);
        } else if (req == 8 && res == RESULT_OK) {
            WidgetPrefs.setPreset(this, Theme.CUSTOM_IMAGE);
            refreshWidgets();
            buildSwatches();
            Toast.makeText(this, "已设为组件背景（可调遮罩增强文字）", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImage(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            int scale = 1;
            int max = Math.max(o.outWidth, o.outHeight);
            while (max / scale > 1440) scale *= 2;

            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = scale;
                Bitmap bmp = BitmapFactory.decodeStream(in2, null, o2);
                if (bmp == null) throw new Exception("decode failed");

                File f = Theme.imageFile(this);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
                }
                WidgetPrefs.setPreset(this, Theme.CUSTOM_IMAGE);
                refreshWidgets();
                buildSwatches();
                Toast.makeText(this, "已设为组件背景", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "图片设置失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void buildScrims() {
        if (scrimRow == null) return;
        scrimRow.removeAllViews();
        String[] labels = {"遮罩·无", "遮罩·轻", "遮罩·中", "遮罩·强"};
        int cur = WidgetPrefs.scrim(this);
        for (int i = 0; i < labels.length; i++) {
            final int level = i;
            TextView t = text(labels[i], 12, level == cur ? BLUE : 0xFF4A4A4F, true);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setColor(level == cur ? 0x142E6FF2 : 0xFFF2F3F5);
            g.setStroke(dp(1), level == cur ? BLUE : 0x22000000);
            t.setBackground(g);
            t.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(10);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                WidgetPrefs.setScrim(this, level);
                refreshWidgets();
                buildScrims();
            });
            scrimRow.addView(t);
        }
    }

    // ---------------- 顶栏与页签 ----------------

    private View build() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setBackgroundColor(BG);
        outer.setPadding(dp(16), dp(20), dp(16), 0);

        TextView title = text("设备监控", 28, TEXT, true);
        outer.addView(title);

        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFFE9E9EB);
        track.setCornerRadius(dp(10));
        tabRow.setBackground(track);
        tabRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
        tlp.topMargin = dp(12);
        tlp.bottomMargin = dp(10);
        tabRow.setLayoutParams(tlp);
        outer.addView(tabRow);

        tabHost = new LinearLayout(this);
        tabHost.setOrientation(LinearLayout.VERTICAL);
        tabHost.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        outer.addView(tabHost);

        pageOverview = new ScrollView(this);
        pageOverview.setVerticalScrollBarEnabled(false);
        pageOverview.addView(buildOverview());
        tabHost.addView(pageOverview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pagePerf = new ScrollView(this);
        pagePerf.setVerticalScrollBarEnabled(false);
        pagePerf.addView(buildPerf());
        tabHost.addView(pagePerf, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTabs();
        selectTab(0);
        return outer;
    }

    private void buildTabs() {
        if (tabRow == null) return;
        tabRow.removeAllViews();
        String[] names = {"概览", "性能"};
        for (int i = 0; i < names.length; i++) {
            boolean on = i == tab;
            TextView t = text(names[i], 14, on ? TEXT : 0xFF3C3C43, false);
            if (on) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            t.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(8));
            bg.setColor(on ? 0xFFFFFFFF : 0x00000000);
            t.setBackground(bg);
            if (on) t.setElevation(dp(1.5f));
            t.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            final int idx = i;
            t.setOnClickListener(v -> selectTab(idx));
            tabRow.addView(t);
        }
    }

    private void selectTab(int i) {
        tab = i;
        buildTabs();
        if (pageOverview != null) pageOverview.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        if (pagePerf != null) pagePerf.setVisibility(i == 1 ? View.VISIBLE : View.GONE);
        if (i == 1) {
            renderLive();
            renderHistory();
            startMemScan();
        } else {
            RootMem.stop();
        }
    }

    // ---------------- 概览页 ----------------

    private View buildOverview() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(30));

        LinearLayout c1 = card();
        c1.addView(text("CPU 温度", 13, SUB, false));
        LinearLayout bigRow = new LinearLayout(this);
        bigRow.setOrientation(LinearLayout.HORIZONTAL);
        bigRow.setGravity(Gravity.BOTTOM);
        cpuTemp = text("--", 58, TEXT, true);
        TextView unit = text("℃", 20, SUB, true);
        bigRow.addView(cpuTemp);
        bigRow.addView(unit);
        c1.addView(bigRow);
        tempTag = text("--", 12, GREEN, false);
        c1.addView(tempTag);
        root.addView(c1);

        LinearLayout c2 = card();
        c2.addView(text("电池", 13, SUB, false));
        batTemp = text("--℃", 16, ORANGE, true);
        batCap = text("--%", 16, TEXT, true);
        batStatus = text("--", 16, TEXT, true);
        c2.addView(row("电池温度", batTemp));
        c2.addView(row("电量", batCap));
        c2.addView(row("状态", batStatus));
        root.addView(c2);

        LinearLayout c3 = card();
        c3.addView(text("内存与 CPU", 13, SUB, false));
        memTxt = text("--", 16, TEXT, true);
        c3.addView(row("内存", memTxt));
        freqTxt = text("--", 12, SUB, false);
        freqTxt.setPadding(0, dp(10), 0, 0);
        c3.addView(freqTxt);
        root.addView(c3);

        LinearLayout c4 = card();
        c4.addView(text("存储与运行", 13, SUB, false));
        stoTxt = text("--", 16, TEXT, true);
        upTxt = text("--", 16, TEXT, true);
        boardCardTxt = text("--℃", 16, TEXT, true);
        c4.addView(row("存储", stoTxt));
        c4.addView(row("运行时长", upTxt));
        c4.addView(row("主板温度", boardCardTxt));
        root.addView(c4);

        LinearLayout c5 = card();
        c5.addView(text("桌面组件背景", 13, SUB, false));
        TextView hint = text("色板 / 透明 / 自定义图片，文字颜色会按背景明暗自动切换", 11, SUB, false);
        hint.setPadding(0, dp(6), 0, dp(12));
        c5.addView(hint);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(swatchRow);
        c5.addView(hs);

        HorizontalScrollView hs2 = new HorizontalScrollView(this);
        hs2.setHorizontalScrollBarEnabled(false);
        hs2.setPadding(0, dp(10), 0, 0);
        scrimRow = new LinearLayout(this);
        scrimRow.setOrientation(LinearLayout.HORIZONTAL);
        hs2.addView(scrimRow);
        c5.addView(hs2);
        root.addView(c5);

        LinearLayout c6 = card();
        TextView historyBtn = text("电池记录  →", 16, BLUE, true);
        c6.addView(historyBtn);
        c6.setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));
        root.addView(c6);

        LinearLayout c7 = card();
        modelTxt = text("--", 14, TEXT, true);
        c7.addView(modelTxt);
        root.addView(c7);

        buildSwatches();
        buildScrims();
        return root;
    }

    // ---------------- 性能页 ----------------

    private View buildPerf() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(4), 0, dp(30));

        // CPU 使用率
        LinearLayout c1 = card();
        c1.addView(text("CPU 使用率", 13, SUB, false));
        LinearLayout bigRow = new LinearLayout(this);
        bigRow.setOrientation(LinearLayout.HORIZONTAL);
        bigRow.setGravity(Gravity.BOTTOM);
        cpuBig = text("--", 54, TEXT, true);
        bigRow.addView(cpuBig);
        bigRow.addView(text("%", 20, SUB, true));
        cpuTag = text("--", 12, GREEN, false);
        cpuTag.setGravity(Gravity.END);
        LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tagLp.bottomMargin = dp(12);
        cpuTag.setLayoutParams(tagLp);
        bigRow.addView(cpuTag);
        c1.addView(bigRow);

        cpuBar = new BarView(this);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        barLp.topMargin = dp(4);
        c1.addView(cpuBar, barLp);

        cpuSub = text("--", 12, SUB, false);
        cpuSub.setPadding(0, dp(10), 0, 0);
        c1.addView(cpuSub);

        cpuSrc = text("正在采样…", 11, SUB, false);
        cpuSrc.setPadding(0, dp(4), 0, 0);
        c1.addView(cpuSrc);
        root.addView(c1);

        // 实时曲线
        LinearLayout c2 = card();
        c2.addView(text("最近 60 秒", 13, SUB, false));
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setPadding(0, dp(8), 0, dp(2));
        legend.addView(text("● CPU", 12, 0xFF007AFF, true));
        TextView memLegend = text("● 内存", 12, PURPLE, true);
        memLegend.setPadding(dp(14), 0, 0, 0);
        legend.addView(memLegend);
        c2.addView(legend);
        liveChart = new UsageChartView(this);
        c2.addView(liveChart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        root.addView(c2);

        // 内存
        LinearLayout c3 = card();
        c3.addView(text("内存", 13, SUB, false));
        LinearLayout memRow = new LinearLayout(this);
        memRow.setOrientation(LinearLayout.HORIZONTAL);
        memRow.setGravity(Gravity.BOTTOM);
        memBig = text("--", 30, TEXT, true);
        memRow.addView(memBig);
        memTag = text("--", 18, PURPLE, true);
        memTag.setGravity(Gravity.END);
        LinearLayout.LayoutParams mtLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mtLp.bottomMargin = dp(4);
        memTag.setLayoutParams(mtLp);
        memRow.addView(memTag);
        c3.addView(memRow);

        memBar = new BarView(this);
        LinearLayout.LayoutParams mbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        mbLp.topMargin = dp(8);
        c3.addView(memBar, mbLp);

        memCached = text("--", 15, TEXT, true);
        memAvail = text("--", 15, TEXT, true);
        memSwap = text("--", 15, TEXT, true);
        c3.addView(row("缓存（可回收）", memCached));
        c3.addView(row("可用", memAvail));
        c3.addView(row("ZRAM / Swap", memSwap));
        root.addView(c3);

        // 各应用内存占用（root 扫描 /proc/*/smaps_rollup）
        LinearLayout c4 = card();
        c4.addView(text("应用内存占用", 13, SUB, false));
        memAppHint = text("按 PSS 统计；装模块后无需授权，未装则需 Root", 11, SUB, false);
        memAppHint.setPadding(0, dp(6), 0, dp(4));
        c4.addView(memAppHint);
        memAppList = new LinearLayout(this);
        memAppList.setOrientation(LinearLayout.VERTICAL);
        c4.addView(memAppList);
        memAppToggle = text("授权 Root 并刷新  →", 13, BLUE, true);
        memAppToggle.setPadding(0, dp(10), 0, dp(2));
        memAppToggle.setOnClickListener(v -> startMemScan());
        c4.addView(memAppToggle);
        root.addView(c4);

        // 每核负载
        LinearLayout c5 = card();
        c5.addView(text("每核负载", 13, SUB, false));
        TextView coreHint = text("最近 1 秒占用率，右侧为该核当前频率", 11, SUB, false);
        coreHint.setPadding(0, dp(6), 0, dp(8));
        c5.addView(coreHint);
        coreList = new LinearLayout(this);
        coreList.setOrientation(LinearLayout.VERTICAL);
        c5.addView(coreList);
        buildCores();
        root.addView(c5);

        // 系统
        LinearLayout c6 = card();
        c6.addView(text("系统", 13, SUB, false));
        freqAvgTxt = text("--", 15, TEXT, true);
        freqRangeTxt = text("--", 15, TEXT, true);
        perfTempTxt = text("--℃", 15, TEXT, true);
        perfUpTxt = text("--", 15, TEXT, true);
        c6.addView(row("平均频率", freqAvgTxt));
        c6.addView(row("频率区间", freqRangeTxt));
        c6.addView(row("CPU 温度", perfTempTxt));
        c6.addView(row("运行时长", perfUpTxt));
        root.addView(c6);

        // 历史
        LinearLayout c7 = card();
        c7.addView(text("负载记录", 13, SUB, false));
        TextView histHint = text("每 5 分钟采样一次，蓝线 CPU、紫线内存", 11, SUB, false);
        histHint.setPadding(0, dp(6), 0, dp(4));
        c7.addView(histHint);
        histChart = new UsageChartView(this);
        c7.addView(histChart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        histAvg = text("--", 12, SUB, false);
        histAvg.setPadding(0, dp(8), 0, 0);
        c7.addView(histAvg);
        histPeak = text("", 11, SUB, false);
        histPeak.setPadding(0, dp(3), 0, 0);
        c7.addView(histPeak);
        root.addView(c7);

        return root;
    }

    private void buildCores() {
        int cores = Perf.coreCount();
        for (int i = 0; i < cores; i++) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(6), 0, dp(6));

            TextView name = text("核 " + i, 12, SUB, false);
            name.setLayoutParams(new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(name);

            BarView bar = new BarView(this);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    0, dp(8), 1f);
            blp.rightMargin = dp(12);
            r.addView(bar, blp);
            coreBars.add(bar);

            TextView freq = text("--", 11, SUB, false);
            freq.setGravity(Gravity.END);
            freq.setLayoutParams(new LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT));
            r.addView(freq);
            coreFreqs.add(freq);

            coreList.addView(r);
        }
    }
}
