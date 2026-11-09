package com.munhana.graph.math;

import java.util.ArrayList;
import java.util.List;

/**
 * Small recursive-descent parser + evaluator for the function expressions used by
 * the grapher. Supports implicit multiplication, unary minus, right-associative
 * powers, absolute value bars, factorials and the usual function library.
 */
public final class Expression {

    public static class ParseError extends Exception {
        public ParseError(String m) { super(m); }
    }

    // node opcodes
    private static final int CONST = 0, VARX = 1, VARY = 2, ADD = 3, SUB = 4, MUL = 5,
            DIV = 6, POW = 7, NEG = 8, FUNC = 9, FACT = 10, VARNAME = 11, LIST = 12;

    private final int op;
    private final double num;
    private final Expression a, b;
    private final int fn;
    private final List<Expression> items;

    private Expression(int op, double num, Expression a, Expression b, int fn) {
        this.op = op; this.num = num; this.a = a; this.b = b; this.fn = fn;
        this.items = null;
    }

    private Expression(List<Expression> items) {
        this.op = LIST; this.num = 0; this.a = null; this.b = null; this.fn = 0;
        this.items = items;
    }

    private static Expression c(double v) { return new Expression(CONST, v, null, null, 0); }

    /** Builds the implicit curve expression lhs - rhs. */
    public static Expression difference(Expression lhs, Expression rhs) {
        return new Expression(SUB, 0, lhs, rhs, 0);
    }

    public double eval(double x, double y) {
        switch (op) {
            case CONST: return num;
            case VARX: return x;
            case VARY: return y;
            case VARNAME: return Double.NaN;
            case LIST: return Double.NaN;
            case ADD: return a.eval(x, y) + b.eval(x, y);
            case SUB: return a.eval(x, y) - b.eval(x, y);
            case MUL: return a.eval(x, y) * b.eval(x, y);
            case DIV: return a.eval(x, y) / b.eval(x, y);
            case POW: return Math.pow(a.eval(x, y), b.eval(x, y));
            case NEG: return -a.eval(x, y);
            case FACT: {
                double v = a.eval(x, y);
                if (v < 0 || v > 170 || v != Math.floor(v)) return Double.NaN;
                double r = 1;
                for (int i = 2; i <= (int) v; i++) r *= i;
                return r;
            }
            case FUNC: {
                if (fn == 50) {
                    double p = b == null ? x : b.eval(x, y);
                    double h = 1e-5 * Math.max(1, Math.abs(p));
                    return (a.eval(p + h, y) - a.eval(p - h, y)) / (2 * h);
                }
                if (fn == 51) {
                    double lo = b == null ? 0 : b.eval(x, y);
                    return integrate(a, lo, x, y);
                }
                if (fn >= 60 && fn <= 70) {
                    return statistics(fn, a, b, x, y);
                }
                if ((fn == 18 || fn == 19) && a != null && a.op == LIST) {
                    double[] vals = values(a, x, y);
                    if (vals.length == 0) return Double.NaN;
                    double best = vals[0];
                    for (double val : vals) best = fn == 18 ? Math.min(best, val) : Math.max(best, val);
                    return best;
                }
                return apply(fn, a == null ? 0 : a.eval(x, y), b == null ? 0 : b.eval(x, y));
            }
            default: return Double.NaN;
        }
    }

    private static boolean degrees = false;

    /** Switches trigonometric functions between radians (default) and degrees. */
    public static void setDegrees(boolean useDegrees) { degrees = useDegrees; }

    private static double angleIn(double v) { return degrees ? Math.toRadians(v) : v; }
    private static double angleOut(double v) { return degrees ? Math.toDegrees(v) : v; }

