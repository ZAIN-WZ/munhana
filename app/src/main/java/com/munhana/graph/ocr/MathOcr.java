package com.munhana.graph.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.munhana.graph.math.EquationText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Offline equation extraction: Tesseract OCR plus a maths-aware clean-up pass.
 *
 * <p>Recognition now works line by line. The photo is first converted to a
 * clean ink mask (colour saturation + background subtraction), deskewed and
 * split into text bands; every band gets its own single-line OCR pass. This is
 * much more reliable than feeding a whole whiteboard photo to the engine.</p>
 */
public final class MathOcr {

    private static final String TAG = "MathOcr";
    private static final String WHITELIST =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+-*/^()=<>.,|[]!";

    private static TessBaseAPI api;
    private static boolean failed = false;

    private MathOcr() { }

    /** True when the engine could not be initialised on this device. */
    public static synchronized boolean initFailed() { return failed; }

    /** Copies the traineddata asset once and boots Tesseract. Safe to call often. */
    public static synchronized boolean init(Context context) {
        if (api != null) return true;
        if (failed) return false;
        try {
            File dataDir = new File(context.getFilesDir(), "tessdata");
            if (!dataDir.exists() && !dataDir.mkdirs()) throw new IllegalStateException("tessdata");
            File trained = new File(dataDir, "eng.traineddata");
            if (!trained.exists() || trained.length() < 100000) {
                copyAsset(context, "eng.traineddata", trained);
            }
            TessBaseAPI engine = new TessBaseAPI();
            if (!engine.init(context.getFilesDir().getAbsolutePath(), "eng")) {
                engine.end();
                failed = true;
                return false;
            }
            engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
            engine.setVariable("tessedit_char_whitelist", WHITELIST);
            engine.setVariable("preserve_interword_spaces", "1");
            api = engine;
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "tesseract init failed", t);
            failed = true;
            return false;
        }
    }

    private static void copyAsset(Context context, String name, File out) throws Exception {
        File tmp = new File(out.getAbsolutePath() + ".tmp");
        try (InputStream in = context.getAssets().open(name);
             OutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        if (out.exists() && !out.delete()) throw new IllegalStateException("delete");
        if (!tmp.renameTo(out)) throw new IllegalStateException("rename");
    }

    /** Runs recognition on a cropped region. Blocking: call from a worker thread. */
    public static List<String> recognize(Context context, Bitmap bitmap) {
        return recognize(context, bitmap, false);
    }

    /**
     * Multi-pass recognition.
     *
     * @param singleLine when true the crop is expected to hold exactly one
     *                   formula, so single-line segmentation is used directly.
     */
    public static List<String> recognize(Context context, Bitmap bitmap, boolean singleLine) {
        if (bitmap == null) return new ArrayList<>();
        if (!init(context)) return new ArrayList<>();

        Bitmap hard = null;
        Bitmap soft = null;
        try {
            Pass best = null;
            int order = 0;

            hard = Preprocess.prepare(bitmap);
            if (singleLine) {
                Pass p = runPass(hard, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, order++);
                if (p != null) best = p;
            } else {
                // 1) whole crop as a block
                Pass block = runPass(hard, TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK, order++);
                if (block != null) best = block;

                // 2) every detected line on its own
                Pass lines = runLines(hard, order++);
                if (lines != null && (best == null || lines.score > best.score)) best = lines;

                // 3) automatic layout as a safety net
                Pass auto = runPass(hard, TessBaseAPI.PageSegMode.PSM_AUTO, order++);
                if (auto != null && (best == null || auto.score > best.score)) best = auto;
            }

            // A soft grayscale variant helps faint pencil and thin pen.
            soft = Preprocess.prepareSoft(bitmap);
            int mode = singleLine
                    ? TessBaseAPI.PageSegMode.PSM_SINGLE_LINE
                    : TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK;
            Pass softPass = runPass(soft, mode, order++);
            if (softPass != null
                    && (best == null || softPass.score > best.score + 6)) {
                best = softPass;
            }

            // Handwriting fallback. Tesseract 3 has no handwriting model, and on
            // marker photos it tends to return long random letter strings such
            // as "HV9M145557J11E". Prefer the geometric reader unless the
            // printed-text result really looks like an equation.
            List<String> hand = HandwritingOcr.recognize(context, hard);
            if (!hand.isEmpty()) {
                boolean printed = best != null && looksPrinted(best.equations);
                if (!printed || (singleLine && best.equations.isEmpty())) {
                    best = new Pass(hand, score(hand, mode, order) + 40);
                }
            }

            return best == null ? new ArrayList<>() : best.equations;
        } catch (Throwable t) {
            Log.w(TAG, "recognition failed", t);
            return new ArrayList<>();
        } finally {
            if (hard != null && hard != bitmap) hard.recycle();
            if (soft != null && soft != bitmap) soft.recycle();
        }
    }

    private static final class Pass {
        final List<String> equations;
        final int score;

        Pass(List<String> equations, int score) {
            this.equations = equations;
            this.score = score;
        }
    }

    private static Pass runLines(Bitmap hard, int order) {
        List<int[]> bands = Preprocess.lineBands(hard);
        if (bands.isEmpty()) return null;

        Set<String> found = new LinkedHashSet<>();
        int height = hard.getHeight();
        int width = hard.getWidth();

        for (int[] band : bands) {
            int top = Math.max(0, band[0] - 6);
            int bottom = Math.min(height, band[1] + 8);
            if (bottom - top < 12) continue;
            Bitmap line = Bitmap.createBitmap(hard, 0, top, width, bottom - top);
            try {
                Bitmap prepared = padAndScale(line);
                Pass p = runPass(prepared, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, order);
                if (prepared != line) prepared.recycle();
                if (p != null) {
                    for (String s : p.equations) found.add(s);
                }
            } catch (Throwable t) {
                Log.w(TAG, "line pass failed", t);
            } finally {
                line.recycle();
            }
        }

        if (found.isEmpty()) return null;
        List<String> out = new ArrayList<>(found);
        return new Pass(out, score(out, TessBaseAPI.PageSegMode.PSM_SINGLE_LINE, order));
    }

    /** Adds a white margin and enlarges short lines, both help the engine. */
    private static Bitmap padAndScale(Bitmap line) {
        int h = line.getHeight();
        float scale = h < 64 ? Math.min(3f, 72f / Math.max(1, h)) : 1f;
        int w = Math.round(line.getWidth() * scale);
        int nh = Math.round(h * scale);
        Bitmap scaled = scale == 1f ? line : Bitmap.createScaledBitmap(line, w, nh, true);

        int pad = Math.max(14, Math.round(nh * 0.25f));
        Bitmap out = Bitmap.createBitmap(w + pad * 2, nh + pad * 2, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        c.drawBitmap(scaled, pad, pad, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (scaled != line) scaled.recycle();
        return out;
    }

    private static Pass runPass(Bitmap image, int mode, int order) {
        try {
            List<String> equations;
            synchronized (MathOcr.class) {
                api.setPageSegMode(mode);
                api.setImage(image);
                String text = api.getUTF8Text();
                api.clear();
                equations = EquationText.fromOcrText(text);
            }
            return new Pass(equations, score(equations, mode, order));
        } catch (Throwable t) {
            Log.w(TAG, "pass failed (" + mode + ")", t);
            return null;
        }
    }

    /**
     * True when a Tesseract result is credible enough to keep over the
     * handwriting reader. Printed pages produce short, structured lines;
     * handwritten pages produce long runs of unrelated letters.
     */
    private static boolean looksPrinted(List<String> equations) {
        if (equations == null || equations.isEmpty()) return false;
        boolean relation = false;
        for (String s : equations) {
            if (s == null) continue;
            if (s.indexOf('=') > 0) relation = true;
            if (s.matches(".*[A-Z]{2,}.*")) return false;
            if (s.length() > 34) return false;
        }
        return relation;
    }

    private static boolean hasRelation(List<String> equations) {
        if (equations == null) return false;
        for (String s : equations) {
            if (s != null && s.indexOf('=') > 0) return true;
        }
        return false;
    }

    private static int score(List<String> equations, int mode, int order) {
        if (equations == null || equations.isEmpty()) return -1000;
        int score = equations.size() * 18;
        for (String eq : equations) {
            int len = eq.length();
            score += Math.min(20, len);
            if (eq.indexOf('=') > 0) score += 8;
            if (eq.matches(".*[+\\-*/^<>].*")) score += 4;
            if (eq.matches(".*[0-9].*")) score += 4;
            if (len > 60) score -= 12;
        }
        score -= order;
        return score;
    }
}
