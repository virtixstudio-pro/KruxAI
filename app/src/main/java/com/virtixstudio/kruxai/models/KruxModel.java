package com.virtixstudio.kruxai.models;

public enum KruxModel {

    // ============================================================
    // ⚡ PETITS MODÈLES
    // Économiques / rapides pour les demandes simples
    // ============================================================

    GEMINI_FLASH_LITE(
            "KRUX Lite",
            "KRUX Lite",
            "gemini-3.5-flash-lite",
            "GEMINI",
            1000000,
            ModelSize.PETIT
    ),

    GROQ_GPT_OSS_20B(
            "KRUX Swift",
            "KRUX Swift",
            "openai/gpt-oss-20b",
            "GROQ",
            131072,
            ModelSize.PETIT
    ),

    MISTRAL_MINISTRAL_3B(
            "KRUX Core",
            "KRUX Core",
            "ministral-3b-latest",
            "MISTRAL",
            131072,
            ModelSize.PETIT
    ),

    MISTRAL_MINISTRAL_8B(
            "KRUX Flow",
            "KRUX Flow",
            "ministral-8b-latest",
            "MISTRAL",
            131072,
            ModelSize.PETIT
    ),

    HF_QWEN_CODER_7B(
            "KRUX Code",
            "KRUX Code",
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
            "KRUX Prime",
            "KRUX Prime",
            "gemini-3.6-flash",
            "GEMINI",
            1000000,
            ModelSize.GRAND
    ),

    GROQ_GPT_OSS_120B(
            "KRUX Ultra",
            "KRUX Ultra",
            "openai/gpt-oss-120b",
            "GROQ",
            131072,
            ModelSize.GRAND
    ),

    CEREBRAS_GPT_OSS_120B(
            "KRUX Velocity",
            "KRUX Velocity",
            "gpt-oss-120b",
            "CEREBRAS",
            131072,
            ModelSize.GRAND
    ),

    MISTRAL_CODESTRAL(
            "KRUX Dev",
            "KRUX Dev",
            "codestral-latest",
            "MISTRAL",
            256000,
            ModelSize.GRAND
    ),

    HF_QWEN_CODER_32B(
            "KRUX Code Pro",
            "KRUX Code Pro",
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