    private static double apply(int fn, double v, double w) {
        switch (fn) {
            case 0: return Math.sin(angleIn(v));
            case 1: return Math.cos(angleIn(v));
            case 2: return Math.tan(angleIn(v));
            case 3: return angleOut(Math.asin(v));
            case 4: return angleOut(Math.acos(v));
            case 5: return angleOut(Math.atan(v));
            case 6: return Math.sinh(v);
            case 7: return Math.cosh(v);
            case 8: return Math.tanh(v);
            case 9: return v <= 0 ? Double.NaN : Math.log(v);
            case 10: return v <= 0 ? Double.NaN
                    : (w > 0 && w != 10 ? Math.log(v) / Math.log(w) : Math.log10(v));
            case 11: return v < 0 ? Double.NaN : Math.sqrt(v);
            case 12: return Math.abs(v);
            case 13: return Math.exp(v);
            case 14: return Math.floor(v);
            case 15: return Math.ceil(v);
            case 16: return Math.rint(v);
            case 17: return Math.signum(v);
            case 18: return Math.min(v, w);
            case 19: return Math.max(v, w);
            case 20: return angleOut(Math.atan2(v, w));
            case 21: return Math.hypot(v, w);
            case 22: return Math.log(1 + v);
            case 23: return Math.cbrt(v);
            case 24: return Math.round(v);
            case 25: { double s = Math.sin(angleIn(v)); return s == 0 ? Double.NaN : 1 / s; }
            case 26: { double c = Math.cos(angleIn(v)); return c == 0 ? Double.NaN : 1 / c; }
            case 27: { double t = Math.tan(angleIn(v)); return t == 0 ? Double.NaN : 1 / t; }
            case 28: { double s = Math.sin(angleIn(v)); return s == 0 ? Double.NaN : angleOut(Math.asin(1 / s)); }
            case 29: { double c = Math.cos(angleIn(v)); return c == 0 ? Double.NaN : angleOut(Math.acos(1 / c)); }
            case 30: { double t = Math.tan(angleIn(v)); return t == 0 ? Double.NaN : angleOut(Math.atan(1 / t)); }
            case 31: return Math.copySign(Math.log(Math.abs(v) + Math.hypot(v, 1)), v);
            case 32: return v < 1 ? Double.NaN : Math.log(v + Math.sqrt(v * v - 1));
            case 33: return Math.abs(v) >= 1 ? Double.NaN : 0.5 * Math.log((1 + v) / (1 - v));
            case 34: { double s = Math.sinh(v); return s == 0 ? Double.NaN : 1 / s; }
            case 35: return 1 / Math.cosh(v);
            case 36: { double t = Math.tanh(v); return t == 0 ? Double.NaN : 1 / t; }
            case 37: return gcd(v, w);
            case 38: return lcm(v, w);
            case 39: return (w == 0 || Double.isNaN(v) || Double.isNaN(w)) ? Double.NaN : v - w * Math.floor(v / w);
            case 40: return permutations(v, w);
            case 41: return combinations(v, w);
            case 42: return root(v, w);
            case 43: return gamma(v);
            case 44: return erf(v);
            case 45: return v == 0 ? 1 : Math.sin(v) / v;
            case 46: return v <= 0 ? Double.NaN : Math.log(v) / Math.log(2);
            default: return Double.NaN;
        }
    }

    /** Evaluates a scalar or a [list] into an array of values. */
    private static double[] values(Expression e, double x, double y) {
        if (e == null) return new double[0];
        if (e.op == LIST) {
            if (e.items == null) return new double[0];
            double[] out = new double[e.items.size()];
            for (int i = 0; i < out.length; i++) out[i] = e.items.get(i).eval(x, y);
            return out;
        }
        return new double[]{e.eval(x, y)};
    }

