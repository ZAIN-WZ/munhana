package com.munhana.graph.graph;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import com.munhana.graph.R;
import com.munhana.graph.math.Equation;
import com.munhana.graph.math.EquationText;
import com.munhana.graph.math.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dark, purple-accented cartesian plot with pan / pinch zoom. */
public class GraphView extends View {

    private final List<Equation> equations = new ArrayList<>();

    private double centerX = 0, centerY = 0;
    private double ppu = 70;            // pixels per world unit
    private float downX, downY;
    private boolean gestureActive = false;

    private final Paint gridMinor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridMajor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint curvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path curvePath = new Path();
    private final Path fillPath = new Path();

    private GestureDetector gestures;
    private ScaleGestureDetector scaleDetector;
    private float density;

    private boolean showGrid = true, showMinorGrid = true, showAxisNumbers = true;
    private boolean showArrows = true, showXAxis = true, showYAxis = true;
    private boolean showShading = false;
    private double xMin = -10, xMax = 10, yMin = -10, yMax = 10;

    public GraphView(Context c) { super(c); init(c); }
    public GraphView(Context c, AttributeSet a) { super(c, a); init(c); }
    public GraphView(Context c, AttributeSet a, int d) { super(c, a, d); init(c); }

    private void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);

        gridMinor.setColor(0xFF1C1428);
        gridMinor.setStrokeWidth(1f);
        gridMajor.setColor(0xFF2A1F3C);
        gridMajor.setStrokeWidth(1.2f);
        axisPaint.setColor(0xFF6E5A88);
        axisPaint.setStrokeWidth(1.5f);
        labelPaint.setColor(0xFF9C8CB8);
        labelPaint.setTextSize(10 * density);
        borderPaint.setColor(0xFF2A1F3C);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1f);

        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(2f * density);
        curvePaint.setStrokeCap(Paint.Cap.ROUND);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(4.8f * density);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setAlpha(18);

        shadePaint.setStyle(Paint.Style.FILL);
        shadePaint.setAlpha(26);

        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scaleDetector.isInProgress()) return false;
                centerX += dx / ppu;
                centerY -= dy / ppu;
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                resetView();
                return true;
            }
        });

        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                double wx = screenToWorldX(d.getFocusX());
                double wy = screenToWorldY(d.getFocusY());
                double next = clamp(ppu * d.getScaleFactor(), 1e-4, 1e7);
                ppu = next;
                centerX = wx - (d.getFocusX() - getWidth() / 2.0) / ppu;
                centerY = wy + (d.getFocusY() - getHeight() / 2.0) / ppu;
                invalidate();
                return true;
            }
        });
    }

    public void setEquations(List<Equation> list) {
        equations.clear();
        equations.addAll(list);
        invalidate();
    }

    /** Applies the graph settings panel state. */
    public void applySettings(GraphSettings s) {
        boolean windowChanged = xMin != s.xMin || xMax != s.xMax
                || yMin != s.yMin || yMax != s.yMax;
        showGrid = s.showGrid;
        showMinorGrid = s.showMinorGrid;
        showAxisNumbers = s.showAxisNumbers;
        showArrows = s.showArrows;
        showXAxis = s.showXAxis;
        showYAxis = s.showYAxis;
        showShading = s.shadeInequalities;
        xMin = s.xMin; xMax = s.xMax; yMin = s.yMin; yMax = s.yMax;
        Expression.setDegrees(s.degrees);
        labelPaint.setTextSize(10 * density);
        if (windowChanged && getWidth() > 0 && getHeight() > 0) resetView();
        else invalidate();
    }

    public void resetView() {
        double cx = (xMin + xMax) / 2.0;
        double cy = (yMin + yMax) / 2.0;
        double sx = getWidth() / Math.max(1e-9, xMax - xMin);
        double sy = getHeight() / Math.max(1e-9, yMax - yMin);
        double fit = Math.min(sx, sy);
        if (fit > 0) {
            ppu = fit;
            centerX = cx;
            centerY = cy;
        } else {
            centerX = 0; centerY = 0; ppu = 70;
        }
        invalidate();
    }

    public void zoomBy(double f) {
        ppu = clamp(ppu * f, 1e-4, 1e7);
        invalidate();
    }

    /**
     * Widens the default window so boundaries far from the origin are visible.
     * Imported constraints often cross the axes at 40 or 300, where the plain
     * -10..10 window would show only empty shading.
     */
    public void fitToEquations() {
        if (equations.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            resetView();
            return;
        }
        double lo = -10, x1 = 10, ylo = -10, y1 = 10;
        // Log-spaced probes along both axes; 1e-2 .. 1e4.
        double[] t = new double[121];
        for (int i = 0; i < 60; i++) {
            double v = Math.pow(10, (i - 20) / 5.0);
            t[i * 2] = v;
            t[i * 2 + 1] = -v;
        }
        t[120] = 0;
        java.util.Arrays.sort(t);

        for (Equation eq : equations) {
            if (eq.g == null || !eq.visible) continue;
            for (int i = 1; i < t.length; i++) {
                double a = safeValue(eq, t[i - 1], 0);
                double b = safeValue(eq, t[i], 0);
                double c = crossing(a, b, t[i - 1], t[i]);
                if (!Double.isNaN(c)) { lo = Math.min(lo, c * 1.15); x1 = Math.max(x1, c * 1.15); }
                a = safeValue(eq, 0, t[i - 1]);
                b = safeValue(eq, 0, t[i]);
                c = crossing(a, b, t[i - 1], t[i]);
                if (!Double.isNaN(c)) { ylo = Math.min(ylo, c * 1.15); y1 = Math.max(y1, c * 1.15); }
            }
        }
        double sx = getWidth() / Math.max(1e-9, x1 - lo);
        double sy = getHeight() / Math.max(1e-9, y1 - ylo);
        ppu = clamp(Math.min(sx, sy), 1e-4, 1e7);
        centerX = (lo + x1) / 2.0;
        centerY = (ylo + y1) / 2.0;
        invalidate();
    }

    private static double safeValue(Equation eq, double x, double y) {
        try {
            double v = eq.g.eval(x, y);
            return (Double.isNaN(v) || Double.isInfinite(v)) ? 0 : v;
        } catch (Exception e) {
            return 0;
        }
    }

    private static double crossing(double a, double b, double ta, double tb) {
        if ((a > 0) == (b > 0)) return Double.NaN;
        return ta + (tb - ta) * (Math.abs(a) / (Math.abs(a) + Math.abs(b)));
    }

    /** Renders the current graph, with an equation footer, for export / sharing. */
    public Bitmap exportBitmap() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return null;

        Bitmap graph = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas graphCanvas = new Canvas(graph);
        draw(graphCanvas);

        List<Equation> shown = new ArrayList<>();
        for (Equation eq : equations) {
            if (eq.visible && eq.text != null && !eq.text.trim().isEmpty()) shown.add(eq);
        }
        if (shown.isEmpty()) return graph;

        float d = density;
        float pad = 16 * d;
        float lineHeight = 22 * d;
        int footer = (int) Math.ceil(pad * 2 + lineHeight + shown.size() * lineHeight);

        Bitmap out = Bitmap.createBitmap(w, h + footer, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(0xFF0B0710);
        canvas.drawBitmap(graph, 0, 0, null);
        graph.recycle();

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0xFF2A1F3C);
        line.setStrokeWidth(1 * d);
        canvas.drawLine(0, h, w, h, line);

        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setColor(0xFFC9A7FF);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(12 * d);
        float titleY = h + pad + 10 * d;
        canvas.drawText(getContext().getString(R.string.export_equations_title), pad, titleY, title);

        Paint item = new Paint(Paint.ANTI_ALIAS_FLAG);
        item.setTypeface(Typeface.MONOSPACE);
        item.setTextSize(13 * d);
        item.setColor(0xFFF3E9FF);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);

        float y = titleY + lineHeight;
        for (Equation eq : shown) {
            dot.setColor(eq.color);
            canvas.drawCircle(pad + 4 * d, y - 4 * d, 4 * d, dot);
            float tx = pad + 16 * d;
            float max = w - tx - pad;
            String text = EquationText.pretty(eq.text.replace('\n', ' '));
            if (item.measureText(text) > max) {
                int keep = text.length();
                while (keep > 1 && item.measureText(text.substring(0, keep) + "…") > max) keep--;
                text = text.substring(0, Math.max(1, keep)) + "…";
            }
            canvas.drawText(text, tx, y, item);
            y += lineHeight;
        }
        return out;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        gestures.onTouchEvent(ev);
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) gestureActive = true;
        if (ev.getActionMasked() == MotionEvent.ACTION_UP
                || ev.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            gestureActive = false;
            invalidate();
        }
        return true;
    }

    // ------------------------------------------------------------- transforms

    public double screenToWorldX(double px) { return centerX + (px - getWidth() / 2.0) / ppu; }
    public double screenToWorldY(double py) { return centerY - (py - getHeight() / 2.0) / ppu; }
    public float worldToScreenX(double wx) { return (float) ((wx - centerX) * ppu + getWidth() / 2.0); }
    public float worldToScreenY(double wy) { return (float) (getHeight() / 2.0 - (wy - centerY) * ppu); }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------------ draw

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (ppu <= 0) resetView();

        canvas.drawColor(0xFF0B0710);
        drawGrid(canvas, w, h);

        for (Equation eq : equations) {
            if (!eq.visible || !eq.plottable) continue;
            drawEquation(canvas, eq, w, h);
        }

        canvas.drawRect(0.5f, 0.5f, w - 0.5f, h - 0.5f, borderPaint);
    }

    private void drawGrid(Canvas canvas, int w, int h) {
        double target = 90 * density / ppu;
        double major = niceStep(target);
        double minor = major / 5.0;

        if (showGrid && showMinorGrid && minor * ppu > 6) {
            double x0 = Math.floor(screenToWorldX(0) / minor) * minor;
            for (double x = x0; x <= screenToWorldX(w); x += minor) {
                float sx = worldToScreenX(x);
                canvas.drawLine(sx, 0, sx, h, gridMinor);
            }
            double y0 = Math.floor(screenToWorldY(h) / minor) * minor;
            for (double y = y0; y <= screenToWorldY(0); y += minor) {
                float sy = worldToScreenY(y);
                canvas.drawLine(0, sy, w, sy, gridMinor);
            }
        }

        if (showGrid) {
            double x0 = Math.floor(screenToWorldX(0) / major) * major;
            for (double x = x0; x <= screenToWorldX(w); x += major) {
                if (Math.abs(x) < major * 1e-6) continue;
                float sx = worldToScreenX(x);
                canvas.drawLine(sx, 0, sx, h, gridMajor);
            }
            double y0 = Math.floor(screenToWorldY(h) / major) * major;
            for (double y = y0; y <= screenToWorldY(0); y += major) {
                if (Math.abs(y) < major * 1e-6) continue;
                float sy = worldToScreenY(y);
                canvas.drawLine(0, sy, w, sy, gridMajor);
            }
        }

        float ax = worldToScreenX(0);
        float ay = worldToScreenY(0);
        boolean yAxisVisible = showYAxis && ax >= 0 && ax <= w;
        boolean xAxisVisible = showXAxis && ay >= 0 && ay <= h;
        if (xAxisVisible) canvas.drawLine(0, ay, w, ay, axisPaint);
        if (yAxisVisible) canvas.drawLine(ax, 0, ax, h, axisPaint);

        // axis numbers
        if (showAxisNumbers && showXAxis) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            float labelY = ay + 14 * density;
            if (labelY > h - 2 * density) labelY = ay - 6 * density;
            if (ay >= 0 && ay <= h) {
                double lx = Math.floor(screenToWorldX(40 * density) / major) * major;
                for (double x = lx; x <= screenToWorldX(w); x += major) {
                    if (Math.abs(x) < major * 1e-6) continue;
                    canvas.drawText(fmt(x, major), worldToScreenX(x), labelY, labelPaint);
                }
            }
        }
        if (showAxisNumbers && showYAxis) {
            labelPaint.setTextAlign(Paint.Align.RIGHT);
            double ly = Math.floor(screenToWorldY(0) / major) * major;
            for (double y = ly; y <= screenToWorldY(h); y += major) {
                if (Math.abs(y) < major * 1e-6) continue;
                float sy = worldToScreenY(y);
                if (sy < 14 * density || sy > h - 4 * density) continue;
                canvas.drawText(fmt(y, major), ax - 6 * density, sy + 4 * density, labelPaint);
            }
        }
        if (showAxisNumbers && xAxisVisible && yAxisVisible) {
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("0", ax - 9 * density, ay + 13 * density, labelPaint);
        }

        if (showArrows) {
            if (xAxisVisible) {
                canvas.drawLine(w - 26 * density, ay - 5 * density, w - 4 * density, ay, axisPaint);
                canvas.drawLine(w - 26 * density, ay + 5 * density, w - 4 * density, ay, axisPaint);
            }
            if (yAxisVisible) {
                canvas.drawLine(ax - 5 * density, 5 * density, ax, 2 * density, axisPaint);
                canvas.drawLine(ax + 5 * density, 5 * density, ax, 2 * density, axisPaint);
            }
        }
    }

    private static String fmt(double v, double step) {
        if (Math.abs(v) < step * 1e-6) return "0";
        double a = Math.abs(v);
        if (a >= 1e6 || a < 1e-4) return String.format(Locale.US, "%.0e", v);
        if (step < 0.01) return String.format(Locale.US, "%.3f", v);
        if (step < 0.1) return String.format(Locale.US, "%.2f", v);
        if (step < 1) return String.format(Locale.US, "%.1f", v);
        if (a == Math.rint(a)) return String.format(Locale.US, "%.0f", v);
        return String.format(Locale.US, "%.2f", v);
    }

    private static double niceStep(double raw) {
        if (raw <= 0 || Double.isNaN(raw) || Double.isInfinite(raw)) return 1;
        double exp = Math.floor(Math.log10(raw));
        double base = Math.pow(10, exp);
        double f = raw / base;
        double nice;
        if (f <= 1) nice = 1;
        else if (f <= 2) nice = 2;
        else if (f <= 5) nice = 5;
        else nice = 10;
        return nice * base;
    }

    private void drawEquation(Canvas canvas, Equation eq, int w, int h) {
        curvePaint.setColor(eq.color);
        glowPaint.setColor(eq.color);

        switch (eq.kind) {
            case FUNCTION:
                drawFunction(canvas, eq, w, h);
                break;
            case VERTICAL:
                drawVertical(canvas, eq, w, h);
                break;
            case INEQUALITY:
                if (showShading) drawShading(canvas, eq, w, h);
                drawContour(canvas, eq, w, h);
                break;
            default:
                drawContour(canvas, eq, w, h);
                break;
        }
    }

    private void drawFunction(Canvas canvas, Equation eq, int w, int h) {
        if (eq.fn == null) return;
        curvePath.reset();
        boolean started = false;
        double prevY = 0;
        double stepPx = 1.0;    // 1px columns
        for (float sx = 0; sx <= w; sx += stepPx) {
            double wx = screenToWorldX(sx);
            double wy;
            try { wy = eq.fn.eval(wx, 0); } catch (Exception e) { wy = Double.NaN; }
            if (Double.isNaN(wy) || Double.isInfinite(wy)) { started = false; continue; }
            float sy = worldToScreenY(wy);
            if (!started || sy < -h * 3f || sy > h * 4f
                    || Math.abs(sy - worldToScreenY(prevY)) > h * 1.5) {
                curvePath.moveTo(sx, sy);
                started = true;
            } else {
                curvePath.lineTo(sx, sy);
            }
            prevY = wy;
        }
        canvas.drawPath(curvePath, glowPaint);
        canvas.drawPath(curvePath, curvePaint);
    }

    private void drawVertical(Canvas canvas, Equation eq, int w, int h) {
        if (eq.fn == null) return;
        double v = eq.fn.eval(0, 0);
        if (Double.isNaN(v) || Double.isInfinite(v)) return;
        float sx = worldToScreenX(v);
        canvas.drawLine(sx, 0, sx, h, glowPaint);
        canvas.drawLine(sx, 0, sx, h, curvePaint);
    }

    private void drawContour(Canvas canvas, Equation eq, int w, int h) {
        if (eq.g == null) return;
        float step = (gestureActive ? 7f : 4f) * density;
        curvePath.reset();
        fillPath.reset();

        int cols = (int) Math.ceil(w / step) + 1;
        int rows = (int) Math.ceil(h / step) + 1;
        float[] vals = new float[cols * rows];
        for (int j = 0; j < rows; j++) {
            double wy = screenToWorldY(Math.min(j * step, h));
            for (int i = 0; i < cols; i++) {
                double wx = screenToWorldX(Math.min(i * step, w));
                float v;
                try { v = (float) eq.g.eval(wx, wy); } catch (Exception e) { v = Float.NaN; }
                vals[j * cols + i] = Float.isNaN(v) || Float.isInfinite(v) ? Float.NaN : v;
            }
        }

        float[] pts = new float[8];
        for (int j = 0; j < rows - 1; j++) {
            for (int i = 0; i < cols - 1; i++) {
                float v00 = vals[j * cols + i];
                float v10 = vals[j * cols + i + 1];
                float v01 = vals[(j + 1) * cols + i];
                float v11 = vals[(j + 1) * cols + i + 1];
                if (Float.isNaN(v00) || Float.isNaN(v10) || Float.isNaN(v01) || Float.isNaN(v11)) continue;
                if ((v00 > 0) == (v10 > 0) && (v00 > 0) == (v01 > 0) && (v00 > 0) == (v11 > 0)) continue;
                float x0 = i * step, x1 = x0 + step;
                float y0 = j * step, y1 = y0 + step;
                int n = 0;
                n = addEdge(pts, n, x0, y0, x1, y0, v00, v10);
                n = addEdge(pts, n, x1, y0, x1, y1, v10, v11);
                n = addEdge(pts, n, x1, y1, x0, y1, v11, v01);
                n = addEdge(pts, n, x0, y1, x0, y0, v01, v00);
                if (n >= 4) {
                    curvePath.moveTo(pts[0], pts[1]);
                    curvePath.lineTo(pts[2], pts[3]);
                    if (n >= 8) {
                        curvePath.moveTo(pts[4], pts[5]);
                        curvePath.lineTo(pts[6], pts[7]);
                    }
                }
            }
        }
        canvas.drawPath(curvePath, glowPaint);
        canvas.drawPath(curvePath, curvePaint);
    }

    private static int addEdge(float[] pts, int n, float ax, float ay, float bx, float by,
                               float va, float vb) {
        if ((va > 0) == (vb > 0)) return n;
        float d = va - vb;
        float t = Math.abs(d) < 1e-9f ? 0.5f : va / d;
        if (n + 2 > pts.length) return n;
        pts[n++] = ax + (bx - ax) * t;
        pts[n++] = ay + (by - ay) * t;
        return n;
    }

    private void drawShading(Canvas canvas, Equation eq, int w, int h) {
        boolean greater = eq.relation.startsWith(">");
        float step = 6 * density;
        shadePaint.setColor(eq.color);
        // setColor also overwrites alpha, so the fill level must be re-applied.
        shadePaint.setAlpha(26);
        for (float y = 0; y < h; y += step) {
            float runStart = -1;
            for (float x = 0; x <= w; x += step) {
                boolean inside = false;
                if (x < w) {
                    double wy = screenToWorldY(y + step / 2);
                    double wx = screenToWorldX(x + step / 2);
                    double v;
                    try { v = eq.g.eval(wx, wy); } catch (Exception ex) { v = Double.NaN; }
                    inside = !Double.isNaN(v) && (greater ? v > 0 : v < 0);
                }
                if (inside && runStart < 0) runStart = x;
                else if (!inside && runStart >= 0) {
                    canvas.drawRect(runStart, y, x, y + step, shadePaint);
                    runStart = -1;
                }
            }
            // A run that reaches the right edge never meets an "outside" sample,
            // so close it here; otherwise the right half of the region is lost.
            if (runStart >= 0) {
                canvas.drawRect(runStart, y, w, y + step, shadePaint);
            }
        }
    }

    public double getPpu() { return ppu; }
}
