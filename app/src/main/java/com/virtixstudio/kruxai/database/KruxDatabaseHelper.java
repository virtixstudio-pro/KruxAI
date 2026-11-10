package com.virtixstudio.kruxai.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.ChatSession;

import java.util.ArrayList;
import java.util.List;

public class KruxDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "krux_ai.db";
    private static final int DATABASE_VERSION = 4;

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
                "updated INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE IF NOT EXISTS session_metadata (" +
                "session_id TEXT PRIMARY KEY, " +
                "title TEXT, " +
                "is_pinned INTEGER DEFAULT 0)");
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
        if (!com.virtixstudio.kruxai.utils.MemoryBook.merge(list, e)) return false;
        writeMemoryEntries(list);
        return true;
    }

    /** Applique les décisions du modèle ; retourne ce qu'on peut afficher. */
    public synchronized List<String> applyMemoryOps(List<com.virtixstudio.kruxai.utils.MemoryBook.Op> ops) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        List<String> log = com.virtixstudio.kruxai.utils.MemoryBook.apply(list, ops);
        if (!log.isEmpty()) writeMemoryEntries(list);
        return log;
    }

    /** Du plus ancien au plus récent. */
    public synchronized List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> getMemoryEntries() {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> out = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT fact, cat, slot, updated FROM memory ORDER BY updated, id", null);
        while (c.moveToNext()) {
            String t = c.getString(0);
            if (t == null || t.trim().isEmpty()) continue;
            String cat = c.getString(1);
            out.add(new com.virtixstudio.kruxai.utils.MemoryBook.Entry(
                    cat == null ? com.virtixstudio.kruxai.utils.MemoryBook.DIVERS : cat,
                    c.getString(2), t.trim(), c.getLong(3)));
        }
        c.close();
        return out;
    }

    private void writeMemoryEntries(List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("memory", null, null);
            for (com.virtixstudio.kruxai.utils.MemoryBook.Entry e : list) {
                ContentValues v = new ContentValues();
                v.put("fact", e.text); v.put("cat", e.cat); v.put("slot", e.slot); v.put("updated", e.ts);
                db.insert("memory", null, v);
            }
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
        for (int i = list.size() - 1; i >= 0; i--) {
            if (com.virtixstudio.kruxai.utils.MemoryExtractor.matchesForget(list.get(i).text, words)) {
                removed.add(0, list.get(i).text);
                list.remove(i);
            }
        }
        if (!removed.isEmpty()) writeMemoryEntries(list);
        return removed;
    }

    /** Remplace toute la mémoire par cette liste (utilisé quand une suppression vient d'un autre appareil). */
    public synchronized void replaceMemory(List<String> facts) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = new ArrayList<>();
        long now = System.currentTimeMillis() / 1000L;
        for (String f : facts) com.virtixstudio.kruxai.utils.MemoryBook.merge(list, com.virtixstudio.kruxai.utils.MemoryBook.decode(f, now));
        writeMemoryEntries(list);
    }

    /** Supprime un souvenir précis (écran Mémoire). */
    public synchronized void deleteMemoryText(String text) {
        List<com.virtixstudio.kruxai.utils.MemoryBook.Entry> list = getMemoryEntries();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).text.equals(text)) list.remove(i);
        writeMemoryEntries(list);
    }
}
