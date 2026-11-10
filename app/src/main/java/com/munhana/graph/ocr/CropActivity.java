package com.munhana.graph.ocr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.munhana.graph.EquationEditActivity;
import com.munhana.graph.R;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets the user frame the equations on a photo, then extracts each one and
 * hands the list back to the main screen.
 */
public class CropActivity extends Activity {

    public static final String EXTRA_RESULT = "com.munhana.graph.EXTRA_RESULT";

    private static final int REQ_EDIT_LINE = 200;
    private static final int REQ_MANUAL = 201;

    private CropOverlayView cropView;
    private ProgressBar progress;
    private TextView status;
    private Button extractBtn;
    private CheckBox cbSingleLine;

    /** Row currently being refined in the maths keyboard editor. */
    private EditText pendingEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop);

        cropView = findViewById(R.id.cropView);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.tvStatus);
        extractBtn = findViewById(R.id.btnExtract);
        cbSingleLine = findViewById(R.id.cbSingleLine);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReset).setOnClickListener(v -> cropView.resetCrop());
        findViewById(R.id.btnManual).setOnClickListener(v -> openManualEditor());
        extractBtn.setOnClickListener(v -> extract());

        Uri uri = getIntent().getData();
        if (uri == null) {
            Toast.makeText(this, R.string.image_error, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // warm the OCR engine up in the background while the photo loads
        new Thread(() -> MathOcr.init(this)).start();
        loadAsync(uri);
    }

    private void loadAsync(Uri uri) {
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                bmp = loadBitmap(uri);
            } catch (Exception ignored) { }
            final Bitmap result = bmp;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(this, R.string.image_error, Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    cropView.setBitmap(result);
                }
            });
        }).start();
    }

    private Bitmap loadBitmap(Uri uri) throws Exception {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(is, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int maxDim = Math.max(bounds.outWidth, bounds.outHeight);
        while (maxDim / sample > 2200) sample *= 2;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            bmp = BitmapFactory.decodeStream(is, null, opts);
        }
        if (bmp == null) return null;

        int rotation = 0;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(is);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
            else if (o == ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
            else if (o == ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
        } catch (Exception ignored) { }

        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        return bmp;
    }

    private void extract() {
        final Bitmap cropped = cropView.cropBitmap();
        if (cropped == null) return;
        final boolean singleLine = cbSingleLine.isChecked();
        setBusy(true);
        status.setText(R.string.searching);
        status.setVisibility(View.VISIBLE);

        new Thread(() -> {
            if (!MathOcr.init(this)) {
                cropped.recycle();
                runOnUiThread(() -> {
                    setBusy(false);
                    new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                            .setTitle(R.string.scan_failed)
                            .setMessage(R.string.ocr_init_failed)
                            .setPositiveButton(R.string.done, null)
                            .show();
                });
                return;
            }
            final List<String> found = MathOcr.recognize(this, cropped, singleLine);
            cropped.recycle();
            runOnUiThread(() -> {
                setBusy(false);
                if (found.isEmpty()) {
                    new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                            .setTitle(R.string.found_title)
                            .setMessage(R.string.recognize_empty)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.manual_entry,
                                    (d, w) -> openManualEditor())
                            .show();
                } else {
                    showResults(found);
                }
            });
        }).start();
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        status.setVisibility(busy ? View.VISIBLE : View.GONE);
        extractBtn.setEnabled(!busy);
        extractBtn.setAlpha(busy ? 0.55f : 1f);
    }

    private void openManualEditor() {
        Intent i = new Intent(this, EquationEditActivity.class);
        startActivityForResult(i, REQ_MANUAL);
    }

    private void showResults(List<String> found) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_ocr_results, null);
        LinearLayout rows = view.findViewById(R.id.rows);
        ScrollView scroll = view.findViewById(R.id.scroll);

        final List<EditText> edits = new ArrayList<>();
        final List<CheckBox> checks = new ArrayList<>();

        for (String eq : found) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_ocr_line, rows, false);
            final EditText edit = row.findViewById(R.id.edit);
            CheckBox check = row.findViewById(R.id.check);
            ImageButton editBtn = row.findViewById(R.id.btnEdit);
            edit.setText(eq);
            edits.add(edit);
            checks.add(check);
            editBtn.setOnClickListener(v -> {
                pendingEdit = edit;
                Intent i = new Intent(this, EquationEditActivity.class);
                String current = edit.getText() == null ? "" : edit.getText().toString().trim();
                if (!current.isEmpty()) i.putExtra(EquationEditActivity.EXTRA_TEXT, current);
                startActivityForResult(i, REQ_EDIT_LINE);
            });
            rows.addView(row);
        }

        float d = getResources().getDisplayMetrics().density;
        int h = (int) Math.min(found.size() * 58 * d, 380 * d);
        scroll.getLayoutParams().height = Math.max((int) (110 * d), h);

        final AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.select_all, null)
                .setPositiveButton(R.string.add_selected, null)
                .create();

        dialog.setOnShowListener(dd -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b -> {
                boolean all = true;
                for (CheckBox c : checks) {
                    if (!c.isChecked()) { all = false; break; }
                }
                for (CheckBox c : checks) c.setChecked(!all);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
                ArrayList<String> chosen = new ArrayList<>();
                for (int i = 0; i < edits.size(); i++) {
                    if (!checks.get(i).isChecked()) continue;
                    String text = edits.get(i).getText().toString().trim();
                    if (text.isEmpty()) continue;
                    chosen.add(text);
                }
                if (chosen.isEmpty()) {
                    Toast.makeText(this, R.string.recognize_empty, Toast.LENGTH_SHORT).show();
                    return;
                }
                returnEquations(chosen);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void returnEquations(ArrayList<String> equations) {
        Intent data = new Intent();
        data.putStringArrayListExtra(EXTRA_RESULT, equations);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            pendingEdit = null;
            return;
        }
        String text = data.getStringExtra(EquationEditActivity.EXTRA_RESULT);
        if (text == null) {
            pendingEdit = null;
            return;
        }
        text = text.trim();

        if (requestCode == REQ_EDIT_LINE) {
            if (pendingEdit != null && !text.isEmpty()) {
                pendingEdit.setText(text);
                pendingEdit.setSelection(text.length());
            }
            pendingEdit = null;
        } else if (requestCode == REQ_MANUAL) {
            if (!text.isEmpty()) {
                ArrayList<String> one = new ArrayList<>();
                one.add(text);
                returnEquations(one);
            }
        }
    }
}
