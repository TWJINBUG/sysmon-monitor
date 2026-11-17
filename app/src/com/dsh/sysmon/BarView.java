package com.dsh.sysmon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 圆角占用条：单色填充或两段填充，用于 CPU / 内存 / 每核 */
public class BarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();

    private float p1 = -1f, p2 = -1f;
    private int c1 = 0xFF2E6FF2, c2 = 0xFFA8C4FF;
    private static final int TRACK = 0xFFE9E9EB;

    public BarView(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    public void setSingle(float pct, int color) {
        p1 = pct;
        p2 = -1f;
        c1 = color;
        invalidate();
    }

    /** 两段：pct1 用 color1，pct2 用 color2，剩余画底 */
    public void setStack(float pct1, int color1, float pct2, int color2) {
        p1 = pct1;
        p2 = pct2;
        c1 = color1;
        c2 = color2;
        invalidate();
    }

    public void reset() {
        p1 = -1f;
        p2 = -1f;
        invalidate();
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float r = h / 2f;
        rect.set(0, 0, w, h);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(TRACK);
        canvas.drawRoundRect(rect, r, r, paint);

        if (p1 < 0) return;

        clip.reset();
        clip.addRoundRect(rect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);

        float x = 0;
        float w1 = w * Math.min(100f, p1) / 100f;
        paint.setColor(c1);
        canvas.drawRect(x, 0, x + w1, h, paint);
        x += w1;

        if (p2 > 0) {
            float w2 = w * Math.min(100f - p1, p2) / 100f;
            paint.setColor(c2);
            canvas.drawRect(x, 0, x + w2, h, paint);
        }
        canvas.restore();
    }
}
