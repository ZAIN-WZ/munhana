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

        // Function names become the symbols they stand for: no "sqrt(" or
        // "floor(" ever reaches the screen.
        s = call(s, "sqrt", "\u221A(", ")");
        s = call(s, "cbrt", "\u221B(", ")");
        s = call(s, "abs", "|", "|");
        s = call(s, "floor", "\u230A", "\u230B");
        s = call(s, "ceil", "\u2308", "\u2309");
        s = s.replace("log10(", "log\u2081\u2080(").replace("log2(", "log\u2082(");
        s = s.replace("nthroot(", "\u207F\u221A(");
        s = s.replace("nderiv(", "d/dx(");
        s = s.replace("integral(", "\u222B(");
        s = s.replace("sign(", "sgn(");

        // Constants.
        s = s.replaceAll("(?<![a-z])pi", "\u03C0");
        s = s.replaceAll("(?<![a-z])tau", "\u03C4");
        s = s.replaceAll("(?<![a-z])phi", "\u03C6");

        // Juxtaposition reads like real algebra: 2*x -> 2x, 3*(x+1) -> 3(x+1).
        s = s.replaceAll("(?<=[0-9A-Za-z)\\u2080-\\u2089\\u03C0\\u03C4\\u03C6])[*\\u00B7](?=[A-Za-z(\\u03C0\\u03C4\\u03C6\\u221A\\u221B])", "");
        s = s.replace("*", "\u00D7");

        // Powers: digits and simple variable exponents become superscripts.
        s = superscripts(s);

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

        s = s.replaceAll("(?<=[0-9A-Za-z)\u2080-\u2089\u00B2\u00B3\u2070-\u2079]) ?([+\\-=\u2264\u2265\u2260])", " $1 ");
        s = s.replaceAll(" +", " ").trim();
        return s;
    }

    /** Replaces name(args) with open+args+close, matching nested brackets. */
    private static String call(String s, String name, String open, String closeSymbol) {
        String needle = name + "(";
        int from = 0;
        while (true) {
            int at = s.indexOf(needle, from);
            if (at < 0) return s;
            int depth = 0, end = -1;
            for (int i = at + needle.length() - 1; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(') depth++;
                else if (c == ')') {
                    depth--;
                    if (depth == 0) { end = i; break; }
                }
            }
            if (end < 0) {
                s = s.substring(0, at) + open + s.substring(at + needle.length());
                return s;
            }
            String inner = s.substring(at + needle.length(), end);
            String replacement = open + inner + closeSymbol;
            s = s.substring(0, at) + replacement + s.substring(end + 1);
            from = at + replacement.length();
        }
    }

    private static String superscripts(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '^') { out.append(c); continue; }
            int j = i + 1;
            boolean paren = j < s.length() && s.charAt(j) == '(';
            if (paren) j++;
            boolean negative = j < s.length() && s.charAt(j) == '-';
            if (negative) j++;
            int digits = j;
            while (digits < s.length() && Character.isDigit(s.charAt(digits)) && digits - j < 3) digits++;
            // A numeric exponent always becomes a superscript, even when the
            // next character is a variable (x^2y means x squared times y).
            boolean closed = true;
            if (paren) closed = digits < s.length() && s.charAt(digits) == ')';
            if (digits > j && closed) {
                if (negative) out.append('\u207B');
                for (int k = j; k < digits; k++) out.append(sup(s.charAt(k)));
                i = paren ? digits : digits - 1;
                continue;
            }
            if (!paren && j < s.length()) {
                char v = s.charAt(j);
                char up = superLetter(v);
                if (up != 0) { out.append(up); i = j; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static char superLetter(char c) {
        switch (c) {
            case 'x': return '\u02E3';
            case 'y': return '\u02B8';
            case 'n': return '\u207F';
            case 'a': return '\u1D43';
            case 'b': return '\u1D47';
            case 'c': return '\u1D9C';
            case 'd': return '\u1D48';
            case 'e': return '\u1D49';
            case 'g': return '\u1D4D';
            case 'h': return '\u02B0';
            case 'i': return '\u2071';
            case 'j': return '\u02B2';
            case 'k': return '\u1D4F';
            case 'l': return '\u02E1';
            case 'm': return '\u1D50';
            case 'o': return '\u1D52';
            case 'p': return '\u1D56';
            case 'r': return '\u02B3';
            case 's': return '\u02E2';
            case 't': return '\u1D57';
            case 'u': return '\u1D58';
            case 'v': return '\u1D5B';
            case 'w': return '\u02B7';
            case 'z': return '\u1DBB';
            default: return 0;
        }
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
