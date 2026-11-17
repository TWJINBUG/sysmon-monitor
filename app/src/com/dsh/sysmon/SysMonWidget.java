package com.dsh.sysmon;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

public class SysMonWidget extends AppWidgetProvider {

    @Override public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateViews(context, mgr, id);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, Bundle newOptions) {
        updateViews(context, mgr, id);
    }

    static int tierFor(Bundle opts) {
        int w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
        if (w < 170 || h < 55) return 0;
        if (w < 240 || h < 85) return 1;
        if (w < 300 || h < 95) return 2;
        return 3;
    }

    static void updateViews(Context c, AppWidgetManager mgr, int id) {
        Bundle opts = mgr.getAppWidgetOptions(id);
        int tier = tierFor(opts);
        int w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);

        int layout = tier == 0 ? R.layout.widget_tiny
                : tier == 1 ? R.layout.widget_small
                : tier == 2 ? R.layout.widget_mid
                : R.layout.widget_large;
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);

        float density = c.getResources().getDisplayMetrics().density;
        int bw = (int) (w * density * 1.4f);
        int bh = (int) (h * density * 1.4f);

        int preset = Theme.preset(c);
        boolean imageMode = preset == Theme.CUSTOM_IMAGE && Theme.hasImage(c);
        boolean isLight;
        if (imageMode) {
            boolean[] light = new boolean[1];
            Bitmap bg = Theme.background(c, bw, bh, 24f, light);
            v.setViewVisibility(R.id.w_bg, android.view.View.VISIBLE);
            v.setInt(R.id.w_root, "setBackgroundResource", R.drawable.bg_clear);
            v.setImageViewBitmap(R.id.w_bg, bg);
            isLight = light[0];
        } else {
            v.setViewVisibility(R.id.w_bg, android.view.View.GONE);
            if (preset < Theme.PRESETS.length) {
                int[] res = {R.drawable.bg_0, R.drawable.bg_1, R.drawable.bg_2, R.drawable.bg_3,
                        R.drawable.bg_4, R.drawable.bg_5, R.drawable.bg_6, R.drawable.bg_7};
                v.setInt(R.id.w_root, "setBackgroundResource", res[preset]);
                isLight = Theme.isLight(Theme.PRESETS[preset][0]);
            } else if (preset == Theme.TRANSPARENT_WHITE) {
                v.setInt(R.id.w_root, "setBackgroundResource", R.drawable.bg_trans_w);
                isLight = false;
            } else if (preset == Theme.GLASS) {
                v.setInt(R.id.w_root, "setBackgroundResource", R.drawable.bg_glass);
                isLight = false;
            } else if (preset == Theme.GLASS_WHITE) {
                v.setInt(R.id.w_root, "setBackgroundResource", R.drawable.bg_glass_white);
                isLight = true;
            } else {
                v.setInt(R.id.w_root, "setBackgroundResource", R.drawable.bg_trans_d);
                isLight = true;
            }
        }

        int primary = Theme.primaryText(isLight);
        int secondary = Theme.secondaryText(isLight);
        int blue = Theme.accent(0xFF2E6FF2, isLight);
        int orange = Theme.accent(0xFFFF9500, isLight);
        int green = Theme.accent(0xFF34C759, isLight);

        float scale = (float) Math.sqrt((w * (double) h) / (320.0 * 140.0));
        scale = Math.min(scale, h / 165f);
        scale = Math.max(0.6f, Math.min(1.5f, scale));
        int pad = (int) (16 * scale * density);
        v.setViewPadding(R.id.w_content, pad, pad, pad, pad);

        float ct = Stats.cpuTemp();
        float bt = Stats.batteryTemp(c);
        long[] mem = Stats.mem();
        int pct = Stats.batteryPct(c);
        boolean charging = Stats.charging(c);

        String cpuText = ct > 0 ? String.format(java.util.Locale.US, "%.1f℃", ct) : "--℃";
        String batText = bt > 0 ? String.format(java.util.Locale.US, "%.1f℃", bt) : "--℃";
        String memText = Stats.gb(mem[0]) + "G";

        if (tier > 0) {
            int px = (int) (8 * scale * density);
            int py = (int) (4 * scale * density);
            v.setInt(R.id.w_live, "setBackgroundResource", isLight ? R.drawable.bg_live_dark : R.drawable.bg_live_light);
            v.setViewPadding(R.id.w_live, px, py, px, py);
        }

        if (tier == 0) {
            v.setViewVisibility(R.id.w_content, android.view.View.VISIBLE);
            v.setTextViewTextSize(R.id.w_label, android.util.TypedValue.COMPLEX_UNIT_SP, 9 * scale);
            v.setTextViewTextSize(R.id.w_cpu, android.util.TypedValue.COMPLEX_UNIT_SP, 26 * scale);
            v.setTextViewText(R.id.w_label, "CPU 温度");
            v.setTextColor(R.id.w_label, secondary);
            v.setTextViewText(R.id.w_cpu, cpuText);
            v.setTextColor(R.id.w_cpu, primary);
        } else if (tier == 1) {
            v.setTextViewTextSize(R.id.w_title, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_live, android.util.TypedValue.COMPLEX_UNIT_SP, 9 * scale);
            v.setTextViewTextSize(R.id.w_cpu, android.util.TypedValue.COMPLEX_UNIT_SP, 20 * scale);
            v.setTextViewTextSize(R.id.w_bat, android.util.TypedValue.COMPLEX_UNIT_SP, 20 * scale);
            v.setTextViewText(R.id.w_title, modelName(c));
            v.setTextColor(R.id.w_title, secondary);
            v.setTextViewText(R.id.w_live, "● 实时");
            v.setTextColor(R.id.w_live, green);
            v.setTextViewText(R.id.w_cpu, cpuText);
            v.setTextColor(R.id.w_cpu, blue);
            v.setTextViewText(R.id.w_bat, batText);
            v.setTextColor(R.id.w_bat, orange);
        } else if (tier == 2) {
            v.setTextViewTextSize(R.id.w_title, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_live, android.util.TypedValue.COMPLEX_UNIT_SP, 9 * scale);
            v.setTextViewTextSize(R.id.w_cpu_label, android.util.TypedValue.COMPLEX_UNIT_SP, 9 * scale);
            v.setTextViewTextSize(R.id.w_bat_label, android.util.TypedValue.COMPLEX_UNIT_SP, 9 * scale);
            v.setTextViewTextSize(R.id.w_mem_label, android.util.TypedValue.COMPLEX_UNIT_SP, 9 * scale);
            v.setTextViewTextSize(R.id.w_cpu, android.util.TypedValue.COMPLEX_UNIT_SP, 19 * scale);
            v.setTextViewTextSize(R.id.w_bat, android.util.TypedValue.COMPLEX_UNIT_SP, 19 * scale);
            v.setTextViewTextSize(R.id.w_mem, android.util.TypedValue.COMPLEX_UNIT_SP, 19 * scale);
            v.setTextViewText(R.id.w_title, modelName(c));
            v.setTextColor(R.id.w_title, secondary);
            v.setTextViewText(R.id.w_live, "● 实时");
            v.setTextColor(R.id.w_live, green);
            v.setTextColor(R.id.w_cpu_label, secondary);
            v.setTextColor(R.id.w_bat_label, secondary);
            v.setTextColor(R.id.w_mem_label, secondary);
            v.setTextViewText(R.id.w_cpu, cpuText);
            v.setTextColor(R.id.w_cpu, blue);
            v.setTextViewText(R.id.w_bat, batText);
            v.setTextColor(R.id.w_bat, orange);
            v.setTextViewText(R.id.w_mem, memText);
            v.setTextColor(R.id.w_mem, green);
        } else {
            v.setTextViewTextSize(R.id.w_title, android.util.TypedValue.COMPLEX_UNIT_SP, 11 * scale);
            v.setTextViewTextSize(R.id.w_live, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_cpu_label, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_bat_label, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_mem_label, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_cpu, android.util.TypedValue.COMPLEX_UNIT_SP, 23 * scale);
            v.setTextViewTextSize(R.id.w_bat, android.util.TypedValue.COMPLEX_UNIT_SP, 23 * scale);
            v.setTextViewTextSize(R.id.w_mem, android.util.TypedValue.COMPLEX_UNIT_SP, 23 * scale);
            v.setTextViewTextSize(R.id.w_cpu_sub, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_bat_sub, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewTextSize(R.id.w_mem_sub, android.util.TypedValue.COMPLEX_UNIT_SP, 10 * scale);
            v.setTextViewText(R.id.w_title, modelName(c));
            v.setTextColor(R.id.w_title, secondary);
            v.setTextViewText(R.id.w_live, "● 实时");
            v.setTextColor(R.id.w_live, green);
            v.setTextColor(R.id.w_cpu_label, secondary);
            v.setTextColor(R.id.w_bat_label, secondary);
            v.setTextColor(R.id.w_mem_label, secondary);
            v.setTextViewText(R.id.w_cpu, cpuText);
            v.setTextColor(R.id.w_cpu, blue);
            v.setTextViewText(R.id.w_bat, batText);
            v.setTextColor(R.id.w_bat, orange);
            v.setTextViewText(R.id.w_mem, memText);
            v.setTextColor(R.id.w_mem, green);

            int subColor = ct >= 80 ? Theme.accent(0xFFFF3B30, isLight) : ct >= 65 ? orange : green;
            v.setTextViewText(R.id.w_cpu_sub, ct >= 80 ? "偏高" : ct >= 65 ? "温热" : "正常");
            v.setTextColor(R.id.w_cpu_sub, subColor);

            v.setTextViewText(R.id.w_bat_sub, (charging ? "充电中 " : "放电中 ") + pct + "%");
            v.setTextColor(R.id.w_bat_sub, secondary);
            v.setTextViewText(R.id.w_mem_sub, "共 " + Stats.gb(mem[1]) + "G");
            v.setTextColor(R.id.w_mem_sub, secondary);

            long[] sto = Stats.storage(c);
            float board = Stats.boardTemp();
            v.setTextViewText(R.id.w_f_sto, "存储 " + Stats.gb(sto[0]) + "/" + Stats.gb(sto[1]) + "G");
            v.setTextViewText(R.id.w_f_up, "运行 " + Stats.uptime());
            v.setTextViewText(R.id.w_f_board, "主板 " + (board > 0 ? String.format(java.util.Locale.US, "%.1f", board) : "--") + "℃");
            int[] fids = {R.id.w_f_sto, R.id.w_f_up, R.id.w_f_board};
            for (int fid : fids) {
                v.setTextColor(fid, secondary);
                v.setTextViewTextSize(fid, android.util.TypedValue.COMPLEX_UNIT_SP, 13 * scale);
            }
        }

        PendingIntent pi = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_bg, pi);

        mgr.updateAppWidget(id, v);
    }

    private static String modelName(Context c) {
        String m = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL;
        if ("PLK110".equalsIgnoreCase(m)) return "OnePlus 15";
        String brand = android.os.Build.BRAND == null ? "" : android.os.Build.BRAND;
        return (brand + " " + m).trim();
    }
}
