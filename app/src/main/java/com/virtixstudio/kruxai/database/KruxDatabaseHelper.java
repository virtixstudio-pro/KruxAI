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
    private static final int DATABASE_VERSION = 2;

    public KruxDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
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
                "fact TEXT)");

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
    }

    public void saveMessage(String sessionId, String sender, String text) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("session_id", sessionId);
        values.put("sender", sender);
        values.put("message", text);
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

        Cursor cursor = db.rawQuery("SELECT sender, message FROM messages WHERE session_id = ? ORDER BY id ASC", new String[]{sessionId});
        if (cursor.moveToFirst()) {
            do {
                String sender = cursor.getString(0);
                String text = cursor.getString(1);
                boolean isUser = "user".equalsIgnoreCase(sender);
                messages.add(new ChatMessage(text, isUser));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return messages;
    }

    public void saveMemoryFact(String fact) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("fact", fact);
        db.insert("memory", null, values);
    }

    public void addMemoryFact(String fact) {
        saveMemoryFact(fact);
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
}
