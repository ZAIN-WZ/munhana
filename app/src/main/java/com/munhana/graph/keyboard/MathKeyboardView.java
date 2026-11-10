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
        final int titleRes;
        final Key[][] rows;
        final int columns;

        Category(int titleRes, int columns, Key[][] rows) {
            this.titleRes = titleRes;
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
        addToolKey(toolbar, "◀", () -> {
            if (listener != null) listener.onLeft();
        }, STYLE_OP, 1f);
        addToolKey(toolbar, "▶", () -> {
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
        categories.add(new Category(R.string.keyboard_basic, 6, new Key[][]{
                {k("a/b", "()/()", STYLE_FUNC), k(">", ">", STYLE_OP),
                        d("7"), d("8"), d("9"), k("÷", "/", STYLE_OP)},
                {k("√", "sqrt(", STYLE_FUNC), k("x", "x", STYLE_OP),
                        d("4"), d("5"), d("6"), k("×", "*", STYLE_OP)},
                {k("x²", "^2", STYLE_OP), k("≤", "<=", STYLE_OP),
                        d("1"), d("2"), d("3"), k("−", "-", STYLE_OP)},
                {k("π", "pi", STYLE_FUNC), k("≥", ">=", STYLE_OP),
                        d("0"), d("."), k("=", "=", STYLE_OP), k("+", "+", STYLE_OP)},
                {k("(", "(", STYLE_FUNC), k(")", ")", STYLE_FUNC), k("y", "y", STYLE_OP),
                        k(",", ",", STYLE_OP), k("e", "e", STYLE_FUNC), k("^", "^", STYLE_OP)}
        }));

        categories.add(new Category(R.string.keyboard_vars, 5, new Key[][]{
                {k("x", "x", STYLE_OP), k("y", "y", STYLE_OP),
                        k("x₁", "x1", STYLE_OP), k("x₂", "x2", STYLE_OP),
                        k("x₃", "x3", STYLE_OP)},
                {k("a", "a", STYLE_OP), k("b", "b", STYLE_OP), k("c", "c", STYLE_OP),
                        k("n", "n", STYLE_OP), k("t", "t", STYLE_OP)},
                {k("k", "k", STYLE_OP), k("m", "m", STYLE_OP), k("r", "r", STYLE_OP),
                        k("p", "p", STYLE_OP), k("q", "q", STYLE_OP)},
                {k("x₁²", "x1^2", STYLE_OP), k("x₂²", "x2^2", STYLE_OP),
                        k("(", "(", STYLE_FUNC), k(")", ")", STYLE_FUNC),
                        k("=", "=", STYLE_OP)},
                {k("≤", "<=", STYLE_OP), k("≥", ">=", STYLE_OP), k("≠", "!=", STYLE_OP),
                        k("+", "+", STYLE_OP), k("−", "-", STYLE_OP)},
                {d("1"), d("2"), d("3"), d("0"), k(",", ",", STYLE_OP)}
        }));

        categories.add(new Category(R.string.keyboard_functions, 5, new Key[][]{
                {f("sin"), f("cos"), f("tan"), f("csc"), f("sec")},
                {f("cot"), k("sin⁻¹", "asin(", STYLE_FUNC), k("cos⁻¹", "acos(", STYLE_FUNC),
                        k("tan⁻¹", "atan(", STYLE_FUNC), k("cot⁻¹", "acot(", STYLE_FUNC)},
                {f("sinh"), f("cosh"), f("tanh"), f("csch"), f("sech")},
                {f("coth"), f("ln"), f("log"), k("log₂", "log2(", STYLE_FUNC), f("exp")},
                {k("√", "sqrt(", STYLE_FUNC), k("∛", "cbrt(", STYLE_FUNC),
                        k("ⁿ√", "nthroot(", STYLE_FUNC), k("|x|", "abs(", STYLE_FUNC),
                        k("eˣ", "e^", STYLE_FUNC)},
                {k("⌊x⌋", "floor(", STYLE_FUNC), k("⌈x⌉", "ceil(", STYLE_FUNC),
                        f("round"), f("sign"), f("mod")}
        }));

        categories.add(new Category(R.string.keyboard_calculus, 5, new Key[][]{
                {k("d/dx", "nderiv(", STYLE_FUNC), k("∫", "integral(", STYLE_FUNC),
                        k("xⁿ", "^", STYLE_OP), k("x²", "^2", STYLE_OP), k("x³", "^3", STYLE_OP)},
                {k("10ˣ", "10^", STYLE_FUNC), k("eˣ", "e^", STYLE_FUNC),
                        k("√", "sqrt(", STYLE_FUNC), k("∛", "cbrt(", STYLE_FUNC),
                        k("ⁿ√", "nthroot(", STYLE_FUNC)},
                {f("ln"), f("log"), k("log₂", "log2(", STYLE_FUNC), f("exp"),
                        k("|x|", "abs(", STYLE_FUNC)},
                {k("⌊x⌋", "floor(", STYLE_FUNC), k("⌈x⌉", "ceil(", STYLE_FUNC),
                        f("round"), f("sign"), f("mod")},
                {k("≤", "<=", STYLE_OP), k("≥", ">=", STYLE_OP), k("≠", "!=", STYLE_OP),
                        k("π", "pi", STYLE_FUNC), k("e", "e", STYLE_FUNC)},
                {k("(", "(", STYLE_FUNC), k(")", ")", STYLE_FUNC), k("[", "[", STYLE_FUNC),
                        k("]", "]", STYLE_FUNC), k(",", ",", STYLE_OP)}
        }));

        categories.add(new Category(R.string.keyboard_stats, 4, new Key[][]{
                {f("mean"), f("median"), f("total"), f("count")},
                {f("min"), f("max"), f("stdev"), f("stdevp")},
                {f("var"), f("varp"), f("percentile"), f("cov")},
                {f("corr"), k("[", "[", STYLE_FUNC), k("]", "]", STYLE_FUNC), k(",", ",", STYLE_OP)},
                {d("1"), d("2"), d("3"), d("4")},
                {d("5"), d("6"), d("7"), d("0")}
        }));

        categories.add(new Category(R.string.keyboard_advanced, 4, new Key[][]{
                {f("gcd"), f("lcm"), f("mod"), f("nPr")},
                {f("nCr"), k("!", "!", STYLE_OP), k("⌊x⌋", "floor(", STYLE_FUNC),
                        k("⌈x⌉", "ceil(", STYLE_FUNC)},
                {f("round"), f("sign"), k("10ˣ", "10^", STYLE_FUNC), k("eˣ", "e^", STYLE_FUNC)},
                {k("π", "pi", STYLE_FUNC), k("e", "e", STYLE_FUNC), k("φ", "phi", STYLE_FUNC),
                        k("τ", "tau", STYLE_FUNC)},
                {k("(", "(", STYLE_FUNC), k(")", ")", STYLE_FUNC), k("[", "[", STYLE_FUNC),
                        k("]", "]", STYLE_FUNC)},
                {k("x", "x", STYLE_OP), k("y", "y", STYLE_OP), k("=", "=", STYLE_OP),
                        k(",", ",", STYLE_OP)}
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
            tab.setText(categories.get(i).titleRes);
            tab.setGravity(Gravity.CENTER);
            tab.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tab.setPadding(dp(14), 0, dp(14), 0);
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
