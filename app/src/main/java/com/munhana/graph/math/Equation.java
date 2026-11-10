package com.munhana.graph.math;

import java.util.Locale;

/** A parsed relation ready for plotting. */
public class Equation {

    public enum Kind { FUNCTION, VERTICAL, IMPLICIT, INEQUALITY }

    public static final int[] PALETTE = {
            0xFFB388FF, 0xFF80CBC4, 0xFFFFB74D, 0xFFF48FB1,
            0xFF81D4FA, 0xFFA5D6A7, 0xFFFFF176, 0xFFCE93D8,
            0xFF9FA8DA, 0xFFFFAB91
    };

    public String text;
    public Kind kind = Kind.FUNCTION;
    public String relation = "=";
    public int color = PALETTE[0];
    public boolean visible = true;
    /** False for a line that was kept as written but cannot be drawn. */
    public boolean plottable = true;

    public Expression fn;   // right hand side for y=f(x) / x=c
    public Expression lhs;  // implicit
    public Expression rhs;  // implicit
    public Expression g;    // lhs - rhs (or boundary for inequalities)

    public static Equation parse(String raw) throws Expression.ParseError {
        String src = Expression.normalize(raw).trim();
        src = src.replace(" ", "");
        if (src.isEmpty()) throw new Expression.ParseError("معادلة فارغة");
        while (src.endsWith(".") || src.endsWith(";")) src = src.substring(0, src.length() - 1);
        // drop a leading list marker like "(1)" or "1)" but never a decimal number
        src = src.replaceAll("^\\s*\\(\\s*\\d{1,2}\\s*\\)\\s*", "");
        src = src.replaceAll("^\\s*\\d{1,2}\\s*\\)\\s*", "");
        if (src.isEmpty()) throw new Expression.ParseError("معادلة فارغة");

        int[] rel = findRelation(src);
        Equation eq = new Equation();
        eq.text = src;

        String lhsS, rhsS;
        if (rel[0] < 0) {
            lhsS = "y";
            rhsS = src;
            eq.relation = "=";
        } else {
            lhsS = src.substring(0, rel[0]);
            int opLen = rel[1];
            rhsS = src.substring(rel[0] + opLen);
            eq.relation = src.substring(rel[0], rel[0] + opLen);
        }
        if (lhsS.isEmpty() || rhsS.isEmpty()) throw new Expression.ParseError("معادلة غير مكتملة");

        Expression l = Expression.parse(lhsS);
        Expression r = Expression.parse(rhsS);

        String lNorm = lhsS.toLowerCase(Locale.ROOT);
        boolean lhsIsY = lNorm.equals("y") || lNorm.equals("y1") || lNorm.equals("y2") || lNorm.equals("f(x)");
        boolean lhsIsX = lNorm.equals("x");

        if (eq.relation.equals("=") && lhsIsY) {
            eq.kind = Kind.FUNCTION;
            eq.fn = r;
            eq.lhs = l;
            eq.rhs = r;
        } else if (eq.relation.equals("=") && lhsIsX && !r.usesX() && !r.usesY()) {
            eq.kind = Kind.VERTICAL;
            eq.fn = r;
            eq.lhs = l;
            eq.rhs = r;
        } else if (!eq.relation.equals("=") && lhsIsY) {
            eq.kind = Kind.INEQUALITY;
            eq.fn = r;
            eq.lhs = l;
            eq.rhs = r;
            eq.g = Expression.difference(l, r);
        } else {
            eq.kind = eq.relation.equals("=") ? Kind.IMPLICIT : Kind.INEQUALITY;
            eq.lhs = l;
            eq.rhs = r;
            eq.g = Expression.difference(l, r);
        }
        return eq;
    }

    /** Returns {index, length} of the top level relation, or {-1, 0}. */
    private static int[] findRelation(String s) {
        int depth = 0;
        boolean inAbs = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == '|') inAbs = !inAbs;
            else if (depth == 0 && !inAbs) {
                if (c == '=' && (i + 1 >= s.length() || s.charAt(i + 1) != '=')) {
                    if (i > 0 && (s.charAt(i - 1) == '<' || s.charAt(i - 1) == '>'
                            || s.charAt(i - 1) == '!' || s.charAt(i - 1) == '=')) {
                        return new int[]{i - 1, 2};
                    }
                    return new int[]{i, 1};
                }
                if (c == '<' || c == '>') {
                    int len = (i + 1 < s.length() && s.charAt(i + 1) == '=') ? 2 : 1;
                    return new int[]{i, len};
                }
            }
        }
        return new int[]{-1, 0};
    }

    /**
     * Builds an equation while preserving the user's spelling for display.
     * When the expression cannot be parsed the line is still kept, marked as
     * not plottable, so nothing the scanner read is silently lost.
     */
    public static Equation fromText(String raw) {
        Equation result = new Equation();
        String display = raw == null ? "" : raw.trim();
        String cleaned = EquationText.sanitize(display);
        if (cleaned.isEmpty()) {
            result.text = display;
            result.plottable = false;
            return result;
        }
        try {
            result = parse(cleaned);
            result.text = display;
            result.plottable = true;
        } catch (Exception e) {
            result = new Equation();
            result.text = cleaned;
            result.plottable = false;
        }
        return result;
    }

    public String describe() {
        switch (kind) {
            case FUNCTION: return "y = ";
            case VERTICAL: return "x = ";
            default: return "";
        }
    }

    public Equation copy() {
        Equation e = new Equation();
        e.text = text; e.kind = kind; e.relation = relation; e.color = color; e.visible = visible;
        e.plottable = plottable;
        e.fn = fn; e.lhs = lhs; e.rhs = rhs; e.g = g;
        return e;
    }

    public String serialize() {
        return color + "|" + (visible ? 1 : 0) + "|" + text.replace("\n", " ");
    }

    public static Equation deserialize(String line) {
        try {
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) return null;
            Equation e = fromText(parts[2]);
            e.color = (int) Long.parseLong(parts[0]);
            e.visible = "1".equals(parts[1]);
            return e;
        } catch (Exception ex) {
            return null;
        }
    }
}
