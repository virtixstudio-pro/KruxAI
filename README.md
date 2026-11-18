# KRUX AI

Application Android native (Java) de Virtix Studio : discussion avec plusieurs modèles d'IA, création d'applications Android, recherche web, voix, mémoire.
Dépôt : `virtixstudio-pro/KruxAI`, branche `main`. Mise à jour du 18 novembre 2026.

## Règles de travail (à respecter)

- Un patch à la fois. Pour chaque patch : le fichier `.patch` (dans `~/storage/downloads`) et UNE commande complète :
  `git apply --check ... && git apply ... && git add -A && git commit -m "..." && git push origin main`
- Le README se met à jour par commandes `sed` ou `printf`, pas par patch (le README local diverge).
- Ne recloner GitHub que si l'utilisateur dit « mise à jour ».
- Ne jamais déplacer ni supprimer de fichiers ou de fonctionnalités sans son accord.
- Rien n'est compilé en local (pas de SDK Android) : toujours le dire. Vérifications : `git apply --check` et `javac` (syntaxe seule). La vraie compilation se fait sur GitHub Actions (artefact `KruxAI-debug-apk`).
- Pas de carte bancaire : uniquement des services gratuits sans carte.
- Interface compacte, réponses courtes, commandes prêtes à copier, honnêteté sur ce qui est testé ou non.
- Économie de quota : jamais de gros `cat` ni de longues sorties, seulement les lignes utiles.
- Ne jamais enregistrer qu'un utilisateur est mineur. Ne pas publier l'APK tant que des clés API sont dedans.

## Déjà poussé sur GitHub

- Voix vivante, menu d'appui long, boutons de lien, navigateur intégré, export (txt, PDF, CSV, ZIP), blocs de code, thème blanc/noir.
- Analyse de photos, pièces jointes en cartes, réflexion discrète sur une ligne, étapes de réflexion réelles.
- Modèles à jour : Prime, Core, Flow, Max, Nova (catalogue vérifié le 17/11).
- Création d'apps : quota de 5 par semaine, plafonds relevés, permissions utiles autorisées, aperçu interactif, secours automatique entre modèles.
- **Fichiers livrés** : bloc compact « N fichiers » avec **Tout enregistrer (ZIP)**, Partager, liste de 5 lignes (`DeliveredFileCards`).
- **Apps générées uniques** : identifiant d'installation différent par app (`com.krux.<nom><6 hex>`, `BuildTemplates.uniqueAppId`) et direction visuelle tirée au hasard (`DesignDirection` : palette, formes, typographie, structure, navigation).

## Mode web (React + TypeScript), en cours

