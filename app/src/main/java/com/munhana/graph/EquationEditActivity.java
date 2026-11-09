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

import java.util.Collections;

/**
 * Full-screen equation editor: live preview, professional maths keyboard and
 * an optional system keyboard for free text.
 */
public class EquationEditActivity extends Activity {

    public static final String EXTRA_TEXT = "com.munhana.graph.EXTRA_TEXT";
    public static final String EXTRA_INDEX = "com.munhana.graph.EXTRA_INDEX";
    public static final String EXTRA_RESULT = "com.munhana.graph.EXTRA_EDIT_RESULT";

    private EditText input;
    private GraphView preview;
    private View previewBox;
    private TextView error;
    private Button save;
    private MathKeyboardView keyboard;

    private int editIndex = -1;
    private boolean systemIme = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equation_edit);

        input = findViewById(R.id.input);
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
            input.setText(initial);
            input.setSelection(initial.length());
        }
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
            @Override public void onClear() { input.setText(""); input.requestFocus(); }
            @Override public void onEnter() { save(); }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { validate(); }
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
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(0, input.getSelectionEnd());
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
        input.setSelection(Math.max(0, Math.min(caret, input.length())));
        input.requestFocus();
    }

    private void backspace() {
        int start = Math.max(0, input.getSelectionStart());
        int end = Math.max(0, input.getSelectionEnd());
        Editable e = input.getText();
        if (end > start) {
            e.delete(start, end);
        } else if (start > 0) {
            e.delete(start - 1, start);
        }
        input.setSelection(Math.max(0, Math.min(start, input.length())));
        input.requestFocus();
    }

    private boolean validate() {
        String raw = input.getText() == null ? "" : input.getText().toString().trim();
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
            return true;
        } catch (Exception ex) {
            error.setVisibility(View.VISIBLE);
            save.setEnabled(false);
            save.setAlpha(0.55f);
            preview.setEquations(Collections.emptyList());
            return false;
        }
    }

    private void save() {
        if (!validate()) {
            error.setVisibility(View.VISIBLE);
            return;
        }
        String raw = input.getText().toString().trim();
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
