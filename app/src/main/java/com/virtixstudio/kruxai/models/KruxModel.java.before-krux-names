package com.virtixstudio.kruxai.models;

public enum KruxModel {

    // ============================================================
    // ⚡ PETITS MODÈLES
    // Économiques / rapides pour les demandes simples
    // ============================================================

    GEMINI_FLASH_LITE(
            "Gemini Flash Lite",
            "Flash Lite",
            "gemini-3.5-flash-lite",
            "GEMINI",
            1000000,
            ModelSize.PETIT
    ),

    GROQ_GPT_OSS_20B(
            "Groq GPT-OSS 20B",
            "GPT-OSS 20B",
            "openai/gpt-oss-20b",
            "GROQ",
            131072,
            ModelSize.PETIT
    ),

    MISTRAL_MINISTRAL_3B(
            "Mistral Ministral 3B",
            "Ministral 3B",
            "ministral-3b-latest",
            "MISTRAL",
            131072,
            ModelSize.PETIT
    ),

    MISTRAL_MINISTRAL_8B(
            "Mistral Ministral 8B",
            "Ministral 8B",
            "ministral-8b-latest",
            "MISTRAL",
            131072,
            ModelSize.PETIT
    ),

    HF_QWEN_CODER_7B(
            "Qwen Coder 7B",
            "Qwen Coder 7B",
            "Qwen/Qwen2.5-Coder-7B-Instruct",
            "HUGGINGFACE",
            131072,
            ModelSize.PETIT
    ),


    // ============================================================
    // 🧠 GRANDS MODÈLES
    // Pour raisonnement, code et tâches complexes
    // ============================================================

    GEMINI_36_FLASH(
            "Gemini 3.6 Flash",
            "3.6 Flash",
            "gemini-3.6-flash",
            "GEMINI",
            1000000,
            ModelSize.GRAND
    ),

    GROQ_GPT_OSS_120B(
            "Groq GPT-OSS 120B",
            "GPT-OSS 120B",
            "openai/gpt-oss-120b",
            "GROQ",
            131072,
            ModelSize.GRAND
    ),

    CEREBRAS_GPT_OSS_120B(
            "Cerebras GPT-OSS 120B",
            "Cerebras 120B",
            "gpt-oss-120b",
            "CEREBRAS",
            131072,
            ModelSize.GRAND
    ),

    MISTRAL_CODESTRAL(
            "Mistral Codestral",
            "Codestral",
            "codestral-latest",
            "MISTRAL",
            256000,
            ModelSize.GRAND
    ),

    HF_QWEN_CODER_32B(
            "Qwen Coder 32B",
            "Qwen Coder 32B",
            "Qwen/Qwen2.5-Coder-32B-Instruct",
            "HUGGINGFACE",
            131072,
            ModelSize.GRAND
    );


    // ============================================================
    // TYPE DE MODÈLE
    // ============================================================

    // ============================================================
    // COMPATIBILITE AVEC L'ANCIEN CODE
    // ============================================================

    public static final KruxModel KRUX_35_FLASH = GEMINI_36_FLASH;
    public static final KruxModel KRUX_33_70B = GROQ_GPT_OSS_120B;
    public static final KruxModel KRUX_SPEED_70B = CEREBRAS_GPT_OSS_120B;
    public static final KruxModel KRUX_CODEUR_PRO = MISTRAL_CODESTRAL;
    public static final KruxModel KRUX_CODEUR_32B = HF_QWEN_CODER_32B;


    public enum ModelSize {
        PETIT,
        GRAND
    }


    // ============================================================
    // PROPRIÉTÉS
    // ============================================================

    private final String displayName;
    private final String shortName;
    private final String modelId;
    private final String provider;
    private final int contextTokens;
    private final ModelSize size;


    // ============================================================
    // CONSTRUCTEUR
    // ============================================================

    KruxModel(
            String displayName,
            String shortName,
            String modelId,
            String provider,
            int contextTokens,
            ModelSize size
    ) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.modelId = modelId;
        this.provider = provider;
        this.contextTokens = contextTokens;
        this.size = size;
    }


    // ============================================================
    // GETTERS
    // ============================================================

    public String getDisplayName() {
        return displayName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getModelId() {
        return modelId;
    }

    public String getProvider() {
        return provider;
    }

    public int getContextTokens() {
        return contextTokens;
    }

    public ModelSize getSize() {
        return size;
    }


    // ============================================================
    // HELPERS
    // ============================================================

    public boolean isPetit() {
        return size == ModelSize.PETIT;
    }

    public boolean isGrand() {
        return size == ModelSize.GRAND;
    }


    public String getSizeLabel() {
        if (isPetit()) {
            return "PETIT";
        }

        return "GRAND";
    }


    // ============================================================
    // AFFICHAGE
    // ============================================================

    @Override
    public String toString() {
        return displayName;
    }
}
