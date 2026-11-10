package com.munhana.graph.math;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns raw OCR text into equation strings. Pure Java so it can be unit tested
 * without an Android device.
 *
 * <p>Recognition is allowed to fail: a line that looks like maths but cannot be
 * plotted is still returned, so the user can keep it, edit it or copy it. That
 * is much friendlier than silently dropping almost every handwritten line.</p>
 */
public final class EquationText {

    private static final Pattern ARABIC = Pattern.compile("[\\u0600-\\u06FF]");

    private EquationText() { }

    public static List<String> fromOcrText(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        for (String line : text.split("\\r?\\n")) {
            for (String part : line.split("[;؛،]")) {
                String fixed = repair(part);
                if (fixed == null) continue;
                if (seen.add(fixed)) out.add(fixed);
            }
        }
        return out;
    }

    /**
     * Cleans a raw line and keeps everything that could be mathematics.
     * Returns an empty string when the line is prose or pure noise.
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String s = Expression.normalize(raw);
        s = s.replace("\u00A0", "").replace(" ", "");
        if (s.isEmpty()) return "";

        // Tesseract 3 often reads the caret as a quote or an "A", and a
        // superscript 2 as "Z". Undo those before anything gets discarded.
        s = s.replace('"', '^').replace('\u201C', '^').replace('\u201D', '^');
        s = s.replace('\u00B4', '^').replace('\u2018', '^').replace('\u2019', '^');
        s = s.replaceAll("(?<=[0-9a-zA-Z)])[Aa](?=[0-9Zz(])", "^");
        s = s.replaceAll("(?<=\\^)[Zz]", "2");

        int arabic = 0;
        for (int i = 0; i < s.length(); i++) {
            if (ARABIC.matcher(String.valueOf(s.charAt(i))).matches()) arabic++;
        }
        if (arabic > 0 && arabic > s.length() * 0.2) return "";

        s = s.replaceAll("[^0-9a-zA-Z+\\-*/^().,=<>!|_\\[\\]{}]", "");
        if (s.isEmpty()) return "";

        s = s.replaceAll("(?<=[0-9])[Oo](?=[0-9])", "0");
        s = s.replaceAll("(?<![a-zA-Z0-9])[Oo](?![a-zA-Z])", "0");
        s = s.replace("**", "^");
        s = s.replace("==", "=");
        s = s.replaceAll("(?<=[0-9])[lI](?=[0-9])", "1");
        s = s.replaceAll("(?<=[0-9])[Ss](?=[0-9])", "5");
        s = s.replaceAll("(?<=[0-9])[Bb](?=[0-9])", "8");
        s = s.replaceAll("(?<=[0-9])[Zz](?=[0-9])", "2");
        s = s.replaceAll("(?<=[0-9])[gG](?=[0-9])", "9");
        s = s.replaceAll("(?<=[0-9a-zA-Z)])[Zz](?=[0-9)])", "2");

        s = s.replaceAll("^[.,:)]+", "");
        s = s.replaceAll("[.,:]+$", "");
        if (s.isEmpty() || s.length() > 70) return "";
        return s;
    }

    /** True when a cleaned line is worth keeping in the equation list. */
    public static boolean looksLikeMath(String s) {
        if (s == null || s.length() < 2) return false;
        boolean hasEquals = s.indexOf('=') > 0 && s.indexOf('=') < s.length() - 1;
        boolean hasDigit = s.matches(".*[0-9].*");
        boolean hasOperator = s.matches(".*[+\\-*/^<>].*");

        // Count real letters after allowing the function library and constants.
        String lettersOnly = s.replaceAll("[^a-zA-Z]", "");
        lettersOnly = lettersOnly.replaceAll("(?i)(sin|cos|tan|log|sqrt|abs|exp|ln|pi|tau|phi|nderiv|integral|e)", "");
        int letters = lettersOnly.length();

        if (!hasEquals && !hasOperator) return false;
        if (!hasEquals && letters > 2) return false;
        if (letters > 6 && !hasEquals) return false;
        if (!hasEquals && !hasDigit && !s.matches(".*[xyXYtT].*")) return false;
        return true;
    }

    /**
     * Formats a stored equation the way it would be written by hand: proper
     * relation glyphs, adjacency instead of "*", subscripts for X1/X2 and
     * superscripts for simple powers. The stored text stays ASCII so parsing
     * and editing are unaffected.
     */
    public static String pretty(String raw) {
        return pretty(raw, true);
    }

    public static String pretty(String raw, boolean subscripts) {
        if (raw == null || raw.isEmpty()) return "";
        String s = Expression.normalize(raw).replaceAll("\\s+", "");
        s = s.replace("<=", "\u2264").replace(">=", "\u2265").replace("!=", "\u2260");

        // Juxtaposition reads like real algebra: 2*x -> 2x, 3*(x+1) -> 3(x+1).
        s = s.replaceAll("(?<=[0-9A-Za-z)])[*\u00B7](?=[A-Za-z(])", "");
        s = s.replace("*", "\u00D7");

        s = s.replaceAll("\\^\\s*([0-9]{1,2})", "\u0001$1\u0002");
        s = toScript(s, '\u0001', '\u0002', true);

        if (subscripts) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                out.append(c);
                if (Character.isLetter(c) && i + 1 < s.length()
                        && Character.isDigit(s.charAt(i + 1))
                        && (i == 0 || s.charAt(i - 1) != '^')
                        && !(c == 'l' && s.startsWith("log", i))) {
                    int j = i + 1;
                    while (j < s.length() && Character.isDigit(s.charAt(j)) && j - i <= 2) j++;
                    for (int k = i + 1; k < j; k++) {
                        out.append(sub(s.charAt(k)));
                    }
                    i = j - 1;
                }
            }
            s = out.toString();
        }

        s = s.replaceAll("(?<=[0-9A-Za-z)\u2080-\u2089\u2070-\u2079]) ?([+\\-=\u2264\u2265\u2260])", " $1 ");
        s = s.replaceAll(" +", " ").trim();
        return s;
    }

    private static char sub(char d) {
        return "₀₁₂₃₄₅₆₇₈₉".charAt(Math.max(0, Math.min(9, d - '0')));
    }

    private static char sup(char d) {
        return "⁰¹²³⁴⁵⁶⁷⁸⁹".charAt(Math.max(0, Math.min(9, d - '0')));
    }

    /** Replaces digits between the given markers with superscript glyphs. */
    private static String toScript(String s, char open, char close, boolean upper) {
        StringBuilder out = new StringBuilder();
        boolean on = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) { on = true; continue; }
            if (c == close) { on = false; continue; }
            if (on && Character.isDigit(c)) out.append(upper ? sup(c) : sub(c));
            else out.append(c);
        }
        return out.toString();
    }

    /**
     * Returns a usable equation string, or null when the text is clearly not
     * mathematics. Parsing is attempted, but a parse failure no longer throws
     * the line away.
     */
    public static String repair(String raw) {
        String s = sanitize(raw);
        if (s.isEmpty() || !looksLikeMath(s)) return null;
        try {
            return Equation.parse(s).text;
        } catch (Exception e) {
            String t = s.replaceAll("[+\\-*/^=<>]+$", "");
            if (t.length() > 2 && !t.equals(s)) {
                try {
                    return Equation.parse(t).text;
                } catch (Exception ignored) { }
            }
            return s;
        }
    }
}
