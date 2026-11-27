# KRUX AI

Application Android native (Java) de Virtix Studio : discussion avec plusieurs modèles d'IA, création d'applications Android, recherche web, voix, mémoire.
Dépôt : `virtixstudio-pro/KruxAI`, branche `main`. Mise à jour du 22 novembre 2026.

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
- **État au 18/11** : activé par `activer-mode-web.patch` (`"web": true`) pour un premier test réel ; jamais lancé sur téléphone ni sur une vraie CI. Si le build échoue : coller les lignes `error:` ou `error TS`.
- **Testé** (hors téléphone) : `npm install`, `tsc` et esbuild sur l'app d'exemple, rendu dans jsdom, 26 vérifications de la logique avec un faux générateur d'IA, compilation d'`AppBuilder` avec des stubs `org.json` et Android.
- **Non testé** : le `MainActivity` de la coque (non compilé), la CSP de `index.html`, l'envoi GitHub et le build Actions réels, tout ce qui se passe sur téléphone.


- **Apps modifiables et réflexion** : bouton **Modifier** dans « Mes apps », palette imposée vérifiée, étape « Vérification de la réponse » (et « Correction de la réponse ») dans la ligne de réflexion, seulement quand la relecture a lieu (`AnswerCheck.Hook`).

## Patchs prêts, à appliquer dans cet ordre


1. `inscription-fluide.patch` : inscription sans faux « trop de temps » ; si l'e-mail existe déjà, reconnexion automatique avec le même mot de passe, sinon bascule vers la connexion (`LoginActivity`).
2. `theme-blanc-lisible.patch` : sélecteur de modèle, boîte « Compte créé » et en-tête des tableaux suivent la palette (thème Blanc lisible).
3. `tts-pause-reprise.patch` : pause puis Play reprend à la phrase en cours (`VoiceStyler.speakFrom`, `MainActivity.toggleTtsPlayback`).
4. `activer-mode-web.patch` : `"web": true` dans `assets/krux_config.json` pour tester la création d'apps React/TypeScript (remettre `false` pour revenir au Java).
5. `reprise-generation.patch` : création d'app : les fichiers déjà reçus sont gardés entre les essais et enregistrés dans `partial` ; la même demande relancée dans les 24 h reprend (`AppBuilder.generateParsed`, `BuildCheckpoint`).
6. `code-exploitable.patch` : repli sur des blocs `=== chemin ===` quand le JSON est inutilisable (`builder/BlockFiles.java`, test `BlockFilesTests`), et raison de chaque essai dans le message d'erreur. À poser après le 5.
7. `splash-pro.patch` : splash de 4,5 s coupé en pleine animation, remplacé par 2,3 s, un toucher pour passer, plus de double lancement (`SplashActivity`).
8. `splash-noir.patch` : splash toujours noir quel que soit le thème (étiquettes de thème retirées du layout, barres système noires, plus de `KruxTheme.apply` donc plus de recréation de l'écran). À poser après `splash-pro`.
9. `cartes-images.patch` : jusqu'à 2 photos en cartes au-dessus du champ de saisie (miniature, croix, bouton Modifier : remplacer, pivoter, rogner au carré), envoyées à Mistral et Gemini (`AttachmentStrip`, `ApiClient.attachNextImages`, `MainActivity`).

Vérifiés avec `git apply --check` et `javac` (syntaxe seule). Rien n'est compilé ni testé sur téléphone.

## Reste à faire, dans l'ordre

Légende : [ ] à faire, [~] en cours, [x] fait. Un patch à la fois, README mis à jour à chaque patch.

### 0. En attente de push
- [x] `zip-code-source.patch` : zip du projet complet (`krux-source.zip`, classe `SourceZip`) rangé à côté de l'APK après chaque build réussi, et bouton **Code** dans « Mes apps » pour le partager. À tester sur un vrai build.
- [x] `reflexion-verification-etape.patch` : étape « Vérification de la réponse » (et « Correction de la réponse ») dans la ligne de réflexion.

### 1. Petits correctifs qui se voient tout de suite
1. [~] **Reprise là où Krux s'est arrêté** (chat : bouton Continuer ; apps : patch `reprise-generation`, à tester) : si une réponse ou une création d'app est coupée ou stoppée, il continue au point d'arrêt au lieu de recommencer (s'appuyer sur `BuildCheckpoint` et `completeTruncated`).
4. [x] **Date et heure** : Krux lit l'heure et la date de l'appareil (ajout dans `SystemPromptBuilder`), pour répondre à « quelle heure est-il ? » et ne plus se tromper sur « aujourd'hui ».
5. [x] **Vrai raisonnement, moins de questions** : fini les « peux-tu préciser… » à répétition. Krux répond d'abord avec une hypothèse annoncée en une ligne ; au plus une question, et seulement s'il est vraiment bloqué. Prompt système plus court, étape de plan réelle, grands modèles en premier.
6. [x] **Réflexion en haut du message**, comme chez Claude : la ligne grise est au-dessus de la réponse (vérifier `ChatAdapter.setupReasoning`), étapes réelles seulement.
7. [~] **Cartes d'images en haut du chat** : miniature de l'image, bouton **Modifier** (recadrer ou remplacer), croix pour la retirer, **2 images au maximum par message**. Patch cartes-images (2 photos, Modifier : remplacer, pivoter, rogner au carré), à tester.
- [ ] **Recadrage libre des photos** : cadre à déplacer (vue dédiée) ; aujourd'hui « Modifier » ne fait que rogner au carré et pivoter.
8. [ ] **Sélecteur de modèles plus agréable** : petite fenêtre compacte qui se déroule comme un parchemin, au lieu d'un grand carré plein écran ; liste qui défile car il y a plus de modèles à afficher ; couleurs du thème, badge PETIT/GRAND, description courte (`showKruxModelSelector`). Couleurs du thème suivies (patch `theme-blanc-lisible`) ; reste le format parchemin.
- [ ] **Thème Blanc, fin de couverture** : blancs et fonds sombres encore codés en dur dans `activity_privacy`, `activity_studio`, `activity_signup`, `activity_auth_welcome` (layouts) et dans le bloc d'aperçu des bulles de `MainActivity` (vers la ligne 3570, à vérifier s'il sert encore). Demander une capture d'un écran resté cassé.
- [~] **Splash : noir de marque fixe** (option) : la tête a des parties blanches et noires fixes, rendu incertain en thème Blanc. Fait par splash-noir, à tester.
- [ ] **TTS : Live et bulle flottante** : `LiveActivity` et `FloatingKruxService` ont leur propre `TextToSpeech` (arrêt seulement, pas de reprise). Vérifier si le même défaut de pause existe.

