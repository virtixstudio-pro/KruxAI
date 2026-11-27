package com.virtixstudio.kruxai.core;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Studios de l'utilisateur (équipe d'agents, conversation, dernière app générée), gardés dans SharedPreferences. */
public final class StudioStore {
    private StudioStore() { }

    private static final String PREFS = "krux_studios";

    public static final class Msg {
        public String who = "", text = "", html = "", test = "";
        public boolean ok;
        public Msg() { }
        public Msg(String who, String text) { this.who = who; this.text = text; }
    }

    public static final class Studio {
        public String id = "", name = "", kind = "libre", goal = "", lastHtml = "";
        public final List<String[]> agents = new ArrayList<>();   // {nom, rôle}
        public final List<Msg> msgs = new ArrayList<>();
    }

    public static List<Studio> load(Context c) {
        List<Studio> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("all", "[]"));
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.getJSONObject(i);
                Studio s = new Studio();
                s.id = o.optString("id");
                s.name = o.optString("name");
                s.kind = o.optString("kind", "libre");
                s.goal = o.optString("goal");
                s.lastHtml = o.optString("lastHtml");
                JSONArray ag = o.optJSONArray("agents");
                for (int j = 0; ag != null && j < ag.length(); j++) {
                    JSONObject a = ag.getJSONObject(j);
                    s.agents.add(new String[]{a.optString("n"), a.optString("r")});
                }
                JSONArray ms = o.optJSONArray("msgs");
                for (int j = 0; ms != null && j < ms.length(); j++) {
                    JSONObject m = ms.getJSONObject(j);
                    Msg g = new Msg(m.optString("who"), m.optString("text"));
                    g.html = m.optString("html");
                    g.test = m.optString("test");
                    g.ok = m.optBoolean("ok");
                    s.msgs.add(g);
                }
                out.add(s);
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static void save(Context c, List<Studio> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Studio s : all) {
                JSONObject o = new JSONObject();
                o.put("id", s.id).put("name", s.name).put("kind", s.kind).put("goal", s.goal).put("lastHtml", s.lastHtml);
                JSONArray ag = new JSONArray();
                for (String[] a : s.agents) ag.put(new JSONObject().put("n", a[0]).put("r", a[1]));
                o.put("agents", ag);
                JSONArray ms = new JSONArray();
                int from = Math.max(0, s.msgs.size() - 80);
                for (int j = from; j < s.msgs.size(); j++) {
                    Msg m = s.msgs.get(j);
                    ms.put(new JSONObject().put("who", m.who).put("text", m.text).put("html", m.html).put("test", m.test).put("ok", m.ok));
                }
                o.put("msgs", ms);
                arr.put(o);
            }
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("all", arr.toString()).apply();
        } catch (Exception ignored) { }
    }
}
