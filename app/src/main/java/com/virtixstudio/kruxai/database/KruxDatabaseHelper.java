package com.virtixstudio.kruxai.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class KruxDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "kruxai.db";
    private static final int DATABASE_VERSION = 1;

    // Table Historique
    public static final String TABLE_CHATS = "chats";
    public static final String COL_CHAT_ID = "id";
    public static final String COL_SESSION_ID = "session_id";
    public static final String COL_SENDER = "sender";
    public static final String COL_MESSAGE = "message";
    public static final String COL_TIMESTAMP = "timestamp";

    // Table Mémoire IA
    public static final String TABLE_MEMORY = "user_memory";
    public static final String COL_MEM_ID = "id";
    public static final String COL_FACT = "fact";

    public KruxDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createChats = "CREATE TABLE " + TABLE_CHATS + " (" +
                COL_CHAT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SESSION_ID + " TEXT, " +
                COL_SENDER + " TEXT, " +
                COL_MESSAGE + " TEXT, " +
                COL_TIMESTAMP + " DATETIME DEFAULT CURRENT_TIMESTAMP)";

        String createMemory = "CREATE TABLE " + TABLE_MEMORY + " (" +
                COL_MEM_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_FACT + " TEXT UNIQUE)";

        db.execSQL(createChats);
        db.execSQL(createMemory);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHATS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MEMORY);
        onCreate(db);
    }

    // --- GESTION HISTORIQUE ---
    public void saveMessage(String sessionId, String sender, String message) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SESSION_ID, sessionId);
        values.put(COL_SENDER, sender);
        values.put(COL_MESSAGE, message);
        db.insert(TABLE_CHATS, null, values);
    }

    // --- GESTION MÉMOIRE IA ---
    public void addMemoryFact(String fact) {
        if (fact == null || fact.trim().isEmpty()) return;
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_FACT, fact.trim());
        db.insertWithOnConflict(TABLE_MEMORY, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public List<String> getAllMemories() {
        List<String> memories = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_FACT + " FROM " + TABLE_MEMORY, null);
        if (cursor.moveToFirst()) {
            do {
                memories.add(cursor.getString(0));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return memories;
    }

    public String getFormattedMemoryForSystemPrompt() {
        List<String> memories = getAllMemories();
        if (memories.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n\n[MÉMOIRE LOCALE DE L'UTILISATEUR]:\n");
        for (String fact : memories) {
            sb.append("- ").append(fact).append("\n");
        }
        return sb.toString();
    }
}
