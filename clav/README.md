# Clav ✦ Clavier certifié

Clavier virtuel Android **sombre, minimaliste et certifié**, écrit en **Java + XML** (aucun
Jetpack Compose, aucune dépendance externe). Il intègre un **badge de certification** —
étoile dentelée bleue avec coche blanche, inspirée du badge de profil vérifié — posé sur la
barre de suggestions, animé et interactif.

![CI](https://github.com/virtixstudio-pro/KruxAI/actions/workflows/clav-android.yml/badge.svg)

## Fonctionnalités

- 🌑 **Design sombre minimaliste** : palette `#0B0E13` / touches `#1A202B` / accent bleu `#1D9BF0`.
- ✦ **Badge de certification** sur la barre de suggestions : pulsation douce en continu,
  popup « Clavier certifié » animé (scale + fade) au toucher.
- ✨ **Feedback visuel fluide** : ripple + réduction à l’appui (`0.94×`), haptique légère,
  apparition en fondu du clavier, chips de suggestion en cascade (`LayoutTransition` + stagger).
- ⌨️ **Clavier complet** : AZERTY (par défaut) / QWERTY commutables, page de symboles,
  majuscules simple + verrouillage (double-tap), retour arrière avec répétition (appui long).
- 🇫🇷 **Accents français** : appui long sur `a e i o u c` → `à â ä é è ê ë î ï ô ö ù û ü ç`.
- 💬 **Suggestions** : dictionnaire français hors-ligne, préfixe + respect des majuscules.
- 😀 **Popup emoji** (24 emoji) et gestion des champs sécurisés (mots de passe : pas de
  composition, suggestions désactivées).
- 🔢 Détection des champs numériques → basculement automatique en page de symboles.
- 🧩 **Code modulaire** : chaque classe a un rôle unique (voir l’architecture ci-dessous).

## Architecture

```
app/src/main/java/com/clav/keyboard/
├── LauncherActivity.java          Onboarding : activation + statut en direct
└── ime/
    ├── ClavImeService.java        Service IME (cycle de vie uniquement)
    ├── KeyboardController.java    Orchestration UI : rangées, états, popups
    ├── KeyboardLayouts.java       Définitions AZERTY / QWERTY / symboles
    ├── KeySpec.java               Description immuable d'une touche
    ├── KeyView.java               Touche : ripple, scale, haptique
    ├── SuggestionBarController.java  Marque, chips de suggestion, badge
    ├── VerifiedBadgeView.java     Badge ✦ : étoile dentelée + coche (Canvas)
    ├── InputMediator.java         Pont InputConnection + mot en composition
    ├── WordSuggester.java         Suggestions par préfixe
    ├── Dictionary.java            Dictionnaire français hors-ligne
    ├── AnchoredPopup.java         Base des popups ancrés (animations)
    ├── BadgePopup.java            Carte de certification
    └── OptionPopup.java           Grille générique (accents / emoji)
```

## Installation de l’APK

1. Téléchargez `app-debug.apk` (artefact de CI ou build local).
2. Installez-le (autorisez « sources inconnues » si demandé).
3. Ouvrez **Clav** → **Activer Clav** → puis choisissez-le comme clavier par défaut.

## Build local (optionnel)

La CI n’a pas besoin de wrapper : elle installe Gradle 8.11.1 via
`gradle/actions/setup-gradle`. En local, avec un Gradle 8.11.x installé :

```bash
gradle assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

Prérequis : JDK 17+ et un SDK Android (`platforms;android-35`, licences acceptées) —
`local.properties` pointe vers le SDK via `sdk.dir`.

## CI / CD (GitHub Actions)

Dans KruxAI : `../.github/workflows/clav-android.yml` (build `clav/` uniquement, déclenché
par les changements sous `clav/**`). Le dossier embarque aussi son workflow autonome
`clav/.github/workflows/android.yml` pour un usage en repo séparé.

- à chaque **push** / **PR** : build `debug` + upload de l’artefact `clav-debug-apk` ;
- à chaque **tag `v*`** : build `release` (signé debug pour installation directe)
  publié automatiquement dans une **GitHub Release** avec l’APK attaché.

Gradle 8.11.1 est provisionné par `gradle/actions/setup-gradle` : aucun wrapper
à committer, le build tourne directement sur les serveurs GitHub.

```bash
git tag v1.0.0 && git push origin v1.0.0   # → Release avec APK
```

## Paramètres du clavier

L’écran d’accueil (également accessible depuis les paramètres système de l’IME) sert de
page d’activation ; la disposition (AZERTY/QWERTY) est mémorisée via `SharedPreferences`.