- **Pourquoi** : la création d'apps en Java coûte 4 à 5 fois plus de tokens et de requêtes (code verbeux, erreurs de compilation, chaque essai passe par GitHub Actions). Les autres générateurs d'apps utilisent TSX, TS ou React.
- **Principe** : l'IA n'écrit que `src/*.tsx`, `src/*.ts`, `src/*.css`. KRUX fournit le reste : coque Android fixe avec WebView (pont `window.Krux` : `toast`, `share`, `copy`, `vibrate`), `package.json` (React 19.3.1, esbuild 0.28.2, TypeScript 5.9.3), `tsconfig.json`, `src/krux.d.ts` et le workflow `npm install`, `tsc --noEmit`, esbuild, puis Gradle. Aucun autre paquet que `react` et `react-dom`.
- **Code** : `builder/WebTemplates.java` (coque, workflow, app d'exemple), `builder/WebBuilder.java` (prompts, chemins, politique de sécurité, vérification locale gratuite), `AppBuilder` (paramètre `web`).
- **Activation** : `"web": true` dans `build` de `assets/krux_config.json` (défaut `false`). Une modification ou une reprise suit le mode de l'app d'origine (présence de `src/main.tsx`). Les apps Java existantes ne changent pas.
- **Testé** (hors téléphone) : `npm install`, `tsc` et esbuild sur l'app d'exemple, rendu dans jsdom, 26 vérifications de la logique avec un faux générateur d'IA, compilation d'`AppBuilder` avec des stubs `org.json` et Android.
- **Non testé** : le `MainActivity` de la coque (non compilé), la CSP de `index.html`, l'envoi GitHub et le build Actions réels, tout ce qui se passe sur téléphone.


- **Apps modifiables et réflexion** : bouton **Modifier** dans « Mes apps », palette imposée vérifiée, étape « Vérification de la réponse » (et « Correction de la réponse ») dans la ligne de réflexion, seulement quand la relecture a lieu (`AnswerCheck.Hook`).

## Patchs prêts, à appliquer dans cet ordre


1. `inscription-fluide.patch` : inscription sans faux « trop de temps » ; si l'e-mail existe déjà, reconnexion automatique avec le même mot de passe, sinon bascule vers la connexion (`LoginActivity`).
2. `theme-blanc-lisible.patch` : sélecteur de modèle, boîte « Compte créé » et en-tête des tableaux suivent la palette (thème Blanc lisible).
3. `tts-pause-reprise.patch` : pause puis Play reprend à la phrase en cours (`VoiceStyler.speakFrom`, `MainActivity.toggleTtsPlayback`).

Vérifiés avec `git apply --check` et `javac` (syntaxe seule). Rien n'est compilé ni testé sur téléphone.

## Reste à faire, dans l'ordre

Légende : [ ] à faire, [~] en cours. Un patch à la fois, README mis à jour à chaque patch.

### 0. En attente de push
- [~] `reflexion-verification-etape.patch` : étape « Vérification de la réponse » (et « Correction de la réponse ») dans la ligne de réflexion.

### 1. Petits correctifs qui se voient tout de suite
1. [~] **Reprise là où Krux s'est arrêté** (chat : bouton Continuer ; apps : patch `reprise-generation`, à tester) : si une réponse ou une création d'app est coupée ou stoppée, il continue au point d'arrêt au lieu de recommencer (s'appuyer sur `BuildCheckpoint` et `completeTruncated`).
4. [x] **Date et heure** : Krux lit l'heure et la date de l'appareil (ajout dans `SystemPromptBuilder`), pour répondre à « quelle heure est-il ? » et ne plus se tromper sur « aujourd'hui ».
5. [x] **Vrai raisonnement, moins de questions** : fini les « peux-tu préciser… » à répétition. Krux répond d'abord avec une hypothèse annoncée en une ligne ; au plus une question, et seulement s'il est vraiment bloqué. Prompt système plus court, étape de plan réelle, grands modèles en premier.
6. [x] **Réflexion en haut du message**, comme chez Claude : la ligne grise est au-dessus de la réponse (vérifier `ChatAdapter.setupReasoning`), étapes réelles seulement.
7. [ ] **Cartes d'images en haut du chat** : miniature de l'image, bouton **Modifier** (recadrer ou remplacer), croix pour la retirer, **2 images au maximum par message**.
8. [ ] **Sélecteur de modèles plus agréable** : petite fenêtre compacte qui se déroule comme un parchemin, au lieu d'un grand carré plein écran ; liste qui défile car il y a plus de modèles à afficher ; couleurs du thème, badge PETIT/GRAND, description courte (`showKruxModelSelector`). Couleurs du thème suivies (patch `theme-blanc-lisible`) ; reste le format parchemin.
- [ ] **Thème Blanc, fin de couverture** : blancs et fonds sombres encore codés en dur dans `activity_privacy`, `activity_studio`, `activity_signup`, `activity_auth_welcome` (layouts) et dans le bloc d'aperçu des bulles de `MainActivity` (vers la ligne 3570, à vérifier s'il sert encore). Demander une capture d'un écran resté cassé.
- [ ] **TTS : Live et bulle flottante** : `LiveActivity` et `FloatingKruxService` ont leur propre `TextToSpeech` (arrêt seulement, pas de reprise). Vérifier si le même défaut de pause existe.

