# KRUX AI

Application Android native (Java) de Virtix Studio : discussion avec plusieurs modèles d'IA, création d'applications Android, recherche web, voix, mémoire.
Dépôt : `virtixstudio-pro/KruxAI`, branche `main`. Mise à jour du 17 novembre 2026.

## Règles de travail (à respecter)

- Un patch à la fois. Pour chaque patch : le fichier `.patch` (dans `~/storage/downloads`) et UNE commande complète :
  `git apply --check ... && git apply ... && git add -A && git commit -m "..." && git push origin main`
- Le README se met à jour par commandes `sed` ou `printf`, pas par patch (le README local diverge).
- Ne recloner GitHub que si l'utilisateur dit « mise à jour ».
- Ne jamais déplacer ni supprimer de fichiers ou de fonctionnalités sans son accord.
- Rien n'est compilé en local (pas de SDK Android) : toujours le dire. Vérifications : `git apply --check` et `javac` (syntaxe seule). La vraie compilation se fait sur GitHub Actions (artefact `KruxAI-debug-apk`).
- Pas de carte bancaire : uniquement des services gratuits sans carte.
- Interface compacte, réponses courtes, commandes prêtes à copier, honnêteté sur ce qui est testé ou non.
- Ne jamais enregistrer qu'un utilisateur est mineur. Ne pas publier l'APK tant que des clés API sont dedans.

## Déjà poussé sur GitHub

- Voix vivante, menu d'appui long, boutons de lien, navigateur intégré, export (txt, PDF, CSV, ZIP), blocs de code, thème blanc/noir.
- Analyse de photos, pièces jointes en cartes, réflexion discrète sur une ligne, étapes de réflexion réelles.
- Modèles à jour : Prime, Core, Flow, Max, Nova (catalogue vérifié le 17/11).
- Création d'apps : quota de 5 par semaine, plafonds relevés, permissions utiles autorisées, aperçu interactif, secours automatique entre modèles.
- **Fichiers livrés** : bloc compact « N fichiers » avec **Tout enregistrer (ZIP)**, Partager, liste de 5 lignes (`DeliveredFileCards`).
- **Apps générées uniques** : identifiant d'installation différent par app (`com.krux.<nom><6 hex>`, `BuildTemplates.uniqueAppId`) et direction visuelle tirée au hasard (`DesignDirection` : palette, formes, typographie, structure, navigation).
- **Apps modifiables et réflexion** : bouton **Modifier** dans « Mes apps », palette imposée vérifiée, étape « Vérification de la réponse » (et « Correction de la réponse ») dans la ligne de réflexion, seulement quand la relecture a lieu (`AnswerCheck.Hook`).

## Patchs prêts, à appliquer dans cet ordre


Aucun patch en attente.

## Reste à faire, dans l'ordre

1. **Pauses anti-saturation** : espacer les appels par fournisseur (petit délai) pour éviter les erreurs 429, et remplacer « Ce moteur est saturé, je passe sur un autre… » (`MainActivity`, `onFallback` et `onWaiting`) par un texte neutre type « Je prends une courte pause… ».
2. **Sélecteur de modèles** plus agréable (`showKruxModelSelector`) : couleurs du thème, badge PETIT/GRAND, description courte.
3. **Échec « L'IA n'a pas produit de code exploitable »** (`AppBuilder.generateParsed`) : cause à confirmer avec la phrase complète affichée après les deux-points. Pistes : chemin refusé par `BuildValidator.pathProblem`, JSON invalide, manifeste manquant, image de l'analyse de photos restée en attente (`ApiClient.nextImage`).
4. **Création en plusieurs passes** (plan et écrans, fichiers par lots, relecture) : c'est la vraie limite de qualité des apps générées, aujourd'hui tout est écrit en une seule génération.
5. **Clé de signature stable par app** : sans elle, une app modifiée ne peut pas remplacer l'ancienne (chaque build GitHub signe avec une clé de test différente).
6. Recherche web mieux structurée : résultats et sources affichés, plusieurs recherches enchaînées, deuxième moteur, plus de pages lues.
7. Mémoire : Krux décide de ce qu'il retient, dès l'inscription.
8. Patch d'économie de quotas, vérifier la bannière hors ligne « Réessayer ».
9. Backlog : déployer le proxy IA puis retirer et renouveler les clés de l'APK ; mode Live ; images (filigrane, partage en fichier) ; préparation Google Play ; test de la connexion Google du site.

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
