# Krux — analyse du projet (11/11/2026)

Bon. J'ai lu le bordel. Voilà ce qui va, ce qui va pas, et ce qu'on peut ajouter.
Tout ce qui est écrit en dessous a été vérifié dans le code, pas au feeling. Les lignes
indiquées sont celles du fichier au moment de l'analyse (branche `main`, commit `adbd23a`).

**Ce que je n'ai PAS pu faire :** compiler. Il n'y a pas de SDK Android sur la machine
(`ANDROID_HOME` vide, pas de `~/Android`), et le Gradle installé est le **9.5.1** alors que
le projet tourne sur **AGP 8.2.2** (AGP 8.2 ne supporte pas Gradle 9). Donc le build est
"à l'aveugle" : juge pas la stabilité du build sur ce doc.

---

## Journal des mises à jour

**11/11/2026 — Mémoire v3, étapes 1 à 4 (voir `OBJECTIFS-MEMOIRE.md`)**

| Objectif | État |
|---|---|
| O4 — journal local + annulation durable | ✅ base v5, `memory_log`, bouton dans l'écran |
| O2 — identité stable d'un souvenir | ✅ base v6, `mid`, écriture en diff |
| O3 — dépôt unique d'écriture | ✅ `core/MemoryRepository` |
| O1 — une seule synchro mémoire | ✅ Firestore mémoire supprimé (~150 lignes), D1 seul |

Conséquences sur ce document :
- Le point **3.2** (quota cloud) et **3.3** (limite 20/jour) restent d'actualité.
- Le point **8** (« KRUX AI » encore affiché) est partiellement corrigé : le prompt mémoire est
  passé à « Krux », reste `SettingsActivity:110`.
- La proposition **« Mémoire v3 »** (section 4) est en cours : journal + undo sont faits.
- Le README et les règles Firestore n'ont pas encore été mis à jour pour refléter la coupe.
- Les **conversations** sont toujours synchronisées deux fois (Firestore + D1) : hors périmètre
  du lot mémoire, à traiter séparément.

---

## 1. Le projet en chiffres

| | |
|---|---|
| Fichiers Java | 79 |
| Lignes de Java | 18 191 |
| Fichiers XML de layout | 20 (dont 6 jamais utilisés) |
| Chaînes traduites | 59 × 4 langues (fr, en, es, zh) — complet |
| Tests | 15 classes, mais **jamais lancés en CI** |
| Fichiers de sauvegarde `.bak` / `.before-*` | 37 (dont 6 encore versionnés dans Git) |
| Branches quasi identiques | 3 (`feat/krux-memory-profil`, `-profile`, `-profill`) |

Les 3 plus gros fichiers :

- `ui/MainActivity.java` — **3 960 lignes**
- `adapters/ChatAdapter.java` — **1 446 lignes**
- `api/ApiClient.java` — **817 lignes**

---

## 2. Comment ça marche vraiment (pas ce que dit le README)

Le README raconte une archi propre. La réalité est un peu différente :

**Chat :** `MainActivity` → `ApiClient.runChain()` → 10 modèles en cascade (`KruxModel`),
avec cooldown sur les 429 (`ModelCooldown`), streaming SSE réel (`onPartialResponse`), et
recherche web dans la boucle (`sendWithWebLoop` → `WebSearchEngine`). Ça, c'est carré.

**Le routeur :** `router/ModelRouter.java` (441 lignes) **n'est appelé nulle part.** Zéro
référence hors de lui-même. Le vrai routage est dans `ApiClient`. Le README dit que le
routage c'est `ModelRouter` : c'est faux.

**Base locale :** `database/KruxDatabaseHelper.java` (SQLite brut, 444 lignes, 3 tables) est
utilisé partout. La couche `db/` (Room : `AppDatabase`, `MemoryDao`, `MemoryEntity`) **n'est
allumée par aucun écran.** Les dépendances Room sont quand même dans `app/build.gradle`.

**Recherche web :** `WebSearchEngine` est utilisée. `WebSearchService`, `TavilyApiClient` et
`WebScraper` : **zéro référence.** Trois implémentations pour un seul usage réel.

**Fonctions mortes :** `ReflectionEngine` (326 lignes), `CloudinaryStore`, `KruxDotsView`
→ aucun appelant. Le README annonce "réflexion", "Cloudinary", les "dots" : dans les faits,
non.

---

## 3. Les incohérences, classées

### 🔴 P0 — à régler avant de distribuer

**1. `targetSdk 34` = refus Google Play.**
Le manifeste et `app/build.gradle` sont en compile/target **34**. La config du projet
(`krux_config.json`) sait déjà que depuis le **31/08/2026** il faut cibler **API 36**, et que
AGP 9.4 / Gradle 9.6 sont les versions qui vont avec. Résultat : le build réel est en retard
sur ce que la doc du projet raconte.
→ Passer `compileSdk`/`targetSdk` à 36, migrer AGP + Gradle, vérifier les changements
Android 14/15/16 (services foreground, permissions).

