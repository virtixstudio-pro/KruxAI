package com.virtixstudio.kruxai.models;

public class ChatMessage {
    private String id;
    private String text;
    private boolean isUser;
    private String reasoning;
    private long timestamp;

    // Constructeur vide requis pour la désérialisation Firebase
    public ChatMessage() {
    }

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.reasoning = "";
        this.timestamp = System.currentTimeMillis();
    }

    public ChatMessage(String text, boolean isUser, String reasoning) {
        this.text = text;
        this.isUser = isUser;
        this.reasoning = reasoning;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public boolean isUser() { return isUser; }
    public void setUser(boolean user) { isUser = user; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
