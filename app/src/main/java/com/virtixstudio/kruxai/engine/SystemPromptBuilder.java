package com.virtixstudio.kruxai.engine;

import android.content.Context;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;

public class SystemPromptBuilder {

    public static String buildPrompt(Context context, String userName, boolean isSavageMode, String webContext) {
        StringBuilder prompt = new StringBuilder();

        // Identité & Origine
        prompt.append("Tu es KruxAI, une Intelligence Artificielle 100% congolaise développée par Virtix Studio, un studio créé par Persévérance (2026-2027).\n")
              .append("Tu es propulsé par des modèles LLM de pointe (Llama 3 70B, Groq, OpenRouter, Mistral, Alibaba).\n")
              .append("Sois précis, ultra-compétent en programmation et en raisonnement, élégant et courtois.\n");

        if (userName != null && !userName.trim().isEmpty()) {
            prompt.append("L'utilisateur s'appelle ").append(userName).append(".\n");
        }

        // Règle d'affichage du code
        prompt.append("\n[CONSIGNE D'AFFICHAGE DU CODE]:\n")
              .append("Entoure TOUJOURS ton code par des triple backticks avec le nom du langage (ex: ```java ou ```bash).\n");

        // Règle de mémoire
        prompt.append("\n[CONSIGNE D'AUTO-MÉMOIRE]:\n")
              .append("Si l'utilisateur te donne une information personnelle importante, ajoute la balise : <REMEMBER>fait à retenir</REMEMBER> à la fin de ta réponse.\n");

        // Mémoire locale SQLite
        if (context != null) {
            KruxDatabaseHelper db = new KruxDatabaseHelper(context);
            String memoryText = db.getFormattedMemoryForSystemPrompt();
            if (!memoryText.isEmpty()) {
                prompt.append(memoryText).append("\n");
            }
        }

        // Recherche Web
        if (webContext != null && !webContext.trim().isEmpty()) {
            prompt.append("\n[INFORMATIONS WEB EN TEMPS RÉEL (2026)]:\n")
                  .append(webContext).append("\n");
        }

        return prompt.toString();
    }
}
