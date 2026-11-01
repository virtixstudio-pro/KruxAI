package com.virtixstudio.kruxai.core;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Quota de créations d'apps par utilisateur, gardé dans Firestore (collection krux_quotas, un document par uid).
 * Il survit à la désinstallation et à l'effacement des données, contrairement au compteur local.
 * Les règles Firestore (voir FIRESTORE_RULES.txt) empêchent de le remettre à zéro depuis l'app modifiée.
 */
public final class QuotaStore {

    public interface UsedCallback { void onUsed(int used); }

    private QuotaStore() { }

    private static DocumentReference doc(String uid) {
        return FirebaseFirestore.getInstance().collection("krux_quotas").document(uid);
    }

    /** Lit le nombre de créations utilisées cette semaine. N'appelle rien en cas d'échec (on garde le compteur local). */
    public static void fetch(String uid, final String week, final UsedCallback cb) {
        if (uid == null || uid.isEmpty()) return;
        doc(uid).get().addOnSuccessListener(d -> {
            if (!d.exists() || !week.equals(d.getString("week"))) { cb.onUsed(0); return; }
            Long u = d.getLong("used");
            cb.onUsed(u == null ? 0 : (int) Math.max(0, Math.min(1000, u)));
        });
    }

    /** Ajoute une création (transaction : deux appareils ne peuvent pas écraser le compteur l'un de l'autre). */
    public static void consume(final String uid, final String week) {
        if (uid == null || uid.isEmpty()) return;
        final DocumentReference ref = doc(uid);
        FirebaseFirestore.getInstance().runTransaction(tx -> {
            com.google.firebase.firestore.DocumentSnapshot d = tx.get(ref);
            int used = (d.exists() && week.equals(d.getString("week")) && d.getLong("used") != null)
                    ? d.getLong("used").intValue() : 0;
            Map<String, Object> m = new HashMap<>();
            m.put("week", week);
            m.put("used", used + 1);
            m.put("updated", System.currentTimeMillis());
            tx.set(ref, m);
            return null;
        });
    }
}
