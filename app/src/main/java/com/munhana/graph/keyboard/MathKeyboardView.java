package com.munhana.graph.keyboard;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.munhana.graph.R;

import java.util.ArrayList;
import java.util.List;

/**
 * A compact mathematical keyboard: an action toolbar (letters, undo, cursor
 * movement, enter, delete), a row of category tabs and a grid of keys.
 * Everything is built from framework widgets so the app stays Gradle-free and
 * lightweight.
 */
public class MathKeyboardView extends LinearLayout {

    public interface Listener {
        void onInsert(String text);
        void onBackspace();
        void onClear();
        void onEnter();
        default void onUndo() { }
        default void onLeft() { }
        default void onRight() { }
        default void onSystemKeyboard() { }
    }

    private static final int STYLE_DIGIT = 0;
    private static final int STYLE_FUNC = 1;
    private static final int STYLE_OP = 2;
    private static final int STYLE_PRIMARY = 3;
    private static final int STYLE_DANGER = 4;

    private static final int C_DIGIT = 0xFF1A1226;
    private static final int C_FUNC = 0xFF241834;
    private static final int C_OP = 0xFF2D1C44;
    private static final int C_PRIMARY = 0xFFA855F7;
    private static final int C_DANGER = 0xFF3A1B28;

    private static final int T_TEXT = 0xFFF3E9FF;
    private static final int T_ACCENT = 0xFFC9A7FF;
    private static final int T_PRIMARY = 0xFF160B24;
    private static final int T_DANGER = 0xFFFF9AAF;

    private LinearLayout tabBar;
    private LinearLayout grid;
    private final List<Category> categories = new ArrayList<>();
    private final List<TextView> tabViews = new ArrayList<>();
    private Listener listener;
    private int current = 0;
    private float density;

    private static final class Key {
        final String label;
        final String insert;
        final int style;
        final int action; // 0 insert, 1 backspace, 2 enter, 3 clear

        Key(String label, String insert, int style, int action) {
            this.label = label;
            this.insert = insert;
            this.style = style;
            this.action = action;
        }
    }

    private static final class Category {
        final String label;
        final Key[][] rows;
        final int columns;

        Category(String label, int columns, Key[][] rows) {
            this.label = label;
            this.columns = columns;
            this.rows = rows;
        }
    }

    public MathKeyboardView(Context context) {
        super(context);
        init(context);
    }

