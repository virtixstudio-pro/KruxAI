package com.clav.keyboard.ime;

/**
 * Offline French word list, roughly ordered by usage frequency so the
 * prefix-matching suggester surfaces the most useful completions first.
 */
final class Dictionary {

    static final String[] WORDS = {
            // Greetings & conversation
            "bonjour", "salut", "coucou", "merci", "oui", "non", "voilà", "d’accord", "super", "bravo",
            "génial", "parfait", "excellent", "incroyable", "magnifique", "formidable", "félicitations",
            // Frequent verbs
            "être", "avoir", "faire", "dire", "aller", "voir", "savoir", "pouvoir", "vouloir",
            "venir", "prendre", "donner", "parler", "aimer", "mettre", "passer", "devoir", "croire",
            "trouver", "rendre", "attendre", "regarder", "devenir", "rester", "porter", "montrer",
            "commencer", "penser", "entendre", "demander", "sentir", "revenir", "tenir", "tourner",
            "vivre", "marcher", "travailler", "jouer", "chanter", "danser", "manger", "boire",
            // Frequent adjectives
            "grand", "petit", "gros", "long", "court", "haut", "bas", "beau", "joli", "nouveau",
            "vieux", "jeune", "bon", "mauvais", "meilleur", "facile", "difficile", "simple",
            "compliqué", "important", "sérieux", "drôle", "marrant", "gentil", "content", "triste",
            "heureux", "malheureux", "fatigué", "reposé", "fort", "faible", "rapide", "lent",
            "chaud", "froid", "propre", "sale", "plein", "vide", "riche", "pauvre",
            // Adverbs & connectors
            "vraiment", "toujours", "jamais", "souvent", "parfois", "maintenant", "aujourd’hui",
            "demain", "hier", "tôt", "tard", "ici", "très", "trop", "assez", "bien", "mieux",
            "encore", "déjà", "aussi", "tout", "rien", "personne", "peut-être", "alors", "ensuite",
            "d’abord", "enfin", "pourtant", "malgré", "plutôt", "autre", "même", "seul",
            // Time
            "matin", "soir", "nuit", "semaine", "mois", "année", "heure", "minute", "temps",
            "jour", "moment", "instant", "saison", "lundi", "mardi", "mercredi", "jeudi",
            "vendredi", "samedi", "dimanche", "janvier", "février", "mars", "avril", "mai", "juin",
            "juillet", "août", "septembre", "octobre", "novembre", "décembre",
            // People & places
            "ami", "amie", "copain", "copine", "famille", "mère", "père", "fils", "fille", "frère",
            "sœur", "enfant", "bébé", "homme", "femme", "personne", "gens", "monde", "ville",
            "village", "rue", "route", "place", "quartier", "maison", "appartement", "chambre",
            "cuisine", "salon", "table", "chaise", "lit", "fenêtre", "porte", "jardin",
            // Work & objects
            "travail", "boulot", "métier", "bureau", "école", "professeur", "élève", "cours",
            "leçon", "devoir", "exercice", "projet", "idée", "question", "réponse", "problème",
            "solution", "exemple", "raison", "chose", "objet", "outil", "téléphone", "portable",
            "ordinateur", "application", "clavier", "écran", "message", "texte", "mot", "phrase",
            "lettre", "page", "livre", "histoire", "nouvelle", "article", "fichier", "photo",
            "image", "vidéo", "musique", "chanson", "film", "série", "jeu", "cadeau",
            // Nature & weather
            "voyage", "vacances", "plage", "montagne", "mer", "soleil", "lune", "étoile", "ciel",
            "nuage", "pluie", "neige", "vent", "orage", "température", "arbre", "fleur", "forêt",
            "champ", "eau", "feu", "terre", "pierre", "animal", "chat", "chien", "oiseau",
            // Misc frequent
            "argent", "prix", "euro", "euros", "carte", "clé", "sac", "valise", "habit",
            "chemise", "pantalon", "robe", "chaussure", "chapeau", "santé", "médecin", "pharmacie",
            "hôpital", "malade", "sport", "football", "tennis", "course", "vélo", "voiture",
            "train", "avion", "gare", "billet", "adresse"
    };

    private Dictionary() {
    }
}
