package com.virtixstudio.kruxai.core;

/**
 * Résultat structuré de l'analyse préalable d'une demande utilisateur.
 */
public final class ReflectionResult {

    public enum Intent {
        CHAT,
        CODE_GENERATION,
        CODE_DEBUG,
        WEB_RESEARCH,
        COMPARISON,
        PROJECT_BUILD,
        FILE_ANALYSIS,
        CALCULATION,
        GITHUB,
        MEMORY,
        CLOCK,
        MULTI_STEP
    }

    private final Intent intent;
    private final ToolRouter.Tool tool;
    private final double confidence;
    private final boolean needsWeb;
    private final boolean needsGithub;
    private final boolean needsCode;
    private final boolean multiStep;

    public ReflectionResult(
            Intent intent,
            ToolRouter.Tool tool,
            double confidence,
            boolean needsWeb,
            boolean needsGithub,
            boolean needsCode,
            boolean multiStep
    ) {
        this.intent = intent;
        this.tool = tool;
        this.confidence = confidence;
        this.needsWeb = needsWeb;
        this.needsGithub = needsGithub;
        this.needsCode = needsCode;
        this.multiStep = multiStep;
    }

    public Intent getIntent() {
        return intent;
    }

    public ToolRouter.Tool getTool() {
        return tool;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean needsWeb() {
        return needsWeb;
    }

    public boolean needsGithub() {
        return needsGithub;
    }

    public boolean needsCode() {
        return needsCode;
    }

    public boolean isMultiStep() {
        return multiStep;
    }
}
