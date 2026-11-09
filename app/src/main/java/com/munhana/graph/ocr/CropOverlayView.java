package com.munhana.graph.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Shows a photo and lets the user drag / resize a rectangle over the equations
 * they want to capture.
 */
public class CropOverlayView extends View {

    public interface Listener { void onCropChanged(RectF viewRect); }

    private Bitmap bitmap;
    private final RectF imageRect = new RectF();
    private final RectF crop = new RectF();
    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dim = new Paint();
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thirds = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private int mode = 0; // 0 none, 1 move, 2 new, 3..10 handles
    private float lastX, lastY, anchorX, anchorY;
    private boolean cropInitialised = false;
    private Listener listener;

    public CropOverlayView(Context c) { super(c); init(c); }
    public CropOverlayView(Context c, AttributeSet a) { super(c, a); init(c); }
    public CropOverlayView(Context c, AttributeSet a, int d) { super(c, a, d); init(c); }

    private void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        dim.setColor(0xB3000000);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(2.2f * density);
        frame.setColor(0xFFB388FF);
        handle.setStyle(Paint.Style.FILL);
        handle.setColor(0xFFC9A7FF);
        thirds.setStyle(Paint.Style.STROKE);
        thirds.setStrokeWidth(0.8f * density);
        thirds.setColor(0x66C9A7FF);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setListener(Listener l) { listener = l; }

    public void setBitmap(Bitmap b) {
        bitmap = b;
        cropInitialised = false;
        requestLayout();
        invalidate();
    }

    public Bitmap getBitmap() { return bitmap; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
    }

    private void computeImageRect() {
        if (bitmap == null) return;
        float vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return;
        float scale = Math.min(vw / bitmap.getWidth(), vh / bitmap.getHeight());
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;
        imageRect.set((vw - w) / 2f, (vh - h) / 2f, (vw + w) / 2f, (vh + h) / 2f);
    }

    private void ensureCrop() {
        if (cropInitialised || imageRect.isEmpty()) return;
        float ix = imageRect.width() * 0.07f;
        float iy = imageRect.height() * 0.24f;
        crop.set(imageRect.left + ix, imageRect.top + iy,
                imageRect.right - ix, imageRect.bottom - iy);
        cropInitialised = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) return;
        computeImageRect();
        ensureCrop();

        canvas.drawBitmap(bitmap, null, imageRect, bmpPaint);

        // dim everything outside the crop
        canvas.drawRect(0, 0, getWidth(), crop.top, dim);
        canvas.drawRect(0, crop.bottom, getWidth(), getHeight(), dim);
        canvas.drawRect(0, crop.top, crop.left, crop.bottom, dim);
        canvas.drawRect(crop.right, crop.top, getWidth(), crop.bottom, dim);

        // rule of thirds
        for (int i = 1; i < 3; i++) {
            float x = crop.left + crop.width() * i / 3f;
            float y = crop.top + crop.height() * i / 3f;
            canvas.drawLine(x, crop.top, x, crop.bottom, thirds);
            canvas.drawLine(crop.left, y, crop.right, y, thirds);
        }

        canvas.drawRoundRect(crop, 6 * density, 6 * density, frame);

        // corner handles
        float r = 9 * density;
        canvas.drawCircle(crop.left, crop.top, r, handle);
        canvas.drawCircle(crop.right, crop.top, r, handle);
        canvas.drawCircle(crop.left, crop.bottom, r, handle);
        canvas.drawCircle(crop.right, crop.bottom, r, handle);

