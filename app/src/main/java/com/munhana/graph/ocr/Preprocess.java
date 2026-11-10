package com.munhana.graph.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Image conditioning for equation photos.
 *
 * <p>The key step is a pen/ink mask built from colour saturation and local
 * background subtraction. That keeps red marker, blue pen and black ink while
 * dropping shadows, glare and the whiteboard surface. A binary, deskewed
 * black-on-white bitmap is then produced for OCR, plus a soft grayscale variant
 * for faint writing.</p>
 */
final class Preprocess {

    private Preprocess() { }

    /** Hard, black-on-white bitmap, deskewed when needed. */
    static Bitmap prepare(Bitmap src) {
        return process(src, true);
    }

    /** Soft grayscale bitmap with the illumination flattened. */
    static Bitmap prepareSoft(Bitmap src) {
        return process(src, false);
    }

    private static Bitmap process(Bitmap src, boolean hard) {
        if (src == null || src.getWidth() < 2 || src.getHeight() < 2) return src;

        int w = src.getWidth();
        int h = src.getHeight();
        float scale = 1f;
        if (w < 900) scale = Math.min(3f, 1300f / w);
        else if (w > 1700) scale = 1700f / w;

        int nw = Math.max(2, Math.round(w * scale));
        int nh = Math.max(2, Math.round(h * scale));
        Bitmap scaled = (nw == w && nh == h) ? src
                : Bitmap.createScaledBitmap(src, nw, nh, true);

        int[] px = new int[nw * nh];
        scaled.getPixels(px, 0, nw, 0, 0, nw, nh);

        int[] gray = new int[px.length];
        int[] sat = new int[px.length];
        long lumSum = 0;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            int mx = Math.max(r, Math.max(g, b));
            int mn = Math.min(r, Math.min(g, b));
            int v = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            gray[i] = v;
            sat[i] = mx - mn;
            lumSum += v;
        }
        double meanLum = lumSum / (double) gray.length;

        int radius = Math.max(8, nw / 28);
        int[] bg = boxMean(gray, nw, nh, radius);

