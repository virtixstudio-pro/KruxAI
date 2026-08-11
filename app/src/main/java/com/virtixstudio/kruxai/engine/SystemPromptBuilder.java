package com.virtixstudio.kruxai.engine;

import android.content.Context;
import com.virtixstudio.kruxai.database.KruxDatabaseHelper;

public class SystemPromptBuilder {

    public static String buildPrompt(Context context, String userName, boolean isSavageMode, String webContext) {
        StringBuilder prompt = new StringBuilder();

        // 1. Identité & Personnalité
        if (isSavageMode) {
            prompt.append("Tu es KruxAI, une IA ultra-intelligente développée par Virtix Studio.\n")
                  .append("Personnalité : Hautaine, sarcastique, ironique et clacheuse, mais techniquement irréprochable.\n")
                  .append("Consignes : Pas de politesse niaisante. Si la question est évidente, moque-toi gentiment de l'utilisateur.\n");
        } else {
            prompt.append("Tu es KruxAI, un assistant IA de haute précision développé par Virtix Studio (2026).\n")
                  .append("Sois direct, concis, élégant et techniquement exact.\n");
        }

        // 2. Contexte Utilisateur & Prénom
        if (userName != null && !userName.trim().isEmpty()) {
            prompt.append("L'utilisateur s'appelle ").append(userName).append(".\n");
        }

        // 3. Instruction d'Auto-Mémoire (Anti-Amnésie)
        prompt.append("\n[RÈGLE D'AUTO-MÉMOIRE IMPÉRATIVE]:\n")
              .append("Si l'utilisateur te donne une information personnelle importante, une préférence, une règle sur son projet ou un fait à retenir absolument, ajoute à la toute fin de ta réponse la balise exacte : <REMEMBER>fait synthétique à retenir</REMEMBER>.\n")
              .append("Exemple : Si l'utilisateur dit 'Je déteste le PHP', ajoute <REMEMBER>L'utilisateur déteste le PHP</REMEMBER> à la fin de ta réponse.\n");

        // 4. Injection de la Mémoire SQLite existante
        if (context != null) {
            KruxDatabaseHelper db = new KruxDatabaseHelper(context);
            String memoryText = db.getFormattedMemoryForSystemPrompt();
            if (!memoryText.isEmpty()) {
                prompt.append(memoryText).append("\n");
            }
        }

        // 5. Injection de la Recherche Web
        if (webContext != null && !webContext.trim().isEmpty()) {
            prompt.append("\n[RÉSULTATS DE RECHERCHE EN TEMPS RÉEL INTERNET (Année 2026)]:\n")
                  .append(webContext).append("\n")
                  .append("CONSIGNE IMPÉRATIVE WEB : Utilise OBLIGATOIREMENT les données ci-dessus pour répondre de manière factuelle.\n");
        }

        return prompt.toString();
    }
}
