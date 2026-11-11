package com.munhana.graph.graph;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persistent graph appearance / window settings for the settings panel.
 */
public class GraphSettings {

    private static final String PREFS = "munhana_graph_settings";

    public boolean showGrid = true;
    public boolean showMinorGrid = true;
    public boolean showAxisNumbers = true;
    public boolean showArrows = true;
    public boolean showXAxis = true;
    public boolean showYAxis = true;
    public boolean degrees = false;
    /** Tint the half-plane that satisfies an inequality. Off by default. */
    public boolean shadeInequalities = false;
    /** Frame the graph automatically whenever the equation list changes. */
    public boolean autoFit = true;
    public double xMin = -10, xMax = 10;
    public double yMin = -10, yMax = 10;

    public static GraphSettings load(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        GraphSettings s = new GraphSettings();
        s.showGrid = p.getBoolean("showGrid", true);
        s.showMinorGrid = p.getBoolean("showMinorGrid", true);
        s.showAxisNumbers = p.getBoolean("showAxisNumbers", true);
        s.showArrows = p.getBoolean("showArrows", true);
        s.showXAxis = p.getBoolean("showXAxis", true);
        s.showYAxis = p.getBoolean("showYAxis", true);
        s.degrees = p.getBoolean("degrees", false);
        s.shadeInequalities = p.getBoolean("shadeInequalities", false);
        s.autoFit = p.getBoolean("autoFit", true);
        s.xMin = p.getFloat("xMin", -10);
        s.xMax = p.getFloat("xMax", 10);
        s.yMin = p.getFloat("yMin", -10);
        s.yMax = p.getFloat("yMax", 10);
        if (!validWindow(s)) resetWindow(s);
        return s;
    }

    public void save(Context c) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putBoolean("showGrid", showGrid);
        e.putBoolean("showMinorGrid", showMinorGrid);
        e.putBoolean("showAxisNumbers", showAxisNumbers);
        e.putBoolean("showArrows", showArrows);
        e.putBoolean("showXAxis", showXAxis);
        e.putBoolean("showYAxis", showYAxis);
        e.putBoolean("degrees", degrees);
        e.putBoolean("shadeInequalities", shadeInequalities);
        e.putBoolean("autoFit", autoFit);
        e.putFloat("xMin", (float) xMin);
        e.putFloat("xMax", (float) xMax);
        e.putFloat("yMin", (float) yMin);
        e.putFloat("yMax", (float) yMax);
        e.apply();
    }

    private static boolean validWindow(GraphSettings s) {
        return s.xMax > s.xMin + 1e-6 && s.yMax > s.yMin + 1e-6
                && Math.abs(s.xMin) < 1e9 && Math.abs(s.xMax) < 1e9
                && Math.abs(s.yMin) < 1e9 && Math.abs(s.yMax) < 1e9;
    }

    public static void resetWindow(GraphSettings s) {
        s.xMin = -10; s.xMax = 10; s.yMin = -10; s.yMax = 10;
    }
}
