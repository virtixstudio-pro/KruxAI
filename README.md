CONTEXTE KRUX (dépôt virtixstudio-pro/KruxAI, Termux ~/KruxAI)

PRÉFÉRENCES DE L'UTILISATEUR
- Un patch à la fois. Pour chaque patch : le fichier .patch + UNE commande complète :
  git apply --check ... && git apply ... && git add -A && git commit -m "..." && git push origin main
  (fichiers dans ~/storage/downloads)
- Mettre le README à jour à chaque patch, par commandes sed/printf (pas par patch : son README local diverge).
- Ne recloner GitHub que s'il dit « mise à jour ».
- Ne jamais déplacer ni supprimer de fichiers ou fonctionnalités sans son accord.
- Rien n'est compilé (pas de SDK Android) : toujours le dire. Vérifier avec git apply --check et javac (syntaxe seule).

DÉJÀ POUSSÉ SUR GITHUB
Voix vivante, menu d'appui long, boutons de lien [btn:Texte|url], navigateur intégré,
export (txt, PDF, CSV, ZIP), blocs de code (couleurs personnalisables, actions séparées),
thème blanc/noir sur toute l'app, ménage des doublons, + les patchs de l'autre session
(icônes de code, recherche sans deviner, apps/sites sans limites).

PATCHS PRÊTS MAIS À APPLIQUER PAR L'UTILISATEUR (dans cet ordre)
1. reflexion-analyse.patch : analyse préalable des demandes (core/Reflection), délai 6 s.
2. reflexion-verification.patch : relecture avant envoi (core/AnswerCheck), délai 8 s, 1 révision max.
Les deux touchent MainActivity (envoi) et ApiClient. Après leur push, l'utilisateur dit « mise à jour ».

RESTE À FAIRE, DANS L'ORDRE
1. Restrictions des apps générées (VALIDÉ : 5 par semaine ; patch restrictions-apps appliqué ; WorkManager reste bloqué) :
   - tailles : 60 fichiers, 40 000 car./fichier, 200 000 au total, 4 corrections auto
     (plafonds de BuildSpec, limits dans krux_config.json)
   - quota de créations : réglable, 5/semaine (avant 2, lu dans krux_builder.json
     ignoré par Git, contrôle côté app seulement)
   - autoriser : notifications, alarmes/WorkManager, localisation, caméra, lecture des médias
     (BuildValidator : ALLOWED_PERMS et BANNED_CODE)
   - GARDER BLOQUÉ : exécution de commandes, chargement de code à la volée, réflexion, SMS,
     accessibilité, admin d'appareil, superposition, démarrage au boot, contacts.
2. Reprise d'apps : (a) création interrompue visible dans « Mes apps » (BuildCheckpoint existe,
   24 h, même description exacte) ; (b) sauvegarder le code en zip à côté de l'APK (WorkerStore ne
   garde que l'APK) + bouton « Modifier » dans MyAppsActivity.
3. Recherche web mieux structurée : affichage des résultats et des sources, plusieurs recherches
   enchaînées, deuxième moteur, plus de pages lues. WebScraper existe, non branché, à conserver.
4. Affichage de l'envoi de fichiers (DeliveredFileCards, ChatAdapter). À préciser avec lui.
5. Mémoire : Krux décide de ce qu'il retient, dès l'inscription (MemoryRepository, MemoryBook,
   MemoryExtractor, PrivacyActivity).
6. Patch d'économie de quotas : s'inspirer du mode économique de l'ancien ModelRouter (supprimé,
   retrouvable avec git log --diff-filter=D) ; le vrai routage est dans ApiClient.
7. Vérifier la bannière hors ligne « Réessayer ».
8. Backlog : déployer le proxy IA puis retirer et renouveler les clés de l'APK ; README « État actuel » ;
   mode Live (conversation continue, mémoire, recherche) ; images (filigrane, partage en fichier) ;
   préparation Google Play ; test de la connexion Google du site.
9. Optionnel, à lui proposer : supprimer ses fichiers de sauvegarde locaux non suivis par Git
   (commande de liste déjà donnée, find ... -print).

