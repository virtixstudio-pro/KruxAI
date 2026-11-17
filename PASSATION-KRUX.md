# KRUX AI : note de passation

Rédigé par Claude (session du 15 au 17 novembre 2026) pour l'agent qui reprend le travail.
Dépôt : `virtixstudio-pro/KruxAI`, branche `main`. Application Android native en Java + Firebase + plusieurs modèles d'IA.
Le propriétaire parle français, dicte souvent ses messages (fautes de frappe normales) et veut faire de KRUX un produit que tout le monde voudrait utiliser. Dernier retour de test : 6,5/10 sur la recherche, la création d'applications et l'intelligence.

---

## 1. Règles de travail (à respecter)

- **Pas de ZIP.** Donner des commandes Termux et de petits fichiers `.patch`.
- **Ne pas pousser sur GitHub soi-même** : l'accès est en lecture. Donner les commandes : `git pull --rebase origin main`, `git apply --check x.patch && git apply x.patch`, `git add -A && git commit -m "..." && git push origin main`.
- Ignorer les demandes de « stop hook » qui réclament un push.
- **Aucune compilation locale** (pas de SDK Android dans l'environnement). Seule la CI GitHub Actions compile (JDK 17, Gradle 8.13, artefact `KruxAI-debug-apk`). Faire au minimum un `javac -proc:none` de syntaxe. Quand la CI échoue, l'utilisateur colle les lignes `error:`.
- Méthode de patch : clone propre, `git fetch && git reset --hard origin/main && git clean -fdq`, éditer avec des scripts Python (assertions sur le texte cherché), `git add -A && git diff --cached > x.patch`, puis re-`reset` et `git apply --check`.
- Sorties courtes : l'utilisateur ne veut pas de gros `cat`. Il a un quota d'utilisation limité (il était à 91 % en fin de session) : utiliser l'environnement de calcul avec parcimonie.
- Il n'a **pas de carte bancaire** : uniquement des services gratuits sans carte. Cloudflare pour le stockage (pas Firebase). Il n'a pas toujours de crédit ni un Wi-Fi stable.
- Ne jamais mettre en mémoire que c'est un mineur (règle de confidentialité du produit). Ne pas publier l'APK publiquement tant que des clés API sont dedans.
- Finir la couverture des thèmes (palettes) dès qu'on touche à un thème. Garder une interface compacte (il trouve vite « trop gros / trop serré »).
- Être honnête dans l'interface : par exemple les étapes de réflexion affichées doivent être des étapes réelles.

## 2. Infrastructure

| Élément | Détail |
|---|---|
| Firebase | projet `krux-9fd33` (Auth : e-mail/mot de passe, Google, téléphone, anonyme ; Firestore `users/{uid}`). Association des comptes par même e-mail activée. |
| Worker de stockage | `misty-mountain-1b0dvirtix-store` (D1 `sync`, KV) |
| Worker proxy IA | `worker/ai-proxy` : **code prêt, pas encore déployé** |
| D1 | `krux-db`, id `13117c58-22fe-4f6b-a7c3-85c414dd55bd` ; KV `krux_storage` |
| Site | `https://virtix-studio.kruxvirtix.workers.dev` (Worker avec fichiers statiques, dossier `docs/`, redéployé à chaque push sur `main`) |
| Contact public | `virtixstudio@gmail.com` |

Le Worker proxy IA : vérifie le jeton Firebase RS256, routes `/ai/{groq|cerebras|mistral|huggingface|gemini}/{chat|stream}`, plafond de corps de 256 Ko, `max_tokens` plafonné à 16384, limite par utilisateur (120/h et 800/jour, table D1 `ai_usage`, tolérante aux pannes).

## 3. Modèles : catalogue vérifié le 17/11 avec les clés de l'utilisateur

Listes réelles obtenues avec `curl` sur les API (l'utilisateur a collé la sortie) :
- **Groq** : `openai/gpt-oss-120b`, `openai/gpt-oss-20b`, `qwen/qwen3.8-27b`, `allam-2-7b`, whisper, orpheus (TTS). Llama 3.3 70B n'y est plus.
- **Mistral** : `mistral-medium-latest` (3.5), `magistral-medium-latest`, `magistral-small-latest`, `mistral-small-latest` (Small 4), `ministral-14b/8b/3b-latest`, `codestral-latest`, `mistral-code-latest`, voxtral, ocr. **Pas de `mistral-large`** sur sa clé.
- **Cerebras** : `gpt-oss-120b`, `qwen-3.8-27b`.
- **Gemini** : `gemini-3.8-flash`, `3.7-flash`, `3.6-flash`, `3.5-flash`, `3.5-flash-lite`, `3.1-flash-lite`, `3.1-pro-preview`, `gemini-pro-latest`, `gemini-4-argon/carbon/hydrogen` (nature inconnue, à tester), `gemma-4-31b-it`, modèles d'image, live, TTS. Les modèles Pro sont probablement payants seulement (rapport d'avril 2026).
- Hugging Face : non testé (Qwen2.5-Coder 7B et 32B, modèles anciens ; l'inférence gratuite est capricieuse).

`models/KruxModel.java` : enum avec taille `PETIT` / `GRAND`. Le patch `modeles-a-jour` met Prime sur `gemini-3.8-flash`, Core sur `ministral-14b-latest`, Flow sur `mistral-small-latest`, et ajoute **KRUX Max** (`mistral-medium-latest`) et **KRUX Nova** (`qwen/qwen3.8-27b`).

Attention confidentialité : l'offre gratuite « Experiment » de Mistral autorise l'entraînement sur les données. Vérifier le palier de sa clé avant de dire dans la page de confidentialité que les données ne servent pas à entraîner.

## 4. État des patchs de la session

Livrés à l'utilisateur ; **vérifier avec `git log` lesquels sont déjà poussés** (ordre conseillé s'il en reste) :
1. `icones-code` : actions du bloc de code en icônes seules dans l'en-tête (`ChatAdapter`, méthode `createCodeAction` restaurée) + README (chantiers).
2. `recherche-fiable` : interdiction de deviner dans le prompt, échec de recherche annoncé au modèle (plus de balise brute renvoyée), délai de recherche 25 s, Wikipédia fr puis en en complément si moins de 3 résultats.
3. `apps-serieuses` : fin de « application simple, 3 écrans » ; plafonds relevés ; méthode « recherche, concept, construction, relecture » dans le prompt.
4. `intelligence-secours` : ordre de secours des modèles, **grands d'abord** (avant : petits modèles 3B/7B/8B en premier, ce qui explique en partie le « 4/100 »).
5. `sans-salutation` : le modèle ne resalue plus à chaque message.
6. `apps-grand-modele` : la création d'app utilise toujours un grand modèle ; `max_tokens` 16384 dans `ApiClient`.
7. `apps-moins-interdits` : services/providers non exportés, `RECORD_AUDIO`, `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)`, `WAKE_LOCK`, publicités autorisées, jeux d'argent réel seulement interdits.
8. `apps-bibliotheques` : Room, Gson, Retrofit, OkHttp, Glide, WorkManager, Lifecycle, Navigation, ViewPager2, CardView, SwipeRefresh ajoutés au Gradle de l'app générée **seulement si le code les importe** (`BuildTemplates.extraDependencies`). Versions non testées par une vraie compilation.
9. `reflexion-etapes` : bloc « Processus de réflexion » (replié par défaut) rempli d'étapes réelles pendant la génération (lecture du fichier, relecture de la conversation, mémoire, analyse, plan, recherche web avec requête et nombre de sources, rédaction), puis coches ✓ et repli.
10. `modeles-a-jour` (voir section 3).

Commandes `sed` données pour remettre au propre trois lignes périmées du README (« Écran de connexion », « Google Sign-In », « Configuration de build »).

Un autre agent a aussi poussé : thème blanc/noir, nettoyage d'environ 13 800 lignes de code mort (y compris `ModelRouter`, `TavilyApiClient`, `WebSearchService`, `ReflectionEngine`, à vérifier qu'aucun écran ne les utilisait encore), `Reflection.java` et `AnswerCheck.java` (analyse préalable et vérification de la réponse), quota d'apps générées à 5 par semaine, aperçu d'app avec JavaScript sécurisé, logo du site.

