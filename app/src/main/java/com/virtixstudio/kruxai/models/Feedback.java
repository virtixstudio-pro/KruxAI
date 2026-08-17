package com.virtixstudio.kruxai.models;

public class Feedback {

    private String id;
    private String messageId;
    private String sessionId;
    private String userId;
    private String model;
    private String type;
    private String comment;
    private long timestamp;

    public Feedback() {
    }

    public Feedback(
            String messageId,
            String sessionId,
            String userId,
            String model,
            String type,
            String comment
    ) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.model = model;
        this.type = type;
        this.comment = comment;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
