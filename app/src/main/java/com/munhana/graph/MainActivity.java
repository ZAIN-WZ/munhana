package com.munhana.graph;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.munhana.graph.graph.GraphSettings;
import com.munhana.graph.graph.GraphView;
import com.munhana.graph.math.Equation;
import com.munhana.graph.math.EquationText;
import com.munhana.graph.ocr.CropActivity;
import com.munhana.graph.ocr.ImageProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Arabic, dark-purple graphing calculator with photo equation capture.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "munhana_prefs";
    private static final String KEY_EQUATIONS = "equations";
    private static final String KEY_SEEDED = "seeded";

    private static final int REQ_CROP = 100;
    private static final int REQ_GALLERY = 101;
    private static final int REQ_CAMERA = 102;
    private static final int REQ_EDIT = 103;
    private static final int REQ_EXPORT_IMAGE = 104;
    private static final int REQ_EXPORT_TEXT = 105;
    private static final int REQ_IMPORT = 106;
    private static final int REQ_EXPORT_PROJECT = 107;

    /** Custom MIME type so the file opens in Munhana rather than an editor. */
    public static final String MIME_PROJECT = "application/vnd.munhana.project";
    public static final String EXT_PROJECT = ".munhana";

    private final List<Equation> equations = new ArrayList<>();
    private GraphView graphView;
    private LinearLayout listContainer;
    private ScrollView listScroll;
    private TextView tvCount;
    private View emptyState;
    private GraphSettings settings;

    private File pendingCameraFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        graphView = findViewById(R.id.graphView);
        listContainer = findViewById(R.id.listContainer);
        listScroll = findViewById(R.id.scrollEquations);
        tvCount = findViewById(R.id.tvCount);
        emptyState = findViewById(R.id.emptyState);
        settings = GraphSettings.load(this);
        graphView.applySettings(settings);

        findViewById(R.id.btnAdd).setOnClickListener(v -> openEditor(null, -1));
        findViewById(R.id.btnImage).setOnClickListener(v -> showImageSource());
        findViewById(R.id.btnZoomIn).setOnClickListener(v -> graphView.zoomBy(1.35));
        findViewById(R.id.btnZoomOut).setOnClickListener(v -> graphView.zoomBy(1 / 1.35));
        findViewById(R.id.btnFit).setOnClickListener(v -> graphView.resetView());
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettings());
        findViewById(R.id.btnMore).setOnClickListener(this::showOverflow);

        load();
        boolean opened = handleIntent(getIntent());
        sync();
        graphView.post(opened ? graphView::fitToEquations : graphView::resetView);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (handleIntent(intent)) {
            sync();
            fitGraph();
        }
    }

    // --------------------------------------------------------------- equations

    private boolean addEquation(String raw, int replaceIndex, boolean showError) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            if (showError) toast(getString(R.string.invalid_equation));
            return false;
        }
        try {
            Equation eq = Equation.fromText(text);
            if (replaceIndex >= 0 && replaceIndex < equations.size()) {
                eq.color = equations.get(replaceIndex).color;
                eq.visible = equations.get(replaceIndex).visible;
                equations.set(replaceIndex, eq);
            } else {
                eq.color = Equation.PALETTE[equations.size() % Equation.PALETTE.length];
                equations.add(eq);
            }
            return true;
        } catch (Exception e) {
            if (showError) toast(getString(R.string.invalid_equation));
            return false;
        }
    }

    private void sync() {
        rebuildList();
        graphView.setEquations(new ArrayList<>(equations));
        tvCount.setText(String.valueOf(equations.size()));
        emptyState.setVisibility(equations.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void rebuildList() {
        listContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < equations.size(); i++) {
            final int index = i;
            final Equation eq = equations.get(i);
            View row = inflater.inflate(R.layout.item_equation, listContainer, false);

            View colorBar = row.findViewById(R.id.colorBar);
            TextView prefix = row.findViewById(R.id.tvPrefix);
            TextView text = row.findViewById(R.id.tvEquation);
            ImageButton eye = row.findViewById(R.id.btnVisibility);
            ImageButton del = row.findViewById(R.id.btnDelete);

            // Show the equation the way it would be written by hand, while the
            // stored text stays ASCII for parsing and editing.
            String body = eq.text == null ? "" : eq.text;
            prefix.setVisibility(View.GONE);

            colorBar.setBackgroundTintList(ColorStateList.valueOf(eq.color));
            text.setText(EquationText.pretty(body, settings.prettyVariables));
            int color = !eq.visible ? Color.parseColor("#7A6A93")
                    : !eq.plottable ? Color.parseColor("#B7A6D0")
                    : Color.parseColor("#F3E9FF");
            text.setTextColor(color);
            eye.setImageResource(eq.visible ? R.drawable.ic_eye : R.drawable.ic_eye_off);
            eye.setAlpha(eq.visible ? 1f : 0.7f);

            row.setOnClickListener(v -> openEditor(eq.text, index));
            eye.setOnClickListener(v -> {
                eq.visible = !eq.visible;
                persist();
                sync();
            });
            del.setOnClickListener(v -> {
                if (index < equations.size()) {
                    equations.remove(index);
                    persist();
                    sync();
                }
            });

            listContainer.addView(row);
        }
    }

    private void openEditor(String initial, int replaceIndex) {
        Intent i = new Intent(this, EquationEditActivity.class);
        if (initial != null) i.putExtra(EquationEditActivity.EXTRA_TEXT, initial);
        i.putExtra(EquationEditActivity.EXTRA_INDEX, replaceIndex);
        startActivityForResult(i, REQ_EDIT);
    }

    // -------------------------------------------------------------------- image

    private void showImageSource() {
        String[] options = {getString(R.string.capture_photo), getString(R.string.pick_image)};
        new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                .setTitle(R.string.choose_source)
                .setItems(options, (d, which) -> {
                    if (which == 0) capturePhoto();
                    else pickFromGallery();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void capturePhoto() {
        try {
            File dir = new File(getCacheDir(), "captured");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException();
            pendingCameraFile = new File(dir, "shot_" + System.currentTimeMillis() + ".jpg");
            Uri uri = ImageProvider.uriFor(pendingCameraFile);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (i.resolveActivity(getPackageManager()) == null) {
                toast(getString(R.string.camera_error));
                return;
            }
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast(getString(R.string.camera_error));
        }
    }

    private void pickFromGallery() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        startActivityForResult(i, REQ_GALLERY);
    }

    private void openCrop(Uri uri) {
        Intent i = new Intent(this, CropActivity.class);
        i.setData(uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQ_CROP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingCameraFile != null && pendingCameraFile.length() > 0) {
                openCrop(ImageProvider.uriFor(pendingCameraFile));
            } else if (resultCode != RESULT_CANCELED) {
                toast(getString(R.string.camera_error));
            }
        } else if (requestCode == REQ_GALLERY) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                openCrop(data.getData());
            }
        } else if (requestCode == REQ_CROP) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> found = data.getStringArrayListExtra(CropActivity.EXTRA_RESULT);
                if (found == null || found.isEmpty()) return;
                int added = 0;
                for (String s : found) if (addEquation(s, -1, false)) added++;
                persist();
                sync();
                fitGraph();
                toast(added + " " + getString(R.string.equation_label) + " — " + getString(R.string.done));
            }
        } else if (requestCode == REQ_EDIT) {
            if (resultCode == RESULT_OK && data != null) {
                String text = data.getStringExtra(EquationEditActivity.EXTRA_RESULT);
                int index = data.getIntExtra(EquationEditActivity.EXTRA_INDEX, -1);
                if (text != null && addEquation(text, index, true)) {
                    persist();
                    sync();
                }
            }
        } else if (requestCode == REQ_EXPORT_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    writeGraphPng(data.getData());
                    toast(getString(R.string.export_ready));
                } catch (Exception e) {
                    toast(getString(R.string.export_failed));
                }
            }
        } else if (requestCode == REQ_EXPORT_TEXT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    writeEquations(data.getData());
                    toast(getString(R.string.export_ready));
                } catch (Exception e) {
                    toast(getString(R.string.export_failed));
                }
            }
        } else if (requestCode == REQ_EXPORT_PROJECT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    writeProject(data.getData());
                    toast(getString(R.string.project_ready));
                } catch (Exception e) {
                    toast(getString(R.string.export_failed));
                }
            }
        } else if (requestCode == REQ_IMPORT) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    int n = readEquations(data.getData());
                    persist();
                    sync();
                    fitGraph();
                    toast(n + " " + getString(R.string.equation_label));
                } catch (Exception e) {
                    toast(getString(R.string.import_failed));
                }
            }
        }
    }

    // ------------------------------------------------------------------ export

    private void exportImage() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/png");
        i.putExtra(Intent.EXTRA_TITLE, "munhana-graph.png");
        startActivityForResult(i, REQ_EXPORT_IMAGE);
    }

    private void exportText() {
        if (equations.isEmpty()) {
            toast(getString(R.string.export_empty));
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, "munhana-equations.txt");
        startActivityForResult(i, REQ_EXPORT_TEXT);
    }

    /**
     * Shares a real project file. The receiver (WhatsApp, Telegram, mail, a file
     * manager) gets a .munhana document which reopens with all equations and
     * colours instead of a flat screenshot.
     */
    private void shareProject() {
        if (equations.isEmpty()) {
            toast(getString(R.string.export_empty));
            return;
        }
        try {
            File dir = new File(getCacheDir(), "shared");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException();
            File out = new File(dir, "munhana-" + System.currentTimeMillis() + EXT_PROJECT);
            try (OutputStream os = new java.io.FileOutputStream(out)) {
                os.write(projectJson().getBytes("UTF-8"));
            }
            Uri uri = ImageProvider.uriFor(out);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(MIME_PROJECT);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.share_project)));
        } catch (Exception e) {
            toast(getString(R.string.export_failed));
        }
    }

    private void exportProject() {
        if (equations.isEmpty()) {
            toast(getString(R.string.export_empty));
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(MIME_PROJECT);
        i.putExtra(Intent.EXTRA_TITLE, "munhana" + EXT_PROJECT);
        startActivityForResult(i, REQ_EXPORT_PROJECT);
    }

    private String projectJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "munhana");
        root.put("format", 1);
        root.put("version", getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName);
        JSONArray list = new JSONArray();
        for (Equation e : equations) {
            JSONObject o = new JSONObject();
            o.put("text", e.text == null ? "" : e.text);
            o.put("color", e.color);
            o.put("visible", e.visible);
            list.put(o);
        }
        root.put("equations", list);
        return root.toString(2);
    }

    private void writeProject(Uri uri) throws Exception {
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("no stream");
            os.write(projectJson().getBytes("UTF-8"));
        }
    }

    /** Loads a shared project. Returns the number of imported equations. */
    private int readProject(Uri uri) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IllegalStateException("no stream");
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) buffer.write(buf, 0, n);
        }
        String text = buffer.toString("UTF-8");

        List<Equation> loaded = new ArrayList<>();
        JSONObject root = new JSONObject(text);
        JSONArray list = root.optJSONArray("equations");
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.optJSONObject(i);
                if (o == null) continue;
                String t = o.optString("text", "").trim();
                if (t.isEmpty()) continue;
                Equation e = Equation.fromText(t);
                e.color = o.optInt("color", Equation.PALETTE[i % Equation.PALETTE.length]);
                e.visible = o.optBoolean("visible", true);
                loaded.add(e);
            }
        } else {
            // Plain list fallback, for hand-written project files.
            BufferedReader reader = new BufferedReader(new java.io.StringReader(text));
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                try {
                    loaded.add(Equation.fromText(t));
                } catch (Exception ignored) { }
            }
        }
        if (loaded.isEmpty()) return 0;
        // Only replace the current work once the file has been read successfully.
        equations.clear();
        equations.addAll(loaded);
        persist();
        return equations.size();
    }

    /** Handles VIEW / SEND intents for .munhana project files. */
    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        Uri uri = intent.getData();
        if (uri == null && Intent.ACTION_SEND.equals(action)) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null) return false;
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action)
                && !Intent.ACTION_EDIT.equals(action)) return false;
        try {
            int n = readProject(uri);
            if (n > 0) {
                toast(n + " " + getString(R.string.equation_label));
            }
            return true;
        } catch (Exception e) {
            toast(getString(R.string.project_open_failed));
            return false;
        }
    }

    private void importEquations() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    private void writeGraphPng(Uri uri) throws Exception {
        Bitmap bmp = graphView.exportBitmap();
        if (bmp == null) throw new IllegalStateException("no bitmap");
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("no stream");
            bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
        } finally {
            bmp.recycle();
        }
    }

    private void writeEquations(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("# منحنى — تصدير المعادلات\n");
        sb.append("# اكتب معادلة واحدة في كل سطر\n\n");
        for (Equation e : equations) sb.append(e.text).append('\n');
        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IllegalStateException("no stream");
            os.write(sb.toString().getBytes("UTF-8"));
        }
    }

    private int readEquations(Uri uri) throws Exception {
        int count = 0;
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IllegalStateException("no stream");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                if (addEquation(t, -1, false)) count++;
            }
        }
        return count;
    }

    // --------------------------------------------------------------------- menu

    private void showOverflow(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, R.string.reset_view);
        menu.getMenu().add(0, 11, 0, R.string.fit_equations);
        menu.getMenu().add(0, 2, 1, R.string.graph_settings);
        menu.getMenu().add(0, 3, 2, R.string.quick_examples);
        menu.getMenu().add(0, 4, 3, R.string.export_image);
        menu.getMenu().add(0, 5, 4, R.string.export_text);
        menu.getMenu().add(0, 10, 5, R.string.export_project);
        menu.getMenu().add(0, 6, 6, R.string.share_project);
        menu.getMenu().add(0, 7, 7, R.string.import_equations);
        menu.getMenu().add(0, 8, 8, R.string.clear_all);
        menu.getMenu().add(0, 9, 9, R.string.about);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: graphView.resetView(); return true;
                case 11: fitGraph(); return true;
                case 2: showSettings(); return true;
                case 3: showExamples(); return true;
                case 4: exportImage(); return true;
                case 5: exportText(); return true;
                case 6: shareProject(); return true;
                case 7: importEquations(); return true;
                case 8: confirmClear(); return true;
                case 9: showAbout(); return true;
                case 10: exportProject(); return true;
            }
            return false;
        });
        menu.show();
    }

    private void showSettings() {
        SettingsDialog.show(this, settings, () -> {
            graphView.applySettings(settings);
            graphView.invalidate();
            sync();
        });
    }

    private void showExamples() {
        final String[] items = {
                getString(R.string.example_parabola),
                getString(R.string.example_sine),
                getString(R.string.example_circle),
                "y = 1 / x",
                "y > x^2 - 2"
        };
        new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                .setTitle(R.string.quick_examples)
                .setItems(items, (d, which) -> {
                    addEquation(items[which], -1, true);
                    persist();
                    sync();
                })
                .show();
    }

    private void confirmClear() {
        new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                .setTitle(R.string.clear_all)
                .setMessage(R.string.clear_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear_all, (d, w) -> {
                    equations.clear();
                    persist();
                    sync();
                })
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this, R.style.Theme_Munhana_Dialog)
                .setTitle(R.string.about)
                .setMessage(R.string.about_text)
                .setPositiveButton(R.string.done, null)
                .show();
    }

    // -------------------------------------------------------------- persistence

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void persist() {
        StringBuilder sb = new StringBuilder();
        for (Equation e : equations) sb.append(e.serialize()).append('\n');
        prefs().edit().putString(KEY_EQUATIONS, sb.toString()).apply();
    }

    private void load() {
        equations.clear();
        String data = prefs().getString(KEY_EQUATIONS, "");
        if (data != null && !data.isEmpty()) {
            for (String line : data.split("\n")) {
                if (line.trim().isEmpty()) continue;
                Equation e = Equation.deserialize(line.trim());
                if (e != null) equations.add(e);
            }
        }
        if (equations.isEmpty() && !prefs().getBoolean(KEY_SEEDED, false)) {
            addEquation("y = x^2 - 4", -1, false);
            addEquation("y = 2 sin(x)", -1, false);
            persist();
            prefs().edit().putBoolean(KEY_SEEDED, true).apply();
        }
    }

    private void fitGraph() {
        graphView.post(graphView::fitToEquations);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
