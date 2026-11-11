package com.munhana.graph;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.munhana.graph.graph.GraphSettings;

/** Graph appearance, inequality and window settings panel. */
public final class SettingsDialog {

    private SettingsDialog() { }

    public static void show(Activity activity, GraphSettings s, Runnable onChange) {
        Dialog dialog = new Dialog(activity, R.style.Theme_Munhana_Dialog);
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null);
        dialog.setContentView(root);

        CheckBox cbGrid = root.findViewById(R.id.cbGrid);
        CheckBox cbMinor = root.findViewById(R.id.cbMinor);
        CheckBox cbNumbers = root.findViewById(R.id.cbNumbers);
        CheckBox cbArrows = root.findViewById(R.id.cbArrows);
        CheckBox cbXAxis = root.findViewById(R.id.cbXAxis);
        CheckBox cbYAxis = root.findViewById(R.id.cbYAxis);
        CheckBox cbShade = root.findViewById(R.id.cbShade);
        CheckBox cbAutoFit = root.findViewById(R.id.cbAutoFit);
        EditText etXMin = root.findViewById(R.id.etXMin);
        EditText etXMax = root.findViewById(R.id.etXMax);
        EditText etYMin = root.findViewById(R.id.etYMin);
        EditText etYMax = root.findViewById(R.id.etYMax);
        RadioButton rbRadians = root.findViewById(R.id.rbRadians);
        RadioButton rbDegrees = root.findViewById(R.id.rbDegrees);
        Button btnApplyWindow = root.findViewById(R.id.btnApplyWindow);
        Button btnDefaults = root.findViewById(R.id.btnDefaults);
        Button btnDone = root.findViewById(R.id.btnDone);
        TextView version = root.findViewById(R.id.tvVersion);

        if (version != null) {
            String name = "1.2";
            try {
                name = activity.getPackageManager()
                        .getPackageInfo(activity.getPackageName(), 0).versionName;
            } catch (Exception ignored) { }
            version.setText(activity.getString(R.string.settings_footer, name));
        }

        final boolean[] updating = {false};

        Runnable push = () -> {
            if (updating[0]) return;
            s.save(activity);
            onChange.run();
        };

        View.OnClickListener toggles = v -> {
            if (updating[0]) return;
            s.showGrid = cbGrid.isChecked();
            s.showMinorGrid = cbMinor.isChecked();
            s.showAxisNumbers = cbNumbers.isChecked();
            s.showArrows = cbArrows.isChecked();
            s.showXAxis = cbXAxis.isChecked();
            s.showYAxis = cbYAxis.isChecked();
            s.shadeInequalities = cbShade.isChecked();
            s.autoFit = cbAutoFit.isChecked();
            s.degrees = rbDegrees.isChecked();
            push.run();
        };
        cbGrid.setOnClickListener(toggles);
        cbMinor.setOnClickListener(toggles);
        cbNumbers.setOnClickListener(toggles);
        cbArrows.setOnClickListener(toggles);
        cbXAxis.setOnClickListener(toggles);
        cbYAxis.setOnClickListener(toggles);
        cbShade.setOnClickListener(toggles);
        cbAutoFit.setOnClickListener(toggles);
        rbRadians.setOnClickListener(toggles);
        rbDegrees.setOnClickListener(toggles);

        btnApplyWindow.setOnClickListener(v -> {
            try {
                double xMin = parse(etXMin.getText().toString());
                double xMax = parse(etXMax.getText().toString());
                double yMin = parse(etYMin.getText().toString());
                double yMax = parse(etYMax.getText().toString());
                if (!(xMax > xMin) || !(yMax > yMin)
                        || Math.abs(xMin) > 1e9 || Math.abs(xMax) > 1e9
                        || Math.abs(yMin) > 1e9 || Math.abs(yMax) > 1e9) {
                    throw new NumberFormatException();
                }
                s.autoFit = false;
                cbAutoFit.setChecked(false);
                s.xMin = xMin; s.xMax = xMax; s.yMin = yMin; s.yMax = yMax;
                push.run();
                Toast.makeText(activity, R.string.done, Toast.LENGTH_SHORT).show();
            } catch (Exception ex) {
                Toast.makeText(activity, R.string.window_invalid, Toast.LENGTH_SHORT).show();
            }
        });

        btnDefaults.setOnClickListener(v -> {
            updating[0] = true;
            s.showGrid = true; s.showMinorGrid = true; s.showAxisNumbers = true;
            s.showArrows = true; s.showXAxis = true; s.showYAxis = true;
            s.degrees = false;
            s.shadeInequalities = false;
            s.autoFit = true;
            GraphSettings.resetWindow(s);
            cbGrid.setChecked(true); cbMinor.setChecked(true); cbNumbers.setChecked(true);
            cbArrows.setChecked(true); cbXAxis.setChecked(true); cbYAxis.setChecked(true);
            cbShade.setChecked(false); cbAutoFit.setChecked(true); rbRadians.setChecked(true);
            fillWindow(etXMin, etXMax, etYMin, etYMax, s);
            updating[0] = false;
            push.run();
        });

        btnDone.setOnClickListener(v -> dialog.dismiss());

        fillControls(s, cbGrid, cbMinor, cbNumbers, cbArrows, cbXAxis, cbYAxis,
                cbShade, cbAutoFit, rbDegrees, etXMin, etXMax, etYMin, etYMax);

        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            w.setLayout((int) (dm.widthPixels - 24 * dm.density),
                    (int) (dm.heightPixels * 0.88f));
        }
    }

    private static void fillControls(GraphSettings s, CheckBox cbGrid, CheckBox cbMinor,
                                     CheckBox cbNumbers, CheckBox cbArrows, CheckBox cbXAxis,
                                     CheckBox cbYAxis, CheckBox cbShade, CheckBox cbAutoFit,
                                     RadioButton rbDegrees,
                                     EditText etXMin, EditText etXMax, EditText etYMin, EditText etYMax) {
        cbGrid.setChecked(s.showGrid);
        cbMinor.setChecked(s.showMinorGrid);
        cbNumbers.setChecked(s.showAxisNumbers);
        cbArrows.setChecked(s.showArrows);
        cbXAxis.setChecked(s.showXAxis);
        cbYAxis.setChecked(s.showYAxis);
        cbShade.setChecked(s.shadeInequalities);
        cbAutoFit.setChecked(s.autoFit);
        rbDegrees.setChecked(s.degrees);
        fillWindow(etXMin, etXMax, etYMin, etYMax, s);
    }

    private static void fillWindow(EditText etXMin, EditText etXMax, EditText etYMin,
                                   EditText etYMax, GraphSettings s) {
        etXMin.setText(format(s.xMin));
        etXMax.setText(format(s.xMax));
        etYMin.setText(format(s.yMin));
        etYMax.setText(format(s.yMax));
    }

    private static String format(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) return String.valueOf((long) v);
        return String.valueOf(v);
    }

    private static double parse(String text) {
        return Double.parseDouble(text.trim().replace(',', '.').replace('−', '-'));
    }
}
