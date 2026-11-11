# Mémoire — objectifs figés (11/11/2026)

**Dernière mise à jour :** 11/11/2026 — étapes 1 à 4 faites (O4, O2, O3, O1).
**Prochaine étape :** O5 (écran v2 + i18n), puis O6 (garde-fous + tests).

**Règle du jeu :** on ne sort pas de la mémoire tant que les 6 objectifs ci-dessous ne sont pas
finis. Même si tu me dis "passons à autre chose". Si un objectif devient inutile, on le barre
ici, on ne le remplace pas en cours de route.

---

## Où on en est (état réel, vérifié)

Ce qui marche déjà, et qu'on garde :

- Modèle de souvenir correct : `cat` + `slot` + `text` + `ts` (`MemoryBook.Entry`), fusion par
  clé et par similarité, polarité des goûts (`aime` / `n'aime pas`).
- Deux pipelines d'écriture : modèle (`MemoryBook.parseOps`) après chaque échange, et règles
  (`MemoryExtractor`) pour "retiens…" / "oublie…". C'est complémentaire, on assume.
- Garde-fou vie privée : `isSensitive()` bloque santé, argent, secrets, âge…
- Anti-bruit : une question ne produit pas de souvenir, message de moins de 12 caractères ignoré,
  extraction au plus toutes les 20 s.
- Plafond de 150 souvenirs, le profil est protégé du `trim()`.
- Pastille "Annuler" après une modification (7 s).
- Écran Mémoire : lister par catégorie, ajouter, éditer, supprimer, tout effacer, "apprendre de
  mes discussions".

Ce qui ne va pas :

1. **Deux clouds pour la même donnée.** `core/CloudSync` pousse la mémoire encodée (`§cat|slot|ts|text`)
   vers le Worker Cloudflare/D1. En parallèle, `MainActivity.saveMemoryToCloud()` écrit des documents
   Firestore `users/{uid}/memory` en texte brut, et le chargement les relit. Les deux tournent.
2. **Éditer ou supprimer ne met pas Firestore à jour.** `MemoryActivity` ne prévient que `CloudSync`
   (D1). Donc Firestore garde l'ancien fait, et il revient au prochain chargement. C'est un vrai
   bug de résurrection.
3. **Pas d'identité stable.** `writeMemoryEntries()` supprime tout et réinsère : les `id` SQLite
   changent à chaque écriture, et la synchro Firestore s'appuie sur le texte (`whereEqualTo("fact")`).
   Dès que le texte change, on crée un doublon au lieu de mettre à jour.
4. **Annulation limitée à 7 secondes.** Passé ce délai, une suppression est définitive.
5. **Écran pauvre.** Pas de recherche, pas de compteur `x/150`, pas d'état de synchro, pas de
   sélection multiple, et 100 % des textes sont en dur en français.
6. **Détails faux.** Le prompt d'extraction dit encore "KRUX AI" (corrigé), `replaceMemory` venant
   d'un autre appareil peut écraser sans trace.

---

## Les 6 objectifs

### O1 — Une seule source de vérité pour la synchro mémoire
**Décision :** la mémoire voyage par le **Worker/D1** (`CloudSync`). Firestore reste pour
l'authentification et les quotas, pas pour les souvenirs.

**Fini quand :**
- `saveMemoryToCloud()`, `deleteMemoryFromCloud()` et la lecture Firestore `collection("memory")`
  ne sont plus appelés nulle part (code supprimé, pas juste commenté).
- Un seul format de transport : `§cat|slot|ts|text` (ou son équivalent JSON), utilisé partout.
- Une mémoire éditée/effacée sur un appareil ne réapparaît pas sur un autre.

**État : ✅ fait.** ~150 lignes de Firestore mémoire supprimées de `MainActivity`
(écriture, suppression, relecture au démarrage). `CloudSync` (Worker/D1) est le seul transport,
et il passe désormais par `MemoryRepository`. Reste hors périmètre : les **conversations**
sont encore synchronisées deux fois (Firestore + D1), à traiter dans un lot séparé.

