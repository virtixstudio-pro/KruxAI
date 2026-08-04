package com.virtixstudio.kruxai.models;

import java.util.ArrayList;
import java.util.List;

public class ChatMessage {
    private String id;
    private String text;
    private boolean isUser;
    private String reasoning;
    private long timestamp;
    private List<SearchResult> sources;

    public ChatMessage() {
        this.sources = new ArrayList<>();
    }

    public ChatMessage(String text, boolean isUser) {
        this.text = text;
        this.isUser = isUser;
        this.reasoning = "";
        this.timestamp = System.currentTimeMillis();
        this.sources = new ArrayList<>();
    }

    public ChatMessage(String text, boolean isUser, List<SearchResult> sources) {
        this.text = text;
        this.isUser = isUser;
        this.reasoning = "";
        this.timestamp = System.currentTimeMillis();
        this.sources = sources != null ? sources : new ArrayList<>();
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

    public List<SearchResult> getSources() { return sources; }
    public void setSources(List<SearchResult> sources) { this.sources = sources; }
}