### 2. Création d'apps
9. [~] **Erreur « L'IA n'a pas produit de code exploitable »** (revient tout le temps) : à confirmer avec la phrase complète après les deux-points. Cause probable : un seul gros JSON avec tous les fichiers, fragile. Remède : blocs `=== chemin ===` suivis du code, en plusieurs passes (plan et écrans, fichiers par lots, relecture) dans `AppBuilder.generateParsed`. Patch `code-exploitable` : repli en blocs et raison de chaque essai dans le message ; si ça revient, coller la phrase complète.
10. [ ] **Pauses anti-saturation** : espacer les appels par fournisseur (erreurs 429) et remplacer « Ce moteur est saturé, je passe sur un autre… » par « Je prends une courte pause… ».
10. [ ] **Clé de signature stable par app** : sans elle, une app modifiée ne peut pas remplacer l'ancienne, et pas d'AAB pour Google Play.
10. [x] **Reprise d'apps** : bouton **Modifier** et zip du code source à côté de l'APK (bouton **Code**, patch `zip-code-source`). Reste à tester sur un vrai build.
11. [ ] **Nouveau mode « Web / React Native »** (inspiré de Snapp AI) : projets en TypeScript, TSX, JS ; aperçu en direct dans l'app (compilateur TSX embarqué + react-native-web dans une WebView, proche du rendu final mais pas identique à Android) ; éditeur de code ; modification par chat. Livraison : **APK** et **AAB** par GitHub Actions. **iOS** : export du code source seulement, car l'installation sur iPhone demande un compte Apple Developer payant avec carte.

