package com.virtixstudio.kruxai.core;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.google.firebase.auth.FirebaseUser;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Synchronisation locale + cloud de l'historique et de la mémoire, via le Worker Cloudflare (D1).
 * Local d'abord : tout reste utilisable hors ligne, le cloud est mis à jour quand le réseau revient.
 * Conversations : la version la plus récente gagne. Mémoire : union des faits (rien n'est perdu).
 * Une déconnexion ou une réinstallation récupère tout au prochain passage.
 */
public final class CloudSync {

    private static final long MIN_GAP_MS = 20_000;
    private static final int MAX_DATA = 180_000;     // le Worker refuse plus de 200 000 caractères
    private static final int MAX_MESSAGES = 2000;
    private static volatile boolean running = false;
    private static volatile long lastRun = 0;

    private CloudSync() { }

    /** Lance une synchronisation en arrière-plan ; ignorée si une autre est en cours ou trop récente. */
    public static void run(final Context ctx, final FirebaseUser user, final KruxDatabaseHelper helper,
                           final boolean force, final Runnable onChanged, final android.os.Handler ui) {
        if (!KruxConfig.workerEnabled() || user == null || helper == null) return;
        long now = System.currentTimeMillis();
        if (running || (!force && now - lastRun < MIN_GAP_MS)) return;
        running = true;
        lastRun = now;
        new Thread(() -> {
            try {
                SharedPreferences sp = ctx.getApplicationContext()
                        .getSharedPreferences("krux_sync_" + user.getUid(), Context.MODE_PRIVATE);
                push(user, helper, sp);
                boolean changed = pull(user, helper, sp);
                if (changed && onChanged != null && ui != null) ui.post(onChanged);
            } catch (Exception e) {
                android.util.Log.w("KRUX_SYNC", "synchro impossible : " + e.getMessage());
            } finally {
                running = false;
            }
        }, "krux-sync").start();
    }

    static String cloudId(String sessionId) {
        String id = sessionId.replaceAll("[^A-Za-z0-9_-]", "_");
        return id.length() > 64 ? id.substring(id.length() - 64) : id;
    }

    // ---------- envoi ----------

    private static void push(FirebaseUser user, KruxDatabaseHelper helper, SharedPreferences sp) throws Exception {
        SQLiteDatabase db = helper.getReadableDatabase();
        long now = System.currentTimeMillis();
        JSONArray batch = new JSONArray();
        int batchChars = 0;
        SharedPreferences.Editor ed = sp.edit();
        Set<String> known = new HashSet<>(sp.getStringSet("known", new HashSet<String>()));
        Set<String> present = new HashSet<>();

        List<String> sessions = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT DISTINCT session_id FROM messages WHERE session_id IS NOT NULL", null)) {
            while (c.moveToNext()) sessions.add(c.getString(0));
        }
        for (String sid : sessions) {
            String id = cloudId(sid);
            present.add(id);
            JSONObject o = exportSession(helper, db, sid);
            String json = o.toString();
            String hash = Integer.toHexString(json.hashCode()) + ":" + json.length();
            if (hash.equals(sp.getString("h_" + id, ""))) continue;
            String data = SyncCodec.pack(json);
            if (data.length() > MAX_DATA) data = SyncCodec.pack(trimmed(o, data.length()).toString());
            if (data.length() > MAX_DATA) continue;
            JSONObject item = new JSONObject().put("kind", "chat").put("id", id).put("updated", now).put("data", data);
            if (batchChars + data.length() > 1_500_000 || batch.length() >= 40) { flush(user, batch); batch = new JSONArray(); batchChars = 0; }
            batch.put(item);
            batchChars += data.length();
            ed.putString("h_" + id, hash).putLong("u_" + id, now).putString("s_" + id, sid);
        }
        // conversations supprimées ici depuis la dernière synchro : on prévient le cloud
        for (String id : known) {
            if (!present.contains(id)) {
                batch.put(new JSONObject().put("kind", "chat").put("id", id).put("updated", now).put("deleted", true));
                ed.putLong("u_" + id, now).remove("h_" + id);
            }
        }
        // mémoire
        List<String> facts = helper.getAllMemoryFacts();
        JSONArray fa = new JSONArray();
        for (String f : facts) fa.put(f);
        String fj = fa.toString();
        String fh = Integer.toHexString(fj.hashCode()) + ":" + fj.length();
        if (!facts.isEmpty() && !fh.equals(sp.getString("h_memory", ""))) {
            String data = SyncCodec.pack(fj);
            if (data.length() <= MAX_DATA) {
                batch.put(new JSONObject().put("kind", "memory").put("id", "facts").put("updated", now).put("data", data));
                ed.putString("h_memory", fh).putLong("u_memory", now);
            }
        }
        if (batch.length() > 0) flush(user, batch);
        ed.putStringSet("known", present).apply();
    }

    private static void flush(FirebaseUser user, JSONArray batch) throws Exception {
        if (batch.length() > 0) WorkerStore.syncPost(user, batch);
    }

    private static JSONObject exportSession(KruxDatabaseHelper helper, SQLiteDatabase db, String sid) throws Exception {
        JSONArray msgs = new JSONArray();
        try (Cursor c = db.rawQuery("SELECT sender, message, timestamp FROM messages WHERE session_id = ? ORDER BY id ASC", new String[]{sid})) {
            while (c.moveToNext()) {
                msgs.put(new JSONObject().put("s", c.getString(0)).put("m", c.getString(1)).put("t", c.getLong(2)));
            }
        }
        String title = helper.getSessionTitle(sid);
        return new JSONObject().put("sid", sid).put("title", title == null ? "" : title)
                .put("pin", helper.isSessionPinned(sid) ? 1 : 0).put("msgs", msgs);
    }

    /** Copie réduite pour le cloud : on garde les messages les plus récents. */
    private static JSONObject trimmed(JSONObject o, int packedLen) throws Exception {
        JSONArray all = o.getJSONArray("msgs");
        int keep = Math.max(1, (int) ((long) all.length() * (MAX_DATA - 2000) / Math.max(1, packedLen)));
        keep = Math.min(keep, MAX_MESSAGES);
        JSONArray kept = new JSONArray();
        for (int i = Math.max(0, all.length() - keep); i < all.length(); i++) kept.put(all.get(i));
        return new JSONObject(o.toString()).put("msgs", kept);
    }

    // ---------- réception ----------

    private static boolean pull(FirebaseUser user, KruxDatabaseHelper helper, SharedPreferences sp) throws Exception {
        long since = sp.getLong("since", 0);
        boolean changed = false;
        SQLiteDatabase db = helper.getWritableDatabase();
        SharedPreferences.Editor ed = sp.edit();
        Set<String> known = new HashSet<>(sp.getStringSet("known", new HashSet<String>()));
        long maxSeen = since;
        for (int page = 0; page < 20; page++) {
            JSONObject r = WorkerStore.syncGet(user, since);
            JSONArray items = r.getJSONArray("items");
            if (items.length() == 0) break;
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);
                String kind = it.getString("kind"), id = it.getString("id");
                long updated = it.getLong("updated");
                maxSeen = Math.max(maxSeen, updated);
                since = Math.max(since, updated);
                if (updated <= sp.getLong("u_" + id, 0) && !"memory".equals(kind)) continue; // déjà à jour ici
                if ("chat".equals(kind)) {
                    String sid = sp.getString("s_" + id, id);
                    if (it.optInt("deleted", 0) == 1) {
                        helper.deleteSession(sid);
                        known.remove(id);
                        ed.remove("h_" + id).putLong("u_" + id, updated);
                        changed = true;
                        continue;
                    }
                    String json = SyncCodec.unpack(it.optString("data", ""));
                    if (json == null) continue;
                    JSONObject o = new JSONObject(json);
                    String realSid = o.optString("sid", sid);
                    importSession(helper, db, realSid, o);
                    known.add(id);
                    ed.putLong("u_" + id, updated).putString("s_" + id, realSid)
                            .putString("h_" + id, hashOf(helper, db, realSid));
                    changed = true;
                } else if ("memory".equals(kind)) {
                    String json = SyncCodec.unpack(it.optString("data", ""));
                    if (json == null) continue;
                    if (updated <= sp.getLong("u_memory", 0)) continue; // notre propre envoi, ou déjà fusionné
                    JSONArray fa = new JSONArray(json);
                    boolean added = false;
                    for (int k = 0; k < fa.length(); k++) {
                        String f = fa.optString(k, "").trim();
                        if (!f.isEmpty() && f.length() < 2000 && !helper.hasMemoryFact(f)) { helper.saveMemoryFact(f); added = true; }
                    }
                    ed.putLong("u_memory", updated);
                    if (added) { ed.remove("h_memory"); changed = true; } // l'union sera renvoyée au prochain passage
                }
            }
            if (items.length() < 500) break;
        }
        ed.putLong("since", Math.max(maxSeen, sp.getLong("since", 0))).putStringSet("known", known).apply();
        return changed;
    }

    private static String hashOf(KruxDatabaseHelper helper, SQLiteDatabase db, String sid) throws Exception {
        String json = exportSession(helper, db, sid).toString();
        return Integer.toHexString(json.hashCode()) + ":" + json.length();
    }

    private static void importSession(KruxDatabaseHelper helper, SQLiteDatabase db, String sid, JSONObject o) throws Exception {
        JSONArray msgs = o.getJSONArray("msgs");
        db.beginTransaction();
        try {
            db.delete("messages", "session_id = ?", new String[]{sid});
            for (int i = 0; i < msgs.length() && i < MAX_MESSAGES; i++) {
                JSONObject m = msgs.getJSONObject(i);
                ContentValues v = new ContentValues();
                v.put("session_id", sid);
                v.put("sender", m.optString("s", "user"));
                v.put("message", m.optString("m", ""));
                v.put("timestamp", m.optLong("t", 0));
                db.insert("messages", null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        String title = o.optString("title", "");
        if (!title.isEmpty()) helper.renameSession(sid, title);
        if (o.optInt("pin", 0) == 1 && !helper.isSessionPinned(sid)) helper.togglePinSession(sid);
    }
}
