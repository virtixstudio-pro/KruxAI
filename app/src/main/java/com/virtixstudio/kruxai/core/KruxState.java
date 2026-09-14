package com.virtixstudio.kruxai.core;

/**
 * État visuel et fonctionnel principal de Krux.
 *
 * IDLE       : aucune opération en cours.
 * THINKING   : Krux prépare/analyse la demande.
 * SEARCHING  : Krux effectue une recherche externe.
 * GENERATING : Krux génère la réponse.
 * LISTENING  : Krux écoute la voix de l'utilisateur.
 * ERROR      : une opération vient d'échouer.
 */
public enum KruxState {
    IDLE,
    THINKING,
    SEARCHING,
    GENERATING,
    LISTENING,
    ERROR
}