**2. Le quota cloud ne fonctionne pas.**
`core/QuotaStore.java` lit et écrit la collection Firestore **`krux_quotas`**. Cette
collection **n'existe pas dans `firestore.rules`** → refus par défaut (`allow read, write` non
déclaré). Et `fetch()` n'a qu'un `addOnSuccessListener`, aucun `addOnFailureListener` : tu ne
sauras jamais que ça échoue. Le commentaire du fichier dit "les règles empêchent de le
remettre à zéro" : c'est faux, il n'y a pas de règle du tout.
→ Soit ajouter le bloc `krux_quotas` aux règles, soit tout ramener sur `users/{uid}/usage`.
Un seul système, pas deux.

**3. La limite "20 requêtes/jour" n'existe pas.**
Les règles Firestore déclarent `/users/{uid}/usage/{day}` avec un plafond à 20. Aucun bout de
code Java ne lit ni n'écrit ce document (`grep` sur `"usage"` : zéro). Le README (ligne 229)
l'annonce comme une protection. C'est de la déco.
→ Soit on l'implémente côté client (et le serveur le vérifie déjà), soit on retire la règle et
la ligne du README.

**4. La signature release n'existe pas.**
Pas de `signingConfig`, pas de `proguard-rules.pro` (le fichier est référencé dans
`app/build.gradle` mais **absent du dépôt**), `minifyEnabled false`. Le README le reconnaît
("release non signé par défaut"), mais ça veut dire qu'aujourd'hui on ne peut pas publier une
release propre.
→ Ajouter `proguard-rules.pro`, une config de signature via variables d'env (keystore hors
dépôt), et activer R8 sur release.