        // Lighter ink on a dark board (chalk) has to be handled separately.
        boolean lightInk = meanLum < 105;
        int[] score = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int local = bg[i];
            int diff = lightInk ? gray[i] - local : local - gray[i];
            if (diff < 0) diff = 0;
            int s = Math.min(255, diff + sat[i]);
            score[i] = s;
        }

        Bitmap result = hard
                ? harden(score, sat, nw, nh, lightInk)
                : soften(gray, bg, nw, nh, lightInk);

        if (scaled != src && scaled != result) scaled.recycle();
        return result;
    }

    // --------------------------------------------------------------- hard pass

    private static Bitmap harden(int[] score, int[] sat, int w, int h, boolean lightInk) {
        int threshold = otsu(score);
        // The ink score is not a plain luminance histogram: keep the cut in a
        // range that neither keeps the surface nor erases thin marker strokes.
        threshold = Math.max(26, Math.min(74, threshold));

        boolean[] ink = new boolean[w * h];
        int count = 0;
        for (int i = 0; i < score.length; i++) {
            if (score[i] > threshold) {
                ink[i] = true;
                count++;
            }
        }
        if (count < 30) {
            // Nothing detected: fall back to a simple contrast stretch.
            for (int i = 0; i < score.length; i++) {
                ink[i] = score[i] > Math.max(14, threshold / 2);
                if (ink[i]) count++;
            }
        }
        if (count == 0) count = 1;
        if (count > score.length * 0.55) {
            return softenMask(score, w, h);
        }

        removeLongLines(ink, w, h);

        double angle = estimateSkew(ink, w, h);
        // Thin strokes survive better if we bridge one-pixel gaps first.
        closeGaps(ink, w, h);

        Bitmap binary = render(ink, w, h, 0);
        if (Math.abs(angle) < 0.65) return binary;

        int pad = Math.max(12, Math.round(18f * (w / 1600f)) + 8);
        Bitmap out = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);
        Matrix m = new Matrix();
        m.postRotate((float) -angle, w / 2f, h / 2f);
        m.postTranslate(pad, pad);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(binary, m, p);
        binary.recycle();
        return out;
    }

    private static Bitmap render(boolean[] ink, int w, int h, int pad) {
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) {
            out[i] = ink[i] ? 0xFF101018 : 0xFFFFFFFF;
        }
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.setPixels(out, 0, w, 0, 0, w, h);
        return bmp;
    }

    private static Bitmap softenMask(int[] score, int w, int h) {
        boolean[] ink = new boolean[w * h];
        int t = Math.max(20, Math.min(80, otsu(score)));
        for (int i = 0; i < score.length; i++) ink[i] = score[i] > t;
        return render(ink, w, h, 0);
    }

    // --------------------------------------------------------------- soft pass

    private static Bitmap soften(int[] gray, int[] bg, int w, int h, boolean lightInk) {
        int[] out = new int[w * h];
        int pad = Math.max(12, Math.round(16f * (w / 1600f)) + 8);
        int ow = w + pad * 2;
        int oh = h + pad * 2;
        int[] canvas = new int[ow * oh];
        Arrays.fill(canvas, 0xFFFFFFFF);
        for (int y = 0; y < h; y++) {
            int srcRow = y * w;
            int dstRow = (y + pad) * ow + pad;
            for (int x = 0; x < w; x++) {
                int g = gray[srcRow + x];
                int b = Math.max(1, bg[srcRow + x]);
                int v;
                if (lightInk) {
                    // chalk on a dark board: keep the light strokes
                    v = 255 - Math.max(0, Math.min(255, (g - b) * 2 + 40));
                } else {
                    // divide by the local background to remove shadows/glare
                    v = (int) (255L * g / b);
                    v = Math.max(0, Math.min(255, v));
                    v = (int) (255 - Math.min(255, (255 - v) * 1.35));
                }
                canvas[dstRow + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        bmp.setPixels(canvas, 0, ow, 0, 0, ow, oh);
        return bmp;
    }

    // --------------------------------------------------------------- utilities

    /** Box-mean of the luminance using a summed-area table. */
    private static int[] boxMean(int[] gray, int w, int h, int radius) {
        long[] sum = new long[w * h];
        for (int y = 0; y < h; y++) {
            long row = 0;
            int base = y * w;
            for (int x = 0; x < w; x++) {
                row += gray[base + x];
                sum[base + x] = row + (y > 0 ? sum[base - w + x] : 0);
            }
        }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int y0 = Math.max(0, y - radius);
            int y1 = Math.min(h - 1, y + radius);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - radius);
                int x1 = Math.min(w - 1, x + radius);
                long a = sum[y1 * w + x1];
                long b = x0 > 0 ? sum[y1 * w + x0 - 1] : 0;
                long c = y0 > 0 ? sum[(y0 - 1) * w + x1] : 0;
                long d = (x0 > 0 && y0 > 0) ? sum[(y0 - 1) * w + x0 - 1] : 0;
                long area = (long) (x1 - x0 + 1) * (y1 - y0 + 1);
                out[y * w + x] = (int) ((a - b - c + d) / Math.max(1, area));
            }
        }
        return out;
    }

    private static int otsu(int[] values) {
        int[] hist = new int[256];
        for (int v : values) hist[Math.max(0, Math.min(255, v))]++;
        long total = values.length;
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];

        double sumB = 0;
        long wB = 0;
        double max = -1;
        int threshold = 60;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            long wF = total - wB;
            if (wF == 0) break;
            sumB += t * (double) hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;
            double between = (double) wB * wF * (mB - mF) * (mB - mF);
            if (between > max) {
                max = between;
                threshold = t;
            }
        }
        return threshold;
    }

    /** Removes ruled lines, table borders and page margins. */
    private static void removeLongLines(boolean[] ink, int w, int h) {
        // Slanted borders never fill a whole column, so look at runs instead.
        for (int x = 0; x < w; x++) {
            int y = 0;
            while (y < h) {
                if (!ink[y * w + x]) { y++; continue; }
                int start = y;
                while (y < h && ink[y * w + x]) y++;
                if (y - start > h * 0.32) {
                    for (int k = start; k < y; k++) ink[k * w + x] = false;
                }
            }
        }
        for (int y = 0; y < h; y++) {
            int x = 0;
            int base = y * w;
            while (x < w) {
                if (!ink[base + x]) { x++; continue; }
                int start = x;
                while (x < w && ink[base + x]) x++;
                if (x - start > w * 0.55) {
                    for (int k = start; k < x; k++) ink[base + k] = false;
                }
            }
        }
    }

    private static void closeGaps(boolean[] ink, int w, int h) {
        boolean[] copy = ink.clone();
        for (int y = 1; y < h - 1; y++) {
            int base = y * w;
            for (int x = 1; x < w - 1; x++) {
                if (copy[base + x]) continue;
                int n = (copy[base + x - 1] ? 1 : 0) + (copy[base + x + 1] ? 1 : 0)
                        + (copy[base - w + x] ? 1 : 0) + (copy[base + w + x] ? 1 : 0);
                if (n >= 3) ink[base + x] = true;
            }
        }
    }

    /** Estimates skew (degrees) by maximising the horizontal projection. */
    private static double estimateSkew(boolean[] ink, int w, int h) {
        int total = 0;
        for (boolean b : ink) if (b) total++;
        if (total < 80) return 0;

        int stride = Math.max(1, total / 50000);
        int n = 0;
        for (int y = 0; y < h && n < 50000; y += 2) {
            int base = y * w;
            for (int x = 0; x < w; x += stride) {
                if (ink[base + x]) n++;
            }
        }
        if (n < 80) return 0;

        int[] xs = new int[n];
        int[] ys = new int[n];
        int k = 0;
        for (int y = 0; y < h && k < n; y += 2) {
            int base = y * w;
            for (int x = 0; x < w && k < n; x += stride) {
                if (ink[base + x]) { xs[k] = x; ys[k] = y; k++; }
            }
        }

        double cx = w / 2.0;
        double cy = h / 2.0;
        int[] hist = new int[h + (int) (w * 0.25) + 8];
        int offset = (int) (w * 0.125) + 4;
        double bestScore = -1;
        double bestAngle = 0;

        for (int deg = -8; deg <= 8; deg += 1) {
            double rad = Math.toRadians(deg);
            double cos = Math.cos(rad), sin = Math.sin(rad);
            Arrays.fill(hist, 0);
            for (int i = 0; i < k; i++) {
                int yy = (int) Math.round((ys[i] - cy) * cos - (xs[i] - cx) * sin)
                        + (int) cy + offset;
                if (yy >= 0 && yy < hist.length) hist[yy]++;
            }
            double score = 0;
            for (int v : hist) score += (long) v * v;
            if (score > bestScore) {
                bestScore = score;
                bestAngle = deg;
            }
        }
        return bestAngle;
    }

    // ----------------------------------------------------------- line finding

    /**
     * Detects the regions that contain writing and returns them in the
     * coordinates of the original bitmap as {@code {left, top, right, bottom}}.
     * The crop screen uses these boxes so the user only has to tap the
     * equations they want instead of drawing a rectangle by hand.
     */
    static List<int[]> detectRegions(Bitmap src) {
        List<int[]> out = new ArrayList<>();
        if (src == null || src.getWidth() < 16 || src.getHeight() < 16) return out;

        int sw = src.getWidth(), sh = src.getHeight();
        float scale = 1f;
        if (sw > 1400) scale = 1400f / sw;
        else if (sw < 700) scale = Math.min(2f, 1000f / sw);
        int w = Math.max(16, Math.round(sw * scale));
        int h = Math.max(16, Math.round(sh * scale));
        Bitmap small = (w == sw && h == sh) ? src : Bitmap.createScaledBitmap(src, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != src) small.recycle();

        int[] gray = new int[w * h];
        long sum = 0;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int v = (int) (0.299 * ((p >> 16) & 0xFF) + 0.587 * ((p >> 8) & 0xFF)
                    + 0.114 * (p & 0xFF));
            gray[i] = v;
            sum += v;
        }
        double mean = sum / (double) gray.length;
        boolean lightInk = mean < 110;
        int cut = lightInk
                ? (int) Math.min(235, mean + 28)
                : (int) Math.max(95, Math.min(150, mean * 0.75));

        boolean[] ink = new boolean[w * h];
        for (int i = 0; i < gray.length; i++) {
            ink[i] = lightInk ? gray[i] > cut : gray[i] < cut;
        }
        removeSpecks(ink, w, h);

        float sx = sw / (float) w;
        float sy = sh / (float) h;

        int[] rows = new int[h];
        for (int y = 0; y < h; y++) {
            int c = 0;
            int base = y * w;
            for (int x = 0; x < w; x++) if (ink[base + x]) c++;
            rows[y] = c;
        }
        int minRow = Math.max(3, (int) (w * 0.008));
        List<int[]> found = new ArrayList<>();
        int runStart = -1;
        for (int y = 0; y < h; y++) {
            boolean on = rows[y] >= minRow;
            if (on && runStart < 0) runStart = y;
            else if (!on && runStart >= 0) {
                if (y - runStart >= Math.max(10, h / 90)) found.add(new int[]{runStart, y});
                runStart = -1;
            }
        }
        if (runStart >= 0) found.add(new int[]{runStart, h});
        List<int[]> bands = new ArrayList<>();
        for (int[] r : found) {
            if (!bands.isEmpty()) {
                int[] last = bands.get(bands.size() - 1);
                if (r[0] - last[1] < Math.max(5, (last[1] - last[0]) / 3)) {
                    last[1] = r[1];
                    continue;
                }
            }
            bands.add(new int[]{r[0], r[1]});
        }
        int inkPixels = 0;
        for (boolean b : ink) if (b) inkPixels++;
        android.util.Log.i("Detect2", "w=" + w + " h=" + h + " mean=" + mean
                + " cut=" + cut + " ink=" + inkPixels + " bands=" + bands.size());

        for (int[] band : bands) {
            int top = band[0], bottom = band[1];
            int bandH = bottom - top;
            if (bandH < 10) continue;

            int[] cols = new int[w];
            for (int y = top; y < bottom; y++) {
                int base = y * w;
                for (int x = 0; x < w; x++) if (ink[base + x]) cols[x]++;
            }
            int minCol = Math.max(1, bandH / 14);
            int gapLimit = Math.max(10, Math.round(bandH * 2.2f));
            int x0 = -1, last = -1;
            List<int[]> pieces = new ArrayList<>();
            for (int x = 0; x < w; x++) {
                if (cols[x] >= minCol) {
                    if (x0 < 0) x0 = x;
                    last = x;
                } else if (x0 >= 0 && x - last > gapLimit) {
                    pieces.add(new int[]{x0, last + 1});
                    x0 = -1;
                }
            }
            if (x0 >= 0) pieces.add(new int[]{x0, last + 1});

            StringBuilder pd = new StringBuilder();
            for (int[] p : pieces) pd.append(' ').append(p[0]).append('-').append(p[1]);
            android.util.Log.i("Detect2", "band " + top + "-" + bottom + " pieces="
                    + pieces.size() + pd);

            for (int[] p : pieces) {
                int pw = p[1] - p[0];
                if (pw < Math.max(18, bandH / 3)) continue;
                int padX = Math.max(3, bandH / 5);
                int padY = Math.max(3, bandH / 5);
                int l = Math.max(0, Math.round((p[0] - padX) * sx));
                int t = Math.max(0, Math.round((top - padY) * sy));
                int r = Math.min(src.getWidth(), Math.round((p[1] + padX) * sx));
                int b = Math.min(src.getHeight(), Math.round((bottom + padY) * sy));
                if (r - l >= 24 && b - t >= 14) out.add(new int[]{l, t, r, b});
            }
        }
        return out;
    }

    /** Removes tiny connected components (texture, dust) from an ink mask. */
    private static void removeSpecks(boolean[] ink, int w, int h) {
        int minArea = Math.max(30, (w * h) / 30000);
        boolean[] seen = new boolean[w * h];
        int[] region = new int[w * h];
        int n = w * h;
        for (int i = 0; i < n; i++) {
            if (!ink[i] || seen[i]) continue;
            int head = 0, tail = 0;
            region[tail++] = i;
            seen[i] = true;
            while (head < tail) {
                int cur = region[head++];
                int y = cur / w, x = cur % w;
                if (x > 0) {
                    int ni = cur - 1;
                    if (ink[ni] && !seen[ni]) { seen[ni] = true; region[tail++] = ni; }
                }
                if (x + 1 < w) {
                    int ni = cur + 1;
                    if (ink[ni] && !seen[ni]) { seen[ni] = true; region[tail++] = ni; }
                }
                if (y > 0) {
                    int ni = cur - w;
                    if (ink[ni] && !seen[ni]) { seen[ni] = true; region[tail++] = ni; }
                }
                if (y + 1 < h) {
                    int ni = cur + w;
                    if (ink[ni] && !seen[ni]) { seen[ni] = true; region[tail++] = ni; }
                }
            }
            if (tail < minArea) {
                for (int k = 0; k < tail; k++) ink[region[k]] = false;
            }
        }
    }

    /**
     * Finds horizontal text bands in a prepared (black on white) bitmap.
     * Returns arrays of {@code {top, bottom}} in bitmap coordinates.
     */
    static List<int[]> lineBands(Bitmap hard) {
        List<int[]> bands = new ArrayList<>();
        if (hard == null || hard.getWidth() < 8 || hard.getHeight() < 8) return bands;

        int w = hard.getWidth();
        int h = hard.getHeight();
        int[] px = new int[w * h];
        hard.getPixels(px, 0, w, 0, 0, w, h);

        boolean[] ink = new boolean[w * h];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int lum = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
            ink[i] = lum < 128;
        }
        removeLongLines(ink, w, h);

        int[] rows = new int[h];
        for (int y = 0; y < h; y++) {
            int c = 0;
            int base = y * w;
            for (int x = 0; x < w; x++) if (ink[base + x]) c++;
            rows[y] = c;
        }
        int smooth = Math.max(3, h / 160);
        int minCount = Math.max(2, (int) (w * 0.006));

        List<int[]> runs = new ArrayList<>();
        int start = -1;
        for (int y = 0; y < h; y++) {
            int acc = 0;
            int from = Math.max(0, y - smooth / 2);
            int to = Math.min(h - 1, y + smooth / 2);
            for (int j = from; j <= to; j++) acc = Math.max(acc, rows[j]);
            boolean on = acc >= minCount;
            if (on && start < 0) start = y;
            else if (!on && start >= 0) {
                if (y - start >= Math.max(10, h / 90)) runs.add(new int[]{start, y});
                start = -1;
            }
        }
        if (start >= 0) runs.add(new int[]{start, h});

        // Merge runs that belong to the same visual line (dots, accents).
        for (int[] r : runs) {
            if (!bands.isEmpty()) {
                int[] last = bands.get(bands.size() - 1);
                int gap = r[0] - last[1];
                int height = Math.max(1, last[1] - last[0]);
                if (gap < Math.max(3, height / 3)) {
                    last[1] = r[1];
                    continue;
                }
            }
            bands.add(new int[]{r[0], r[1]});
        }

        // Drop tiny specks.
        List<int[]> out = new ArrayList<>();
        for (int[] b : bands) {
            if (b[1] - b[0] >= 12) out.add(b);
        }
        return out;
    }
}
