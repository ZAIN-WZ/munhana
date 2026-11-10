package com.munhana.graph.ocr;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Tiny fully-connected digit classifier (784-128-10) trained offline on
 * handwritten digits and embedded as raw float weights. It is used only as a
 * fallback for handwriting, never for printed text where Tesseract is better.
 */
final class DigitNet {

    private static final String TAG = "DigitNet";
    private static final String ASSET = "mnist_mlp.bin";

    private static DigitNet instance;
    private static boolean failed;

    private final float[] w1;   // 784 x 128
    private final float[] b1;   // 128
    private final float[] w2;   // 128 x 10
    private final float[] b2;   // 10

    private DigitNet(float[] w1, float[] b1, float[] w2, float[] b2) {
        this.w1 = w1;
        this.b1 = b1;
        this.w2 = w2;
        this.b2 = b2;
    }

    static synchronized DigitNet get(Context context) {
        if (instance != null) return instance;
        if (failed) return null;
        try {
            byte[] raw = readAsset(context, ASSET);
            final int n1 = 784 * 128, n2 = 128 * 10;
            int need = (n1 + 128 + n2 + 10) * 4;
            if (raw.length < need) throw new IllegalStateException("weights too short");
            float[] w1 = new float[n1], b1 = new float[128];
            float[] w2 = new float[n2], b2 = new float[10];
            int p = 0;
            p = floats(raw, p, w1);
            p = floats(raw, p, b1);
            p = floats(raw, p, w2);
            floats(raw, p, b2);
            instance = new DigitNet(w1, b1, w2, b2);
            return instance;
        } catch (Throwable t) {
            Log.w(TAG, "cannot load digit model", t);
            failed = true;
            return null;
        }
    }

    private static byte[] readAsset(Context context, String name) throws Exception {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(450000);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private static int floats(byte[] raw, int offset, float[] dst) {
        for (int i = 0; i < dst.length; i++) {
            int bits = (raw[offset] & 0xFF)
                    | ((raw[offset + 1] & 0xFF) << 8)
                    | ((raw[offset + 2] & 0xFF) << 16)
                    | ((raw[offset + 3] & 0xFF) << 24);
            dst[i] = Float.intBitsToFloat(bits);
            offset += 4;
        }
        return offset;
    }

    /**
     * Classifies a tight glyph. Returns {digit, confidence} or null.
     */
    int[] predict(boolean[] mask, int width, int x0, int y0, int w, int h) {
        if (w < 3 || h < 3) return null;
        float[] input = normalize(mask, width, x0, y0, w, h);
        if (input == null) return null;

        float[] hidden = new float[128];
        for (int j = 0; j < 128; j++) {
            float sum = b1[j];
            int base = j;
            for (int i = 0; i < 784; i++) {
                float v = input[i];
                if (v != 0f) sum += v * w1[i * 128 + j];
            }
            hidden[j] = sum > 0 ? sum : 0;
        }

        float[] out = new float[10];
        float max = -Float.MAX_VALUE;
        for (int k = 0; k < 10; k++) {
            float sum = b2[k];
            for (int j = 0; j < 128; j++) sum += hidden[j] * w2[j * 10 + k];
            out[k] = sum;
            if (sum > max) max = sum;
        }
        float total = 0;
        for (int k = 0; k < 10; k++) {
            out[k] = (float) Math.exp(out[k] - max);
            total += out[k];
        }
        int best = 0;
        float bestP = 0;
        for (int k = 0; k < 10; k++) {
            out[k] /= total;
            if (out[k] > bestP) { bestP = out[k]; best = k; }
        }
        return new int[]{best, Math.round(bestP * 1000)};
    }

    /** Scales the glyph to 20x20, centres it in 28x28 and shifts by mass. */
    private static float[] normalize(boolean[] mask, int width, int x0, int y0, int w, int h) {
        double scale = 20.0 / Math.max(w, h);
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        if (nw > 20) nw = 20;
        if (nh > 20) nh = 20;

        float[] small = new float[nw * nh];
        for (int dy = 0; dy < nh; dy++) {
            int sy0 = y0 + (dy * h) / nh;
            int sy1 = y0 + Math.max(sy0 - y0 + 1, ((dy + 1) * h) / nh);
            for (int dx = 0; dx < nw; dx++) {
                int sx0 = x0 + (dx * w) / nw;
                int sx1 = x0 + Math.max(sx0 - x0 + 1, ((dx + 1) * w) / nw);
                int ink = 0, total = 0;
                for (int sy = sy0; sy < sy1 && sy < y0 + h; sy++) {
                    int row = sy * width;
                    for (int sx = sx0; sx < sx1 && sx < x0 + w; sx++) {
                        if (mask[row + sx]) ink++;
                        total++;
                    }
                }
                small[dy * nw + dx] = total == 0 ? 0f : ink / (float) total;
            }
        }

        float[] out = new float[784];
        int ox = (28 - nw) / 2;
        int oy = (28 - nh) / 2;
        for (int y = 0; y < nh; y++) {
            System.arraycopy(small, y * nw, out, (oy + y) * 28 + ox, nw);
        }

        double sum = 0, cy = 0, cx = 0;
        for (int y = 0; y < 28; y++) {
            for (int x = 0; x < 28; x++) {
                float v = out[y * 28 + x];
                sum += v;
                cy += v * y;
                cx += v * x;
            }
        }
        if (sum <= 0) return null;
        int dy = (int) Math.round(13.5 - cy / sum);
        int dx = (int) Math.round(13.5 - cx / sum);
        return shift(out, dy, dx);
    }

    private static float[] shift(float[] src, int dy, int dx) {
        float[] out = new float[784];
        for (int y = 0; y < 28; y++) {
            int sy = y - dy;
            if (sy < 0 || sy >= 28) continue;
            for (int x = 0; x < 28; x++) {
                int sx = x - dx;
                if (sx < 0 || sx >= 28) continue;
                out[y * 28 + x] = src[sy * 28 + sx];
            }
        }
        return out;
    }
}
