package com.virtixstudio.kruxai.models;

public enum KruxModel {

    KRUX_35_FLASH(
            "Krux 3.5 Flash",
            "gemini-2.5-flash",
            "GEMINI"
    ),

    KRUX_33_70B(
            "Krux 3.3 70B",
            "llama-3.1-8b-instant",
            "GROQ"
    ),

    KRUX_SPEED_70B(
            "Krux Speed 70B",
            "llama-3.1-8b-instant",
            "CEREBRAS"
    ),

    KRUX_CODEUR_PRO(
            "Krux Codeur Pro",
            "codestral-latest",
            "MISTRAL"
    ),

    KRUX_CODEUR_32B(
            "Krux Codeur 32B",
            "Qwen/Qwen2.5-Coder-32B-Instruct",
            "HUGGINGFACE"
    );

    private final String displayName;
    private final String modelId;
    private final String provider;

    KruxModel(String displayName, String modelId, String provider) {
        this.displayName = displayName;
        this.modelId = modelId;
        this.provider = provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getModelId() {
        return modelId;
    }

    public String getProvider() {
        return provider;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