    private static double statistics(int fn, Expression a, Expression b, double x, double y) {
        double[] v = values(a, x, y);
        if (fn == 67) return v.length;
        if (v.length == 0) return Double.NaN;
        for (double d : v) if (Double.isNaN(d)) return Double.NaN;

        if (fn == 69 || fn == 70) {
            double[] w = values(b, x, y);
            if (w.length != v.length || w.length < 2) return Double.NaN;
            double mx = mean(v), my = mean(w), sxy = 0, sxx = 0, syy = 0;
            for (int i = 0; i < v.length; i++) {
                double dx = v[i] - mx, dy = w[i] - my;
                sxy += dx * dy; sxx += dx * dx; syy += dy * dy;
            }
            if (fn == 69) return sxy / (v.length - 1);
            return (sxx == 0 || syy == 0) ? Double.NaN : sxy / Math.sqrt(sxx * syy);
        }

        double m = mean(v);
        if (fn == 60) return m;
        if (fn == 66) { double s = 0; for (double d : v) s += d; return s; }
        if (fn == 61) {
            double[] sorted = v.clone();
            java.util.Arrays.sort(sorted);
            int n = sorted.length;
            return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        }
        if (fn == 68) {
            double p = b == null ? 50 : b.eval(x, y);
            if (Double.isNaN(p)) return Double.NaN;
            p = Math.max(0, Math.min(100, p));
            double[] sorted = v.clone();
            java.util.Arrays.sort(sorted);
            double pos = (sorted.length - 1) * p / 100.0;
            int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
            return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
        }
        if (fn == 64 || fn == 65 || fn == 62 || fn == 63) {
            double sum = 0;
            for (double d : v) { double dx = d - m; sum += dx * dx; }
            boolean sample = fn == 64 || fn == 62;
            double var = sum / (sample ? Math.max(1, v.length - 1) : v.length);
            return (fn == 62 || fn == 63) ? Math.sqrt(var) : var;
        }
        return Double.NaN;
    }

    private static double mean(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return s / v.length;
    }

    /** Simpson integration used by integral(expr, lower). */
    private static double integrate(Expression f, double lo, double hi, double y) {
        if (Double.isNaN(lo) || Double.isNaN(hi)) return Double.NaN;
        int sign = 1;
        if (hi < lo) { double t = lo; lo = hi; hi = t; sign = -1; }
        if (hi == lo) return 0;
        int n = 200;
        double h = (hi - lo) / n;
        double s = f.eval(lo, y) + f.eval(hi, y);
        if (Double.isNaN(s)) return Double.NaN;
        for (int i = 1; i < n; i++) {
            double v = f.eval(lo + i * h, y);
            if (Double.isNaN(v)) return Double.NaN;
            s += (i % 2 == 1 ? 4 : 2) * v;
        }
        return sign * s * h / 3;
    }

