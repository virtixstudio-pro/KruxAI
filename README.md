# Krux

Application Android de chat IA, de création d'applications et de conversation vocale, développée par **Virtix Studio**.

> Anciennement « KRUX AI ». Le nom affiché dans l'application est désormais **Krux**.

| | |
|---|---|
| **Nom affiché** | Krux |
| **Package / applicationId** | `com.virtixstudio.kruxai` |
| **Version** | `2.10.0` (versionCode 38) |
| **Android minimum** | API 24 (Android 7.0) |
| **Cible / compilation** | API 34 (Android 14) |
| **Langage** | Java 8 |
| **Interface** | XML + AppCompat, thème personnalisé |

---

## Fonctionnalités

- **Chat IA multi-fournisseurs** avec routage et repli automatique entre Groq, Cerebras, Mistral, Hugging Face et Gemini (`router/ModelRouter`, `api/ApiClient`).
- **Recherche web et sources** : recherche, classement et scraping de pages, avec affichage des sources (`api/WebSearchEngine`, `api/SearchRanker`, `web/WebScraper`).
- **Mode vocal et Live** : reconnaissance vocale, synthèse vocale, avatar animé et service flottant (`live/`).
- **Voix plus vivante** : meilleure voix française installée, lecture phrase par phrase avec pauses et intonation, sans lire les emojis, astérisques ni liens (`utils/VoiceStyler`).
- **Menu d'appui long sur les réponses** : copier, tout sélectionner, partager (`ui/MessageActions`).
- **Boutons de lien** : l'IA écrit `[btn:Texte|https://…]`, l'app affiche un bouton arrondi aux couleurs du thème (`utils/LinkButtons`).
- **Navigateur intégré** : les liens (boutons, texte, sources) s'ouvrent dans l'app, avec retour, actualiser et « Chrome » (`ui/HtmlPreviewActivity`, `ui/LinkOpener`).
- **Studio / App Builder** : génération d'applications, modèles de build, validation, suivi de progression, stockage Cloudinary / Worker (`AppBuilder`, `builder/`, `core/`).
- **Mémoire et historique** : base Room, extraction et sélection de souvenirs, synchronisation cloud (`db/`, `utils/MemoryExtractor`, `utils/ChatHistoryManager`, `core/CloudSync`).
- **Personnalisation** : palettes et couleurs modifiables via un système de thème par tags `krux:*` (`ui/KruxTheme`).
- **Multilingue** : français, anglais, espagnol et chinois (`utils/LanguageManager`, `res/values-*`).
- **Comptes et synchronisation** : Firebase Auth (email / mot de passe) et Firestore (`firestore.rules`).
- **Export et partage** : export de la discussion en texte, PDF, CSV ou archive ZIP (PDF + CSV + texte + fichiers créés par Krux), à enregistrer dans Téléchargements ou à partager (`utils/ChatExporter`) ; copie de code, partage de fichiers et gestion des médias.

---

## Stack technique

| Domaine | Technologie |
|---|---|
| Plateforme | Android SDK 34, minSdk 24 |
| Langage | Java 8 |
| Build | Gradle, Android Gradle Plugin `8.2.2` |
| UI | AppCompat `1.6.1`, Material Components `1.11.0`, ConstraintLayout `2.1.4` |
| Backend | Firebase Auth + Firestore (BOM `32.7.2`) |
| Base locale | Room `2.6.1` |
| Réseau | OkHttp `4.12.0` |
| JSON | Gson `2.10.1` |
| Markdown | Markwon `4.6.1` |
| HTML / scraping | Jsoup `1.16.1` |
| CI | GitHub Actions, JDK 17, Gradle 8.13 |

---

## Architecture du projet

