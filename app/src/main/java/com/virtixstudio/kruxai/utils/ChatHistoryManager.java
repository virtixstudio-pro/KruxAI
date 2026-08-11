package com.virtixstudio.kruxai.utils;

import android.content.Context;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.models.ChatSession;

import java.util.List;

public class ChatHistoryManager {

    public static void saveMessage(Context context, String sessionId, ChatMessage message) {
        if (context == null || sessionId == null || message == null) return;
        KruxDatabaseHelper db = new KruxDatabaseHelper(context);
        db.saveMessage(sessionId, message.isUser() ? "user" : "ai", message.getText());
    }

    public static List<ChatSession> getAllSessions(Context context) {
        if (context == null) return java.util.Collections.emptyList();
        KruxDatabaseHelper db = new KruxDatabaseHelper(context);
        return db.getAllSessions();
    }
}
