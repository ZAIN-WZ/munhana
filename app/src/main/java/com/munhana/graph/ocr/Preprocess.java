package com.munhana.graph.ocr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.util.Arrays;

/**
 * Image conditioning for equation photos. The hard variant produces a clean,
 * deskewed black-on-white bitmap (best for printed formulas and ruled paper),
 * while the soft variant keeps anti-aliased grayscale for faint handwriting.
 */
final class Preprocess {

    private Preprocess() { }

    static Bitmap prepare(Bitmap src) {
        return process(src, true);
    }

    static Bitmap prepareSoft(Bitmap src) {
        return process(src, false);
    }

    private static Bitmap process(Bitmap src, boolean hard) {
        if (src == null || src.getWidth() < 2 || src.getHeight() < 2) return src;

        int w = src.getWidth();
        int h = src.getHeight();
        float scale = 1f;
        if (w < 1000) {
            scale = Math.min(2.5f, 1500f / w);
        } else if (w > 2400) {
            scale = 1900f / w;
        }

        int nw = Math.max(2, Math.round(w * scale));
        int nh = Math.max(2, Math.round(h * scale));
        Bitmap scaled = (nw == w && nh == h) ? src
                : Bitmap.createScaledBitmap(src, nw, nh, true);

        int[] px = new int[nw * nh];
        scaled.getPixels(px, 0, nw, 0, 0, nw, nh);

        int[] gray = new int[px.length];
        long sum = 0;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int v = (int) (0.299 * ((p >> 16) & 0xFF)
                    + 0.587 * ((p >> 8) & 0xFF)
                    + 0.114 * (p & 0xFF));
            gray[i] = v;
            sum += v;
        }
        double mean = sum / (double) gray.length;

        // White chalk on a blackboard / dark paper.
        if (mean < 112) {
            for (int i = 0; i < gray.length; i++) gray[i] = 255 - gray[i];
        }

        Bitmap result = hard ? harden(gray, nw, nh) : soften(gray, nw, nh);

        if (scaled != src && scaled != result) scaled.recycle();
        return result;
    }

    // --------------------------------------------------------------- soft pass

    private static Bitmap soften(int[] gray, int w, int h) {
        int[] hist = new int[256];
        for (int v : gray) hist[v]++;
        int loCut = Math.max(1, (int) (gray.length * 0.02));
        int hiCut = loCut;

        int lo = 0, acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc >= loCut) { lo = i; break; }
        }
        int hi = 255;
        acc = 0;
        for (int i = 255; i >= 0; i--) {
            acc += hist[i];
            if (acc >= hiCut) { hi = i; break; }
        }
        if (hi - lo < 40) { lo = 0; hi = 255; }
        float range = Math.max(1, hi - lo);

        int pad = Math.max(14, Math.round(18f * (w / 1600f)) + 10);
        int ow = w + pad * 2;
        int oh = h + pad * 2;
        int[] out = new int[ow * oh];
        Arrays.fill(out, 0xFFFFFFFF);
        for (int y = 0; y < h; y++) {
            int srcRow = y * w;
            int dstRow = (y + pad) * ow + pad;
            for (int x = 0; x < w; x++) {
                int v = (int) ((gray[srcRow + x] - lo) * 255f / range);
                v = Math.max(0, Math.min(255, v));
                out[dstRow + x] = 0xFF000000 | (v << 16) | (v << 8) | v;
            }
        }
        Bitmap bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888);
        bmp.setPixels(out, 0, ow, 0, 0, ow, oh);
        return bmp;
    }

    // --------------------------------------------------------------- hard pass

    private static Bitmap harden(int[] gray, int w, int h) {
        int threshold = otsu(gray);
        boolean[] dark = new boolean[w * h];
        int darkCount = 0;
        for (int i = 0; i < gray.length; i++) {
            if (gray[i] < threshold) {
                dark[i] = true;
                darkCount++;
            }
        }
        // A nearly blank crop: fall back to the soft rendering.
        if (darkCount < 20 || darkCount > gray.length * 0.6) {
            return soften(gray, w, h);
        }

        removeLongLines(dark, w, h);

        double angle = estimateSkew(dark, w, h);
        Bitmap binary = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        int[] bin = new int[w * h];
        Arrays.fill(bin, 0xFFFFFFFF);
        for (int i = 0; i < bin.length; i++) {
            if (dark[i]) bin[i] = 0xFF000000;
        }
        binary.setPixels(bin, 0, w, 0, 0, w, h);

        int pad = Math.max(16, Math.round(22f * (w / 1600f)) + 10);
        Bitmap out = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        canvas.drawColor(Color.WHITE);

        Matrix m = new Matrix();
        if (Math.abs(angle) >= 0.6) {
            m.postRotate((float) -angle, w / 2f, h / 2f);
        }
        m.postTranslate(pad, pad);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(binary, m, p);
        binary.recycle();
        return out;
    }

    private static int otsu(int[] gray) {
        int[] hist = new int[256];
        for (int v : gray) hist[v]++;
        int total = gray.length;
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];

        double sumB = 0;
        int wB = 0;
        double max = -1;
        int threshold = 128;
        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
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
        return Math.max(40, Math.min(215, threshold));
    }

    /** Removes notebook rules and page margins which confuse line detection. */
    private static void removeLongLines(boolean[] dark, int w, int h) {
        int rowLimit = (int) (w * 0.62);
        for (int y = 0; y < h; y++) {
            int count = 0;
            int base = y * w;
            for (int x = 0; x < w; x++) if (dark[base + x]) count++;
            if (count > rowLimit) {
                for (int x = 0; x < w; x++) dark[base + x] = false;
            }
        }
        int colLimit = (int) (h * 0.72);
        for (int x = 0; x < w; x++) {
            int count = 0;
            for (int y = 0; y < h; y++) if (dark[y * w + x]) count++;
            if (count > colLimit) {
                for (int y = 0; y < h; y++) dark[y * w + x] = false;
            }
        }
    }

    /** Estimates the page skew (degrees) by maximising horizontal projection. */
    private static double estimateSkew(boolean[] dark, int w, int h) {
        int n = 0;
        for (boolean b : dark) if (b) n++;
        if (n < 50) return 0;

        int[] xs = new int[n];
        int[] ys = new int[n];
        int k = 0;
        for (int y = 0; y < h; y++) {
            int base = y * w;
            for (int x = 0; x < w; x++) {
                if (dark[base + x]) { xs[k] = x; ys[k] = y; k++; }
            }
        }

        double cx = w / 2.0;
        double cy = h / 2.0;
        int[] hist = new int[h + (int) (w * 0.25) + 8];
        int offset = (int) (w * 0.125) + 4;
        double bestScore = -1;
        double bestAngle = 0;

        for (int deg = -6; deg <= 6; deg++) {
            double rad = Math.toRadians(deg);
            double cos = Math.cos(rad), sin = Math.sin(rad);
            Arrays.fill(hist, 0);
            for (int i = 0; i < n; i++) {
                int yy = (int) Math.round((ys[i] - cy) * cos - (xs[i] - cx) * sin) + (int) cy + offset;
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
}