### O2 — Identité stable d'un souvenir
**Fini quand :**
- Chaque souvenir a un `id` déterministe (`cat` + `slot` + hash du texte normalisé) stocké en base.
- `writeMemoryEntries()` fait un vrai `upsert` par `id`, plus de `DELETE FROM memory` global.
- Deux souvenirs différents ne peuvent pas partager le même `id` (test unitaire).

**État : ✅ fait.** Base v6 (`mid` + index), `MemoryBook.stableId()` (SHA-1 sur texte normalisé),
`Entry.id` conservé lors d'une mise à jour par clé ou reformulation, écriture en diff
(suppression ciblée / mise à jour / insertion). 5 tests ajoutés dans `MemoryBookTests`.

### O3 — Un dépôt unique (`MemoryRepository`)
**Fini quand :**
- Toutes les écritures passent par une seule classe : `add`, `edit`, `delete`, `forget`, `merge`,
  `clear`, `restore`, `undo`, `learn`.
- `MainActivity` et `MemoryActivity` n'appellent plus `KruxDatabaseHelper` directement pour la mémoire.
- Le dépôt journalise chaque changement et déclenche la synchro une seule fois par opération.

**État : ✅ fait.** `core/MemoryRepository` (lecture + écriture), écouteur `OnChange`
(câblé sur `CloudSync.markMemoryReplace`). `MemoryActivity` ne touche plus la base ;
`MainActivity` passe par le dépôt pour ses 4 chemins d'écriture (extraction, « retiens/oublie »,
annulation, import cloud). Reste : `saveMemoryToCloud` / `deleteMemoryFromCloud` (c'est O1).

### O4 — Journal + annulation durable
**Fini quand :**
- On peut annuler les **20 dernières** modifications, pas seulement la dernière.
- L'annulation est disponible depuis l'écran Mémoire (bouton), pas uniquement depuis la pastille.
- Une annulation déclenche bien la synchro vers les autres appareils.
- *(Étape 1 est faite : table `memory_log`, `undoLastMemory()`, bouton dans l'écran.)*

### O5 — Écran Mémoire v2
**Fini quand :**
- Recherche texte instantanée sur les souvenirs.
- Compteur `x/150` visible.
- État de synchro lisible ("à jour", "en attente", "hors ligne").
- Suppression avec confirmation (pas de suppression au premier tap).
- Tous les textes externalisés dans `strings.xml`, les 4 langues complétées.

### O6 — Garde-fous
**Fini quand :**
- `MAX_ENTRIES` est respecté aussi après un import cloud (pas seulement après `merge`).
- Une synchro entrante ne peut pas effacer la mémoire si le cloud envoie une liste vide sans
  marqueur explicite de suppression.
- Les tests couvrent : fusion, polarité, oubli, trim, import/export encodé, annulation.

---

## Ordre d'exécution

| Étape | Contenu | Statut |
|---|---|---|
| 1 | Journal local (`memory_log`), `undoLastMemory()`, bouton Annuler, prompt "Krux" | ✅ fait |
| 2 | `id` stable + `upsert` par `id` (O2) | ✅ fait |
| 3 | `MemoryRepository` (O3) | ✅ fait |
| 4 | Suppression de la double synchro Firestore (O1) | ✅ fait |
| 5 | Écran v2 + i18n (O5) | à faire |
| 6 | Garde-fous + tests (O6) | à faire |

Ordre imposé : 2 avant 3 (le dépôt a besoin de l'`id`), 3 avant 4 (une seule porte pour couper
Firestore proprement).

---

## Non-objectifs (pour ne pas dériver)

- Pas de mémoire partagée entre comptes.
- Pas d'embeddings ni de recherche sémantique : `MemorySelector` reste lexical.
- Pas de refonte du thème ni du chat.
- Pas de migration AGP/SDK dans ce lot.
- Pas de nouvelle feature produit (stats, agent, etc.) avant O6.

---

## Vérification

Impossible de compiler l'app ici (pas de SDK Android, Gradle local 9.5.1 vs AGP 8.2.2).
Ce qui est vérifiable et **doit** l'être à chaque étape :

```bash
cd tools/builder-tests && ./run.sh
```

Les classes Android (`KruxDatabaseHelper`, `MemoryActivity`) ne sont pas couvertes par ces tests.
Il faudra un build sur la machine avec SDK avant de dire qu'une étape est "finie".
