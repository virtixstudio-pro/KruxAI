package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages « Capacités » de Krux : agent, navigateur visible, nombre d'actions. */
final class AgentPrefs {
    private AgentPrefs() { }

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("krux_capacites", Context.MODE_PRIVATE); }

    static boolean agentOn(Context c) { return sp(c).getBoolean("agent", true); }
    static boolean showBrowser(Context c) { return sp(c).getBoolean("voir_navigateur", true); }
    static int maxSteps(Context c) { return sp(c).getInt("max_actions", 8); }

    static void set(Context c, String key, boolean v) { sp(c).edit().putBoolean(key, v).apply(); }
    static void setMax(Context c, int v) { sp(c).edit().putInt("max_actions", v).apply(); }
}
