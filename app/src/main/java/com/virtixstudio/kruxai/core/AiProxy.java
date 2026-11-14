package com.virtixstudio.kruxai.core;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

/**
 * Accès au Worker proxy IA. Aucune clé dans l'app : on envoie le jeton Firebase de l'utilisateur
 * connecté, le Worker le vérifie puis appelle le fournisseur avec la clé stockée chez Cloudflare.
 * Les méthodes bloquent : à appeler hors du thread principal (comme les appels réseau d'ApiClient).
 */
public final class AiProxy {

    private AiProxy() { }

    public static boolean enabled() {
        return KruxConfig.aiProxyEnabled();
    }

    /** URL complète d'une route du proxy, ex. route("groq/chat"). */
    public static String url(String route) {
        return KruxConfig.aiProxyUrl() + "/ai/" + route;
    }

    /** Jeton Firebase de l'utilisateur connecté (renouvelé automatiquement s'il a expiré). */
    public static String idToken() throws Exception {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) throw new Exception("Connexion requise pour utiliser l'IA");
        String t = Tasks.await(u.getIdToken(false), 15, TimeUnit.SECONDS).getToken();
        if (t == null || t.isEmpty()) throw new Exception("Jeton Firebase indisponible");
        return t;
    }
}