### 3. Intelligence et services
12. [ ] **Mémoire** : Krux décide de ce qu'il retient, dès l'inscription (questions d'accueil). Base : `MemoryBook`, `MemoryExtractor`, `MemorySelector`.
13. [ ] **Recherche web, partie 2** : deuxième moteur, recherches enchaînées, plus de pages lues, vérification croisée, sources citées.
14. [ ] **Patch d'économie de quotas** ; vérifier la bannière hors ligne « Réessayer ».

### 3b. Fichiers et Krux Code
- [~] **Onglet Fichiers et barre latérale compacte** (import de fichiers, tuiles Mes apps / Fichiers) : `fichiers-onglet.patch`.
- [~] **Krux Code** (fichiers-krux-code, 22/11, à tester) : écran réservé au code, sans bavardage, qui édite les fichiers importés comme dans un terminal ; style sombre original, diff rouge et vert, bouton Annuler.

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
- Tests sans Android : `JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8" sh tools/builder-tests/run.sh`. `BuildProgressTests` échoue (14/21) depuis avant les patchs (à examiner) et `run.sh` s'arrête à la première série en échec : `BlockFilesTests`, en fin de fichier, peut ne pas se lancer ; le lancer seul si besoin.

## Poussé le 18/11 (soir)
- `cle-signature-stable.patch` : clé debug fixe (secret GitHub `KRUX_DEBUG_KEYSTORE_B64`, étape « Restore stable debug keystore » de `build.yml`). Une mise à jour remplace l'ancienne version. Concerne l'APK de KRUX, pas encore les apps générées. Garder une copie de `~/krux-debug.keystore` hors du dépôt.

## Studio de création d'apps (priorité n°1, la raison d'être de KRUX) - noté le 18/11/2026
Objectif : qualité de pointe. L'utilisateur doit se dire « j'abandonne ChatGPT et Android Studio ».
- [ ] **Écran de personnalisation** soigné avant la création : nom, palette, style, et **carte « Importe l'icône de l'application »** (l'image devient l'icône de l'app générée).
- [ ] **Aperçu dans un faux téléphone** (style émulateur) : beau fond d'écran, seulement 2 apps (VX Browser et l'app demandée), comportement de vrai téléphone ; glisser du haut vers le bas ouvre les réglages rapides (mode avion, Wi-Fi, point d'accès mobile…).
- [ ] **Agent Krux à côté du téléphone** (visage du mode Live, allure d'agent), à préciser.
- [ ] **Édition manuelle sans IA** dans l'aperçu : ajouter des éléments, coller des blocs, agrandir, déplacer.
- [ ] **« Modifier avec KRUX » = petite carte de conversation** dans le Studio (pas de retour aux messages), modification visible en direct.
- [ ] **Tous les fichiers créés visibles dans « Mes fichiers »** (voir `fichiers-onglet.patch`).
- [~] **Krux Code** (fichiers-krux-code, 22/11, à tester) : l'onglet n'existe pas encore (voir 3b), à créer.

- **Aperçu : Demander à Krux** (19/11) : champ « Demander à Krux » dans l aperçu web (AppBuilder.Refiner, WebPreviewDialog), correction automatique des erreurs de l aperçu (2 essais), relance automatique d un build annulé ou expiré (2 fois). Jamais testé sur téléphone ni sur une vraie CI.

## Journal du 22/11/2026

### Fait (patchs à appliquer : zip-code-source puis fichiers-krux-code)
- [x] `zip-code-source` : après chaque build réussi, `krux-source.zip` (projet complet, classe `SourceZip`) est rangé à côté de l'APK ; bouton **Code** dans « Mes apps » pour le partager. Les apps déjà créées n'ont pas de zip.
- [x] `fichiers-krux-code` : **Krux Code** (`KruxCodeActivity`) : écran réservé au code, ouvre un fichier de Fichiers, répond seulement par du code, diff rouge/vert, « Appliquer » puis « Annuler la modification ».
- [x] Barre latérale modernisée : trois tuiles égales (Mes apps, Fichiers, Krux Code), tuiles de 64 dp, bouton « Nouveau chat » plus haut.
- [x] Tout fichier généré par Krux est rangé à plat dans l'onglet **Fichiers** (`WorkspaceStore.saveGenerated`, appelé par `MainActivity.deliverToWorkspace`). Avant, ils allaient dans des sous-dossiers `workspace/<date>/` que l'onglet ne montrait pas, et un chemin à sous-dossiers faisait échouer l'écriture.
- [x] Anciens fichiers des sous-dossiers : COPIÉS dans Fichiers au premier passage (`adoptSubfolders`, marqueur `.adopted`), originaux intacts.

### À tester sur téléphone (rien n'est compilé en local)
- [ ] Compilation GitHub Actions des deux patchs.
- [ ] Krux Code : ouvrir un fichier, demander une modif, Appliquer, Annuler ; créer un nouveau fichier.
- [ ] Demander un fichier à Krux : il apparaît dans Fichiers (pas en double au redémarrage).
- [ ] Bouton Code de « Mes apps » sur un nouveau build.

### Reste à faire, dans l'ordre
1. [ ] Mémoire : Krux décide de ce qu'il retient dès l'inscription (`MemoryBook`, `MemoryExtractor`, `MemorySelector`).
2. [ ] Recherche web partie 2 (2e moteur, recherches enchaînées, plus de pages lues, sources citées).
3. [ ] Patch d'économie de quotas ; bannière hors ligne « Réessayer ».
4. [ ] Sélecteur de modèles en parchemin ; recadrage libre des photos ; fin du thème Blanc ; TTS Live et bulle.
5. [ ] Création d'apps : erreur « code exploitable », pauses anti-saturation, clé de signature stable, mode Web / React Native.
6. [ ] Proxy IA à déployer, retirer les clés de l'APK, puis révoquer et renouveler toutes les clés (pas d'APK public avant).

## Connexion Google - noté le 22/11/2026
- [x] `google-connexion` : `GoogleSignInHelper` (Credential Manager + Firebase `GoogleAuthProvider`) branché sur les boutons de `AuthWelcomeActivity` et `LoginActivity`. Avant, les deux boutons étaient des bouchons. Nouveau compte Google : profil enregistré (`saveProfile`).
- [ ] Côté Firebase (projet `krux-9fd33`) : activer le fournisseur Google ; la SHA-1 de la clé stable doit être dans l'app Android (étape « Print Debug Certificate Fingerprints » du workflow) ; retélécharger `google-services.json` après ajout.
- [ ] À tester sur téléphone : connexion Google nouveau compte et compte existant ; erreur « SHA-1 » si l'empreinte manque.

## À faire, noté le 22/11/2026 (demandes du propriétaire, dans l'ordre)
1. [~] **Faux téléphone de l’aperçu** (agrandi, avatar déplaçable et navigateur faits, à tester sur téléphone) : grand (presque tout l'écran, un peu moins en haut comme une fenêtre Windows) ; 3e app factice **Navigateur** qui fonctionne ; avatar Krux sorti du téléphone, posé à côté (centre gauche), déplaçable, seulement pour personnaliser l'app avec lui.
2. [ ] **Éditeur intégré à l'aperçu** : personnaliser l'app sans passer par Krux (ajouter une icône, rognage automatique...) pour économiser les quotas.
3. [ ] **Bug photos** : envoi d'image = « API absent » (ex. Krux Flow) alors que le texte marche en choisissant Flow ; l'analyse de photo ne marche pas. Vérifier le choix du modèle vision et la clé.
4. [ ] **Krux Code = vrai agent** (pas factice) : style terminal, sessions (bouton + en haut), conversations et fichiers conservés, coloration syntaxique, ouvre le navigateur de Krux, lit/extrait le texte des pages, parle peu et code plus.
5. [ ] **Aperçu web** (HTML/CSS/JS) : mêmes systèmes que le faux téléphone.
Méthode : le téléphone d'abord, puis un ou deux points à la fois. Économie de quota : peu de lecture, pas de gros cat.
- [~] **Bug photos (22/11)** : avec une image, seuls Gemini et Mistral sont utilisés (`visionOnly`). KRUX Flow = Mistral. Hypothèse : Mistral refusait `image_url` en objet `{url}` (il documente une chaîne) ; la chaîne est maintenant envoyée pour Mistral. Le message « Clé Gemini absente » vu ensuite vient du repli sur Gemini (secret `GEMINI_API_KEY` vide ?) et masque la vraie erreur de Flow. À tester ; si ça échoue encore, coller l'erreur.
- [~] **Mini chat Krux (22/11)** : avatar à droite, déplaçable ; panneau avec bulles (dictée ou texte) et éditeur intégré (nom, couleur, icône rognée en carré) sans appeler Krux. À tester.
- [~] **Krux Code, partie 1 (22/11)** : sessions conservées (bouton ＋, menu sur le titre, suppression), conversations et fichier de chaque session gardés dans `SharedPreferences` (`krux_code`), les 5 dernières demandes sont renvoyées à Krux pour garder le fil, coloration syntaxique légère dans le diff (`CodeHighlighter`). À tester.
- [ ] **Krux Code, partie 2 (agent)** : Krux ouvre le navigateur intégré, lit/extrait le texte des pages, enchaîne plusieurs actions (lire, modifier, créer) avec peu de texte ; coloration aussi dans la vue du fichier ouvert.
- [~] **Krux Code, partie 2 (22/11)** : agent à actions. Krux répond par une ligne `OUVRIR: https://...` ou `CLIQUER: texte` ; l'app ouvre la page dans un navigateur visible en haut de l'écran (WebView, `NavigateurK`), extrait le texte (`document.body.innerText`, 6000 caractères max) et le renvoie à Krux, jusqu'à 5 actions par demande, puis la réponse habituelle (diff à appliquer). Seulement http/https. À tester sur téléphone : sites qui refusent le WebView, pages très longues, quotas.
- [ ] Reste : coloration dans la vue du fichier ouvert, bouton Stop pendant l'agent, choix du modèle de Krux Code.
- [~] **Aperçu web HTML/CSS/JS dans le faux téléphone (22/11)** : menu « Paramètres de l'aperçu » > **Aperçu téléphone Krux** ouvre l'app HTML dans le même téléphone que les apps React (barre d'état, réglages rapides, Bloc-notes, Navigateur, avatar, éditeur d'icône). `chrome.js` affiche tel quel un `index.html` sans TSX/TS/JSX. Limites : le mini chat/dictée n'est pas branché pour les pages HTML (pas de `Refiner`) ; l'iframe est isolée (`allow-scripts` seul) donc `localStorage` peut être refusé dans la page. À tester. Reste : brancher la dictée sur « Modifier avec Krux », et décider si ce téléphone devient l'aperçu par défaut.
- [x] **Bouton Stop de l'agent Krux Code (22/11)** : pendant que Krux travaille, le bouton ▶ devient ■ ; un appui coupe la requête (`ApiClient.cancelCurrentRequest`), le chargement du navigateur et la suite des actions (compteur `runId` qui ignore les réponses tardives). À tester.

## À faire, noté le 22/11/2026 (demandes du propriétaire, dans l'ordre)
1. [~] **Faux téléphone de l’aperçu** (agrandi, avatar déplaçable et navigateur faits, à tester sur téléphone) : grand (presque tout l'écran, un peu moins en haut comme une fenêtre Windows) ; 3e app factice **Navigateur** qui fonctionne ; avatar Krux sorti du téléphone, posé à côté (centre gauche), déplaçable, seulement pour personnaliser l'app avec lui.
2. [ ] **Éditeur intégré à l'aperçu** : personnaliser l'app sans passer par Krux (ajouter une icône, rognage automatique...) pour économiser les quotas.
3. [ ] **Bug photos** : envoi d'image = « API absent » (ex. Krux Flow) alors que le texte marche en choisissant Flow ; l'analyse de photo ne marche pas. Vérifier le choix du modèle vision et la clé.
4. [ ] **Krux Code = vrai agent** (pas factice) : style terminal, sessions (bouton + en haut), conversations et fichiers conservés, coloration syntaxique, ouvre le navigateur de Krux, lit/extrait le texte des pages, parle peu et code plus.
5. [ ] **Aperçu web** (HTML/CSS/JS) : mêmes systèmes que le faux téléphone.
Méthode : le téléphone d'abord, puis un ou deux points à la fois. Économie de quota : peu de lecture, pas de gros cat.
- [~] **Bug photos (22/11)** : avec une image, seuls Gemini et Mistral sont utilisés (`visionOnly`). KRUX Flow = Mistral. Hypothèse : Mistral refusait `image_url` en objet `{url}` (il documente une chaîne) ; la chaîne est maintenant envoyée pour Mistral. Le message « Clé Gemini absente » vu ensuite vient du repli sur Gemini (secret `GEMINI_API_KEY` vide ?) et masque la vraie erreur de Flow. À tester ; si ça échoue encore, coller l'erreur.
- [~] **Mini chat Krux (22/11)** : avatar à droite, déplaçable ; panneau avec bulles (dictée ou texte) et éditeur intégré (nom, couleur, icône rognée en carré) sans appeler Krux. À tester.
- [~] **Krux Code, partie 1 (22/11)** : sessions conservées (bouton ＋, menu sur le titre, suppression), conversations et fichier de chaque session gardés dans `SharedPreferences` (`krux_code`), les 5 dernières demandes sont renvoyées à Krux pour garder le fil, coloration syntaxique légère dans le diff (`CodeHighlighter`). À tester.
- [ ] **Krux Code, partie 2 (agent)** : Krux ouvre le navigateur intégré, lit/extrait le texte des pages, enchaîne plusieurs actions (lire, modifier, créer) avec peu de texte ; coloration aussi dans la vue du fichier ouvert.
- [~] **Krux Code, partie 2 (22/11)** : agent à actions. Krux répond par une ligne `OUVRIR: https://...` ou `CLIQUER: texte` ; l'app ouvre la page dans un navigateur visible en haut de l'écran (WebView, `NavigateurK`), extrait le texte (`document.body.innerText`, 6000 caractères max) et le renvoie à Krux, jusqu'à 5 actions par demande, puis la réponse habituelle (diff à appliquer). Seulement http/https. À tester sur téléphone : sites qui refusent le WebView, pages très longues, quotas.
- [ ] Reste : coloration dans la vue du fichier ouvert, bouton Stop pendant l'agent, choix du modèle de Krux Code.
- [~] **Aperçu web HTML/CSS/JS dans le faux téléphone (22/11)** : menu « Paramètres de l'aperçu » > **Aperçu téléphone Krux** ouvre l'app HTML dans le même téléphone que les apps React (barre d'état, réglages rapides, Bloc-notes, Navigateur, avatar, éditeur d'icône). `chrome.js` affiche tel quel un `index.html` sans TSX/TS/JSX. Limites : le mini chat/dictée n'est pas branché pour les pages HTML (pas de `Refiner`) ; l'iframe est isolée (`allow-scripts` seul) donc `localStorage` peut être refusé dans la page. À tester. Reste : brancher la dictée sur « Modifier avec Krux », et décider si ce téléphone devient l'aperçu par défaut.
- [x] **Bouton Stop de l'agent Krux Code (22/11)** : pendant que Krux travaille, le bouton ▶ devient ■ ; un appui coupe la requête (`ApiClient.cancelCurrentRequest`), le chargement du navigateur et la suite des actions (compteur `runId` qui ignore les réponses tardives). À tester.
- [~] **Menu + modernisé (22/11)** : grandes icônes (appareil photo, photos, fichiers), vrais interrupteurs (web, Réflexion avec mention ×4 de quota, Apprentissage qui agit sur les réponses). À tester sur téléphone (menu-plus.patch).

## Mise à jour du 22/11/2026 (soir)

### Fait (rien n'est compilé en local : à vérifier par la CI puis sur téléphone)
- [~] **Navigateur de Krux Code par session** (navigateur-session.patch) : réduit par défaut, pages visitées rangées dans la session (6 au plus, en pastilles), rechargées seulement à l'agrandissement. Texte de page envoyé à l'IA : 6000 → 2000 caractères (total 3500 par demande). Lecture d'écran : 6000 → 2500.
- [~] **Panneau de questions avant la création d'une app** (panneau-creation.patch) : style, fonctions en plus, nom, icône importée (devient l'icône de l'APK, apps web), base de données (Supabase : pour l'instant seulement la couche `src/data.ts`). Les choix s'ajoutent à la demande, sans appel IA.
- [~] **Krux Code agent** (krux-code-agent.patch) : pilote les sites à rythme humain (OUVRIR, CLIQUER, TAPER, DEFILER ; pause de 0,5 à 1,3 s avant chaque action, frappe de 35 à 95 ms par caractère, 8 actions au plus). Seules limites : jamais de mot de passe, confirmation avant un clic « payer / acheter / supprimer le compte ». Une demande « crée une application… » ouvre le créateur d'apps.
- [~] **« Clé API absente » en reconnaissance d'images** (même patch) : un moteur sans clé dans l'APK est sauté au lieu d'écraser la vraie erreur ; si aucun moteur n'a de clé, le message le dit. Si ça revient, vérifier les secrets GitHub (surtout MISTRAL_API_KEY) et lire la ligne « (détail : … ) ».

### À faire, dans l'ordre
1. [ ] Guide Supabase pas à pas dans le navigateur intégré (carte « clique ici »), collage de l'URL et de la clé, branchement dans l'app.
2. [ ] Icône importée aussi pour les apps natives (aujourd'hui : apps web seulement).
3. [ ] Compteur de 20 messages par jour (code de ChatGPT, non poussé, bloque l'envoi si le réseau est lent) : décider, puis version non bloquante.
4. [ ] Lecture d'écran : autoriser « paramètres restreints » sur le téléphone (le patch est déjà poussé).
5. [ ] Écran de création façon Snapp AI : attendre une capture d'écran du propriétaire.

## Mise à jour du 22/11/2026 (documentation)

- Fait (patch krux-lit-la-doc) : Krux lit docs.txt du site Virtix quand la question le concerne (utils/VirtixDocs.java). Patch site : site-pages-pro.
- À tester sur téléphone. Rien n'est compilé ici (pas de SDK Android).
- Mis de côté : patch de la barre latérale, à refaire en plus sobre.

## Mise à jour du 22/11/2026 (site)

- Fait (patch site-virtix-docs) : pages Applications, Documentation et Créateurs dans docs/, menu étendu sur toutes les pages, docs/docs.txt pour Krux.
- À faire : compléter createurs.html (nom, liens) ; vérifier que docs.txt répond une fois le site redéployé.
- Mis de côté : barre latérale, à refaire plus sobre.

- [~] **Guide d'activation de la lecture d'écran (22/11)** : écran pas à pas (Accessibilité, puis « paramètres restreints » si grisé), détecte l'activation et se ferme tout seul ; ouvert par le visage de Krux et par Réglages > Lecture de l'écran. À tester sur téléphone.

## Journal du 24/11/2026

### Fait
- Mode web : Krux ne refuse plus les demandes Java ou natif, il les réalise en React + TypeScript.
- API : délai de lecture porté à 120 s pour les longues générations.
- Appui long sur une réponse : feuille de sélection presque plein écran, sans « tout sélectionner » d'office.
- « Réessayer » : efface la réponse puis régénère au même endroit.
- Clés IA : les 5 secrets GitHub sont créés (GROQ, CEREBRAS, MISTRAL, HF, GEMINI). `gh` est connecté dans Termux.

### Reste à faire
- Tester l'APK une seule fois, après toutes les modifications (photos, appui long, réessayer, longues générations).
- Plantage pendant l'écriture des réponses (mises à jour trop fréquentes de la liste).
- Boutons d'action : plus modernes, visibles seulement une fois la réponse terminée.
- Tableaux à refaire.
- Écran mémoire : liste par sections, comme dans l'app Claude.
- Krux Code : import d'un zip, de fichiers ou d'un projet Android, compilation sans appeler l'IA ; pilotage de sites ; clonage de dépôts et commandes.
- Sites générés trop sobres : ajouter des consignes de design dans le prompt.
- Panneau de questions avant la création d'une app, réflexion, envoi de photos, menu « + ».
- Cloudflare : token (bouton « Continuer » bloqué), proxy, puis révocation des clés.

- [~] **Editeur photo (24/11)** : menu Modifier la photo > Dessiner / entourer (PhotoEditorDialog : trait, cercle, flèche, couleurs, annuler, pivoter). A tester.

- [~] **Capacités (24/11)** : Paramètres > Capacités (CapacitesActivity, AgentPrefs) : mode agent, navigateur en direct, actions par demande (3/5/8/12). A tester.

- [~] **Ecran memoire (24/11)** : refait façon "Fichiers mémoire" (MemoryActivity) : sections, cartes avec date de mise à jour, vue d'un fichier à puces avec corbeille, barre "Indiquez à Krux ce qu'il faut modifier" (micro + IA). A tester, surtout les suppressions par IA.

- [~] **Sources en haut (24/11)** : le bloc des sources web passe en haut de la réponse (item_chat_ai.xml). A tester.

- [~] **Krux Code, édition ciblée (24/11)** : modifications par blocs CHERCHER/REMPLACER (seules les lignes concernées changent), fichier ouvert coloré avec numéros, menu Fichier > Ajouter des fichiers. A tester.

- [~] **Aperçu libre et grand (24/11)** : fenêtre plein écran sans cadre (WebPreviewDialog), Retoucher/Couleurs en pastilles, aperçu HTML dans le même téléphone (réglage krux_capacites/html_telephone, défaut vrai). Correction : le menu "Aperçu téléphone Krux" bloquait le thread principal. A tester.

## Notes du 24/11/2026 (patchs de la session, dans l'ordre)
Ordre d'application : editeur-photo, capacites-agent, krux-code-edition-ciblee, ecran-memoire, sources-en-haut, apercu-libre-grand. Appliquer avec `git apply --exclude=README.md` (le README se met à jour à la main).
- [~] **Éditeur photo** : menu Modifier la photo > Dessiner / entourer (`PhotoEditorDialog` : trait, cercle, flèche, couleurs, annuler, pivoter). Le bug des photos Mistral n'a pas eu besoin de correctif (les images passent).
- [~] **Capacités** : Paramètres > Capacités (`CapacitesActivity`, `AgentPrefs`) : mode agent, navigateur en direct, actions par demande (3/5/8/12). Réglages dans `krux_capacites`.
- [~] **Krux Code, édition ciblée** : blocs CHERCHER/REMPLACER (seules les lignes concernées changent), fichier ouvert coloré avec numéros, menu Fichier > Ajouter des fichiers. Surveiller les modèles qui respectent mal le format.
- [~] **Écran mémoire** : refait façon « Fichiers mémoire » (sections, cartes avec date, vue à puces avec corbeille, barre « Indiquez à Krux ce qu'il faut modifier » avec micro). Tester les suppressions par IA.
- [~] **Sources en haut** : le bloc des sources web est en haut de la réponse (`item_chat_ai.xml`).
- [~] **Aperçu libre et grand** : fenêtre plein écran sans cadre (`WebPreviewDialog`), Retoucher/Couleurs en pastilles, aperçu HTML dans le même téléphone (`krux_capacites/html_telephone`, défaut vrai). Corrige un blocage : le menu « Aperçu téléphone Krux » appelait la fenêtre sur le thread principal.
### À faire ensuite
- [ ] Outil no-code dans le téléphone : choisir un élément pour changer texte, couleur, taille ; ajouter bouton ou image ; sauvegarder.
- [ ] Interrupteur `html_telephone` dans Capacités.
- [ ] Krux Code : import d'un zip ou d'un projet Android, compilation sans IA, clonage de dépôts et commandes.
- [ ] Brancher la dictée du mini chat sur les pages HTML.
- [ ] Plantage pendant l'écriture des réponses ; boutons d'action plus modernes ; tableaux ; design des sites générés ; panneau de questions avant création d'app.
- [ ] Cloudflare : token, proxy IA, puis révocation et renouvellement des clés.

## Journal du 24/11/2026 (soir)

### Fait
- [x] `anti-plantage-streaming` : mises à jour de la bulle regroupées (120 ms) pendant l'écriture. Poussé (7f32532). À tester.

### Patchs prêts, à appliquer dans cet ordre (pas confirmés poussés)
1. `recherche-longue` : attente du moteur de recherche web 25 s → 45 s (`ApiClient`). Hypothèse : si la coupure continue, coller l'erreur.
2. `verif-cles-ci` : étape du build GitHub qui signale les clés IA vides (noms seulement). Si « clé API absente » revient, regarder ce journal.
3. `boutons-actions` : boutons d'action 36 dp (`item_chat_ai.xml`).
4. `boutons-pastille` : barre d'actions en pastille (`bg_actions_pill.xml`), après `boutons-actions`.
5. `krux-code-import-zip` : « Importer un projet (zip) » dans Krux Code (`core/ProjectStore.java`, testé hors téléphone).
6. `krux-code-import-dossier` : « Importer un dossier / projet Android » (après le 5).
7. `projets-fichiers-krux-code` : projets visibles dans Fichiers, navigateur et édition dans Krux Code, nouveaux fichiers créés dans le projet (après le 6).

### Reste à faire, dans l'ordre
1. [ ] Écran de stockage dans les quotas : « Stockage X / 20 Mo » et bouton **Libérer de l'espace** (liste des projets par taille, suppression). Où se trouve l'écran des quotas : à préciser.
2. [ ] Sauvegarde en ligne liée au compte (les projets reviennent après reconnexion) : D1 (5 Go gratuits), un projet compressé en morceaux d'environ 1 Mo, seuls les fichiers modifiés renvoyés. 20 Mo par compte, compté sur la taille compressée. Dépend du Worker Cloudflare déployé (token bloqué). Pas KV (1000 écritures par jour pour tout le compte).
3. [ ] Krux Code : compiler un projet importé sans IA (circuit `GithubClient` : createRepo, putFile base64, dispatch, waitRun, downloadApk ; workflow `build.yml` Gradle à fournir si le zip n'en a pas). Puis clonage de dépôts et commandes.
4. [ ] Bouton « Réessayer » dans la barre d'actions ; tableaux à refaire ; consignes de design dans le prompt des sites générés.
5. [ ] Proxy Cloudflare, retrait des clés de l'APK, puis révocation des clés.
- Tester l'APK une seule fois, après toutes les modifications.

### Nettoyage du README à faire
- Le bloc « À faire, noté le 22/11/2026 » est en double.
- `README.m` est vide : à supprimer seulement avec ton accord.

## Journal du 24/11/2026 (nuit)

### Fait
- [x] Emoji remplacés par des icônes SVG dans l'app, en 3 patchs poussés (afd4b80, 7a7a244, c8b8cf3).
  - Nouvelles icônes au trait : ic_line_agent, globe, package, file, mic, trash, check, pin, play, chat. Helper ui/UiIcons (teinte et taille par le code).
  - Écrans touchés : Capacités, fichiers livrés, Mémoire (micro, corbeille), Live (micro), liste des modèles (coche), Krux Code (barre de page, liste de fichiers), historique (épingle), boutons de code (codebar.js, SVG intégrés), écran Studio.
  - Emoji simplement retirés du texte : photo jointe, remerciement du retour, minuteur de BuildProgress (tests mis à jour, 21/21), vidéo WebScraper, commentaires de KruxModel.
- [x] Patchs du soir (recherche 45 s, vérif des clés CI, boutons d'action et pastille, import zip et dossier, projets dans Fichiers) : présents dans l'historique git, donc poussés.

### À tester sur téléphone (rien n'est compilé en local)
- Micro de LiveActivity : icône centrée dans le rond ? (sinon passer à un ImageView).
- Écran Studio : icônes des deux boutons (teinte fixe, ne suit pas le thème par tags).
- Barre Copier / Partager / Télécharger sous les blocs de code.

### Laissé volontairement
- Périmètre : l'app seulement. Les emoji des .md ne sont pas touchés.
- ChatAdapter : les caractères de coche dans la regex de nettoyage restent (ce n'est pas de l'affichage).
- Symboles typographiques (flèches, coches, ▶ ■) : 38 occurrences gardées comme icônes d'interface.
- clav/arrays.xml : emoji gardés (clavier d'emoji).

## Journal du 27/11/2026

### Fait
- [x] Menu latéral refait (896ffbf) : Nouveau chat = seul bouton plein, liste de navigation sans cadres avec une icône au trait par entrée (apps, dossier, code, studio, réglages, loupe, croix), quota inchangé placé sous la liste, loupe à côté du titre Discussions, Se déconnecter retiré du menu (il reste dans Paramètres).
- [x] Historique (fa5e84c) : groupes Épinglées / Aujourd'hui / Hier / 7 derniers jours / Plus ancien ; emoji retirés des anciens titres à l'affichage ; repli sur la date de l'identifiant session_ quand la date du message est invalide.
- [x] Accueil : tracé du globe adouci (facteur 0.6 sur k dans WelcomeGlobeView) pour que le titre et les suggestions restent lisibles.
- [x] Correctif CI (be08a19) : this remplacé par KruxCodeActivity.this dans le callback agent de Krux Code.

### À tester sur téléphone (rien n'est compilé en local)
- Menu latéral : couleur du bouton Nouveau chat selon le thème, icônes teintées, loupe qui ouvre la recherche.
- Historique : titres de groupes lisibles dans tous les thèmes ; anciennes discussions rangées dans Plus ancien si leur date est invalide.
- Accueil : globe encore visible mais sans gêner le texte (ajuster le 0.6 de WelcomeGlobeView si besoin).

### Reste à faire
- Paramètres : même logique de hiérarchie ; la ligne Mes apps y double l'entrée du menu.
- README.m (fichier vide) : à supprimer sur accord.
- Écran de stockage dans les quotas, sauvegarde en ligne (D1), proxy Cloudflare pour retirer les clés de l'APK.
