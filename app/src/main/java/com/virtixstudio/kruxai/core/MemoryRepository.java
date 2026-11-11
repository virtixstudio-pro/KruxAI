package com.virtixstudio.kruxai.core;

import com.virtixstudio.kruxai.database.KruxDatabaseHelper;
import com.virtixstudio.kruxai.utils.MemoryBook;
import com.virtixstudio.kruxai.utils.MemoryExtractor;

import java.util.List;

/**
 * Seule porte d'écriture de la mémoire de Krux.
 *
 * Avant, quatre endroits écrivaient directement dans SQLite (écran Mémoire, extraction après un
 * échange, commandes « retiens / oublie », import cloud). Un seul oubliait de prévenir la synchro
 * et la mémoire divergente apparaissait. Ici : une écriture = une notification, point.
 *
 * Le dépôt ne fait pas la synchro lui-même : il prévient un écouteur, qui décide (marquer la
 * mémoire comme « à remplacer », lancer une synchro…). Ça garde les tests et l'UI indépendants
 * du réseau.
 */
public final class MemoryRepository {

    public interface OnChange {
        void onMemoryChanged();
    }

    private final KruxDatabaseHelper db;
    private OnChange onChange;

    public MemoryRepository(KruxDatabaseHelper db) {
        this.db = db;
    }

    /** Appelé une fois après chaque écriture qui a réellement changé quelque chose. */
    public void setOnChange(OnChange listener) {
        this.onChange = listener;
    }

    private void notifyChanged(boolean changed) {
        if (changed && onChange != null) onChange.onMemoryChanged();
    }

    // ---------- lecture ----------

    public List<MemoryBook.Entry> all() {
        return db.getMemoryEntries();
    }

    public int count() {
        return db.getMemoryEntries().size();
    }

    public boolean has(String fact) {
        return db.hasMemoryFact(fact);
    }

    public int undoCount() {
        return db.memoryUndoCount();
    }

    public List<String> encoded() {
        return db.getMemoryEncoded();
    }

    public List<String> recentUserMessages(int limit) {
        return db.getRecentUserMessages(limit);
    }

    // ---------- écriture ----------

    /** Retient un fait (texte simple ou « §cat|clé|date|texte »). Vrai si la mémoire a changé. */
    public boolean remember(String fact) {
        boolean changed = db.upsertMemory(fact);
        notifyChanged(changed);
        return changed;
    }

    /** Applique les décisions du modèle. Renvoie le journal lisible (« mémorisé : … »). */
    public List<String> apply(List<MemoryBook.Op> ops) {
        List<String> log = db.applyMemoryOps(ops);
        notifyChanged(log != null && !log.isEmpty());
        return log;
    }

    /** « oublie que j'habite à Lyon » : retire les souvenirs qui correspondent. Renvoie les textes oubliés. */
    public List<String> forget(String target) {
        List<String> gone = db.forgetMemory(MemoryExtractor.forgetWords(target));
        notifyChanged(gone != null && !gone.isEmpty());
        return gone;
    }

    public void edit(String oldText, String newText) {
        db.editMemoryText(oldText, newText);
        notifyChanged(true);
    }

    public void delete(String text) {
        db.deleteMemoryText(text);
        notifyChanged(true);
    }

    public void clear() {
        db.clearMemory();
        notifyChanged(true);
    }

    /** Remet une liste complète (annulation d'une pastille, import…). */
    public void restore(List<MemoryBook.Entry> entries) {
        db.restoreMemory(entries);
        notifyChanged(true);
    }

    /** Remplace toute la mémoire par une liste de faits encodés ou simples (import depuis un autre appareil). */
    public void replaceAll(List<String> facts) {
        db.replaceMemory(facts);
        notifyChanged(true);
    }

    /** Annule la dernière modification journalisée. Vrai si quelque chose a été annulé. */
    public boolean undo() {
        boolean undone = db.undoLastMemory();
        notifyChanged(undone);
        return undone;
    }
}
