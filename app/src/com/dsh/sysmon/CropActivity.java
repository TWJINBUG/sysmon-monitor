package com.dsh.sysmon;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.InputStream;

/** 选图后手动裁剪/缩放：拖动平移、双指缩放，固定 2:1 取景框 */
public class CropActivity extends Activity {

    private CropView cropView;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(build());
        Uri uri = Uri.parse(getIntent().getStringExtra("uri"));
        Bitmap bmp = load(uri);
        if (bmp == null) {
            Toast.makeText(this, "图片读取失败", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cropView.setBitmap(bmp);
    }

    private Bitmap load(Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int scale = 1;
            int max = Math.max(o.outWidth, o.outHeight);
            while (max / scale > 1600) scale *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = scale;
            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                Bitmap b = BitmapFactory.decodeStream(in2, null, o2);
                if (b != null) return b;
            }
        } catch (Exception e) {
            android.util.Log.e("CropActivity", "load via resolver failed: " + e);
        }
        try {
            if (uri.getScheme() == null || "file".equals(uri.getScheme())) {
                return BitmapFactory.decodeFile(uri.getPath());
            }
        } catch (Exception e) {
            android.util.Log.e("CropActivity", "load file failed: " + e);
        }
        return null;
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1C1C1E);

        TextView title = new TextView(this);
        title.setText("拖动 / 双指缩放，调整背景");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(14);
        title.setPadding(dp(20), dp(20), dp(20), dp(12));
        root.addView(title);

        cropView = new CropView();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(cropView, lp);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(16), dp(14), dp(16), dp(24));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });

        Button ok = new Button(this);
        ok.setText("确定");
        ok.setOnClickListener(v -> {
            if (cropView.save()) {
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            }
        });

        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(cancel, blp);
        bar.addView(ok, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(bar);
        return root;
    }

    private int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    private class CropView extends FrameLayout {
        private Bitmap bmp;
        private final Matrix matrix = new Matrix();
        private final RectF frame = new RectF();
        private float baseScale = 1f;
        private float lastX, lastY;
        private int pointerCount;
        private ScaleGestureDetector scaleDetector;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dim = new Paint();

        CropView() {
            super(CropActivity.this);
            setWillNotDraw(false);
            dim.setColor(0xB3000000);
            scaleDetector = new ScaleGestureDetector(CropActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) {
                    if (bmp == null) return false;
                    matrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                    ensureCover(d.getFocusX(), d.getFocusY());
                    clamp();
                    invalidate();
                    return true;
                }
            });
        }

        void setBitmap(Bitmap b) {
            bmp = b;
            requestLayout();
            if (getWidth() > 0) reset();
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            computeFrame(w, h);
            reset();
        }

        private void computeFrame(int w, int h) {
            float pad = dp(16);
            float availW = w - pad * 2;
            float availH = h - pad * 2;
            float fw = availW;
            float fh = fw / 2f;
            if (fh > availH) {
                fh = availH;
                fw = fh * 2f;
            }
            float left = (w - fw) / 2f;
            float top = (h - fh) / 2f;
            frame.set(left, top, left + fw, top + fh);
        }

        private void reset() {
            if (bmp == null || frame.width() <= 0) return;
            baseScale = Math.max(frame.width() / bmp.getWidth(), frame.height() / bmp.getHeight());
            matrix.reset();
            matrix.postScale(baseScale, baseScale);
            matrix.postTranslate(
                    frame.centerX() - bmp.getWidth() * baseScale / 2f,
                    frame.centerY() - bmp.getHeight() * baseScale / 2f);
            clamp();
            invalidate();
        }

        private void ensureCover(float fx, float fy) {
            if (bmp == null || frame.width() <= 0) return;
            float[] v = new float[9];
            matrix.getValues(v);
            float scale = v[Matrix.MSCALE_X];
            float bw = bmp.getWidth() * scale;
            float bh = bmp.getHeight() * scale;
            float need = 1f;
            if (bw < frame.width()) need = Math.max(need, frame.width() / bw);
            if (bh < frame.height()) need = Math.max(need, frame.height() / bh);
            if (need > 1.001f) matrix.postScale(need, need, fx, fy);
        }

        private void clamp() {
            if (bmp == null) return;
            float[] v = new float[9];
            matrix.getValues(v);
            float scale = v[Matrix.MSCALE_X];
            float bw = bmp.getWidth() * scale;
            float bh = bmp.getHeight() * scale;
            float left = v[Matrix.MTRANS_X];
            float top = v[Matrix.MTRANS_Y];
            float dx = 0, dy = 0;
            if (left > frame.left) dx = frame.left - left;
            if (left + bw < frame.right) dx = frame.right - (left + bw);
            if (top > frame.top) dy = frame.top - top;
            if (top + bh < frame.bottom) dy = frame.bottom - (top + bh);
            if (dx != 0 || dy != 0) matrix.postTranslate(dx, dy);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            scaleDetector.onTouchEvent(e);
            if (e.getPointerCount() > 1) return true;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX(); lastY = e.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    lastX = e.getX(); lastY = e.getY();
                    clamp();
                    invalidate();
                    break;
            }
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bmp == null) return;
            canvas.save();
            canvas.clipRect(frame);
            canvas.drawBitmap(bmp, matrix, paint);
            canvas.restore();

            canvas.drawRect(0, 0, getWidth(), frame.top, dim);
            canvas.drawRect(0, frame.bottom, getWidth(), getHeight(), dim);
            canvas.drawRect(0, frame.top, frame.left, frame.bottom, dim);
            canvas.drawRect(frame.right, frame.top, getWidth(), frame.bottom, dim);

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(2));
            border.setColor(0xFFFFFFFF);
            canvas.drawRoundRect(frame, dp(14), dp(14), border);
        }

        boolean save() {
            if (bmp == null) return false;
            try {
                ensureCover(frame.centerX(), frame.centerY());
                clamp();
                int outW = 1200, outH = 600;
                Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                Matrix m = new Matrix(matrix);
                m.postTranslate(-frame.left, -frame.top);
                m.postScale(outW / frame.width(), outH / frame.height());
                canvas.drawBitmap(bmp, m, new Paint(Paint.ANTI_ALIAS_FLAG));
                try (FileOutputStream fos = new FileOutputStream(Theme.imageFile(CropActivity.this))) {
                    out.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