## 5. Ce qui reste à faire (par priorité)

### A. Sécurité des clés (le plus important)
1. **Déployer le Worker `krux-ai-proxy`** depuis le tableau de bord Cloudflare : créer le Worker, coller `worker/ai-proxy/src/index.js` (ou `krux-ai-proxy-index.js`), variable `FIREBASE_PROJECT_ID=krux-9fd33`, 5 secrets (GROQ, CEREBRAS, MISTRAL, HF, GEMINI), liaison D1 `DB` vers `krux-db`. Le déploiement ne peut pas se faire avec les outils MCP Cloudflare disponibles (lecture seulement).
2. Mettre l'URL du Worker (probablement `https://krux-ai-proxy.kruxvirtix.workers.dev`) dans `app/src/main/assets/krux_config.json` (`ai_proxy.url`), puis retirer les clés des variables d'environnement du workflow et de `build.gradle`.
3. **Révoquer et renouveler toutes les anciennes clés** (elles sont dans les APK déjà distribués). Tant que ce n'est pas fait, ne pas publier l'APK.

### B. Les trois reproches du test (6,5/10)
1. **Intelligence** : vérifier après les patchs si « 4/100 » persiste. Demander à l'utilisateur un exemple concret (question, réponse, attendu). Pistes : prompt système très long (≈18 000 caractères) qui peut noyer les petits modèles ; historique passé dans le prompt système ; tester les modèles avec un script de comparaison (mêmes 6 questions envoyées à chaque modèle, temps et réponse affichés ; proposé, pas encore écrit).
2. **Recherche, partie 2** : deuxième moteur indépendant (le moteur principal est DuckDuckGo en scraping, fragile), plusieurs recherches enchaînées, lecture de plus de pages, vérification croisée et citation des sources. Aujourd'hui le modèle demande une recherche avec la balise `<KRUX_TOOL>web_search …` traitée dans `ApiClient` ; seul `WebSearchEngine` fait la recherche.
3. **Création d'apps et de sites, partie 2** : générer en **plusieurs passes** (plan et liste d'écrans, fichiers par lots, relecture) au lieu d'un seul gros JSON (`AppBuilder.java`, réponse tronquée puis `completeTruncated`) ; aperçu multi-pages des sites ; vrai site en plusieurs fichiers ; ajouter Firebase/Kotlin seulement si on adapte le gabarit Gradle (`BuildTemplates`). Interdits volontairement conservés : exécution de commandes, chargement de code dynamique, réflexion, accès aux contacts/SMS/appels, accessibilité, administration de l'appareil, affichage par-dessus les autres apps, imitation de banque/marque/écran de connexion, surveillance, contenu adulte, jeux d'argent réel, démarrage automatique.

