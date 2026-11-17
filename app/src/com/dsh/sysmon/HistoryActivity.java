package com.dsh.sysmon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** iOS 健康 / 电池风格 */
public class HistoryActivity extends Activity {

    private static final double WH_PER_PCT = 0.281;
    private static final int BG = 0xFFF2F2F7;
    private static final int CARD = 0xFFFFFFFF;
    private static final int TEXT = 0xFF000000;
    private static final int SUB = 0xFF8E8E93;
    private static final int SEP = 0xFFE5E5EA;
    private static final int BLUE = 0xFF007AFF;
    private static final int GREEN = 0xFF34C759;
    private static final int ORANGE = 0xFFFF9500;
    private static final int RED = 0xFFFF3B30;

    private TextView dateText, bigPct, bigTemp, powerText, usedText, leftText, hintText;
    private LinearLayout segRow, sceneList;
    private ChartView capChart, tempChart;
    private long selDay;
    private int mode = 0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        selDay = startOfDay(System.currentTimeMillis());
        setContentView(build());
        render();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String s, float size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(16));
        c.setBackground(g);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        return c;
    }

    private View build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(44), dp(16), dp(30));
        sv.addView(root);

        // 顶部
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 34, BLUE, false);
        back.setPadding(0, 0, dp(6), dp(2));
        back.setOnClickListener(v -> finish());
        bar.addView(back);
        bar.addView(text("电池", 32, TEXT, true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView del = text("删除", 15, RED, false);
        del.setPadding(dp(10), dp(8), dp(2), dp(8));
        del.setOnClickListener(v -> confirmDelete());
        bar.addView(del);
        root.addView(bar);

        // 分段控件
        segRow = new LinearLayout(this);
        segRow.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable track = new GradientDrawable();
        track.setColor(0xFFE9E9EB);
        track.setCornerRadius(dp(9));
        segRow.setBackground(track);
        segRow.setPadding(dp(2), dp(2), dp(2), dp(2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        slp.topMargin = dp(12);
        slp.bottomMargin = dp(6);
        segRow.setLayoutParams(slp);
        root.addView(segRow);

        // 日期
        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding(0, dp(4), 0, dp(10));
        TextView prev = text("‹", 28, BLUE, false);
        prev.setPadding(dp(8), 0, dp(12), 0);
        prev.setOnClickListener(v -> shift(-1));
        dateText = text("", 16, SUB, false);
        dateText.setGravity(Gravity.CENTER);
        dateText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView next = text("›", 28, BLUE, false);
        next.setPadding(dp(12), 0, dp(8), 0);
        next.setOnClickListener(v -> shift(1));
        dateRow.addView(prev);
        dateRow.addView(dateText);
        dateRow.addView(next);
        root.addView(dateRow);

        // 图表卡
        LinearLayout chartCard = card();
        LinearLayout bigRow = new LinearLayout(this);
        bigRow.setOrientation(LinearLayout.HORIZONTAL);
        bigRow.setGravity(Gravity.BOTTOM);
        bigPct = text("--", 40, BLUE, true);
        bigRow.addView(bigPct);
        bigTemp = text("", 15, SUB, false);
        bigTemp.setPadding(dp(8), 0, 0, dp(8));
        bigRow.addView(bigTemp);
        chartCard.addView(bigRow);
        TextView capTitle = text("● 电量", 13, BLUE, true);
        capTitle.setPadding(0, dp(8), 0, dp(2));
        chartCard.addView(capTitle);
        capChart = new ChartView(this, ChartView.CAP);
        capChart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150)));
        chartCard.addView(capChart);

        TextView tempTitle = text("● 电池温度", 13, ORANGE, true);
        tempTitle.setPadding(0, dp(12), 0, dp(2));
        chartCard.addView(tempTitle);
        tempChart = new ChartView(this, ChartView.TEMP);
        tempChart.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(140)));
        chartCard.addView(tempChart);

        root.addView(chartCard);

        // 统计卡
        LinearLayout statCard = card();
        powerText = statRow(statCard, "平均功耗", "W", false);
        usedText = statRow(statCard, "已使用", "小时", true);
        leftText = statRow(statCard, "理论续航", "小时", true);
        root.addView(statCard);

        // 使用场景
        LinearLayout sceneCard = card();
        TextView st = text("使用场景", 13, SUB, false);
        st.setPadding(0, 0, 0, dp(8));
        sceneCard.addView(st);
        sceneList = new LinearLayout(this);
        sceneList.setOrientation(LinearLayout.VERTICAL);
        sceneCard.addView(sceneList);
        hintText = text("", 11, SUB, false);
        hintText.setPadding(0, dp(8), 0, 0);
        sceneCard.addView(hintText);
        TextView grant = text("开启「使用情况访问」→", 13, BLUE, false);
        grant.setPadding(0, dp(8), 0, 0);
        grant.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        sceneCard.addView(grant);
        root.addView(sceneCard);

        return sv;
    }

    private TextView statRow(LinearLayout parent, String label, String unit, boolean withTopLine) {
        if (withTopLine) {
            View line = new View(this);
            line.setBackgroundColor(SEP);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(0.6f));
            line.setLayoutParams(lp);
            parent.addView(line);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        row.addView(text(label, 16, TEXT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = text("--", 18, TEXT, true);
        row.addView(value);
        TextView u = text(unit, 14, SUB, false);
        u.setPadding(dp(4), dp(2), 0, 0);
        row.addView(u);
        parent.addView(row);
        return value;
    }

    private void buildSegments() {
        segRow.removeAllViews();
        String[] names = {"日", "周", "月"};
        for (int i = 0; i < 3; i++) {
            boolean on = i == mode;
            TextView t = text(names[i], 14, on ? TEXT : 0xFF3C3C43, on);
            t.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(7));
            bg.setColor(on ? 0xFFFFFFFF : 0x00000000);
            t.setBackground(bg);
            if (on) t.setElevation(dp(1.5f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            t.setLayoutParams(lp);
            final int m = i;
            t.setOnClickListener(v -> { mode = m; render(); });
            segRow.addView(t);
        }
    }

    private long rangeStart() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selDay);
        if (mode == 1) c.add(Calendar.DAY_OF_YEAR, -((c.get(Calendar.DAY_OF_WEEK) + 5) % 7));
        else if (mode == 2) c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long rangeEnd() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(rangeStart());
        if (mode == 1) c.add(Calendar.DAY_OF_YEAR, 7);
        else if (mode == 2) c.add(Calendar.MONTH, 1);
        else c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    private void shift(int d) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selDay);
        if (mode == 0) c.add(Calendar.DAY_OF_YEAR, d);
        else if (mode == 1) c.add(Calendar.WEEK_OF_YEAR, d);
        else c.add(Calendar.MONTH, d);
        long t = c.getTimeInMillis();
        if (t > System.currentTimeMillis()) return;
        selDay = t;
        render();
    }

    private void confirmDelete() {
        if (mode != 0) {
            Toast.makeText(this, "切到「日」后可删除单天记录", Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(selDay);
        String label = String.format(Locale.US, "%d-%02d-%02d", c.get(Calendar.YEAR),
                c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setMessage("删除 " + label + " 当天的电池记录？不可恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    HistoryStore.deleteDay(this, selDay, selDay + 86400000L);
                    render();
                })
                .show();
    }

    private List<HistoryStore.Sample> rangeSamples(List<HistoryStore.Sample> all, long start, long end) {
        List<HistoryStore.Sample> out = new ArrayList<>();
        for (HistoryStore.Sample s : all) if (s.t >= start && s.t < end) out.add(s);
        return out;
    }

    private double[] rate(List<HistoryStore.Sample> list) {
        double h = 0, pct = 0;
        for (int i = 1; i < list.size(); i++) {
            HistoryStore.Sample a = list.get(i - 1), b = list.get(i);
            double dt = (b.t - a.t) / 3600000.0;
            if (dt <= 0 || dt > 0.25) continue;
            if (a.charging || b.charging) continue;
            int d = a.cap - b.cap;
            if (d < 0 || d > 10) continue;
            h += dt;
            pct += d;
        }
        return new double[]{h <= 0 ? -1 : pct / h, pct, h};
    }

    private String dateLabel(long start, long end) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(start);
        if (mode == 0) {
            boolean today = start == startOfDay(System.currentTimeMillis());
            return String.format(Locale.US, "%d年%d月%d日%s", c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), today ? " · 今天" : "");
        }
        if (mode == 2) return String.format(Locale.US, "%d年%d月", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
        Calendar e = Calendar.getInstance();
        e.setTimeInMillis(end - 1);
        return String.format(Locale.US, "%d月%d日 – %d月%d日", c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH), e.get(Calendar.MONTH) + 1, e.get(Calendar.DAY_OF_MONTH));
    }

    private void render() {
        buildSegments();
        long start = rangeStart(), end = rangeEnd();
        List<HistoryStore.Sample> all = HistoryStore.read(this);
        List<HistoryStore.Sample> range = rangeSamples(all, start, end);
        dateText.setText(dateLabel(start, end));
        capChart.setData(range);
        tempChart.setData(range);

        if (!range.isEmpty()) {
            HistoryStore.Sample last = range.get(range.size() - 1);
            bigPct.setText(last.cap + "%");
            bigTemp.setText(String.format(Locale.US, "%.0f℃", last.temp));
        } else {
            bigPct.setText("--");
            bigTemp.setText("");
        }

        double[] r = rate(range);
        if (r[0] > 0) {
            powerText.setText(String.format(Locale.US, "%.2f", r[0] * WH_PER_PCT));
            leftText.setText(String.format(Locale.US, "%.1f", 100 / r[0]));
        } else {
            powerText.setText("--");
            leftText.setText("--");
        }
        double used = 0;
        for (int i = 1; i < range.size(); i++) {
            double dt = (range.get(i).t - range.get(i - 1).t) / 3600000.0;
            if (dt > 0 && dt <= 0.25 && range.get(i - 1).interactive) used += dt;
        }
        usedText.setText(used > 0 ? String.format(Locale.US, "%.1f", used) : "--");

        renderScenes(range, start, end);
    }

    private void renderScenes(List<HistoryStore.Sample> range, long start, long end) {
        sceneList.removeAllViews();
        boolean access = AppUsage.hasAccess(this);
        Map<String, Long> usage = access ? AppUsage.usageForDay(this, start, end) : new HashMap<>();

        Map<String, long[]> agg = new HashMap<>();
        Map<String, double[]> pwr = new HashMap<>();
        for (int i = 1; i < range.size(); i++) {
            HistoryStore.Sample a = range.get(i - 1), b = range.get(i);
            double dt = (b.t - a.t) / 3600000.0;
            if (dt > 0 && dt <= 0.25 && !a.charging && !b.charging) {
                int d = a.cap - b.cap;
                if (d >= 0 && d <= 10 && a.pkg != null && !a.pkg.isEmpty() && a.pkg.equals(b.pkg)) {
                    double[] p = pwr.get(a.pkg);
                    if (p == null) p = new double[]{0, 0};
                    p[0] += d; p[1] += dt;
                    pwr.put(a.pkg, p);
                }
            }
        }
        for (HistoryStore.Sample s : range) {
            if (s.pkg == null || s.pkg.isEmpty()) continue;
            long[] a = agg.get(s.pkg);
            if (a == null) a = new long[]{0, 0, 0};
            a[0]++; a[1] += (long) (s.temp * 10); a[2] = Math.max(a[2], (long) (s.temp * 10));
            agg.put(s.pkg, a);
        }
        Map<String, Long> durations = new HashMap<>(usage);
        for (Map.Entry<String, long[]> en : agg.entrySet())
            if (!durations.containsKey(en.getKey())) durations.put(en.getKey(), en.getValue()[0] * 300000L);

        double[] rr = rate(range);
        double dayPower = rr[0] > 0 ? rr[0] * WH_PER_PCT : -1;
        long totalDur = 0;
        for (String k : durations.keySet()) if (!k.equals(getPackageName())) totalDur += durations.get(k);

        List<Map.Entry<String, Long>> list = new ArrayList<>(durations.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        int shown = 0, total = 0;
        for (Map.Entry<String, Long> en : list) {
            if (en.getKey().equals(getPackageName())) continue;
            if (en.getValue() < 10000) continue;
            total++;
            if (shown >= 30) continue;
            double estW = (dayPower > 0 && totalDur > 0) ? dayPower * en.getValue() / totalDur : -1;
            sceneList.addView(sceneRow(en.getKey(), en.getValue(), agg.get(en.getKey()),
                    pwr.get(en.getKey()), estW));
            shown++;
        }
        if (shown == 0) {
            TextView empty = text("该时间段还没有应用使用记录", 13, SUB, false);
            empty.setPadding(0, dp(10), 0, 0);
            sceneList.addView(empty);
        }
        hintText.setText((access ? "共 " + total + " 个应用，按使用时长排序；功耗为估算值"
                : "共 " + total + " 个；未开启使用情况访问，时长按采样估算"));
    }

    private View sceneRow(String pkg, long ms, long[] agg, double[] pwr, double estW) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        PackageManager pm = getPackageManager();
        String name = pkg;
        Drawable icon = null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            name = pm.getApplicationLabel(ai).toString();
            icon = pm.getApplicationIcon(ai);
        } catch (Exception ignored) {}

        android.widget.ImageView iv = new android.widget.ImageView(this);
        if (icon != null) iv.setImageDrawable(icon);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(34), dp(34));
        ilp.rightMargin = dp(12);
        iv.setLayoutParams(ilp);
        row.addView(iv);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        mid.addView(text(name, 16, TEXT, false));

        StringBuilder sub = new StringBuilder();
        if (pwr != null && pwr[1] > 0) sub.append(String.format(Locale.US, "%.2fW", pwr[0] / pwr[1] * WH_PER_PCT));
        else if (estW > 0) sub.append(String.format(Locale.US, "≈%.2fW", estW));
        if (agg != null && agg[0] > 0) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(String.format(Locale.US, "%.0f/%.0f℃", agg[1] / 10f / agg[0], agg[2] / 10f));
        }
        if (sub.length() > 0) {
            TextView t = text(sub.toString(), 12, SUB, false);
            t.setPadding(0, dp(1), 0, 0);
            mid.addView(t);
        }
        row.addView(mid);

        TextView dur = text(fmtDur(ms), 15, SUB, false);
        dur.setGravity(Gravity.END);
        row.addView(dur, new LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row);
        View line = new View(this);
        line.setBackgroundColor(SEP);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.6f));
        llp.leftMargin = dp(46);
        line.setLayoutParams(llp);
        wrap.addView(line);
        return wrap;
    }

    private String fmtDur(long ms) {
        long m = ms / 60000;
        long h = m / 60;
        m = m % 60;
        return h > 0 ? (h + "h" + m + "m") : (m + "m");
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
}
