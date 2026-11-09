package com.munhana.graph.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.munhana.graph.math.EquationText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline equation extraction: Tesseract OCR plus a maths-aware clean-up pass.
 * Everything runs on the device, no network involved.
 *
 * <p>Recognition runs several passes (two image variants x up to three page
 * segmentation modes) and keeps the result that yields the most plausible
 * equations. This noticeably helps with photos of handwritten formulas, where
 * no single threshold / segmentation setting works for everything.</p>
 */
public final class MathOcr {

    private static final String TAG = "MathOcr";
    private static final String WHITELIST =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+-*/^()=<>.,|";

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
            // Keep the original line structure; it helps multi-equation crops.
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
     *                   formula, so single-line segmentation is tried first.
     */
    public static List<String> recognize(Context context, Bitmap bitmap, boolean singleLine) {
        if (bitmap == null) return new ArrayList<>();
        if (!init(context)) return new ArrayList<>();

        Bitmap hard = null;
        Bitmap soft = null;
        try {
            // Order matters on ties: the first pass is the "most natural" mode
            // for this kind of crop.
            int[] modes = singleLine
                    ? new int[]{
                            TessBaseAPI.PageSegMode.PSM_SINGLE_LINE,
                            TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                            TessBaseAPI.PageSegMode.PSM_AUTO}
                    : new int[]{
                            TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK,
                            TessBaseAPI.PageSegMode.PSM_AUTO,
                            TessBaseAPI.PageSegMode.PSM_SINGLE_LINE};

            Pass best = null;
            int order = 0;

            // Process one variant at a time so two full-size bitmaps are never
            // alive together on low-memory devices.
            hard = Preprocess.prepare(bitmap);
            for (int mode : modes) {
                Pass pass = runPass(hard, mode, order++);
                if (pass != null && (best == null || pass.score > best.score)) best = pass;
            }
            if (hard != bitmap) { hard.recycle(); hard = null; }

            soft = Preprocess.prepareSoft(bitmap);
            for (int mode : modes) {
                Pass pass = runPass(soft, mode, order++);
                if (pass != null && (best == null || pass.score > best.score)) best = pass;
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
     * Heuristic confidence: more valid equations is better, and each equation
     * earns a small bonus for length and for containing both sides of "=".
     * Very early passes win ties so that a clean, expected segmentation is
     * preferred over an accidental duplicate.
     */
    private static int score(List<String> equations, int mode, int order) {
        if (equations == null || equations.isEmpty()) return -1000;
        int score = equations.size() * 24;
        for (String eq : equations) {
            int len = eq.length();
            score += Math.min(18, len);
            if (eq.indexOf('=') > 0) score += 6;
            if (eq.matches(".*[+\\-*/^].*")) score += 3;
        }
        // Tiny preference for the earlier (more natural) passes.
        score -= order;
        return score;
    }
}