### 2. Création d'apps
9. [ ] **Erreur « L'IA n'a pas produit de code exploitable »** (revient tout le temps) : à confirmer avec la phrase complète après les deux-points. Cause probable : un seul gros JSON avec tous les fichiers, fragile. Remède : blocs `=== chemin ===` suivis du code, en plusieurs passes (plan et écrans, fichiers par lots, relecture) dans `AppBuilder.generateParsed`.
10. [ ] **Pauses anti-saturation** : espacer les appels par fournisseur (erreurs 429) et remplacer « Ce moteur est saturé, je passe sur un autre… » par « Je prends une courte pause… ».
10. [ ] **Clé de signature stable par app** : sans elle, une app modifiée ne peut pas remplacer l'ancienne, et pas d'AAB pour Google Play.
10. [ ] **Reprise d'apps** : le bouton **Modifier** existe déjà dans « Mes apps ». Reste à vérifier et ajouter le **zip du code source à côté de l'APK** (aucun code de zip trouvé pour l'instant).
11. [ ] **Nouveau mode « Web / React Native »** (inspiré de Snapp AI) : projets en TypeScript, TSX, JS ; aperçu en direct dans l'app (compilateur TSX embarqué + react-native-web dans une WebView, proche du rendu final mais pas identique à Android) ; éditeur de code ; modification par chat. Livraison : **APK** et **AAB** par GitHub Actions. **iOS** : export du code source seulement, car l'installation sur iPhone demande un compte Apple Developer payant avec carte.

### 3. Intelligence et services
12. [ ] **Mémoire** : Krux décide de ce qu'il retient, dès l'inscription (questions d'accueil). Base : `MemoryBook`, `MemoryExtractor`, `MemorySelector`.
13. [ ] **Recherche web, partie 2** : deuxième moteur, recherches enchaînées, plus de pages lues, vérification croisée, sources citées.
14. [ ] **Patch d'économie de quotas** ; vérifier la bannière hors ligne « Réessayer ».

### 4. Sécurité et publication
15. [ ] **Proxy IA** : déployer `worker/ai-proxy`, mettre son URL dans `krux_config.json`, retirer les clés de l'APK (`build.gradle`, workflow), puis **révoquer et renouveler toutes les clés**. Pas d'APK public avant.
16. [ ] Backlog : mode Live ; images (filigrane, partage en fichier) ; préparation Google Play ; test de la connexion Google du site.

## Sécurité des clés (priorité)

Le Worker `worker/ai-proxy` est prêt mais **pas déployé** : variable `FIREBASE_PROJECT_ID`, secrets GROQ, CEREBRAS, MISTRAL, HF, GEMINI, liaison D1 `DB`. Ensuite : mettre son URL dans `assets/krux_config.json` (`ai_proxy.url`), retirer les clés de `build.gradle` et du workflow, puis **révoquer et renouveler toutes les anciennes clés** (elles sont dans les APK déjà distribués).

## Infrastructure

- Firebase : projet `krux-9fd33` (Auth e-mail, Google, téléphone, anonyme ; Firestore `users/{uid}`).
- Cloudflare : Worker de stockage (D1 `krux-db`, KV `krux_storage`), site `https://virtix-studio.kruxvirtix.workers.dev` (dossier `docs/`, redéployé à chaque push sur `main`).
- Contact public : `virtixstudio@gmail.com`.
- Documentation détaillée : `PASSATION-KRUX.md`, `ANALYSE-PROJET.md`, `OBJECTIFS-MEMOIRE.md`.

## État actuel et limites connues

- Aucune compilation locale : seule la CI GitHub compile. Quand elle échoue, coller les lignes `error:`.
- Les apps générées n'ont pas été testées sur téléphone par Krux : l'aperçu et la compilation donnent un APK, pas une garantie de qualité.
- Mistral (offre gratuite « Experiment ») peut entraîner sur les données : vérifier avant d'affirmer le contraire dans la page de confidentialité.
- Hugging Face non testé (inférence gratuite capricieuse).
- Le mode web (React/TSX) n'a jamais été lancé sur téléphone ni sur un vrai build GitHub (voir « Mode web »). En mode web, coller aussi les lignes `error TS` quand la CI échoue.