        // edge grips
        float gr = 4 * density;
        canvas.drawCircle(crop.centerX(), crop.top, gr, handle);
        canvas.drawCircle(crop.centerX(), crop.bottom, gr, handle);
        canvas.drawCircle(crop.left, crop.centerY(), gr, handle);
        canvas.drawCircle(crop.right, crop.centerY(), gr, handle);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (bitmap == null) return false;
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lastX = x; lastY = y;
                mode = hitTest(x, y);
                if (mode == 2) {
                    anchorX = x; anchorY = y;
                    crop.set(x, y, x, y);
                    cropInitialised = true;
                }
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = x - lastX, dy = y - lastY;
                lastX = x; lastY = y;
                if (mode == 1) {
                    crop.offset(dx, dy);
                    clampCrop();
                } else if (mode == 2) {
                    crop.set(Math.min(anchorX, x), Math.min(anchorY, y),
                            Math.max(anchorX, x), Math.max(anchorY, y));
                } else if (mode >= 3) {
                    resize(mode, dx, dy);
                    clampCrop();
                }
                if (mode != 0) {
                    invalidate();
                    if (listener != null) listener.onCropChanged(new RectF(crop));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mode == 2 && (crop.width() < 12 * density || crop.height() < 12 * density)) {
                    cropInitialised = false;
                    invalidate();
                }
                mode = 0;
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    private int hitTest(float x, float y) {
        float t = 30 * density;
        boolean nearL = Math.abs(x - crop.left) < t;
        boolean nearR = Math.abs(x - crop.right) < t;
        boolean nearT = Math.abs(y - crop.top) < t;
        boolean nearB = Math.abs(y - crop.bottom) < t;
        if (nearL && nearT) return 3;
        if (nearR && nearT) return 4;
        if (nearL && nearB) return 5;
        if (nearR && nearB) return 6;
        if (nearL) return 7;
        if (nearR) return 8;
        if (nearT) return 9;
        if (nearB) return 10;
        if (crop.contains(x, y)) return 1;
        return 2;
    }

    private void resize(int m, float dx, float dy) {
        switch (m) {
            case 3: crop.left += dx; crop.top += dy; break;
            case 4: crop.right += dx; crop.top += dy; break;
            case 5: crop.left += dx; crop.bottom += dy; break;
            case 6: crop.right += dx; crop.bottom += dy; break;
            case 7: crop.left += dx; break;
            case 8: crop.right += dx; break;
            case 9: crop.top += dy; break;
            case 10: crop.bottom += dy; break;
        }
        if (crop.right - crop.left < 40 * density) {
            if (m == 3 || m == 5 || m == 7) crop.left = crop.right - 40 * density;
            else crop.right = crop.left + 40 * density;
        }
        if (crop.bottom - crop.top < 30 * density) {
            if (m == 3 || m == 4 || m == 9) crop.top = crop.bottom - 30 * density;
            else crop.bottom = crop.top + 30 * density;
        }
    }

    private void clampCrop() {
        crop.left = Math.max(crop.left, imageRect.left);
        crop.top = Math.max(crop.top, imageRect.top);
        crop.right = Math.min(crop.right, imageRect.right);
        crop.bottom = Math.min(crop.bottom, imageRect.bottom);
    }

    public void resetCrop() {
        cropInitialised = false;
        invalidate();
    }

    /** Crops the source bitmap using the current selection. */
    public Bitmap cropBitmap() {
        if (bitmap == null) return null;
        computeImageRect();
        ensureCrop();
        if (imageRect.width() <= 0 || crop.width() < 1 || crop.height() < 1) return bitmap;
        float scale = imageRect.width() / bitmap.getWidth();
        Rect src = new Rect(
                Math.round((crop.left - imageRect.left) / scale),
                Math.round((crop.top - imageRect.top) / scale),
                Math.round((crop.right - imageRect.left) / scale),
                Math.round((crop.bottom - imageRect.top) / scale));
        src.left = Math.max(0, Math.min(bitmap.getWidth() - 1, src.left));
        src.top = Math.max(0, Math.min(bitmap.getHeight() - 1, src.top));
        src.right = Math.max(src.left + 1, Math.min(bitmap.getWidth(), src.right));
        src.bottom = Math.max(src.top + 1, Math.min(bitmap.getHeight(), src.bottom));
        return Bitmap.createBitmap(bitmap, src.left, src.top,
                src.right - src.left, src.bottom - src.top);
    }
}
