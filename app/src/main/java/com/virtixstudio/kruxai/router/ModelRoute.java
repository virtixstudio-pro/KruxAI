package com.virtixstudio.kruxai.router;

public class ModelRoute {

    private final String name;
    private final String provider;
    private final String model;
    private final boolean large;
    private final int priority;

    public ModelRoute(
            String name,
            String provider,
            String model,
            boolean large,
            int priority
    ) {
        this.name = name;
        this.provider = provider;
        this.model = model;
        this.large = large;
        this.priority = priority;
    }

    public String getName() {
        return name;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public boolean isLarge() {
        return large;
    }

    public int getPriority() {
        return priority;
    }
}
