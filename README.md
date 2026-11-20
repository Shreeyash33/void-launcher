# Void Launcher

**Author:** Ark

A calm, text-first Android home screen inspired by *Unlauncher: Minimalist Home*. No icon grid, no widgets, no distractions — just your essentials, a searchable app drawer, and a quiet place to start.

## Status

This is the initial project setup plus a small working first version (v0.1.0).

- Text-only home screen with a clock and pinned apps
- Searchable text app drawer with PIN / REMOVE controls
- System / dark / light theme
- Pinned apps and theme saved locally with DataStore
- "Set as home" shortcut to Android's default-home settings
- Kotlin + Jetpack Compose, single-module Android app

Planned later: custom app labels, hiding apps, gestures, usage stats, focus mode, wallpaper/text options, backups.

## Tech stack

- **Kotlin** (1.9.24) with **Jetpack Compose** (Compose UI 1.6.7, Material 3 1.2.1)
- Android Gradle Plugin 8.4.2, Gradle 8.7 (via wrapper)
- `minSdk 24`, `compileSdk`/`targetSdk 34`
- Preferences stored with `androidx.datastore:datastore-preferences`

Kotlin/Compose was chosen because it is Google's current recommended way to build Android UIs, keeps the code compact, and makes a simple text-based interface easy to change later.

## How to build

`local.properties` is gitignored and points at the local Android SDK (`D:\AndroidSdk`). On another machine, change `sdk.dir` in `local.properties` or let Android Studio generate it.

From Android Studio: open this folder, let Gradle sync, then Run on a device or emulator.

From the command line on Windows:

```powershell
.\gradlew assembleDebug
```

The APK will be at `app\build\outputs\apk\debug\app-debug.apk`.

## How to try it

1. Install the APK or Run from Android Studio.
2. Press Home. Android will ask which launcher to use — pick **Void Launcher** (or use **SETTINGS → SET AS HOME**).
3. Tap **APPS**, search for an app, and tap **PIN** next to it to add it to the home screen.
4. Tap an app name on the home screen to open it.

## Repository setup

See [docs/WORKFLOW.md](docs/WORKFLOW.md) for the backup-before-revision rule,
the workspace boundary, and the low-memory build commands. Backups live in
`backups\` and are gitignored. All work must stay inside this project directory.

The local repo is initialised on `main` and has `origin` set to `https://github.com/Shreeyash33/void-launcher.git`. The initial commit has been pushed to `main`. To make future commits:

```powershell
git add .
git commit -m "Initial Void Launcher setup"
git push -u origin main
```
