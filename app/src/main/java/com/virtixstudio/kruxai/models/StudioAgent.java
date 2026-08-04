package com.virtixstudio.kruxai.models;

public class StudioAgent {
    private String name;
    private String role;

    public StudioAgent(String name, String role) {
        this.name = name;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }
}