```text
.
├── app/
│   ├── google-services.json          # Configuration Firebase
│   ├── build.gradle                  # Config Android, clés API, dépendances
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   ├── krux_config.json      # Build, limites, worker, Cloudinary, modèles
│       │   ├── codebar.js            # Barre d'outils des blocs de code
│       │   ├── krux_media.json       # Généré par la CI (non versionné)
│       │   └── krux_builder.json     # Généré par la CI (non versionné)
│       ├── java/com/virtixstudio/kruxai/
│       │   ├── adapters/             # Adaptateurs RecyclerView
│       │   ├── api/                  # Clients IA, recherche web, classement
│       │   ├── builder/              # Moteur de génération d'applications
│       │   ├── core/                 # Config, sync, quota, réflexion, worker
│       │   ├── db/                   # Room (mémoire)
│       │   ├── live/                 # Mode vocal, avatar, service flottant
│       │   ├── models/               # Modèles de données
│       │   ├── router/               # Routage des modèles IA
│       │   ├── ui/                   # Activités, vues et thème custom
│       │   ├── utils/                # Langue, fichiers, mémoire, markdown
│       │   └── web/                  # Scraping HTTP
│       └── res/
│           ├── drawable/             # Icônes et formes vectorielles
│           ├── layout/               # Écrans et composants XML
│           ├── values/               # Chaînes FR, couleurs, styles
│           └── values-{en,es,zh}/    # Traductions
├── tools/builder-tests/              # Tests Java du moteur de build
├── firestore.rules                   # Règles de sécurité Firestore
├── build.gradle
├── settings.gradle
└── .github/workflows/                # CI Android
```

### Écrans principaux

- `SplashActivity` : écran de démarrage et routage selon l'état de connexion.
- `AuthWelcomeActivity` : accueil avant authentification.
- `LoginActivity` : connexion (et mode inscription intégré).
- `SignupActivity` : écran d'inscription dédié.
- `MainActivity` : chat, historique, réglages rapides, vocal, export.
- `StudioActivity` : agents et création d'applications.
- `LiveActivity` : conversation vocale et avatar.
- `HtmlPreviewActivity` : aperçu plein écran des pages HTML créées par l'IA, et navigateur intégré pour les sites.
- `SettingsActivity` : thème, langue, compte, informations.

---

## Prérequis

- **JDK 17** (utilisé par la CI).
- **Android SDK Platform 34** et les Build Tools associés.
- **Gradle 8.x** installé localement : le dépôt ne contient pas de wrapper `gradlew`.
- Un projet **Firebase** avec `app/google-services.json`.
- Les clés API des fournisseurs IA (voir ci-dessous).

---

## Configuration

### Variables d'environnement / fichier `.env`

Le fichier `.env` est **ignoré par Git** et ne doit jamais être publié. Gradle lit les clés depuis `.env`, avec repli sur les variables d'environnement système.

```dotenv
GROQ_API_KEY=...
CEREBRAS_API_KEY=...
MISTRAL_API_KEY=...
HF_API_KEY=...
GEMINI_API_KEY=...
```

Ces valeurs sont injectées dans `BuildConfig` au moment du build.

### Fichiers d'assets générés

Ces fichiers sont ignorés par Git et créés par la CI ou manuellement :

| Fichier | Contenu | Secret CI |
|---|---|---|
| `app/src/main/assets/krux_media.json` | Clé Pexels pour la galerie média | `PEXELS_API_KEY` |
| `app/src/main/assets/krux_builder.json` | Token et quota du builder | `KRUX_BUILDER_TOKEN` |

### Configuration applicative

`app/src/main/assets/krux_config.json` centralise :

- les versions et limites de build ;
- le nombre de tentatives ;
- l'URL du Worker Cloudflare ;
- la configuration Cloudinary (sans secret) ;
- la liste documentaire des modèles IA.

Aucune clé d'API ne doit être placée dans ce fichier.

### Firebase

- `app/google-services.json` : configuration du projet Firebase.
- Authentification : email / mot de passe.
- Firestore : profils, chats, mémoire, quotas et feedbacks.
- Les règles de sécurité sont dans `firestore.rules`.

Déploiement des règles (avec Firebase CLI) :

```bash
firebase deploy --only firestore:rules
```

---

## Build

```bash
# Depuis la racine du projet
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME="/chemin/vers/jdk-17"

gradle assembleDebug
```