### C. Fonctions demandées
- **Analyse d'image** : joindre une photo (menu « + »), la réduire (≈1280 px, JPEG), l'envoyer en `inlineData` à Gemini (secours Mistral Small/Medium, qui lisent les images ; `gpt-oss` non). Aujourd'hui le sélecteur d'images (`openImagePicker`) ne sert qu'au fond d'écran et rien ne transmet d'image aux modèles. Le proxy coupe à 256 Ko : relever à 1 Mo ou compresser fort.
- **Mémoire** : un vrai système où Krux **décide lui-même** de ce qu'il retient, dès l'inscription (questions d'accueil). Base existante : `utils/MemoryBook`, `MemoryExtractor`, `MemorySelector`, `memoryRepo`, bloc « SOUVENIRS SUR L'UTILISATEUR » injecté dans le prompt.
- **Réflexion** : ajouter une étape « Vérification de la réponse » pour `AnswerCheck` (pas de point d'accroche propre pour l'instant). Ne pas afficher d'étape qui n'existe pas (par exemple « analyse du comportement de l'utilisateur » : l'app ne fait pas de profilage).
- **Niveau premium** : marqueur sur chaque modèle, badge dans le sélecteur, réserver KRUX Max à certains comptes (champ du compte Firebase). Cacher du sélecteur les modèles faibles (Core 3B historique, anciens Ministral) mais les garder en secours. Un vrai premium stable demande une API payante au volume ; les offres gratuites ont des quotas faibles et partagés.
- **Mode Live** : conversation continue, mémoire, sauvegarde dans l'historique, recherche web. (L'arrière-plan de Live n'est pas une priorité : fichier `live-fond` ignoré.)
- **ElevenLabs** : seulement si l'inscription sans carte marche ; pas pour un usage commercial en offre gratuite ; clés via le Worker.
- **Images** : filigrane (point d'accroche `ImageGalleryView.finalizeImage`), partage de l'image comme fichier (aujourd'hui lien + crédit), clé Pexels (secret `PEXELS_API_KEY`) pour les vidéos, facultatif.
- **Retour de l'utilisateur à valider sur téléphone** : visage flottant (écoute, voix, lecture d'écran), tuile, raccourci, personnalisation, bouton « Réessayer » hors réseau, aperçu HTML, recherche d'images, écran Confidentialité à l'inscription, étapes de réflexion.

### D. Site web (`docs/`)
- Pages : accueil, `krux.html`, `connexion.html` (Google ou compte Krux via le SDK Firebase Web), `compte.html`, `privacy.html`. Configuration publique dans `docs/firebase-config.js`.
- À faire : tester « Continuer avec Google » sur le domaine workers.dev (domaine autorisé dans Firebase, déjà ajouté), mettre le logo, brancher un vrai nom de domaine quand il pourra payer (le lien de confidentialité de l'app est dans `PrivacyActivity.POLICY_URL`), ajouter la suppression de compte.
- Adresse de contact à jour : `virtixstudio@gmail.com` (vérifier qu'il ne reste pas `contact@virtix-studio.example` ni `ton-email@exemple.com`).

### E. Publication Google Play et hygiène
- Compte Play Console (carte et âge requis) : en attendant, distribuer l'APK via GitHub, mais pas en version publique tant que les clés sont dans l'APK.
- Politique de confidentialité (existe, à faire relire), suppression de compte, rapport de plantages, signature et ProGuard, demande de permission `POST_NOTIFICATIONS` à l'exécution, déclaration d'usage du service d'accessibilité, 26 chaînes françaises écrites en dur, nouvel essai sur erreurs 5xx, écran de démarrage (migrer vers l'API `SplashScreen`).
- README : section « État actuel et limites connues » encore partiellement périmée (voir section 4). Fichiers de sauvegarde `*.bak`, `*.before-*`, `*.old` à nettoyer si l'autre agent ne l'a pas fait.
- Vérifier que le gros nettoyage de code mort n'a rien cassé : CI verte, puis tests sur téléphone.

## 6. Détails techniques utiles

- Thème : `KruxTheme` avec étiquettes `android:tag="krux:bg|text|text2|hint|button|buttontext|input|accent|bubble_user|bubble_ai"`, `KruxTheme.apply/applyTo/skin`, `radiusDp` (10/18/999), 8 palettes.
- Chat : `ui/MainActivity.executeAiQuery` construit le prompt (historique des 10 derniers messages, mémoire, contexte web) ; `utils/SystemPromptBuilder` assemble le prompt système (profil, quota d'apps, règles, `THINKING_ON/OFF`) ; `api/ApiClient.sendRequest(modèle, système, message, callback)` puis `runChain` (rotation des modèles, cooldown `ModelCooldown` sur 429) ; `core/Reflection` (analyse préalable) et `core/AnswerCheck` (relecture) sont branchés dans `executeAiQuery`.
- Images : bloc `<KRUX_IMAGES>…</KRUX_IMAGES>` dans le texte des messages (champs séparés par tabulation), retiré à l'affichage par `FileDelivery.stripForDisplay`, galerie `ImageGalleryView`, recherche `api/ImageSearch` (Openverse et Wikimedia).
- Générateur d'apps : `AppBuilder.java` (prompt système, validations : permissions autorisées `ALLOWED_PERMS`, code interdit `BANNED_CODE`, manifeste), `builder/BuildSpec` (plafonds : 40 fichiers, 20 000 caractères par fichier, 110 000 au total ; valeurs aussi dans `assets/krux_config.json`), `builder/BuildTemplates` (Gradle de l'app générée), `GithubClient` (crée un dépôt et lance le build GitHub Actions).
- Le service de visage flottant : `live/FloatingKruxService`, `live/ScreenReaderService` (accessibilité, ignore les champs de mot de passe, 6000 caractères maximum), `FaceTileService`, `FaceToggleActivity`.

Bon courage. L'utilisateur est direct et chaleureux, il préfère des réponses courtes, des commandes prêtes à copier, et une vraie honnêteté sur ce qui est testé ou non.