    private static boolean isWhole(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v)
                && Math.abs(v - Math.rint(v)) < 1e-9;
    }

    private static double gcd(double a, double b) {
        if (!isWhole(a) || !isWhole(b)) return Double.NaN;
        long x = Math.abs(Math.round(a));
        long y = Math.abs(Math.round(b));
        while (y != 0) { long t = x % y; x = y; y = t; }
        return x;
    }

    private static double lcm(double a, double b) {
        double g = gcd(a, b);
        if (Double.isNaN(g) || g == 0) return Double.isNaN(g) ? Double.NaN : 0;
        return Math.abs((Math.round(a) / g) * Math.round(b));
    }

    private static double permutations(double n, double r) {
        if (!isWhole(n) || !isWhole(r) || n < 0 || r < 0 || r > n) return Double.NaN;
        double p = 1;
        for (long i = 0; i < Math.round(r); i++) p *= (n - i);
        return p;
    }

    private static double combinations(double n, double r) {
        double p = permutations(n, r);
        if (Double.isNaN(p)) return Double.NaN;
        for (long i = 2; i <= Math.round(r); i++) p /= i;
        return Math.rint(p);
    }

    private static double root(double v, double n) {
        if (!isWhole(n) || n == 0) return Double.NaN;
        if (v < 0) {
            if (Math.abs(Math.round(n)) % 2 == 0) return Double.NaN;
            return -Math.pow(-v, 1.0 / n);
        }
        return Math.pow(v, 1.0 / n);
    }

    /** Lanczos approximation, good enough for graphing. */
    private static double gamma(double z) {
        if (Double.isNaN(z)) return Double.NaN;
        if (z < 0.5) {
            double s = Math.sin(Math.PI * z);
            return s == 0 ? Double.NaN : Math.PI / (s * gamma(1 - z));
        }
        z -= 1;
        double x = 0.99999999999980993;
        double[] c = {
                676.5203681218851, -1259.1392167224028, 771.32342877765313,
                -176.61502916214059, 12.507343278686905, -0.13857109526572012,
                9.9843695780195716e-6, 1.5056327351493116e-7
        };
        for (int i = 0; i < c.length; i++) x += c[i] / (z + i + 1);
        double t = z + c.length - 0.5;
        return Math.sqrt(2 * Math.PI) * Math.pow(t, z + 0.5) * Math.exp(-t) * x;
    }

    /** Abramowitz & Stegun 7.1.26. */
    private static double erf(double x) {
        double t = 1 / (1 + 0.3275911 * Math.abs(x));
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return Math.copySign(y, x);
    }

    public static final String[] FUNC_NAMES = {
            "arcsinh", "arccosh", "arctanh", "arcsin", "arccos", "arctan",
            "asinh", "acosh", "atanh", "sinh", "cosh", "tanh", "csch", "sech", "coth",
            "asin", "acos", "atan", "acsc", "asec", "acot",
            "sqrt", "cbrt", "abs", "log10", "log2", "log", "ln", "exp",
            "floor", "ceil", "round", "sign", "min", "max", "atan2", "hypot", "log1p",
            "sin", "cos", "tan", "csc", "sec", "cot",
            "gcd", "lcm", "mod", "npr", "ncr", "nthroot", "root", "gamma", "erf", "sinc",
            "nderiv", "deriv", "nint", "integral",
            "mean", "median", "stdev", "stdevp", "var", "varp", "total", "count",
            "percentile", "cov", "corr"
    };

    private static int funcId(String name) {
        switch (name) {
            case "sin": return 0;
            case "cos": return 1;
            case "tan": return 2;
            case "asin": case "arcsin": return 3;
            case "acos": case "arccos": return 4;
            case "atan": case "arctan": return 5;
            case "sinh": return 6;
            case "cosh": return 7;
            case "tanh": return 8;
            case "ln": return 9;
            case "log": case "log10": return 10;
            case "sqrt": return 11;
            case "abs": return 12;
            case "exp": return 13;
            case "floor": return 14;
            case "ceil": return 15;
            case "round": return 16;
            case "sign": return 17;
            case "min": return 18;
            case "max": return 19;
            case "atan2": return 20;
            case "hypot": return 21;
            case "log1p": return 22;
            case "cbrt": return 23;
            case "csc": return 25;
            case "sec": return 26;
            case "cot": return 27;
            case "acsc": return 28;
            case "asec": return 29;
            case "acot": return 30;
            case "asinh": case "arcsinh": return 31;
            case "acosh": case "arccosh": return 32;
            case "atanh": case "arctanh": return 33;
            case "csch": return 34;
            case "sech": return 35;
            case "coth": return 36;
            case "gcd": return 37;
            case "lcm": return 38;
            case "mod": return 39;
            case "npr": return 40;
            case "ncr": return 41;
            case "nthroot": case "root": return 42;
            case "gamma": return 43;
            case "erf": return 44;
            case "sinc": return 45;
            case "log2": return 46;
            case "nderiv": case "deriv": return 50;
            case "nint": case "integral": return 51;
            case "mean": return 60;
            case "median": return 61;
            case "stdev": return 62;
            case "stdevp": return 63;
            case "var": return 64;
            case "varp": return 65;
            case "total": return 66;
            case "count": return 67;
            case "percentile": return 68;
            case "cov": return 69;
            case "corr": return 70;
            default: return 24;
        }
    }

    private static boolean twoArg(int id) {
        return id == 10 || id == 18 || id == 19 || id == 20 || id == 21
                || (id >= 37 && id <= 42) || id == 50 || id == 51
                || id == 68 || id == 69 || id == 70;
    }

    /** Normalises unicode math glyphs and Arabic-Indic digits into ASCII. */
    public static String normalize(String in) {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char ch = in.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') { sb.append((char) ('0' + ch - '\u0660')); continue; }
            if (ch >= '\u06F0' && ch <= '\u06F9') { sb.append((char) ('0' + ch - '\u06F0')); continue; }
            if (ch >= '\u0966' && ch <= '\u096F') { sb.append((char) ('0' + ch - '\u0966')); continue; }
            switch (ch) {
                case '\u2212': case '\u2013': case '\u2014': case '\u2010': case '\u2011':
                case '\u2012': case '\uFE63': case '\uFF0D':
                    sb.append('-'); break;
                case '\u00D7': case '\u00B7': case '\u22C5': case '\u2219': case '\uFF0A':
                    sb.append('*'); break;
                case '\u00F7': case '\u2215': case '\uFF0F':
                    sb.append('/'); break;
                case '\u221A': sb.append("sqrt"); break;
                case '\u03C0': sb.append("pi"); break;
                case '\u00B2': sb.append("^2"); break;
                case '\u00B3': sb.append("^3"); break;
                case '\u00B9': sb.append("^1"); break;
                case '\u2070': sb.append("^0"); break;
                case '\u2074': sb.append("^4"); break;
                case '\u2075': sb.append("^5"); break;
                case '\u2076': sb.append("^6"); break;
                case '\u2077': sb.append("^7"); break;
                case '\u2078': sb.append("^8"); break;
                case '\u2079': sb.append("^9"); break;
                case '\u2264': sb.append("<="); break;
                case '\u2265': sb.append(">="); break;
                case '\u2260': sb.append("!="); break;
                case '\u066B':
                    sb.append('.'); break;
                case '\u060C': case '\uFF0C':
                    sb.append(','); break;
                case '\u00A0': case '\u200F': case '\u200E': case '\u061C':
                    break;
                case '\u007C': case '\u2223':
                    sb.append('|'); break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    public static Expression parse(String src) throws ParseError {
        String s = normalize(src).replace(" ", "");
        if (s.isEmpty()) throw new ParseError("تعبير فارغ");
        Parser p = new Parser(s);
        Expression e = p.parseExpr();
        if (!p.atEnd()) throw new ParseError("رمز غير متوقع: " + p.peekText());
        return e;
    }

    // ------------------------------------------------------------------ parser

    private static final class Parser {
        private final String s;
        private int i = 0;

        Parser(String s) { this.s = s; }

        boolean atEnd() { return i >= s.length(); }

        char peek() { return i < s.length() ? s.charAt(i) : '\0'; }

        char peek(int k) { return i + k < s.length() ? s.charAt(i + k) : '\0'; }

        String peekText() { return atEnd() ? "" : String.valueOf(s.charAt(i)); }

        boolean eat(char c) {
            if (peek() == c) { i++; return true; }
            return false;
        }

        void expect(char c) throws ParseError {
            if (!eat(c)) throw new ParseError("متوقع '" + c + "'");
        }

        Expression parseExpr() throws ParseError {
            Expression left = parseTerm();
            while (true) {
                char c = peek();
                if (c == '+') { i++; left = new Expression(ADD, 0, left, parseTerm(), 0); }
                else if (c == '-') { i++; left = new Expression(SUB, 0, left, parseTerm(), 0); }
                else break;
            }
            return left;
        }

        Expression parseTerm() throws ParseError {
            Expression left = parseUnary();
            while (true) {
                char c = peek();
                if (c == '*' || c == '\u00D7') { i++; left = new Expression(MUL, 0, left, parseUnary(), 0); }
                else if (c == '/') { i++; left = new Expression(DIV, 0, left, parseUnary(), 0); }
                else if (startsPrimary(c)) {
                    // implicit multiplication: 2x, 3(x+1), (x+1)(x-1), 2sin(x)
                    left = new Expression(MUL, 0, left, parseUnary(), 0);
                } else break;
            }
            return left;
        }

        private boolean startsPrimary(char c) {
            // '|' is deliberately excluded: it closes absolute-value groups
            return c == '(' || c == '[' || Character.isDigit(c)
                    || Character.isLetter(c) || c == '\u03C0' || c == '$';
        }

        Expression parseUnary() throws ParseError {
            if (eat('-')) return new Expression(NEG, 0, parseUnary(), null, 0);
            if (eat('+')) return parseUnary();
            return parsePower();
        }

        Expression parsePower() throws ParseError {
            Expression base = parsePostfix();
            if (eat('^')) {
                Expression exp = parseUnary(); // right associative, allows 2^-1
                return new Expression(POW, 0, base, exp, 0);
            }
            return base;
        }

        Expression parsePostfix() throws ParseError {
            Expression e = parsePrimary();
            while (eat('!')) e = new Expression(FACT, 0, e, null, 0);
            return e;
        }

        Expression parsePrimary() throws ParseError {
            char c = peek();
            if (c == '(') {
                i++;
                Expression e = parseExpr();
                expect(')');
                return e;
            }
            if (c == '[') {
                i++;
                List<Expression> list = new ArrayList<>();
                if (peek() != ']') {
                    do {
                        list.add(parseExpr());
                    } while (eat(','));
                }
                expect(']');
                return new Expression(list);
            }
            if (c == '|') {
                i++;
                Expression e = parseExpr();
                expect('|');
                return new Expression(FUNC, 0, e, null, 12);
            }
            if (Character.isDigit(c) || c == '.') return parseNumber();
            if (Character.isLetter(c) || c == '\u03C0') return parseIdent();
            throw new ParseError("تعبير غير مكتمل");
        }

        Expression parseNumber() throws ParseError {
            int start = i;
            boolean dot = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) { i++; }
                else if (c == '.' && !dot) { dot = true; i++; }
                else break;
            }
            if (i == start) throw new ParseError("رقم غير صالح");
            try {
                return c(Double.parseDouble(s.substring(start, i).replace(',', '.')));
            } catch (NumberFormatException e) {
                throw new ParseError("رقم غير صالح");
            }
        }

        Expression parseIdent() throws ParseError {
            // longest function-name match first
            String best = null;
            for (String f : FUNC_NAMES) {
                if (s.regionMatches(true, i, f, 0, f.length())) {
                    if (best == null || f.length() > best.length()) best = f;
                }
            }
            if (best != null && best.length() > 1) {
                i += best.length();
                int id = funcId(best.toLowerCase());
                if (eat('(')) {
                    Expression arg1 = parseExpr();
                    Expression arg2 = null;
                    if (eat(',')) arg2 = parseExpr();
                    expect(')');
                    if (twoArg(id) && arg2 == null && id != 10 && id != 68) {
                        arg2 = c(1);
                    }
                    return new Expression(FUNC, 0, arg1, arg2, id);
                }
                // "sinx" -> sin(x) : apply to the next primary
                if (startsPrimary(peek())) {
                    return new Expression(FUNC, 0, parseUnary(), null, id);
                }
                return new Expression(FUNC, 0, parsePrimary(), null, id);
            }
            char ch = s.charAt(i);
            if (ch == '\u03C0') { i++; return c(Math.PI); }
            if (s.regionMatches(true, i, "pi", 0, 2)) { i += 2; return c(Math.PI); }
            if (s.regionMatches(true, i, "tau", 0, 3)) { i += 3; return c(2 * Math.PI); }
            if (s.regionMatches(true, i, "phi", 0, 3)) { i += 3; return c((1 + Math.sqrt(5)) / 2); }
            if (ch == 'e' || ch == 'E') { i++; return c(Math.E); }
            if (ch == 'x' || ch == 'X' || ch == 't' || ch == 'T') { i++; return new Expression(VARX, 0, null, null, 0); }
            if (ch == 'y' || ch == 'Y') { i++; return new Expression(VARY, 0, null, null, 0); }
            i++;
            return new Expression(VARNAME, 0, null, null, 0);
        }
    }

    // ------------------------------------------------------------- inspection

    public boolean usesX() { return uses(VARX); }

    public boolean usesY() { return uses(VARY); }

    private boolean uses(int code) {
        if (op == code) return true;
        if (a != null && a.uses(code)) return true;
        if (b != null && b.uses(code)) return true;
        if (items != null) {
            for (Expression e : items) if (e.uses(code)) return true;
        }
        return false;
    }

    /** Collects the distinct single-letter variables present in the expression. */
    public List<String> variables() {
        List<String> out = new ArrayList<>();
        collectVars(out);
        return out;
    }

    private void collectVars(List<String> out) {
        if (op == VARNAME) { if (!out.contains("?")) out.add("?"); }
        if (a != null) a.collectVars(out);
        if (b != null) b.collectVars(out);
    }
}