    public MathKeyboardView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MathKeyboardView(Context context, android.util.AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(Context context) {
        density = context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
        setBackground(topRounded(0xFF120C1C, 18 * density));
        setPadding(dp(6), dp(6), dp(6), dp(8));

        buildCategories();

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(HORIZONTAL);
        toolbar.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        addView(toolbar, new LayoutParams(LayoutParams.MATCH_PARENT, dp(46)));

        addToolKey(toolbar, "abc", () -> {
            if (listener != null) listener.onSystemKeyboard();
        }, STYLE_FUNC, 1.35f);
        addToolKey(toolbar, "↶", () -> {
            if (listener != null) listener.onUndo();
        }, STYLE_OP, 1f);
        addToolKey(toolbar, "←", () -> {
            if (listener != null) listener.onLeft();
        }, STYLE_OP, 1f);
        addToolKey(toolbar, "→", () -> {
            if (listener != null) listener.onRight();
        }, STYLE_OP, 1f);
        addToolKey(toolbar, "↵", () -> {
            if (listener != null) listener.onEnter();
        }, STYLE_PRIMARY, 1.35f);
        addToolKey(toolbar, "⌫", () -> {
            if (listener != null) listener.onBackspace();
        }, STYLE_DANGER, 1f);

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);

        tabBar = new LinearLayout(context);
        tabBar.setOrientation(HORIZONTAL);
        scroll.addView(tabBar, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)));
        LayoutParams tabsParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(40));
        tabsParams.topMargin = dp(4);
        addView(scroll, tabsParams);

        grid = new LinearLayout(context);
        grid.setOrientation(VERTICAL);
        grid.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = dp(4);
        addView(grid, gridParams);

        buildTabs();
        showCategory(0);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    // ------------------------------------------------------------- categories

    private void buildCategories() {
        // The arrangement mirrors a pocket calculator: a template column, a
        // symbol column, a 3x4 number pad and an operator column.
        categories.add(new Category("\u00B1 \u00F7\nx \u00F7", 6, new Key[][]{
                {k("( )", "(", STYLE_FUNC), k(">", ">", STYLE_OP),
                        d("7"), d("8"), d("9"), k("\u00F7", "\u00F7", STYLE_OP)},
                {k("a/b", "()/()", STYLE_FUNC), k("\u221A", "\u221A(", STYLE_FUNC),
                        d("4"), d("5"), d("6"), k("\u00D7", "\u00D7", STYLE_OP)},
                {k("x\u00B2", "\u00B2", STYLE_OP), k("x", "x", STYLE_OP),
                        d("1"), d("2"), d("3"), k("\u2212", "\u2212", STYLE_OP)},
                {k("\u03C0", "\u03C0", STYLE_FUNC), k("%", "%", STYLE_FUNC),
                        d("0"), d("."), k("=", "=", STYLE_OP), k("+", "+", STYLE_OP)}
        }));

        categories.add(new Category("> <\n\u2265 \u2264", 6, new Key[][]{
                {k(">", ">", STYLE_OP), k("<", "<", STYLE_OP),
                        k("\u2265", "\u2265", STYLE_OP), k("\u2264", "\u2264", STYLE_OP),
                        k("\u2260", "\u2260", STYLE_OP), k("=", "=", STYLE_OP)},
                {k("x", "x", STYLE_OP), k("y", "y", STYLE_OP),
                        k("\u03C0", "\u03C0", STYLE_FUNC), k("e", "e", STYLE_FUNC),
                        k(",", ",", STYLE_OP), k("!", "!", STYLE_OP)},
                {k("(", "(", STYLE_FUNC), k(")", ")", STYLE_FUNC),
                        k("[", "[", STYLE_FUNC), k("]", "]", STYLE_FUNC),
                        k("|\u25A1|", "||", STYLE_FUNC), k("a/b", "()/()", STYLE_FUNC)},
                {d("1"), d("2"), d("3"), d("0"), k(".", ".", STYLE_DIGIT),
                        k("\u2212", "\u2212", STYLE_OP)}
        }));

        categories.add(new Category("f(x) e\nlog ln", 6, new Key[][]{
                {k("\u221A\u25A1", "\u221A(", STYLE_FUNC), k("\u221B\u25A1", "\u221B(", STYLE_FUNC),
                        k("\u25A1^\u25A1", "^(", STYLE_FUNC), k("x\u00B2", "\u00B2", STYLE_OP),
                        k("x\u00B3", "\u00B3", STYLE_OP), k("|\u25A1|", "||", STYLE_FUNC)},
                {k("e\u02E3", "e^(", STYLE_FUNC), k("10\u02E3", "10^(", STYLE_FUNC),
                        k("ln", "ln(", STYLE_FUNC), k("log", "log(", STYLE_FUNC),
                        k("log\u2082", "log\u2082(", STYLE_FUNC), k("log\u2081\u2080", "log\u2081\u2080(", STYLE_FUNC)},
                {k("\u230A\u25A1\u230B", "\u230A\u230B", STYLE_FUNC),
                        k("\u2308\u25A1\u2309", "\u2308\u2309", STYLE_FUNC),
                        k("sgn", "sign(", STYLE_FUNC), f("round"), f("mod"),
                        k("e^\u25A1", "e^(", STYLE_FUNC)},
                {k("\u03C0", "\u03C0", STYLE_FUNC), k("e", "e", STYLE_FUNC),
                        k("\u03C6", "\u03C6", STYLE_FUNC), k("\u03C4", "\u03C4", STYLE_FUNC),
                        f("min"), f("max")}
        }));

        categories.add(new Category("sin cos\ntan cot", 6, new Key[][]{
                {f("sin"), f("cos"), f("tan"), f("cot"), f("sec"), f("csc")},
                {k("arcsin", "arcsin(", STYLE_FUNC), k("arccos", "arccos(", STYLE_FUNC),
                        k("arctan", "arctan(", STYLE_FUNC), k("arccot", "arccot(", STYLE_FUNC),
                        k("arcsec", "arcsec(", STYLE_FUNC), k("arccsc", "arccsc(", STYLE_FUNC)},
                {f("sinh"), f("cosh"), f("tanh"), f("coth"), f("sech"), f("csch")},
                {k("arsinh", "arsinh(", STYLE_FUNC), k("arcosh", "arcosh(", STYLE_FUNC),
                        k("artanh", "artanh(", STYLE_FUNC), k("arcoth", "arcoth(", STYLE_FUNC),
                        k("arsech", "arsech(", STYLE_FUNC), k("arcsch", "arcsch(", STYLE_FUNC)}
        }));

        categories.add(new Category("lim dx\n\u03A3 \u221E", 6, new Key[][]{
                {k("d/dx", "d/dx(", STYLE_FUNC), k("\u222B dx", "\u222B(", STYLE_FUNC),
                        k("x\u00B2", "\u00B2", STYLE_OP), k("x\u00B3", "\u00B3", STYLE_OP),
                        k("x\u207F", "^", STYLE_OP), k("\u221A\u25A1", "\u221A(", STYLE_FUNC)},
                {k("e\u02E3", "e^(", STYLE_FUNC), k("10\u02E3", "10^(", STYLE_FUNC),
                        k("ln", "ln(", STYLE_FUNC), k("log", "log(", STYLE_FUNC),
                        k("log\u2082", "log\u2082(", STYLE_FUNC), k("|\u25A1|", "||", STYLE_FUNC)},
                {k("\u2264", "\u2264", STYLE_OP), k("\u2265", "\u2265", STYLE_OP),
                        k("\u2260", "\u2260", STYLE_OP), k(",", ",", STYLE_OP),
                        k("!", "!", STYLE_OP), k("%", "%", STYLE_FUNC)},
                {k("nCr", "ncr(", STYLE_FUNC), k("nPr", "npr(", STYLE_FUNC),
                        k("gcd", "gcd(", STYLE_FUNC), k("lcm", "lcm(", STYLE_FUNC),
                        f("min"), f("max")}
        }));

        categories.add(new Category("| |\n\u230A \u230B", 6, new Key[][]{
                {k("|\u25A1|", "||", STYLE_FUNC), k("(\u25A1)", "(", STYLE_FUNC),
                        k("a/b", "()/()", STYLE_FUNC), k("\u25A1^\u25A1", "^(", STYLE_FUNC),
                        k("\u221A\u25A1", "\u221A(", STYLE_FUNC), k("\u221B\u25A1", "\u221B(", STYLE_FUNC)},
                {k("\u230A\u25A1\u230B", "\u230A\u230B", STYLE_FUNC),
                        k("\u2308\u25A1\u2309", "\u2308\u2309", STYLE_FUNC),
                        k("x\u00B2", "\u00B2", STYLE_OP), k("x\u00B3", "\u00B3", STYLE_OP),
                        k("x\u207F", "^", STYLE_OP), k("%", "%", STYLE_FUNC)},
                {k("\u03C0", "\u03C0", STYLE_FUNC), k("e", "e", STYLE_FUNC),
                        k("\u03C6", "\u03C6", STYLE_FUNC), k("\u03C4", "\u03C4", STYLE_FUNC),
                        k(",", ",", STYLE_OP), k(".", ".", STYLE_DIGIT)},
                {k("\u2264", "\u2264", STYLE_OP), k("\u2265", "\u2265", STYLE_OP),
                        k("\u2260", "\u2260", STYLE_OP), k("=", "=", STYLE_OP),
                        k("<", "<", STYLE_OP), k(">", ">", STYLE_OP)}
        }));

        categories.add(new Category("x y\nz", 6, new Key[][]{
                {k("x", "x", STYLE_OP), k("y", "y", STYLE_OP), k("z", "z", STYLE_OP),
                        k("x\u2081", "x\u2081", STYLE_OP), k("x\u2082", "x\u2082", STYLE_OP),
                        k("x\u2083", "x\u2083", STYLE_OP)},
                {k("a", "a", STYLE_OP), k("b", "b", STYLE_OP), k("c", "c", STYLE_OP),
                        k("n", "n", STYLE_OP), k("t", "t", STYLE_OP), k("k", "k", STYLE_OP)},
                {k("m", "m", STYLE_OP), k("r", "r", STYLE_OP), k("p", "p", STYLE_OP),
                        k("q", "q", STYLE_OP), k("s", "s", STYLE_OP), k("u", "u", STYLE_OP)},
                {k("x\u2081\u00B2", "x\u2081^2", STYLE_OP), k("x\u2082\u00B2", "x\u2082^2", STYLE_OP),
                        k("(", "(", STYLE_FUNC), k(")", ")", STYLE_FUNC),
                        k(",", ",", STYLE_OP), k("=", "=", STYLE_OP)}
        }));
    }

    private Key k(String label, String insert, int style) {
        return new Key(label, insert, style, 0);
    }

    private Key d(String digit) {
        return new Key(digit, digit, STYLE_DIGIT, 0);
    }

    private Key f(String name) {
        return new Key(name, name + "(", STYLE_FUNC, 0);
    }

    private Key back() {
        return new Key("⌫", null, STYLE_DANGER, 1);
    }

    private Key enter() {
        return new Key("↵", null, STYLE_PRIMARY, 2);
    }

    // ------------------------------------------------------------------ tabs

    private void buildTabs() {
        tabViews.clear();
        tabBar.removeAllViews();
        for (int i = 0; i < categories.size(); i++) {
            final int index = i;
            TextView tab = new TextView(getContext());
            tab.setText(categories.get(i).label);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            tab.setIncludeFontPadding(false);
            tab.setSingleLine(false);
            tab.setMaxLines(2);
            tab.setTextDirection(TEXT_DIRECTION_LTR);
            tab.setPadding(dp(11), dp(2), dp(11), dp(2));
            tab.setOnClickListener(v -> showCategory(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
            lp.setMargins(dp(2), dp(4), dp(2), dp(4));
            tabBar.addView(tab, lp);
            tabViews.add(tab);
        }

        TextView clear = new TextView(getContext());
        clear.setText(R.string.keyboard_clear);
        clear.setGravity(Gravity.CENTER);
        clear.setTextColor(T_DANGER);
        clear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        clear.setPadding(dp(14), 0, dp(14), 0);
        clear.setBackground(keyBackground(C_DANGER, dp(12)));
        clear.setOnClickListener(v -> {
            if (listener != null) listener.onClear();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(6), dp(4), dp(2), dp(4));
        tabBar.addView(clear, lp);
    }

    private void showCategory(int index) {
        current = index;
        for (int i = 0; i < tabViews.size(); i++) {
            TextView tab = tabViews.get(i);
            boolean on = i == index;
            tab.setTextColor(on ? T_PRIMARY : T_ACCENT);
            tab.setBackground(keyBackground(on ? C_PRIMARY : C_FUNC, dp(12)));
        }
        grid.removeAllViews();
        Category cat = categories.get(index);
        for (int r = 0; r < cat.rows.length; r++) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(HORIZONTAL);
            Key[] keys = cat.rows[r];
            for (int c = 0; c < cat.columns; c++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1);
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                if (c < keys.length) {
                    row.addView(makeKey(keys[c]), lp);
                } else {
                    View spacer = new View(getContext());
                    row.addView(spacer, lp);
                }
            }
            grid.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private void addToolKey(LinearLayout toolbar, String label, Runnable action,
                            int style, float weight) {
        TextView v = new TextView(getContext());
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, label.length() <= 1 ? 18 : 14);
        int bg;
        int fg;
        switch (style) {
            case STYLE_PRIMARY: bg = C_PRIMARY; fg = T_PRIMARY; break;
            case STYLE_DANGER: bg = C_DANGER; fg = T_DANGER; break;
            case STYLE_OP: bg = C_OP; fg = 0xFFEAD9FF; break;
            default: bg = C_FUNC; fg = T_ACCENT; break;
        }
        v.setTextColor(fg);
        v.setBackground(keyBackground(bg, dp(11)));
        v.setContentDescription(label);
        v.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight);
        lp.setMargins(dp(3), dp(2), dp(3), dp(2));
        toolbar.addView(v, lp);
    }

    private TextView makeKey(Key key) {
        TextView v = new TextView(getContext());
        v.setText(key.label);
        v.setGravity(Gravity.CENTER);
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        int len = key.label.length();
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, len <= 1 ? 19 : len <= 3 ? 15 : len <= 6 ? 12 : 10);

        int bg;
        int fg;
        switch (key.style) {
            case STYLE_DIGIT: bg = C_DIGIT; fg = T_TEXT; break;
            case STYLE_FUNC: bg = C_FUNC; fg = T_ACCENT; break;
            case STYLE_OP: bg = C_OP; fg = 0xFFEAD9FF; break;
            case STYLE_PRIMARY: bg = C_PRIMARY; fg = T_PRIMARY; break;
            case STYLE_DANGER: bg = C_DANGER; fg = T_DANGER; break;
            default: bg = C_FUNC; fg = T_TEXT; break;
        }
        v.setTextColor(fg);
        v.setBackground(keyBackground(bg, dp(11)));
        v.setContentDescription(key.label);
        v.setOnClickListener(view -> {
            if (listener == null) return;
            switch (key.action) {
                case 0: listener.onInsert(key.insert); break;
                case 1: listener.onBackspace(); break;
                case 2: listener.onEnter(); break;
                case 3: listener.onClear(); break;
            }
        });
        return v;
    }

    // ---------------------------------------------------------------- drawing

    private Drawable keyBackground(int color, float radius) {
        GradientDrawable content = new GradientDrawable();
        content.setColor(color);
        content.setCornerRadius(radius);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(0xFFFFFFFF);
        mask.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(0x44C9A7FF), content, mask);
    }

    private Drawable topRounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return g;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