**5. Clés API dans l'APK + `allowBackup="true"`.**
Les clés Groq/Cerebras/Mistral/HF/Gemini sont injectées dans `BuildConfig`, donc extractibles
de l'APK. Le README prévient pour le debug, mais rien ne bloque techniquement : `allowBackup`
est activé sans `dataExtractionRules`, et `REQUEST_INSTALL_PACKAGES` est déclarée (installation
d'APK depuis `MainActivity:2221` et `MyAppsActivity:179`).
→ Au minimum : désactiver le backup des préférences sensibles, et à terme passer les appels
fournisseurs par le Worker Cloudflare (la clé reste côté serveur). C'est le vrai fix.

### 🟠 P1 — incohérences qui coûtent cher

**6. Deux mondes pour la même chose (le pattern principal du projet).**

| Sujet | Utilisé | Mort |
|---|---|---|
| Routage IA | `api/ApiClient` | `router/ModelRouter` (441 l.) |
| Base locale | `database/KruxDatabaseHelper` | `db/*` Room (3 fichiers + 2 deps Gradle) |
| Recherche web | `api/WebSearchEngine` | `WebSearchService`, `TavilyApiClient`, `web/WebScraper` |
| Réflexion | — | `core/ReflectionEngine` (326 l.) |
| Stockage apps | `WorkerStore` | `CloudinaryStore` |

→ Choisir, supprimer le reste. Là on maintient deux fois le même travail, et le lecteur ne
sait jamais quel chemin est le bon.

**7. Le multilingue est faux à moitié.**
Les 4 fichiers `strings.xml` sont complets (59 chaînes chacun, bravo). Mais dans le code :
**26 `Toast`/`AlertDialog` avec du texte français en dur** et seulement **5 appels
`getString(R.string...)`**. Donc un utilisateur en anglais ou en chinois voit quand même du
français dès qu'il y a une erreur.
→ Extraire les littéraux dans `strings.xml` (les 26 sont listables en une commande, cf. annexe).

**8. Le renommage "KRUX AI" → "Krux" n'est pas fini.**
`ui/SettingsActivity.java:110` affiche encore **"KRUX AI"** dans À propos, et
`utils/MemoryBook.java:250` dit au modèle "Tu es le gestionnaire de mémoire de KRUX AI".
Visible par l'utilisateur ET injecté dans le prompt.
→ Corriger les deux, et grep le reste.

**9. Deux calculs de "semaine" différents (mais identiques, donc dupliqués).**
`MainActivity.quotaWeek()` et `MyAppsActivity.week()` font exactement la même chose, à deux
endroits. Basé sur `Calendar.WEEK_OF_YEAR`, donc dépendant de la locale du téléphone : deux
appareils peuvent ne pas être d'accord sur le numéro de semaine, donc sur le reset du quota.
→ Une seule classe `QuotaWeek` (et idéalement ISO-8601 : `Year-Www`).

**10. Retry réseau : les 5xx sont traités comme des erreurs définitives.**
Dans `ApiClient`, tout code hors 200-299 lève une exception générique. Seul le 429 déclenche
un cooldown ; le reste part dans `failedHard` et le modèle est abandonné pour la requête. Un
503 passager = un modèle perdu.
→ Retry avec backoff (2 tentatives) sur 5xx et erreurs réseau, pas sur 4xx.

**11. Le build n'est pas reproductible.**
Pas de Gradle wrapper (`gradlew` absent), Gradle local en 9.5.1, CI en 8.13, AGP 8.2.2.
Trois versions différentes selon où tu compiles.
→ Générer le wrapper en 8.13, le committer, et faire `./gradlew` partout.

**12. Les tests ne tournent jamais.**
15 classes de test, un `run.sh` qui marche (JDK seul, sans Android), et la CI qui fait
uniquement `gradle assembleDebug`. Personne ne lance `run.sh` automatiquement.
→ Ajouter un job `./tools/builder-tests/run.sh` avant le build. Ça prend 30 secondes.

### 🟡 P2 — ménage

**13. 37 fichiers de sauvegarde, dont 6 trackés.**
`.gitignore` a bien `*.bak` et `*.before-*`, mais ils ont été ajoutés après coup : 6 fichiers
sont déjà dans l'index (`git ls-files | grep bak`). Pareil côté layouts : 6 XML ne sont
référencés nulle part (`activity_profile`, `drawer_header`, `item_history_chat`,
`item_message_ai`, `item_message_user`, `krux_status`).
→ `git rm --cached` les 6, supprimer les 37, supprimer les 6 layouts. Archiver dans un tag si
tu veux garder une trace.

**14. Trois branches pour la même feature.**
`feat/krux-memory-profil`, `feat/krux-memory-profile`, `feat/krux-memory-profill`. Une seule
doit survivre.
→ Fusionner, supprimer.

**15. `MainActivity` est un dieu-objet.**
3 960 lignes : navigation, chat, vocal, TTS, quota, mémoire, thème, export, médias, builder.
Impossible à tester, douloureux à modifier.
→ Extraire au moins : `ChatController` (envoi/réception), `QuotaController`, `VoiceController`,
`ThemeController`. Objectif : MainActivity < 800 lignes.

**16. Splash de 4,5 s.**
`SPLASH_DURATION = 4500L` dans `SplashActivity`. C'est long, et sur Android 12+ il y a l'API
`SplashScreen` prévue pour ça (le README le note déjà).
→ 1,2 s max + transition, ou API native.

**17. Notifications muettes sur Android 13+.**
`POST_NOTIFICATIONS` est déclarée dans le manifeste mais **jamais demandée** au runtime
(`grep` : 0). Le service flottant (`FloatingKruxService`) crée sa notification mais elle ne
s'affichera pas.
→ Demander la permission au premier lancement du mode Live.

**18. Version bizarre.**
`versionName "2.9.8.25"` avec `versionCode 37`. Le format à 4 chiffres ne veut rien dire et
les stores comparent le `versionCode`, pas le nom.
→ `2.10.0` (38) par exemple. Et le dériver d'un seul endroit.

**19. 12 `catch (Exception ignored)`.**
Douze blocs qui avalent une erreur sans rien dire. C'est exactement comme ça qu'on découvre
six mois plus tard que la synchro ne marchait pas.
→ Au minimum `Log.w(TAG, ...)`.

---

## 4. Ce que je propose d'ajouter (par ordre de rentabilité)

### 🥇 Les 5 qui rapportent le plus

**A. Recherche dans l'historique (FTS5).**
La table `messages` existe déjà en SQLite. Un `CREATE VIRTUAL TABLE messages_fts USING fts5(...)`
+ un champ de recherche dans le drawer, et tu as une vraie recherche instantanée dans des
milliers de messages. ~1 journée. Impact énorme.

**B. Cache de recherche web + sources cliquables.**
`WebSearchEngine` refait les mêmes requêtes à chaque fois. Ajouter un cache disque avec TTL
(24 h), et rendre chaque source de `SourcesSheet` cliquable (ouvrir dans le navigateur +
copier l'URL). Coût faible, gain de latence et de quota.

**C. Export propre (Markdown / PDF).**
L'export existe déjà mais reste basique. Un vrai partage : conversation en `.md` avec blocs
de code, ou PDF via `PrintManager` (zéro dépendance). Très attendu sur une app de chat.

**D. Une seule source de vérité pour le quota.**
Un `QuotaManager` unique : `isAllowed()`, `remaining()`, `consume()`. Local + Firestore +
règles alignées + message clair quand c'est bloqué ("2 créations/semaine, retour lundi").
Aujourd'hui il y a trois compteurs qui se disputent (`SharedPreferences`, `krux_quotas`,
`users/uid/usage`).

**E. Tests + CI qui les lance.**
Job `run.sh` + `./gradlew lint` + build debug. Et un `gradlew` versionné. C'est le meilleur
investissement du lot : ça évite de re-casser la mémoire ou le builder à chaque commit.

### 🥈 Ensuite, si tu veux du produit

| Feature | Pourquoi | Effort |
|---|---|---|
| **Mode hors-ligne** | `NetPolicy` et `NetworkWatcher` existent déjà : file d'attente des messages + renvoi auto au retour du réseau | 2-3 j |
| **Mémoire v3** | ~~`MemoryBook` gère 150 entrées max, sans historique ni undo. Ajouter un diff avant/après et un bouton "annuler"~~ **fait : journal + 20 annulations, id stable, dépôt unique, Firestore mémoire supprimé** | — |
| **Stats d'usage** | Tokens, latence, modèle utilisé, coût estimé, par jour/semaine. Table locale, écran simple | 2 j |
| **Mode Agent** | `ToolRouter` est déjà là. Un plan en 3 étapes affiché, validation utilisateur à chaque étape | 4-5 j |
| **Réglages par modèle** | Température, budget de tokens, "toujours utiliser ce modèle pour le code" | 1 j |
| **Recherche de conversations** | Voir A, version drawer | 1 j |
| **Thème auto (clair/sombre système)** | `KruxTheme` est centralisé, c'est juste un mode à ajouter | 0,5 j |
| **Accessibilité** | 13 `contentDescription` pour 20 layouts, contrastes à vérifier, tailles de texte | 1 j |
| **Sauvegarde chiffrée** | Export/import de tout (chats + mémoire) en `.krux` chiffré AES | 2 j |

### 🥉 Plus tard

- Passer les appels IA par le Worker Cloudflare (clés hors APK — cf. P0-5).
- Télémétrie opt-in (Crashlytics ou Sentry) + écran "Journal" pour voir les erreurs.
- Mode "commande" avec `/` dans le champ de saisie.
- Widget d'écran d'accueil.

---

## 5. Plan proposé

**Sprint 1 — débloquer (2-3 jours)**
1. Gradle wrapper 8.13 + `./gradlew` partout.
2. CI : lancer `run.sh` + lint.
3. Quota : une seule implémentation + règles Firestore.
4. `proguard-rules.pro` + signature release.
5. Nettoyage : 37 `.bak`, 6 layouts, 3 branches.

**Sprint 2 — conformité (3-4 jours)**
6. compileSdk/targetSdk 36, migration AGP/Gradle.
7. Clés hors APK (Worker) ou minimum : backup désactivé.
8. i18n : les 26 littéraux → `strings.xml`.
9. Permissions runtime (notifications).
10. Renommage "KRUX AI" → "Krux" partout.

**Sprint 3 — produit (1-2 semaines)**
11. Découper `MainActivity`.
12. Recherche historique FTS.
13. Export Markdown/PDF.
14. Cache web + sources cliquables.
15. ~~Mémoire v3 (diff/undo).~~ **fait (O1–O4, voir OBJECTIFS-MEMOIRE.md)**

---

## 6. Quick wins (moins d'une heure chacun)

- [ ] `git rm --cached` les 6 `.bak` versionnés
- [ ] Supprimer les 6 layouts inutilisés
- [x] Corriger "KRUX AI" dans `MemoryBook:250` (fait) — reste `SettingsActivity:110`
- [ ] Demander `POST_NOTIFICATIONS`
- [ ] `addOnFailureListener` sur `QuotaStore.fetch`
- [ ] Ajouter `proguard-rules.pro` (même vide)
- [ ] Lancer `tools/builder-tests/run.sh` une fois pour voir
- [ ] Ajouter le job de test dans `.github/workflows/build.yml`

---

## 7. Annexe — les commandes que j'ai utilisées

```bash
# littéraux français dans les Toast/Dialog (à extraire)
grep -rnE '(Toast\.makeText|setMessage|setTitle)\([^,]*, *"' app/src/main/java --include=*.java

# classes jamais appelées
grep -rl "NomDeClasse" app/src/main/java --include=*.java | grep -v "/NomDeClasse.java"

# layouts morts
for f in app/src/main/res/layout/*.xml; do n=$(basename "$f" .xml); \
  grep -rq "R\.layout\.$n\b\|@layout/$n\b" app/src/main/java app/src/main/res || echo "mort: $n"; done

# fichiers de sauvegarde
find . -path ./.git -prune -o -type f \( -name "*.bak" -o -name "*before*" \) -print | wc -l

# tests
cd tools/builder-tests && ./run.sh
```

---

*Doc généré à partir du code réel, pas du README. Si un point te semble faux, dis-le, je
vérifie la ligne et je corrige.*
