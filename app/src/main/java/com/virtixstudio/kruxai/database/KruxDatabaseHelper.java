package com.virtixstudio.kruxai.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.ChatSession;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KruxDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "krux_ai.db";
    private static final int DATABASE_VERSION = 6;

    /** Nombre de modifications de mémoire qu'on peut annuler, de la plus récente à la plus ancienne. */
    private static final int MEMORY_LOG_MAX = 20;

    public KruxDatabaseHelper(Context context) {
        this(context, "default");
    }

    public KruxDatabaseHelper(Context context, String accountId) {
        super(
                context,
                DATABASE_NAME + "_" + safeAccountId(accountId),
                null,
                DATABASE_VERSION
        );
    }

    private static String safeAccountId(String accountId) {
        if (accountId == null || accountId.trim().isEmpty()) {
            return "default";
        }

        return accountId
                .trim()
                .replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "session_id TEXT, " +
                "sender TEXT, " +
                "message TEXT, " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)");

        db.execSQL("CREATE TABLE IF NOT EXISTS memory (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "fact TEXT, " +
                "cat TEXT, " +
                "slot TEXT, " +
                "updated INTEGER DEFAULT 0, " +
                "mid TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS session_metadata (" +
                "session_id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "is_pinned INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS memory_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "op TEXT, " +
                "snapshot TEXT, " +
                "created INTEGER DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS session_metadata (" +
                    "session_id TEXT PRIMARY KEY, " +
                    "title TEXT, " +
                    "is_pinned INTEGER DEFAULT 0)");
        }

        if (oldVersion < 3) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session ON messages(session_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_fact ON memory(fact)");
        }

        if (oldVersion < 4) {
            // Mémoire v2 : catégorie, clé (pour remplacer au lieu d'empiler) et date
            db.execSQL("ALTER TABLE memory ADD COLUMN cat TEXT");
            db.execSQL("ALTER TABLE memory ADD COLUMN slot TEXT");
            db.execSQL("ALTER TABLE memory ADD COLUMN updated INTEGER DEFAULT 0");
            // on range les anciens souvenirs ; l'ordre d'origine devient l'ordre des dates
            Cursor c = db.rawQuery("SELECT id, fact FROM memory ORDER BY id", null);
            long base = System.currentTimeMillis() / 1000L - 100000L;
            List<Object[]> rows = new ArrayList<>();
            while (c.moveToNext()) rows.add(new Object[]{c.getLong(0), c.getString(1)});
            c.close();
            int n = 0;
            for (Object[] r : rows) {
                com.virtixstudio.kruxai.utils.MemoryBook.Entry e =
                        com.virtixstudio.kruxai.utils.MemoryBook.classify(String.valueOf(r[1]), base + n++);
                ContentValues v = new ContentValues();
                v.put("cat", e.cat); v.put("slot", e.slot); v.put("updated", e.ts);
                db.update("memory", v, "id = ?", new String[]{String.valueOf(r[0])});
            }
        }

        if (oldVersion < 5) {
            // Mémoire v3 : journal des modifications, pour annuler autre chose que la dernière pastille
            db.execSQL("CREATE TABLE IF NOT EXISTS memory_log (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "op TEXT, " +
                    "snapshot TEXT, " +
                    "created INTEGER DEFAULT 0)");
        }

        if (oldVersion < 6) {
            // Mémoire v4 : identité stable d'un souvenir (mise à jour ciblée au lieu de tout réécrire)
            db.execSQL("ALTER TABLE memory ADD COLUMN mid TEXT");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_memory_mid ON memory(mid)");
            Cursor c = db.rawQuery("SELECT id, fact, cat, slot FROM memory", null);
            List<Object[]> rows = new ArrayList<>();
            while (c.moveToNext()) {
                rows.add(new Object[]{c.getLong(0), c.getString(1), c.getString(2), c.getString(3)});
            }
            c.close();
            for (Object[] r : rows) {
                String fact = r[1] == null ? "" : String.valueOf(r[1]);
                String cat = r[2] == null ? com.virtixstudio.kruxai.utils.MemoryBook.DIVERS : String.valueOf(r[2]);
                String slot = r[3] == null ? "" : String.valueOf(r[3]);
                ContentValues v = new ContentValues();
                v.put("mid", com.virtixstudio.kruxai.utils.MemoryBook.stableId(cat, slot, fact));
                db.update("memory", v, "id = ?", new String[]{String.valueOf(r[0])});
            }
        }
    }

    public void debugDuplicateMessages() {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT session_id, sender, message, COUNT(*) AS total " +
                "FROM messages " +
                "GROUP BY session_id, sender, message " +
                "HAVING COUNT(*) > 1 " +
                "ORDER BY total DESC",
                null
        );

        try {
            while (cursor.moveToNext()) {
                String sessionId = cursor.getString(0);
                String sender = cursor.getString(1);
                String message = cursor.getString(2);
                int total = cursor.getInt(3);

                android.util.Log.d(
                        "KRUX_DUPLICATES",
                        "session=" + sessionId +
                        " | sender=" + sender +
                        " | count=" + total +
                        " | message=" + message
                );
            }
        } finally {
            cursor.close();
        }
    }

    public boolean hasMessage(String sessionId, String sender, String text, long timestamp) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM messages WHERE session_id = ? AND sender = ? AND message = ? AND (timestamp = ? OR (typeof(timestamp) = 'integer' AND abs(timestamp - ?) < 2000)) LIMIT 1",
                new String[]{sessionId, sender, text, String.valueOf(timestamp), String.valueOf(timestamp)}
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }

    public boolean hasMessage(String sessionId, String sender, String text) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM messages WHERE session_id = ? AND sender = ? AND message = ? LIMIT 1",
                new String[]{sessionId, sender, text}
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }

    public boolean hasMemoryFact(String fact) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT 1 FROM memory WHERE fact = ? LIMIT 1",
                new String[]{fact}
        );

        boolean exists = cursor.moveToFirst();
        cursor.close();

        return exists;
    }

    public void saveMessage(String sessionId, String sender, String text) {
        saveMessage(sessionId, sender, text, System.currentTimeMillis());
    }

    public void saveMessage(String sessionId, String sender, String text, long timestamp) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("sender", sender);
        values.put("message", text);
        values.put("timestamp", timestamp);
        db.insert("messages", null, values);
    }

    public void renameSession(String sessionId, String newTitle) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("title", newTitle);
        db.insertWithOnConflict("session_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void togglePinSession(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        boolean isPinned = isSessionPinned(sessionId);
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("is_pinned", isPinned ? 0 : 1);
        db.insertWithOnConflict("session_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public boolean isSessionPinned(String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT is_pinned FROM session_metadata WHERE session_id = ?", new String[]{sessionId});
        boolean pinned = false;
        if (cursor.moveToFirst()) {
            pinned = cursor.getInt(0) == 1;
        }
        cursor.close();
        return pinned;
    }

    public String getSessionTitle(String sessionId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT title FROM session_metadata WHERE session_id = ?", new String[]{sessionId});
        String title = null;
        if (cursor.moveToFirst()) {
            title = cursor.getString(0);
        }
        cursor.close();
        return title;
    }

    public void deleteSession(String sessionId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("messages", "session_id = ?", new String[]{sessionId});
        db.delete("session_metadata", "session_id = ?", new String[]{sessionId});
    }

    public List<ChatSession> getAllSessions() {
        List<ChatSession> sessions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String query = "SELECT DISTINCT m.session_id, COALESCE(s.is_pinned, 0) as pinned " +
                       "FROM messages m " +
                       "LEFT JOIN session_metadata s ON m.session_id = s.session_id " +
                       "GROUP BY m.session_id " +
                       "ORDER BY pinned DESC, MAX(m.id) DESC";

        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                String sessionId = cursor.getString(0);
                List<ChatMessage> messages = getMessagesForSession(sessionId);
                sessions.add(new ChatSession(sessionId, messages));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return sessions;
    }

    public List<ChatMessage> getMessagesForSession(String sessionId) {
        List<ChatMessage> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT sender, message, timestamp " +
                "FROM messages " +
                "WHERE session_id = ? " +
                "ORDER BY id ASC",
                new String[]{sessionId}
        );
        if (cursor.moveToFirst()) {
            do {
                String sender = cursor.getString(0);
                String text = cursor.getString(1);
                long timestamp = cursor.getLong(2);
                boolean isUser = "user".equalsIgnoreCase(sender);
                ChatMessage message = new ChatMessage(text, isUser);
                message.setSessionId(sessionId);
                if (timestamp > 0) {
                    message.setTimestamp(timestamp);
                }
                messages.add(message);
            } while (cursor.moveToNext());
        }
        cursor.close();
        return messages;
    }

    /** Retient un souvenir (texte simple ou « §cat|clé|date|texte ») : il remplace ce qu'il contredit au lieu de s'empiler. */
    public void saveMemoryFact(String fact) {
        upsertMemory(fact);
    }

    public void addMemoryFact(String fact) {
        saveMemoryFact(fact);
    }

    /** Vrai si la mémoire a changé. */
    public synchronized boolean upsertMemory(String fact) {
        if (fact != null && !fact.trim().startsWith("§") && hasMemoryFact(fact.trim())) return false;
        com.virtixstudio.kruxai.utils.MemoryBook.Entry e =
                com.virtixstudio.kruxai.utils.MemoryBook.decode(fact, System.currentTimeMillis() / 1000L);
        if (e == null) return false;
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = copyMemoryList(list);
        if (!com.virtixstudio.kruxai.utils.MemoryBook.merge(list, e)) return false;
        logMemoryChange(before, "upsert");
        writeMemoryEntries(list);
        return true;
    }

    /** Applique les décisions du modèle ; retourne ce qu'on peut afficher. */
    public synchronized List<String> applyMemoryOps(List<com.virtixstudio.kruxai.utils.MemoryBook.Op> ops) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = copyMemoryList(list);
        List<String> log = com.virtixstudio.kruxai.utils.MemoryBook.apply(list, ops);
        if (!log.isEmpty()) {
            logMemoryChange(before, "learn");
            writeMemoryEntries(list);
        }
        return log;
    }

    /** Du plus ancien au plus récent. */
    public synchronized List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> getMemoryEntries() {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> out = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT fact, cat, slot, updated, mid FROM memory ORDER BY updated, id", null);
        while (c.moveToNext()) {
            String t = c.getString(0);
            if (t == null || t.trim().isEmpty()) continue;
            String cat = c.getString(1);
            com.virtixstudio.kruxai.utils.MemoryBook.Entry e =
                    new com.virtixstudio.kruxai.utils.MemoryBook.Entry(
                            cat == null ? com.virtixstudio.kruxai.utils.MemoryBook.DIVERS : cat,
                            c.getString(2), t.trim(), c.getLong(3));
            String mid = c.getString(4);
            if (mid != null && !mid.trim().isEmpty()) e.id = mid.trim();
            out.add(e);
        }
        c.close();
        return out;
    }

    /**
     * Enregistre la liste en ne touchant QUE ce qui change : chaque souvenir est retrouvé par son
     * identité stable (mid), mis à jour sur place, ajouté ou supprimé. Plus de « tout effacer puis
     * tout réinsérer ». Les identités vides (anciennes lignes) sont réinsérées puis nettoyées.
     */
    private void writeMemoryEntries(List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            LinkedHashMap<String, com.virtixstudio.kruxai.utils.MemoryBook.Entry> want = new LinkedHashMap<>();
            if (list != null) {
                for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : list) {
                    if (e == null || e.text == null || e.text.trim().isEmpty()) continue;
                    com.virtixstudio.kruxai.utils.MemoryBook.Entry c =
                            new com.virtixstudio.kruxai.utils.MemoryBook.Entry(e);
                    c.text = c.text.trim();
                    if (c.cat == null || c.cat.trim().isEmpty()) c.cat = com.virtixstudio.kruxai.utils.MemoryBook.DIVERS;
                    if (c.id == null || c.id.trim().isEmpty()) {
                        c.id = com.virtixstudio.kruxai.utils.MemoryBook.stableId(c.cat, c.slot, c.text);
                    }
                    want.put(c.id, c);
                }
            }

            // Lignes existantes, une par identité (la plus ancienne gagne).
            Map<String, Long> have = new HashMap<>();
            Cursor c = db.rawQuery(
                    "SELECT MIN(id), mid FROM memory WHERE mid IS NOT NULL AND mid <> '' GROUP BY mid", null);
            while (c.moveToNext()) have.put(c.getString(1), c.getLong(0));
            c.close();

            // Doublons d'identité : on ne garde que la ligne la plus ancienne.
            db.execSQL("DELETE FROM memory WHERE mid IS NOT NULL AND mid <> '' AND id NOT IN (" +
                    "SELECT MIN(id) FROM memory WHERE mid IS NOT NULL AND mid <> '' GROUP BY mid)");

            for (Map.Entry<String, Long> h : have.entrySet()) {
                if (!want.containsKey(h.getKey())) {
                    db.delete("memory", "id = ?", new String[]{String.valueOf(h.getValue())});
                }
            }

            for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : want.values()) {
                ContentValues v = new ContentValues();
                v.put("fact", e.text);
                v.put("cat", e.cat);
                v.put("slot", e.slot);
                v.put("updated", e.ts);
                v.put("mid", e.id);
                Long rowId = have.get(e.id);
                if (rowId != null) db.update("memory", v, "id = ?", new String[]{String.valueOf(rowId)});
                else db.insert("memory", null, v);
            }

            // Anciennes lignes sans identité : réinsérées juste au-dessus, on les retire.
            db.delete("memory", "mid IS NULL OR mid = ''", null);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Souvenirs encodés pour la synchronisation (gardent catégorie, clé et date). */
    public List<String> getMemoryEncoded() {
        List<String> out = new ArrayList<>();
        for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : getMemoryEntries()) out.add(com.virtixstudio.kruxai.utils.MemoryBook.encode(e));
        return out;
    }

    public List<String> getAllMemoryFacts() {
        List<String> facts = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT fact FROM memory", null);

        if (cursor.moveToFirst()) {
            do {
                facts.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return facts;
    }

    public String getFormattedMemoryForSystemPrompt() {
        StringBuilder memoryText = new StringBuilder();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT fact FROM memory", null);

        if (cursor.moveToFirst()) {
            memoryText.append("\n[MÉMOIRE CONSERVÉE SUR L'UTILISATEUR]:\n");
            do {
                memoryText.append("- ").append(cursor.getString(0)).append("\n");
            } while (cursor.moveToNext());
        }
        cursor.close();
        return memoryText.toString();
    }

    /** Oublie les souvenirs qui correspondent aux mots donnés ; retourne ceux qui ont été supprimés. */
    public synchronized List<String> forgetMemory(List<String> words) {
        List<String> removed = new ArrayList<>();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = copyMemoryList(list);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (com.virtixstudio.kruxai.utils.MemoryExtractor.matchesForget(list.get(i).text, words)) {
                removed.add(0, list.get(i).text);
                list.remove(i);
            }
        }
        if (!removed.isEmpty()) {
            logMemoryChange(before, "forget");
            writeMemoryEntries(list);
        }
        return removed;
    }

    /** Remplace toute la mémoire par cette liste (utilisé quand une suppression vient d'un autre appareil). */
    public synchronized void replaceMemory(List<String> facts) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = getMemoryEntries();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;
        for (String f : facts) {
            com.virtixstudio.kruxai.utils.MemoryBook.Entry e =
                    com.virtixstudio.kruxai.utils.MemoryBook.decode(f, now);
            if (e == null) continue;
            // On garde l'identité d'un souvenir identique déjà présent : pas de churn à la synchro.
            for (com.virtixstudio.kruxai.utils.MemoryBook.Entry old : before) {
                if (old.text != null && old.text.equalsIgnoreCase(e.text)) {
                    e.id = old.id;
                    break;
                }
            }
            com.virtixstudio.kruxai.utils.MemoryBook.merge(list, e);
        }
        if (!before.isEmpty() || !list.isEmpty()) logMemoryChange(before, "replace");
        writeMemoryEntries(list);
    }

    /** Supprime un souvenir précis (écran Mémoire). */
    public synchronized void deleteMemoryText(String text) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = copyMemoryList(list);
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).text.equals(text)) {
                list.remove(i);
                changed = true;
            }
        }
        if (changed) {
            logMemoryChange(before, "delete");
            writeMemoryEntries(list);
        }
    }

    /** Écran Mémoire : change le texte d'un souvenir en gardant sa catégorie et sa clé. */
    public synchronized void editMemoryText(String oldText, String newText) {
        if (newText == null || newText.trim().isEmpty()) return;
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = copyMemoryList(list);
        boolean changed = false;
        for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : list) {
            if (e.text.equals(oldText) && !e.text.equals(newText.trim())) {
                e.text = newText.trim();
                e.ts = System.currentTimeMillis() / 1000L;
                changed = true;
                break;
            }
        }
        if (changed) {
            logMemoryChange(before, "edit");
            writeMemoryEntries(list);
        }
    }

    /** Écran Mémoire : tout effacer. */
    public synchronized void clearMemory() {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before = getMemoryEntries();
        if (!before.isEmpty()) logMemoryChange(before, "clear");
        writeMemoryEntries(new ArrayList<com.virtixstudio.kruxai.utils.MemoryBook.Entry>());
    }

    /** Copie détachée d'une liste de souvenirs (les objets Entry sont modifiés en place par merge/apply). */
    private static List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> copyMemoryList(
            List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> in) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> out = new ArrayList<>();
        if (in == null) return out;
        for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : in) {
            out.add(new com.virtixstudio.kruxai.utils.MemoryBook.Entry(e));
        }
        return out;
    }

    /** Annuler : remet la mémoire telle qu'elle était. */
    public synchronized void restoreMemory(List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> entries) {
        writeMemoryEntries(entries);
    }

    // ---------- journal mémoire (annulation durable) ----------

    /**
     * Copies l'état de la mémoire AVANT une modification, pour pouvoir l'annuler plus tard.
     * Les souvenirs sont encodés en texte : la copie reste valable même si les objets changent.
     * Ne fait jamais échouer l'opération en cours.
     */
    private void logMemoryChange(List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> before, String op) {
        if (before == null) return;
        try {
            JSONArray a = new JSONArray();
            for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : before) {
                a.put(com.virtixstudio.kruxai.utils.MemoryBook.encode(e));
            }
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues();
            v.put("op", op == null ? "" : op);
            v.put("snapshot", a.toString());
            v.put("created", System.currentTimeMillis());
            db.insert("memory_log", null, v);
            db.execSQL("DELETE FROM memory_log WHERE id NOT IN (" +
                    "SELECT id FROM memory_log ORDER BY id DESC LIMIT " + MEMORY_LOG_MAX + ")");
        } catch (Exception ignore) {
            // Le journal ne doit jamais bloquer une écriture de mémoire.
        }
    }

    /** Nombre de modifications de mémoire encore annulables. */
    public synchronized int memoryUndoCount() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM memory_log", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public synchronized boolean hasMemoryUndo() {
        return memoryUndoCount() > 0;
    }

    /** Remet la mémoire dans l'état d'avant la dernière modification. Vrai si quelque chose a été annulé. */
    public synchronized boolean undoLastMemory() {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id, snapshot FROM memory_log ORDER BY id DESC LIMIT 1", null);
        if (!c.moveToFirst()) {
            c.close();
            return false;
        }
        long id = c.getLong(0);
        String snapshot = c.getString(1);
        c.close();

        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;
        try {
            JSONArray a = new JSONArray(snapshot == null ? "[]" : snapshot);
            for (int i = 0; i < a.length(); i++) {
                com.virtixstudio.kruxai.utils.MemoryBook.Entry e =
                        com.virtixstudio.kruxai.utils.MemoryBook.decode(a.optString(i, ""), now);
                if (e != null) list.add(e);
            }
        } catch (Exception e) {
            return false;
        }

        writeMemoryEntries(list);
        db.delete("memory_log", "id = ?", new String[]{String.valueOf(id)});
        return true;
    }

    /** Les derniers messages écrits par l'utilisateur, tous chats confondus (du plus ancien au plus récent). */
    public synchronized List<String> getRecentUserMessages(int limit) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT message FROM messages WHERE sender = 'user' ORDER BY id DESC LIMIT " + Math.max(1, limit), null);
        while (c.moveToNext()) {
            String m = c.getString(0);
            if (m != null && !m.trim().isEmpty()) out.add(0, m);
        }
        c.close();
        return out;
    }
}
