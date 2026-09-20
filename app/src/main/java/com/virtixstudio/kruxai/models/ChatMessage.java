package com.virtixstudio.kruxai.models;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatMessage {

    private static android.content.Context appContext;

    public static void initializeContext(android.content.Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private static String resolveSessionId() {
        if (appContext == null) return null;

        return appContext
                .getSharedPreferences("krux_chat", android.content.Context.MODE_PRIVATE)
                .getString("current_session_id", null);
    }

    private String id;
    private String text;
    private boolean isUser;
    private String reasoning;
    private long timestamp;
    private List<SearchResult> sources;
    private String model;
    private String sessionId;
    private boolean streaming;

    public ChatMessage() {
        this.id = UUID.randomUUID().toString();
        this.reasoning = "";
        this.sources = new ArrayList<>();
        this.sessionId = resolveSessionId();
    }

    public ChatMessage(String text, boolean isUser) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.isUser = isUser;
        this.reasoning = "";
        this.timestamp = System.currentTimeMillis();
        this.sources = new ArrayList<>();
        this.sessionId = resolveSessionId();
    }

    public ChatMessage(String text, boolean isUser, List<SearchResult> sources) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.isUser = isUser;
        this.reasoning = "";
        this.timestamp = System.currentTimeMillis();
        this.sources = sources != null ? sources : new ArrayList<>();
        this.sessionId = resolveSessionId();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isUser() {
        return isUser;
    }

    public void setUser(boolean user) {
        isUser = user;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public List<SearchResult> getSources() {
        return sources;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setSources(List<SearchResult> sources) {
        this.sources = sources != null
                ? sources
                : new ArrayList<>();
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