APK généré :

```text
app/build/outputs/apk/debug/app-debug.apk
```

Build de release (non signé par défaut) :

```bash
gradle assembleRelease
```

> En l'absence de SDK Android configuré, le build échoue avant la compilation. Le projet n'a pas été compilé dans l'environnement de développement actuel.

---

## Tests

Les tests du moteur de build sont des classes Java autonomes :

```bash
cd tools/builder-tests
./run.sh
```

---

## Intégration continue

### `.github/workflows/build.yml`

Déclenché sur `push` vers `main` et `agent/api-unification`, ainsi que sur les pull requests vers `main`.

Étapes :

1. Checkout du dépôt.
2. Installation de **JDK 17** (Temurin).
3. Installation de **Gradle 8.13**.
4. Génération de `krux_media.json` et `krux_builder.json` depuis les secrets GitHub.
5. `gradle assembleDebug`.
6. Affichage des empreintes du certificat de debug.
7. Upload de l'APK en artefact `KruxAI-debug-apk`.

### `.github/workflows/android-builder.yml`

Workflow manuel destiné à la compilation de dépôts publics.

---

## Sécurité

- `.env`, `local.properties`, les builds et les fichiers `*.bak` sont ignorés par Git.
- Les clés API sont intégrées dans `BuildConfig` : **ne pas distribuer publiquement un APK de debug**.
- `google-services.json` contient la configuration Firebase du projet ; il est nécessaire au build.
- Les règles Firestore limitent l'accès aux documents de l'utilisateur connecté.
- Le quota d'usage est plafonné à 20 requêtes par jour dans les règles Firestore.

---

## État actuel et limites connues

- **Nom** : renommé de « KRUX AI » en **Krux** dans le manifeste, les chaînes et les textes visibles.
- **Écran de connexion** : nettoyage visuel en cours (espacements, tailles, icône de visibilité du mot de passe, autofill).
- **Splash** : écran custom de 4,5 s encore en place ; une migration vers l'API `SplashScreen` Android 12+ est recommandée mais pas encore implémentée.
- **Google Sign-In** : bouton présent, configuration Firebase à finaliser.
- **Build local** : non vérifié faute de SDK Android dans l'environnement actuel.
- **Configuration de build** : `krux_config.json` documente des versions plus récentes (AGP 8.13, compileSdk 36) que celles réellement utilisées par le projet (AGP 8.2.2, compileSdk 34).
- De nombreux fichiers de sauvegarde (`*.bak`, `*.before-*`, `*.old`) restent présents dans les sources ; ils sont ignorés par Gradle mais pourraient être archivés ou supprimés lors d'un nettoyage dédié.

---

## Auteur

**Virtix Studio** — projet privé.

---

## Boutons de lien

L'IA affiche un lien en bouton avec ce format, seul sur sa ligne :

```text
[btn:Ouvrir la documentation|https://exemple.com/doc]
```

- Texte du bouton : 40 caractères maximum.
- Seules les adresses `http://` et `https://` sont acceptées.
- Le bouton n'est pas lu à voix haute ; copié ou partagé, il devient `Texte : lien`.

---

## Journal des mises à jour

**14/11/2026**
- Voix : `VoiceStyler` (voix, pauses, intonation, nettoyage du markdown et des emojis) branché sur le chat, le mode Live et la bulle flottante.
- Réponses : menu d'appui long (copier, tout sélectionner, partager) et boutons de lien.
- Liens : navigateur intégré, réutilise `HtmlPreviewActivity`.
- Export : menu « Exporter la discussion » avec 4 formats (texte, PDF, CSV, ZIP), chacun à « Enregistrer » ou « Partager ». Le CSV a les colonnes numero, date, auteur, modele, message.
- Fichiers créés par Krux : le bouton « Aperçu » devient « Visualiser ».
- À venir : blocs de code (couleurs personnalisables, coloration syntaxique, actions séparées), patch d'économie de quotas, vérification de la bannière hors ligne, écran Mémoire v2.
