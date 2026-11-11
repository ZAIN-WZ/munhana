package com.munhana.graph;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.munhana.graph.graph.GraphSettings;
import com.munhana.graph.graph.GraphView;
import com.munhana.graph.keyboard.MathKeyboardView;
import com.munhana.graph.math.Equation;
import com.munhana.graph.math.EquationText;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

/**
 * Full-screen equation editor: live preview, professional maths keyboard and
 * an optional system keyboard for free text.
 */
public class EquationEditActivity extends Activity {

    public static final String EXTRA_TEXT = "com.munhana.graph.EXTRA_TEXT";
    public static final String EXTRA_INDEX = "com.munhana.graph.EXTRA_INDEX";
    public static final String EXTRA_RESULT = "com.munhana.graph.EXTRA_EDIT_RESULT";

    private EditText input;
    private TextView pretty;
    private GraphView preview;
    private View previewBox;
    private TextView error;
    private Button save;
    private MathKeyboardView keyboard;

    private int editIndex = -1;
    private boolean systemIme = false;

    /** Lightweight undo history; every text change pushes the previous state. */
    private final Deque<String> history = new ArrayDeque<>();
    private String lastText = "";
    private boolean restoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equation_edit);

        input = findViewById(R.id.input);
        pretty = findViewById(R.id.tvPretty);
        preview = findViewById(R.id.preview);
        previewBox = findViewById(R.id.previewBox);
        error = findViewById(R.id.tvError);
        save = findViewById(R.id.btnSave);
        keyboard = findViewById(R.id.keyboard);

        editIndex = getIntent().getIntExtra(EXTRA_INDEX, -1);
        TextView title = findViewById(R.id.tvTitle);
        if (editIndex >= 0) {
            title.setText(R.string.edit);
            save.setText(R.string.save);
        }

        GraphSettings settings = GraphSettings.load(this);
        preview.applySettings(settings);

        String initial = getIntent().getStringExtra(EXTRA_TEXT);
        if (initial != null && !initial.isEmpty()) {
            // Show the equation in its written form; the parser normalises the
            // symbols back to ASCII, so editing stays lossless.
            String shown = EquationText.pretty(initial);
            input.setText(shown);
            input.setSelection(shown.length());
        }
        lastText = input.getText() == null ? "" : input.getText().toString();
        input.setShowSoftInputOnFocus(false);

        ImageButton back = findViewById(R.id.btnBack);
        back.setOnClickListener(v -> finish());
        findViewById(R.id.btnPreview).setOnClickListener(v -> {
            boolean show = previewBox.getVisibility() != View.VISIBLE;
            previewBox.setVisibility(show ? View.VISIBLE : View.GONE);
        });
        findViewById(R.id.btnIme).setOnClickListener(v -> toggleSystemKeyboard());
        save.setOnClickListener(v -> save());

        keyboard.setListener(new MathKeyboardView.Listener() {
            @Override public void onInsert(String text) { insert(text); }
            @Override public void onBackspace() { backspace(); }
            @Override public void onClear() {
                pushHistory();
                input.setText("");
                input.requestFocus();
                input.setSelection(0);
            }
            @Override public void onEnter() { save(); }
            @Override public void onUndo() { undo(); }
            @Override public void onLeft() { moveCursor(-1); }
            @Override public void onRight() { moveCursor(1); }
            @Override public void onSystemKeyboard() { toggleSystemKeyboard(); }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                String now = s.toString();
                if (!restoring && !now.equals(lastText)) {
                    history.push(lastText);
                    while (history.size() > 60) history.removeLast();
                    lastText = now;
                }
                showPretty(now);
                validate();
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            save();
            return true;
        });

        validate();
        preview.post(() -> preview.resetView());
    }

    /** Inserts text, automatically closing a freshly opened bracket. */
    private void insert(String text) {
        if (text == null) return;
        int start = input.getSelectionStart();
        int end = input.getSelectionEnd();
        if (start < 0) start = input.length();
        if (end < 0) end = start;
        if (start > end) { int t = start; start = end; end = t; }

        String insert = text;
        int cursorBack = 0;
        if (insert.equals("nderiv(")) {
            // d/dx placeholder: derivative with respect to the current x.
            insert = "nderiv(,x)";
            cursorBack = 3;
        } else if (insert.equals("integral(")) {
            // Definite accumulation from 0 up to the current x.
            insert = "integral(,0)";
            cursorBack = 3;
        } else if (insert.equals("()/()")) {
            // Fraction template: start typing in the numerator.
            cursorBack = 4;
        } else if (insert.equals("||") || insert.equals("\u230A\u230B")
                || insert.equals("\u2308\u2309")) {
            // Paired glyphs: put the caret between the two bars.
            cursorBack = 1;
        } else if (insert.endsWith("(")) {
            insert = insert + ")";
            cursorBack = 1;
        } else if (insert.equals("[")) {
            insert = "[]";
            cursorBack = 1;
        }
        Editable e = input.getText();
        e.replace(start, end, insert);
        int caret = start + insert.length() - cursorBack;
        input.requestFocus();
        input.setSelection(Math.max(0, Math.min(caret, input.length())));
    }

    private void backspace() {
        int start = input.getSelectionStart();
        int end = input.getSelectionEnd();
        if (start < 0) start = input.length();
        if (end < 0) end = start;
        if (start > end) { int t = start; start = end; end = t; }

        Editable e = input.getText();
        int caret;
        if (end > start) {
            e.delete(start, end);
            caret = start;
        } else if (start > 0) {
            e.delete(start - 1, start);
            caret = start - 1;
        } else {
            caret = 0;
        }
        input.requestFocus();
        input.setSelection(Math.max(0, Math.min(caret, input.length())));
    }

    private void moveCursor(int delta) {
        int pos = input.getSelectionStart();
        if (pos < 0) pos = input.length();
        pos = Math.max(0, Math.min(input.length(), pos + delta));
        input.requestFocus();
        input.setSelection(pos);
    }

    private void pushHistory() {
        history.push(lastText);
        while (history.size() > 60) history.removeLast();
    }

    private void undo() {
        if (history.isEmpty()) return;
        String previous = history.pop();
        restoring = true;
        input.setText(previous);
        input.setSelection(previous.length());
        restoring = false;
        lastText = previous;
        input.requestFocus();
    }

    private boolean validate() {
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        showPretty(raw);
        if (raw.isEmpty()) {
            error.setVisibility(View.GONE);
            save.setEnabled(false);
            save.setAlpha(0.55f);
            preview.setEquations(Collections.emptyList());
            return false;
        }
        try {
            Equation eq = Equation.parse(raw);
            error.setVisibility(View.GONE);
            save.setEnabled(true);
            save.setAlpha(1f);
            preview.setEquations(Collections.singletonList(eq));
        } catch (Exception ex) {
            // Keep the line: the user may still be editing, or may simply want
            // to save a note that cannot be plotted.
            error.setText(R.string.not_plottable);
            error.setVisibility(View.VISIBLE);
            save.setEnabled(true);
            save.setAlpha(1f);
            preview.setEquations(Collections.emptyList());
        }
        return true;
    }

    private void showPretty(String raw) {
        if (pretty == null) return;
        String s = raw == null ? "" : raw.trim();
        pretty.setText(s.isEmpty() ? "" : EquationText.pretty(s));
    }

    private void save() {
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
        if (raw.isEmpty()) {
            error.setVisibility(View.VISIBLE);
            return;
        }
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT, raw);
        data.putExtra(EXTRA_INDEX, editIndex);
        setResult(RESULT_OK, data);
        finish();
    }

    private void toggleSystemKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return;
        if (!systemIme) {
            systemIme = true;
            keyboard.setVisibility(View.GONE);
            input.setShowSoftInputOnFocus(true);
            input.requestFocus();
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        } else {
            systemIme = false;
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.setShowSoftInputOnFocus(false);
            keyboard.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        if (systemIme) {
            toggleSystemKeyboard();
            return;
        }
        super.onBackPressed();
    }
}
