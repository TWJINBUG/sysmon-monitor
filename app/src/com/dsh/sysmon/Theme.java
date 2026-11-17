package com.dsh.sysmon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.File;

public final class Theme {

    public static final int[][] PRESETS = {
            {0xFFFFFFFF, 0xFFF3F7FF}, // 云白
            {0xFF1C1C1E, 0xFF2C2C2E}, // 深空
            {0xFF2E6FF2, 0xFF5A93FF}, // 晴蓝
            {0xFF34C759, 0xFF6EDB8A}, // 青提
            {0xFFFF9500, 0xFFFFB340}, // 暖橙
            {0xFFAF52DE, 0xFFC98BF0}, // 紫罗兰
            {0xFFFF3B30, 0xFFFF6B62}, // 珊瑚
            {0xFF8E8E93, 0xFFB0B0B5}, // 石墨
    };

    public static final String[] NAMES = {
            "云白", "深空", "晴蓝", "青提", "暖橙", "紫罗兰", "珊瑚", "石墨",
            "透明·白字", "透明·黑字", "自定义图片", "液态玻璃", "白玻璃"
    };

    public static final int TRANSPARENT_WHITE = 8;
    public static final int TRANSPARENT_DARK = 9;
    public static final int CUSTOM_IMAGE = 10;
    public static final int GLASS = 11;
    public static final int GLASS_WHITE = 12;

    private Theme() {}

    public static int preset(Context c) {
        int i = WidgetPrefs.preset(c);
        if (i < 0 || i >= NAMES.length) i = 0;
        return i;
    }

    public static File imageFile(Context c) {
        return new File(c.getFilesDir(), "widget_bg.jpg");
    }

    public static boolean hasImage(Context c) {
        File f = imageFile(c);
        return f.exists() && f.length() > 0;
    }

    public static boolean isLight(int color) {
        double r = Color.red(color) / 255.0;
        double g = Color.green(color) / 255.0;
        double b = Color.blue(color) / 255.0;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.55;
    }

    public static int primaryText(boolean light) { return light ? 0xFF1C1C1E : 0xFFFFFFFF; }
    public static int secondaryText(boolean light) { return light ? 0xAA3A3A3F : 0xCCFFFFFF; }
    public static int accent(int base, boolean light) { return light ? base : lighten(base, 0.45f); }

    public static int lighten(int color, float f) {
        int r = (int) (Color.red(color) + (255 - Color.red(color)) * f);
        int g = (int) (Color.green(color) + (255 - Color.green(color)) * f);
        int b = (int) (Color.blue(color) + (255 - Color.blue(color)) * f);
        return Color.rgb(r, g, b);
    }

    private static Bitmap cachedImage;
    private static long cachedStamp;

    /** 遮罩 alpha（0-255）；level 0/1/2/3 */
    private static int scrimAlpha(Context c) {
        switch (WidgetPrefs.scrim(c)) {
            case 0: return 0;
            case 1: return 60;
            case 3: return 170;
            default: return 110;
        }
    }

    /**
     * 生成 widget 背景位图。尺寸必须控制在 RemoteViews 传输上限内：
     * 非透明用 RGB_565（640x320≈410KB），透明用 ARGB_8888（480x240≈460KB）。
     */
    public static Bitmap background(Context c, int w, int h, float radiusDp, boolean[] lightOut) {
        int preset = preset(c);
        float density = c.getResources().getDisplayMetrics().density;
        float r = radiusDp * density;
        boolean alpha = (preset == TRANSPARENT_WHITE || preset == TRANSPARENT_DARK);

        int bw = Math.min(alpha ? 400 : 512, Math.max(200, w));
        int bh = Math.min(alpha ? 200 : 256, Math.max(90, h));

        int scrim = scrimAlpha(c);

        if (alpha) {
            lightOut[0] = preset == TRANSPARENT_DARK;
            Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            int base = preset == TRANSPARENT_DARK ? 0x00FFFFFF : 0x00000000;
            int tint = preset == TRANSPARENT_DARK ? 0xFFFFFFFF : 0xFF000000;
            p.setColor(withAlpha(tint, Math.max(18, scrim / 2)));
            canvas.drawRoundRect(new RectF(0, 0, bw, bh), r, r, p);
            return bmp;
        }

        if (preset == CUSTOM_IMAGE && loadImage(c) != null) {
            Bitmap src = loadImage(c);
            boolean light = avgBright(src);
            int iw = Math.min(480, Math.max(200, bw));
            int ih = Math.min(240, Math.max(90, bh));
            Bitmap bmp = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float scale = Math.max((float) iw / src.getWidth(), (float) ih / src.getHeight());
            BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.postScale(scale, scale);
            m.postTranslate((iw - src.getWidth() * scale) / 2f, (ih - src.getHeight() * scale) / 2f);
            shader.setLocalMatrix(m);
            p.setShader(shader);
            canvas.drawRoundRect(new RectF(0, 0, iw, ih), r, r, p);
            if (scrim > 0) {
                Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
                sp.setColor(withAlpha(light ? 0xFFFFFFFF : 0xFF000000, scrim));
                canvas.drawRoundRect(new RectF(0, 0, iw, ih), r, r, sp);
            }
            lightOut[0] = light;
            return bmp;
        }

        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bmp);

        int idx = (preset == CUSTOM_IMAGE) ? 0 : Math.min(preset, PRESETS.length - 1);
        int[] g = PRESETS[idx];
        lightOut[0] = isLight(g[0]);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, bh, g[0], g[1], Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, bw, bh), r, r, p);
        return bmp;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static Bitmap loadImage(Context c) {
        File f = imageFile(c);
        if (!f.exists()) return null;
        long stamp = f.lastModified();
        if (cachedImage != null && cachedStamp == stamp) return cachedImage;
        Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (b != null) {
            cachedImage = b;
            cachedStamp = stamp;
        }
        return b;
    }

    private static boolean avgBright(Bitmap b) {
        int w = Math.min(32, b.getWidth());
        int h = Math.min(32, b.getHeight());
        long total = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = b.getPixel(x * b.getWidth() / w, y * b.getHeight() / h);
                total += (int) (0.2126 * Color.red(px) + 0.7152 * Color.green(px) + 0.0722 * Color.blue(px));
                count++;
            }
        }
        return count > 0 && total / count > 140;
    }
}
