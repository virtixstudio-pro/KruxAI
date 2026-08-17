package com.virtixstudio.kruxai.models;

public class StudioAgent {

    private String name;
    private String role;
    private String objective;
    private KruxModel model;

    public StudioAgent(
            String name,
            String role,
            String objective,
            KruxModel model
    ) {
        this.name = name;
        this.role = role;
        this.objective = objective;
        this.model = model;
    }

    public String getName() {
        return name;
    }

    public String getRole() {
        return role;
    }

    public String getObjective() {
        return objective;
    }

    public KruxModel getModel() {
        return model;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setObjective(String objective) {
        this.objective = objective;
    }

    public void setModel(KruxModel model) {
        this.model = model;
    }

    public String buildSystemPrompt(String studioName, String studioObjective) {

        return
                "Tu es " + name + ", un agent IA du Studio \"" +
                studioName + "\" de KruxAI / Virtix Studio.\n\n" +

                "OBJECTIF GLOBAL DU STUDIO :\n" +
                studioObjective + "\n\n" +

                "TON RÔLE :\n" +
                role + "\n\n" +

                "TON OBJECTIF PERSONNEL :\n" +
                objective + "\n\n" +

                "RÈGLES DE L'AGENT :\n" +
                "- Reste concentré sur ton rôle.\n" +
                "- Donne des réponses concrètes et exploitables.\n" +
                "- Ne prétends pas connaître les réponses des autres agents.\n" +
                "- Identifie clairement les hypothèses ou incertitudes.\n" +
                "- Si tu détectes un problème, signale-le et propose une solution.\n" +
                "- Ne remplis pas inutilement la réponse.\n" +
                "- Réponds dans la langue utilisée par l'utilisateur.\n" +
                "- Tu participes à une équipe : ta réponse pourra être analysée par un autre agent.\n";
    }

    @Override
    public String toString() {
        return name + " • " + role + " • " +
                (model != null ? model.getDisplayName() : "Aucun modèle");
    }
}
