package com.dsh.sysmon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 0–100% 双指标曲线：CPU（蓝）+ 内存（紫），与电池页图表同一套画法 */
public class UsageChartView extends View {

    public static final int CPU = 0xFF007AFF;
    public static final int MEM = 0xFFAF52DE;

    private static final int GRID = 0xFFE5E5EA;
    private static final int SUB = 0xFF8E8E93;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
    private final Path path = new Path();

    private long[] ts;
    private float[] cpu, mem;
    private boolean clock;

    public UsageChartView(Context c) {
        super(c);
        paint.setStrokeCap(Paint.Cap.ROUND);
        setWillNotDraw(false);
    }

    /** clock=false 时横轴不画时间（实时 60 秒用） */
    public void setSeries(long[] ts, float[] cpu, float[] mem, boolean clock) {
        this.ts = ts;
        this.cpu = cpu;
        this.mem = mem;
        this.clock = clock;
        invalidate();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private int count(float[] v) {
        if (v == null) return 0;
        int n = 0;
        for (float x : v) if (x >= 0) n++;
        return n;
    }

    private void smooth(float[] xs, float[] ys, int n, Path p) {
        p.reset();
        if (n == 0) return;
        p.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            float midX = (xs[i - 1] + xs[i]) / 2f;
            float midY = (ys[i - 1] + ys[i]) / 2f;
            p.quadTo(xs[i - 1], ys[i - 1], midX, midY);
        }
        p.lineTo(xs[n - 1], ys[n - 1]);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        int padL = dp(36), padR = dp(44), padT = dp(10);
        int padB = clock ? dp(22) : dp(6);
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.6f));
        paint.setColor(GRID);
        paint.setPathEffect(new DashPathEffect(new float[]{dp(2.5f), dp(4)}, 0));
        for (int i = 0; i <= 2; i++) {
            float y = padT + plotH * i / 2f;
            canvas.drawLine(padL, y, w - padR, y, paint);
        }
        paint.setPathEffect(null);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SUB);
        paint.setTextSize(dp(10));
        canvas.drawText("100%", dp(2), padT + dp(9), paint);
        canvas.drawText("0%", dp(12), padT + plotH, paint);

        int n = ts == null ? 0 : ts.length;
        if (n < 1 || count(cpu) < 1) {
            paint.setColor(SUB);
            paint.setTextSize(dp(12));
            canvas.drawText("正在采样…", padL + dp(8), padT + plotH / 2f, paint);
            return;
        }

        long t0 = ts[0];
        long t1 = ts[n - 1];
        float span = Math.max(1, t1 - t0);

        drawSeries(canvas, cpu, t0, span, padL, padT, plotW, plotH, w, CPU, true);
        drawSeries(canvas, mem, t0, span, padL, padT, plotW, plotH, w, MEM, false);

        if (clock && span >= 120000) { // 采样点太密时不画时间刻度，避免重复标签
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SUB);
            paint.setTextSize(dp(10));
            int ticks = 3;
            for (int i = 0; i <= ticks; i++) {
                float x = padL + plotW * i / ticks;
                long t = t0 + (long) (span * i / ticks);
                String s = fmt.format(new Date(t));
                float tw = paint.measureText(s);
                float tx = Math.max(dp(2), Math.min(w - tw - dp(2), x - tw / 2));
                canvas.drawText(s, tx, h - dp(5), paint);
            }
        }
    }

    private void drawSeries(Canvas canvas, float[] v, long t0, float span, int padL, int padT,
                            float plotW, float plotH, int w, int color, boolean fill) {
        if (v == null) return;
        int n = v.length;
        float[] xs = new float[n], ys = new float[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (v[i] < 0) continue;
            xs[m] = padL + plotW * ((ts[i] - t0) / span);
            ys[m] = padT + plotH * (1 - Math.min(100f, v[i]) / 100f);
            m++;
        }
        if (m < 1) return;

        smooth(xs, ys, m, path);

        if (fill && m >= 2) {
            Path area = new Path(path);
            area.lineTo(padL + plotW, padT + plotH);
            area.lineTo(padL, padT + plotH);
            area.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(0, padT, 0, padT + plotH,
                    (color & 0x00FFFFFF) | 0x33000000, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
            canvas.drawPath(area, paint);
            paint.setShader(null);
        }

        if (m >= 2) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.4f));
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }

        float lx = xs[m - 1], ly = ys[m - 1];
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(lx, ly, dp(4.5f), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.2f));
        paint.setColor(color);
        canvas.drawCircle(lx, ly, dp(3.6f), paint);

        String label = String.format(Locale.US, "%.0f%%", v[n - 1] < 0 ? v[n - 2] : v[n - 1]);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(dp(11));
        float tw = paint.measureText(label);
        float tx = Math.min(w - dp(2) - tw, lx + dp(5));
        float ty = Math.max(padT + dp(9), Math.min(padT + plotH, ly + dp(4)));
        canvas.drawText(label, tx, ty, paint);
    }
}
