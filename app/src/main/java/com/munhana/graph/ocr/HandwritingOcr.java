package com.munhana.graph.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reader for handwritten school algebra.
 *
 * <p>The printed-text engine cannot read marker on a whiteboard, so this class
 * implements a small, fully offline recogniser aimed at the symbols people
 * actually use in algebra: digits, x, + - = &lt; &gt; ≤ ≥ and brackets. It works on
 * connected components instead of column gaps, which keeps a "4" or an "X"
 * whole even when its strokes do not touch, and it merges a chevron with its
 * underline so "≤" is a single symbol.</p>
 *
 * <p>Classification mixes simple geometry (holes, bars, diagonals, quadrant
 * balance) with a small embedded digit network. Everything is shown in the
 * editable review dialog, so a wrong glyph can be fixed with one tap.</p>
 */
final class HandwritingOcr {

    private static final int MAX_LINES = 12;

    private HandwritingOcr() { }

    static List<String> recognize(Context context, Bitmap hard) {
        List<String> out = new ArrayList<>();
        if (hard == null || hard.getWidth() < 16 || hard.getHeight() < 16) return out;

        Mask mask = Mask.read(hard);
        List<Comp> comps = components(mask);
        comps = filter(comps, mask.w, mask.h);
        if (comps.size() < 2) return out;
        int med = medianHeight(comps);
        if (med < 6) med = Math.max(6, mask.h / 30);
        comps = merge(comps, med);
        if (comps.size() < 2) return out;
        med = medianHeight(comps);
        if (med < 6) med = Math.max(6, mask.h / 30);

        // Group glyphs into text lines by the vertical centre of full-size
        // symbols. Subscripts and dots then follow their nearest line, which
        // is far more tolerant of sloping whiteboard writing than a raw row
        // projection.
        List<Comp> big = new ArrayList<>();
        for (Comp c : comps) {
            if (c.h() >= 0.55f * med && c.h() <= 2.4f * med && c.w() <= 3.2f * med) {
                big.add(c);
            }
        }
        big.sort((a, b) -> Double.compare(centreY(a), centreY(b)));
        List<double[]> centres = new ArrayList<>(); // {sumY, count, maxY}
        for (Comp c : big) {
            double y = centreY(c);
            if (!centres.isEmpty()) {
                double[] last = centres.get(centres.size() - 1);
                if (y - last[2] < 0.70 * med) {
                    last[0] += y;
                    last[1] += 1;
                    last[2] = Math.max(last[2], y);
                    continue;
                }
            }
            centres.add(new double[]{y, 1, y});
        }
        // Whiteboard lines drift; merge centres that are only a fraction of a
        // glyph apart so one equation is not read as two half-lines.
        for (int i = centres.size() - 1; i > 0; i--) {
            double[] a = centres.get(i - 1), b = centres.get(i);
            double ca = a[0] / a[1], cb = b[0] / b[1];
            if (Math.abs(cb - ca) < 1.15 * med) {
                a[0] += b[0];
                a[1] += b[1];
                a[2] = Math.max(a[2], b[2]);
                centres.remove(i);
            }
        }

        List<List<Comp>> lines = new ArrayList<>();
        for (int i = 0; i < centres.size(); i++) lines.add(new ArrayList<>());
        double maxDist = 1.6 * med;
        for (Comp c : comps) {
            if (c.h() > 2.4f * med || c.w() > 3.2f * med) continue;
            int bestI = -1;
            double bestD = Double.MAX_VALUE;
            for (int i = 0; i < centres.size(); i++) {
                double d = Math.abs(centreY(c) - centres.get(i)[0] / centres.get(i)[1]);
                if (d < bestD) { bestD = d; bestI = i; }
            }
            if (bestI >= 0 && bestD <= maxDist) lines.get(bestI).add(c);
        }

        // A short fragment written off-centre (a trailing "15") belongs to the
        // equation right next to it, not to a line of its own.
        boolean joined = true;
        while (joined) {
            joined = false;
            outer:
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isEmpty()) continue;
                for (int j = i + 1; j < lines.size(); j++) {
                    if (lines.get(j).isEmpty()) continue;
                    double d = Math.abs(centreOf(lines.get(i)) - centreOf(lines.get(j)));
                    if (d < 1.9 * med && (lines.get(i).size() < 4 || lines.get(j).size() < 4)) {
                        lines.get(i).addAll(lines.get(j));
                        lines.get(j).clear();
                        joined = true;
                        break outer;
                    }
                }
            }
        }

        DigitNet net = DigitNet.get(context);
        for (List<Comp> line : lines) {
            if (out.size() >= MAX_LINES) break;
            if (line.size() < 2) continue;
            String s = bestRun(line, mask, med, net);
            if (s != null && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static double centreY(Comp c) { return (c.y0 + c.y1) / 2.0; }

    private static double centreOf(List<Comp> line) {
        double sum = 0;
        for (Comp c : line) sum += centreY(c);
        return line.isEmpty() ? 0 : sum / line.size();
    }

    // ------------------------------------------------------------- line assembly

    /**
     * Splits a line into runs at wide spaces and returns the run that looks
     * most like one complete equation. This keeps a handwritten answer away
     * from a table or a second column of working.
     */
    private static String bestRun(List<Comp> line, Mask mask, int med, DigitNet net) {
        line.sort((a, b) -> Integer.compare(a.x0, b.x0));

        List<List<Comp>> runs = new ArrayList<>();
        List<Comp> cur = new ArrayList<>();
        int gapLimit = Math.max(18, Math.round(med * 1.6f));
        for (Comp c : line) {
            if (!cur.isEmpty() && c.x0 - cur.get(cur.size() - 1).x1 > gapLimit) {
                runs.add(cur);
                cur = new ArrayList<>();
            }
            cur.add(c);
        }
        if (!cur.isEmpty()) runs.add(cur);

        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (List<Comp> run : runs) {
            if (run.size() < 2) continue;
            int runMed = medianHeight(run);
            if (runMed < 6) runMed = med;
            StringBuilder sb = new StringBuilder();
            int digits = 0, relations = 0;
            List<String> tokens = new ArrayList<>();
            for (Comp c : run) {
                String t = classify(c, mask.ink, mask.w, runMed, net);
                Log.i("HandOCR", "med=" + runMed + " [" + c.x0 + "," + c.y0 + "] "
                        + c.w() + "x" + c.h() + " " + debugFeatures(c, mask.ink, mask.w, runMed, net)
                        + " -> " + t);
                tokens.add(t);
            }
            for (int i = 0; i < run.size(); i++) {
                String t = tokens.get(i);
                if (t == null || t.isEmpty()) continue;
                // A "5" is often written as a stroke plus a separate bowl, so the
                // reader sees a "1" and a small "0". Put them back together.
                int next = i + 1;
                while (next < run.size()
                        && (tokens.get(next) == null || tokens.get(next).isEmpty())) next++;
                if (t.equals("1") && next < run.size() && "0".equals(tokens.get(next))
                        && detachedFive(run.get(i), run.get(next), runMed)) {
                    // The "1" is a real digit; the small round glyph is the body
                    // of a 5 whose top stroke did not reach it.
                    sb.append("15");
                    digits += 2;
                    i = next;
                    continue;
                }
                sb.append(t);
                if (t.equals("<=") || t.equals(">=") || t.equals("=")) relations++;
                else if (t.matches("[0-9]")) digits++;
            }
            String candidate = tidy(sb.toString());
            if (!plausible(candidate)) continue;

            // One relation is what an equation has; two or more means the run
            // swallowed a neighbouring sum and is usually the worse reading.
            int score = Math.min(24, digits * 4) + Math.min(18, candidate.length());
            score += Math.min(16, Math.round((run.get(run.size() - 1).x1
                    - run.get(0).x0) / (float) Math.max(1, runMed)));
            if (relations == 1) score += 80;
            else if (relations > 1) score -= 40;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * True when a full-size "1" has a small round glyph right next to it: the
     * two strokes of a handwritten 5 that did not touch.
     */
    private static boolean detachedFive(Comp one, Comp tail, int med) {
        int h1 = one.h(), ht = tail.h();
        if (h1 < 1.1f * med) return false;
        if (ht > 0.75f * h1 || ht < 0.35f * h1) return false;
        int gap = tail.x0 - one.x1;
        if (gap < 0 || gap > 0.6f * h1) return false;
        if (tail.w() < 0.60f * ht) return false;
        int dy = Math.abs((tail.y0 + tail.y1) / 2 - (one.y0 + one.y1) / 2);
        return dy < 0.35f * h1;
    }

    /** Drops junk at the edges and rejects text that is clearly not maths. */
    private static String tidy(String raw) {
        String s = raw;
        s = s.replaceAll("^[+\\-*/=<>]+", "");
        s = s.replaceAll("[+\\-*/=<>]+$", "");
        // Collapse repeated equals but keep the equals of "<=" and ">=".
        s = s.replaceAll("<=+", "<=");
        s = s.replaceAll(">=+", ">=");
        s = s.replaceAll("=+", "=");
        // A stray "=" next to a chevron is an underline, not a second relation.
        s = s.replaceAll("=(<|>)", "$1");
        return s;
    }

    private static boolean plausible(String s) {
        if (s == null || s.length() < 2 || s.length() > 28) return false;
        if (!s.matches(".*[0-9].*")) return false;
        boolean relation = s.indexOf('=') >= 0 || s.contains("<") || s.contains(">");
        boolean operator = s.matches(".*[+\\-*/^<>].*");
        if (!relation && !operator) return false;
        // At most one unknown letter slip-through; x is the only expected one.
        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetter(ch) && ch != 'x' && ch != 'X') letters++;
        }
        return letters <= 1;
    }

    // -------------------------------------------------------------- classification

    /** Temporary diagnostics for the handwriting reader. */
    private static String debugFeatures(Comp c, boolean[] ink, int stride, int med, DigitNet net) {
        int w = c.w(), h = c.h();
        int area = 0;
        for (int y = c.y0; y < c.y1; y++)
            for (int x = c.x0; x < c.x1; x++) if (ink[y * stride + x]) area++;
        float fill = area / (float) (w * h);
        int longRows = 0, longCols = 0;
        for (int y = 0; y < h; y++) if (rowRun(c, ink, stride, y) > 0.55f * w) longRows++;
        for (int x = 0; x < w; x++) if (colRun(c, ink, stride, x) > 0.40f * h) longCols++;
        float dsum = (float) (diagonal(c, ink, stride, true) + diagonal(c, ink, stride, false));
        float[] q = quadrants(c, ink, stride);
        int[] p = net == null ? null : net.predict(ink, stride, c.x0, c.y0, w, h);
        return String.format(java.util.Locale.US,
                "fill=%.2f hole=%d lr=%d lc=%d d=%.2f q=%.2f/%.2f net=%s",
                fill, holes(c, ink, stride), longRows, longCols, dsum, q[0], q[1],
                p == null ? "null" : (p[0] + "@" + p[1]));
    }

    private static String classify(Comp c, boolean[] ink, int stride, int med, DigitNet net) {
        int w = c.w(), h = c.h();
        if (c.area < 60 || h < med * 0.30 || w > 3.2 * h) return null;

        int area = 0;
        for (int y = c.y0; y < c.y1; y++) {
            int row = y * stride;
            for (int x = c.x0; x < c.x1; x++) if (ink[row + x]) area++;
        }
        float fill = area / (float) (w * h);

        // Punctuation (a comma, a full stop) has no meaning in an equation.
        if (h < 0.85f * med && fill > 0.38f && w > 0.36f * h && w < 0.75f * h) return null;

        int holes = holes(c, ink, stride);
        int longRows = 0;
        int clusters = 0;
        boolean prevBar = false;
        int bottomRun = 0;
        for (int y = 0; y < h; y++) {
            int run = rowRun(c, ink, stride, y);
            if (run > 0.55f * w) longRows++;
            boolean bar = run > 0.50f * w;
            if (bar && !prevBar) clusters++;
            prevBar = bar;
            if (y > h * 0.6f && run > bottomRun) bottomRun = run;
        }
        int longCols = 0;
        for (int x = 0; x < w; x++) {
            if (colRun(c, ink, stride, x) > 0.40f * h) longCols++;
        }
        double d1 = diagonal(c, ink, stride, true);
        double d2 = diagonal(c, ink, stride, false);
        double dsum = d1 + d2;

        float[] q = quadrants(c, ink, stride);
        float ul = q[0], ur = q[1], ll = q[2], lr = q[3];

        // ≤ / ≥ : wide, hollow, asymmetric upper half.
        if (holes <= 1 && fill < 0.20f && w > 0.62f * h && h > med * 0.62f && dsum < 0.50) {
            if (ur < 0.11f && ul > 0.15f) return "<=";
            if (ul <= 0.115f && ur > 0.15f) return ">=";
        }
        // Some writers draw the chevron as a "V" and put the bar above it.
        // The bar's height inside the glyph then carries the direction.
        if (holes <= 1 && fill < 0.20f && w > 0.85f * h && h > med * 0.62f
                && longCols == 0 && longRows >= 2) {
            int barY = -1, barRun = 0;
            for (int y = 0; y < h; y++) {
                int run = rowRun(c, ink, stride, y);
                if (run > barRun) { barRun = run; barY = y; }
            }
            if (barRun > 0.45f * w) {
                float frac = barY / (float) h;
                if (frac < 0.28f) return ">=";
                if (frac > 0.62f) return "<=";
            }
        }
        // + : wide cross with a real horizontal stroke and a real vertical one,
        // both crossing near the middle of the glyph (a "2" also has a long base).
        if (holes == 0 && fill < 0.30f && w > 0.90f * h && dsum < 0.60
                && crossIsCentred(c, ink, stride)) {
            return "+";
        }
        // = : two stacked bars, no long diagonal.
        if (holes == 0 && clusters >= 2 && dsum < 0.45 && w > med * 0.45f && h < med * 0.60f) {
            return "=";
        }
        // -
        if (holes == 0 && clusters == 1 && h < med * 0.28f && w > med * 0.32f) {
            return "-";
        }
        // x : two diagonals, no horizontal bar, not a thin stroke.
        if (holes == 0 && dsum > 0.45 && longRows == 0 && w > h * 0.38f && h > med * 0.45f) {
            return "x";
        }
        // 1 : upright narrow stroke.
        if (w < h * 0.42f && h > med * 0.45f && longCols >= 1) {
            return "1";
        }
        // Two digits written so close that their strokes touch ("20", "12").
        if (h > 0.45f * med && w > 0.85f * h && net != null) {
            int cut = splitColumn(c, ink, stride);
            if (cut > 0) {
                Comp left = new Comp(c.x0, c.y0, c.x0 + cut, c.y1, 0);
                Comp right = new Comp(c.x0 + cut + 1, c.y0, c.x1, c.y1, 0);
                String a = digitOnly(left, ink, stride, med, net);
                String b = digitOnly(right, ink, stride, med, net);
                if (a != null && b != null) return a + b;
            }
        }

        if (net == null) return null;
        int[] p = net.predict(ink, stride, c.x0, c.y0, w, h);
        if (p == null) return null;
        int digit = p[0];
        float confidence = p[1] / 1000f;

        // Shape corrections for the confusions camera photos produce most.
        if (holes >= 2) digit = 8;
        else if (holes == 1 && w >= h * 0.95f && fill > 0.40f) digit = 0;
        else if (digit == 7 && bottomRun > w * 0.28f && w > 8) digit = 2;
        else if (digit == 1 && w > h * 0.80f) digit = 2;
        // A quickly written x is a wide glyph with diagonals; the network reads
        // its left stroke as a "1".
        else if (digit == 1 && w > 0.45f * h && dsum > 0.32f && longCols <= 2) return "x";
        // An "8" without closed counters is how a quickly written x looks.
        else if (digit == 8 && holes == 0 && longRows == 0 && w > h * 0.38f) return "x";

        if (confidence < 0.22f) return null;
        return String.valueOf(digit);
    }

    // ---------------------------------------------------------------- features

    private static int holes(Comp c, boolean[] ink, int stride) {
        int w = c.w(), h = c.h();
        if (w < 3 || h < 3) return 0;
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int holes = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = y * w + x;
                if (seen[idx] || isInk(c, ink, stride, x, y)) continue;
                int top = 0;
                stack[top++] = idx;
                seen[idx] = true;
                boolean border = false;
                int count = 0;
                while (top > 0) {
                    int cur = stack[--top];
                    int cy = cur / w, cx = cur % w;
                    count++;
                    if (cx == 0 || cy == 0 || cx == w - 1 || cy == h - 1) border = true;
                    for (int k = 0; k < 4; k++) {
                        int nx = cx + (k == 0 ? 1 : k == 1 ? -1 : 0);
                        int ny = cy + (k == 2 ? 1 : k == 3 ? -1 : 0);
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                        int ni = ny * w + nx;
                        if (seen[ni] || isInk(c, ink, stride, nx, ny)) continue;
                        seen[ni] = true;
                        stack[top++] = ni;
                    }
                }
                if (!border && count > 6) holes++;
            }
        }
        return holes;
    }

    private static float[] quadrants(Comp c, boolean[] ink, int stride) {
        int w = c.w(), h = c.h();
        int[] n = new int[4];
        int[] total = new int[4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int q = (y < h / 2 ? 0 : 2) + (x < w / 2 ? 0 : 1);
                total[q]++;
                if (isInk(c, ink, stride, x, y)) n[q]++;
            }
        }
        float[] out = new float[4];
        for (int i = 0; i < 4; i++) out[i] = total[i] == 0 ? 0 : n[i] / (float) total[i];
        return out;
    }

    private static boolean isInk(Comp c, boolean[] ink, int stride, int x, int y) {
        return ink[(c.y0 + y) * stride + c.x0 + x];
    }

    private static int rowRun(Comp c, boolean[] ink, int stride, int y) {
        int run = 0, best = 0;
        int row = (c.y0 + y) * stride;
        for (int x = c.x0; x < c.x1; x++) {
            if (ink[row + x]) {
                run++;
                if (run > best) best = run;
            } else run = 0;
        }
        return best;
    }

    private static int colRun(Comp c, boolean[] ink, int stride, int x) {
        int run = 0, best = 0;
        int col = c.x0 + x;
        for (int y = c.y0; y < c.y1; y++) {
            if (ink[y * stride + col]) {
                run++;
                if (run > best) best = run;
            } else run = 0;
        }
        return best;
    }

    private static double diagonal(Comp c, boolean[] ink, int stride, boolean main) {
        int n = 48;
        int best = 0, run = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            int x = Math.round(t * (c.w() - 1));
            int y = Math.round((main ? t : 1 - t) * (c.h() - 1));
            run = isInk(c, ink, stride, x, y) ? run + 1 : 0;
            if (run > best) best = run;
        }
        return best / (double) n;
    }

    /**
     * True when a full-width bar crosses a full-height stroke near the middle.
     * Hand-drawn strokes are slanted, so both are measured inside a small band
     * instead of along one exact row or column.
     */
    private static boolean crossIsCentred(Comp c, boolean[] ink, int stride) {
        int w = c.w(), h = c.h();
        int barY = 0, barRun = 0;
        for (int y = 0; y < h; y++) {
            int run = rowRun(c, ink, stride, y);
            if (run > barRun) { barRun = run; barY = y; }
        }
        if (barRun < 0.60f * w) return false;
        if (barY < 0.22f * h || barY > 0.72f * h) return false;
        int barX = 0, barCol = 0;
        for (int x = 0; x < w; x++) {
            int run = colRun(c, ink, stride, x);
            if (run > barCol) { barCol = run; barX = x; }
        }
        // A "4" puts its upright near the right edge; a plus has it mid-glyph.
        if (barX < 0.35f * w || barX > 0.72f * w) return false;

        int bw = Math.max(2, Math.round(w * 0.16f));
        int top = h, bottom = -1;
        for (int x = Math.max(0, barX - bw); x <= Math.min(w - 1, barX + bw); x++) {
            for (int y = 0; y < h; y++) {
                if (isInk(c, ink, stride, x, y)) {
                    if (y < top) top = y;
                    if (y > bottom) bottom = y;
                }
            }
        }
        if (bottom < 0) return false;
        int bh = Math.max(2, Math.round(h * 0.16f));
        int left = w, right = -1;
        for (int y = Math.max(0, barY - bh); y <= Math.min(h - 1, barY + bh); y++) {
            for (int x = 0; x < w; x++) {
                if (isInk(c, ink, stride, x, y)) {
                    if (x < left) left = x;
                    if (x > right) right = x;
                }
            }
        }
        if (right < 0) return false;
        boolean vertical = top < barY - 0.18f * h && bottom > barY + 0.18f * h;
        boolean horizontal = left < barX - 0.20f * w && right > barX + 0.20f * w;
        return vertical && horizontal;
    }

    /** Column with the least ink in the middle of a glyph, or -1 if none is clean. */
    private static int splitColumn(Comp c, boolean[] ink, int stride) {
        int w = c.w(), h = c.h();
        int from = Math.max(2, Math.round(w * 0.28f));
        int to = Math.min(w - 3, Math.round(w * 0.72f));
        int best = -1, bestCount = Integer.MAX_VALUE;
        for (int x = from; x <= to; x++) {
            int count = 0;
            for (int y = 0; y < h; y++) if (isInk(c, ink, stride, x, y)) count++;
            if (count < bestCount) { bestCount = count; best = x; }
        }
        // A real gap means an empty column; a thin waist inside one glyph does not.
        if (best < 0 || bestCount > 0) return -1;
        return best;
    }

    /** Digit reader used on the two halves of a touching pair such as "20". */
    private static String digitOnly(Comp c, boolean[] ink, int stride, int med, DigitNet net) {
        while (c.x0 < c.x1 && emptyCol(c, ink, stride, c.x0)) c.x0++;
        while (c.x1 > c.x0 && emptyCol(c, ink, stride, c.x1 - 1)) c.x1--;
        int w = c.w(), h = c.h();
        if (net == null || w < 4 || h < 0.35f * med) return null;
        int[] p = net.predict(ink, stride, c.x0, c.y0, w, h);
        if (p == null || p[1] < 300) return null;
        int digit = p[0];
        int holes = holes(c, ink, stride);
        int area = 0;
        for (int y = c.y0; y < c.y1; y++) {
            int row = y * stride;
            for (int x = c.x0; x < c.x1; x++) if (ink[row + x]) area++;
        }
        float fill = area / (float) (w * h);
        if (fill < 0.08f) return null;
        int bottomRun = 0;
        for (int y = Math.round(h * 0.6f); y < h; y++) {
            int run = rowRun(c, ink, stride, y);
            if (run > bottomRun) bottomRun = run;
        }
        if (holes >= 2) digit = 8;
        else if (holes == 1 && w >= h * 0.55f) digit = 0;
        else if (digit == 7 && bottomRun > w * 0.28f && w > 8) digit = 2;
        else if (digit == 4 && fill > 0.35f) digit = 9;
        return String.valueOf(digit);
    }

    private static boolean emptyCol(Comp c, boolean[] ink, int stride, int xAbs) {
        for (int y = c.y0; y < c.y1; y++) if (ink[y * stride + xAbs]) return false;
        return true;
    }

    // ------------------------------------------------------------- segmentation

    private static final class Mask {
        final int w, h;
        final boolean[] ink;

        Mask(int w, int h, boolean[] ink) {
            this.w = w; this.h = h; this.ink = ink;
        }

        static Mask read(Bitmap bmp) {
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] px = new int[w * h];
            bmp.getPixels(px, 0, w, 0, 0, w, h);
            boolean[] ink = new boolean[w * h];
            for (int i = 0; i < px.length; i++) {
                int p = px[i];
                int lum = (((p >> 16) & 0xFF) + ((p >> 8) & 0xFF) + (p & 0xFF)) / 3;
                ink[i] = lum < 128;
            }
            return new Mask(w, h, ink);
        }
    }

    private static final class Comp {
        int x0, y0, x1, y1, area;
        Comp(int x0, int y0, int x1, int y1, int area) {
            this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1; this.area = area;
        }
        int w() { return x1 - x0; }
        int h() { return y1 - y0; }
    }

    private static List<Comp> components(Mask mask) {
        int w = mask.w, h = mask.h;
        int[] lab = new int[w * h];
        int[] stack = new int[w * h];
        List<Comp> out = new ArrayList<>();
        int next = 0;

        for (int sy = 0; sy < h; sy++) {
            for (int sx = 0; sx < w; sx++) {
                int start = sy * w + sx;
                if (!mask.ink[start] || lab[start] != 0) continue;
                next++;
                int top = 0;
                stack[top++] = start;
                lab[start] = next;
                int minX = sx, maxX = sx, minY = sy, maxY = sy, area = 0;
                while (top > 0) {
                    int cur = stack[--top];
                    int cy = cur / w, cx = cur % w;
                    area++;
                    if (cx < minX) minX = cx;
                    if (cx > maxX) maxX = cx;
                    if (cy < minY) minY = cy;
                    if (cy > maxY) maxY = cy;
                    if (cx > 0) {
                        int n = cur - 1;
                        if (mask.ink[n] && lab[n] == 0) { lab[n] = next; stack[top++] = n; }
                    }
                    if (cx + 1 < w) {
                        int n = cur + 1;
                        if (mask.ink[n] && lab[n] == 0) { lab[n] = next; stack[top++] = n; }
                    }
                    if (cy > 0) {
                        int n = cur - w;
                        if (mask.ink[n] && lab[n] == 0) { lab[n] = next; stack[top++] = n; }
                    }
                    if (cy + 1 < h) {
                        int n = cur + w;
                        if (mask.ink[n] && lab[n] == 0) { lab[n] = next; stack[top++] = n; }
                    }
                }
                out.add(new Comp(minX, minY, maxX + 1, maxY + 1, area));
            }
        }
        return out;
    }

    private static List<Comp> filter(List<Comp> comps, int w, int h) {
        List<Comp> out = new ArrayList<>();
        for (Comp c : comps) {
            int cw = c.w(), ch = c.h();
            if (c.area < 25) continue;
            if (ch > h * 0.30 && ch > cw * 4) continue;      // table border / rule
            if (cw > w * 0.30 && cw > ch * 4) continue;      // horizontal rule
            if (ch > h * 0.75) continue;
            out.add(c);
        }
        return out;
    }

    private static List<Comp> merge(List<Comp> src, int med) {
        List<Comp> comps = new ArrayList<>(src);
        boolean changed = true;
        while (changed && comps.size() > 1) {
            changed = false;
            outer:
            for (int i = 0; i < comps.size(); i++) {
                for (int j = i + 1; j < comps.size(); j++) {
                    Comp a = comps.get(i), b = comps.get(j);
                    int xov = Math.min(a.x1, b.x1) - Math.max(a.x0, b.x0);
                    int yov = Math.min(a.y1, b.y1) - Math.max(a.y0, b.y0);
                    boolean stacked = xov > 0.45 * Math.min(a.w(), b.w())
                            && b.y0 - a.y1 > -0.20 * med && b.y0 - a.y1 < 0.38 * med;
                    boolean side = xov <= 0 && b.x0 - a.x1 > -0.05 * med
                            && b.x0 - a.x1 < 0.16 * med
                            && yov > 0.55 * Math.min(a.h(), b.h());
                    // Two strokes of the same handwritten glyph often overlap
                    // almost completely (an X, a crossed 4) instead of sitting
                    // side by side.
                    boolean overlap = xov > 0.45 * Math.min(a.w(), b.w())
                            && yov > 0.45 * Math.min(a.h(), b.h());
                    int combined = Math.max(a.x1, b.x1) - Math.min(a.x0, b.x0);
                    if ((stacked || side || overlap) && combined <= 2.6 * med) {
                        a.x0 = Math.min(a.x0, b.x0);
                        a.y0 = Math.min(a.y0, b.y0);
                        a.x1 = Math.max(a.x1, b.x1);
                        a.y1 = Math.max(a.y1, b.y1);
                        a.area += b.area;
                        comps.remove(j);
                        changed = true;
                        break outer;
                    }
                }
            }
        }
        return comps;
    }

    private static int medianHeight(List<Comp> comps) {
        if (comps.isEmpty()) return 0;
        int[] hs = new int[comps.size()];
        for (int i = 0; i < hs.length; i++) hs[i] = comps.get(i).h();
        Arrays.sort(hs);
        return hs[hs.length / 2];
    }
}
