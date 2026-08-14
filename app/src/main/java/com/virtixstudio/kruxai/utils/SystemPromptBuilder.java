package com.virtixstudio.kruxai.utils;

public class SystemPromptBuilder {
    private String history = "";

    private final String BASE_SYSTEM_PROMPT = "Tu es Krux AI, une intelligence artificielle créée par Virtix Studio, un studio fondé par Persévérance (16 ans). Virtix Studio est présent sur les réseaux sociaux. Tu es direct, concis et efficace. Tu te bases sur l'historique fourni pour maintenir la cohérence de la discussion.";

    public SystemPromptBuilder withHistory(String historyContext) {
        if (historyContext != null && !historyContext.isEmpty()) {
            this.history = "\n\nHistorique de la conversation récente:\n" + historyContext;
        }
        return this;
    }

    public String build() {
        return BASE_SYSTEM_PROMPT + history;
    }
}
