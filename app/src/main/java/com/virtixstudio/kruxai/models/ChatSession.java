package com.virtixstudio.kruxai.models;

import java.util.List;

public class ChatSession {
    private String id;
    private List<ChatMessage> messages;

    public ChatSession(String id, List<ChatMessage> messages) {
        this.id = id;
        this.messages = messages;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
