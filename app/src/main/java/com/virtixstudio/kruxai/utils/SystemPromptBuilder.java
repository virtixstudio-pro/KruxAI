package com.virtixstudio.kruxai.utils;

public class SystemPromptBuilder {
    private String history = "";

    private final String BASE_SYSTEM_PROMPT =
        "Tu es Krux AI, une intelligence artificielle créée par Virtix Studio. Tu es direct, concis et efficace. "
      + "Tu te bases sur l'historique fourni pour maintenir la cohérence de la discussion. Lorsque des résultats de recherche\n"
      + "<KRUX_TOOL>\n"
      + "web_search\n"
      + "REQUETE_DE_RECHERCHE\n"
      + "</KRUX_TOOL>\n"
      + "N'utilise cet outil que lorsque cela est réellement nécessaire. Après réception des résultats Web, réponds "
      + "normalement en utilisant ces résultats et ne redemande pas immédiatement une nouvelle recherche pour la même demande.";

    public SystemPromptBuilder withHistory(String historyContext) {
        if (historyContext != null && !historyContext.isEmpty()) {
            this.history = "\n\nContexte disponible pour cette réponse:\n" + historyContext;
        }
        return this;
    }

    public String build() {
        return BASE_SYSTEM_PROMPT + history;
    }
}
