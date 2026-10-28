# TV Launcher

Un écran d'accueil personnalisé pour **Android TV / Google TV**, écrit en Kotlin + Jetpack Compose for TV, avec un **installeur Windows** qui fait tout à ta place : connexion à la TV, sauvegarde, nettoyage (debloat), compilation et installation.

## Fonctionnalités

- Fond d'écran nature renouvelé toutes les 30 minutes (ou à la demande), horloge, date et météo actuelle
- Encart météo de la semaine (7 jours, barres de températures)
- Applications favorites réorganisables (appui long : déplacer / retirer) et tiroir « Toutes les applications »
- **Suggestions** quand tu survoles YouTube, Prime Video, Apple TV, Canal+ ou Disney+ *(option)*
- Carrousel « Dernières sorties Canal+ » avec résumé
- Utilisation de la RAM et du stockage sous l'horloge *(option)*

## Compatibilité

| | |
|---|---|
| ✅ Prévu pour | TV et box **Android TV / Google TV**, **Android 8.0 (API 26) ou plus** : Philips (Android TV), Sony Bravia, TCL, Hisense (Android TV), Xiaomi Mi Box, Nvidia Shield, Chromecast avec Google TV… |
| ❌ Incompatible | Samsung (Tizen), LG (webOS), Roku, Apple TV, Fire TV d'Amazon, TV sous VIDAA ou Titan OS |

**Testé uniquement sur un Philips 55OLED810.** Sur les autres modèles, la touche Paramètres de la télécommande, le nettoyage et la définition du launcher par défaut peuvent se comporter différemment. Le flou d'arrière-plan du tiroir d'applications demande Android 12 ou plus.

## À savoir

- Les **suggestions ouvrent l'application** (Prime Video, Apple TV, Canal+, Disney+), pas la page du film ou de la série. **Seul YouTube** ouvre directement la vidéo.
- Selon le modèle de TV, le **bouton Paramètres de la télécommande peut ne plus fonctionner** (la TV le garde pour elle). Utilise l'icône ⚙ en haut à droite de l'écran d'accueil.
- La rangée « Reprendre la lecture » (Watch Next) n'est **pas possible** : elle exige une permission réservée aux applications système.
- Les suggestions et les sorties Canal+ sont réglées sur la **France**.
- Le nettoyage ne supprime rien : il désactive des paquets (`pm disable-user`) et peut être annulé depuis l'installeur. La liste vient de pratiques courantes sur Android TV et n'est pas garantie pour toutes les marques.

## Installer (Windows)

1. Télécharge `Installeur-TV-Launcher.zip` dans l'onglet [Releases](../../releases), décompresse-le et lance `Installeur-TV-Launcher.exe`.
   Windows peut afficher un avertissement (exécutable non signé) : *Informations complémentaires › Exécuter quand même*. L'empreinte SHA-256 est dans `SHA256SUMS.txt`.
2. Suis les 6 étapes de l'assistant : prérequis (mode développeur de la TV, Android Studio, clés API), informations et options, connexion, sauvegarde et nettoyage, projet et compilation, installation.

Il te faut :

- une TV compatible, en **mode développeur** avec le **débogage sans fil** activé, sur le même réseau que le PC ;
- **Android Studio** installé et lancé une fois (il fournit Java et le SDK) ;
- trois clés API gratuites : [OpenWeather](https://home.openweathermap.org/api_keys), [TMDB](https://www.themoviedb.org/settings/api) (clé v3), [YouTube Data API v3](https://console.cloud.google.com/) (inutile si tu désactives les suggestions).

L'installeur détaille chaque étape et peut tester les clés.

## Compiler depuis les sources

**Le launcher** (`launcher/`) : ouvre le dossier dans Android Studio, copie `local.properties.example` en `local.properties`, renseigne les clés, puis *Build › Build APK(s)* ou `./gradlew assembleRelease`. Installation : `adb install -r app/build/outputs/apk/release/app-release.apk`.

**L'installeur** (`installer/`, Go 1.22+ et Python 3) :

```
python scripts/build_installer.py
```

Produit `dist/Installeur-TV-Launcher.zip` et `dist/SHA256SUMS.txt`. Le projet Android est embarqué dans l'exécutable.

## Structure

```
launcher/    projet Android (Kotlin, Compose for TV)
installer/   installeur (Go + interface web locale, un seul .exe)
scripts/     construction de l'installeur
.github/     CI et publication automatique des releases (tag v*)
```

## Sécurité

L'installeur n'embarque aucune clé. Il enregistre les tiens dans son dossier de données (`TvLauncher-Installer-Data/`) et dans `TvLauncher-Projet.zip` : ne les partage pas. `local.properties` est ignoré par git.

## Crédits et mentions

- Ce produit utilise l'API TMDB mais n'est ni approuvé ni certifié par TMDB. Usage non commercial.
- Données météo : [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0) et OpenWeather.
- Netflix, YouTube, Prime Video, Apple TV, Canal+ et Disney+ sont des marques de leurs propriétaires. Ce projet n'est affilié à aucun d'eux.
- Licence : [MIT](LICENSE).
