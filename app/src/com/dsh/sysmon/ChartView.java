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
import java.util.List;
import java.util.Locale;

/** iOS 健康风格单指标曲线：kind=0 电量，kind=1 温度 */
public class ChartView extends View {

    public static final int CAP = 0;
    public static final int TEMP = 1;

    private static final int BLUE = 0xFF007AFF;
    private static final int ORANGE = 0xFFFF9500;
    private static final int GRID = 0xFFE5E5EA;
    private static final int SUB = 0xFF8E8E93;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);
    private final Path path = new Path();
    private final int kind;
    private List<HistoryStore.Sample> data;

    public ChartView(Context c, int kind) {
        super(c);
        this.kind = kind;
        paint.setStrokeCap(Paint.Cap.ROUND);
        setWillNotDraw(false);
    }

    public void setData(List<HistoryStore.Sample> d) {
        data = d;
        invalidate();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

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
        int padL = dp(40), padR = dp(46), padT = dp(10);
        int padB = kind == TEMP ? dp(24) : dp(8);
        float plotW = w - padL - padR;
        float plotH = h - padT - padB;

        if (data == null || data.size() < 2) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(SUB);
            paint.setTextSize(dp(12));
            canvas.drawText("暂无足够数据", padL + dp(8), h / 2f, paint);
            return;
        }

        long t0 = data.get(0).t;
        long t1 = data.get(data.size() - 1).t;
        float span = Math.max(1, t1 - t0);

        float tmin = 40, tmax = 20;
        for (HistoryStore.Sample s : data) {
            if (s.temp > 0) { tmin = Math.min(tmin, s.temp); tmax = Math.max(tmax, s.temp); }
        }
        if (tmax - tmin < 2) { tmin -= 1; tmax += 1; }
        float tSpan = Math.max(0.1f, tmax - tmin);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(0.6f));
        paint.setColor(GRID);
        paint.setPathEffect(new DashPathEffect(new float[]{dp(2.5f), dp(4)}, 0));
        for (int i = 0; i <= 3; i++) {
            float y = padT + plotH * i / 3f;
            canvas.drawLine(padL, y, w - padR, y, paint);
        }
        paint.setPathEffect(null);

        int n = data.size();
        float[] xs = new float[n], ys = new float[n];
        for (int i = 0; i < n; i++) {
            HistoryStore.Sample s = data.get(i);
            xs[i] = padL + plotW * ((s.t - t0) / span);
            if (kind == CAP) ys[i] = padT + plotH * (1 - s.cap / 100f);
            else ys[i] = padT + plotH * (1 - (s.temp - tmin) / tSpan);
        }

        int mainColor = kind == CAP ? BLUE : ORANGE;
        int fillTop = kind == CAP ? 0x4D007AFF : 0x33FF9500;
        int fillBottom = kind == CAP ? 0x00007AFF : 0x00FF9500;

        smooth(xs, ys, n, path);
        Path fill = new Path(path);
        fill.lineTo(padL + plotW, padT + plotH);
        fill.lineTo(padL, padT + plotH);
        fill.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, padT, 0, padT + plotH, fillTop, fillBottom, Shader.TileMode.CLAMP));
        canvas.drawPath(fill, paint);
        paint.setShader(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.8f));
        paint.setColor(mainColor);
        canvas.drawPath(path, paint);

        // 末端圆点
        float lx = padL + plotW;
        float ly = ys[n - 1];
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(lx, ly, dp(5), paint);
        paint.setColor(mainColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.5f));
        canvas.drawCircle(lx, ly, dp(4), paint);

        // 刻度
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(SUB);
        paint.setTextSize(dp(10));
        String endLabel;
        if (kind == CAP) {
            canvas.drawText("100%", dp(4), padT + dp(9), paint);
            canvas.drawText("0%", dp(12), padT + plotH, paint);
            endLabel = data.get(n - 1).cap + "%";
        } else {
            canvas.drawText(String.format(Locale.US, "%.0f℃", tmax), dp(4), padT + dp(9), paint);
            canvas.drawText(String.format(Locale.US, "%.0f℃", tmin), dp(4), padT + plotH, paint);
            endLabel = String.format(Locale.US, "%.0f℃", data.get(n - 1).temp);
        }

        // 末端数字（与线同色，直观对应）
        paint.setColor(mainColor);
        paint.setTextSize(dp(12));
        float tw = paint.measureText(endLabel);
        float tx = Math.min(w - dp(2) - tw, lx + dp(4));
        float ty = Math.max(padT + dp(10), Math.min(padT + plotH, ly + dp(4)));
        canvas.drawText(endLabel, tx, ty, paint);

        // 时间刻度
        if (kind == TEMP) {
            paint.setColor(SUB);
            paint.setTextSize(dp(10));
            int ticks = 4;
            for (int i = 0; i <= ticks; i++) {
                float x = padL + plotW * i / ticks;
                long t = t0 + (long) (span * i / ticks);
                String s = fmt.format(new Date(t));
                float tw2 = paint.measureText(s);
                float txx = Math.max(dp(2), Math.min(w - tw2 - dp(2), x - tw2 / 2));
                canvas.drawText(s, txx, h - dp(6), paint);
            }
        }
    }
}
