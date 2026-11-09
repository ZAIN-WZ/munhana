package com.munhana.graph.math;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns raw OCR text into plottable equations. Pure Java so it can be unit
 * tested without an Android device.
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

    /** Returns a plottable equation string or null when the text is not maths. */
    public static String repair(String raw) {
        String s = Expression.normalize(raw);
        s = s.replace(" ", "").replace("\u00A0", "");
        if (s.isEmpty()) return null;

        // Tesseract 3 frequently reads the caret as a quote or an "A", and a
        // superscript 2 as "Z". Undo those before anything gets discarded.
        s = s.replace('"', '^').replace('\u201C', '^').replace('\u201D', '^');
        s = s.replace('\u00B4', '^').replace('\u2018', '^').replace('\u2019', '^');
        s = s.replaceAll("(?<=[0-9a-zA-Z)])[Aa](?=[0-9Zz(])", "^");
        s = s.replaceAll("(?<=\\^)[Zz]", "2");

        int arabic = 0;
        for (int i = 0; i < s.length(); i++) {
            if (ARABIC.matcher(String.valueOf(s.charAt(i))).matches()) arabic++;
        }
        if (arabic > 0 && arabic > s.length() * 0.25) return null;

        s = s.replaceAll("[^0-9a-zA-Z+\\-*/^().,=<>!|]", "");
        if (s.isEmpty()) return null;

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
        if (s.isEmpty()) return null;
        if (s.length() < 3 || s.length() > 70) return null;

        boolean hasEquals = s.indexOf('=') > 0 && s.indexOf('=') < s.length() - 1;
        boolean hasDigit = s.matches(".*[0-9].*");
        boolean hasVariable = s.matches(".*[xyXY].*");
        boolean hasOperator = s.matches(".*[+\\-*/^].*");

        if (!hasEquals && !hasDigit && !hasVariable) return null;
        if (!hasEquals && !hasOperator) return null;
        if (!hasEquals && s.replaceAll("[^a-zA-Z]", "").length() > 6) return null;

        try {
            return Equation.parse(s).text;
        } catch (Exception e) {
            String t = s.replaceAll("[+\\-*/^=<>]+$", "");
            if (!t.equals(s) && t.length() > 2) {
                try {
                    return Equation.parse(t).text;
                } catch (Exception ignored) { }
            }
            return null;
        }
    }
}
