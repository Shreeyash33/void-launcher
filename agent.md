# Agent handoff — read this first

**Last updated:** 2026-11-20
**Baseline:** v0.6.0 (`app/build.gradle.kts`: `versionCode = 8`, `versionName = "0.6.0"`)
**Current stage:** in-progress UX revision, decisions 42–48 in `docs/INTERACTIVE_WIDGET_UX.md`.

## Workspace boundary

The only project workspace is `D:\projects\Void Launcher`. All new files, scratch copies and archives stay inside that folder. No worktrees, clones or "temporary" project folders outside it. If a copy is needed, put it in `backups\`.

## Backup rule

Back up before any refactor or revision, and before touching source, config or docs:

```powershell
.\gradlew --stop
powershell -ExecutionPolicy Bypass -File .\scripts\create-backup.ps1
```

Confirm exactly one fresh, non-zero `backups\void-launcher-source-<yyyyMMdd-HHmmss>.zip`. An existing backup for this revision is `backups\void-launcher-source-20261120-175526.zip`; do not delete or overwrite it. `backups\` is gitignored. Restore only by extracting into the workspace root, preserving relative paths.

## Low-memory build and tests

Never run parallel Gradle builds. The laptop has crashed under daemon pressure:

```powershell
.\gradlew --stop
.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease
```

Subagents do not run Gradle; the lead builds, reviews and verifies centrally. Toolchain is frozen: JDK 17, Gradle 8.7, AGP 8.4.2, Kotlin 1.9.24, Compose 1.6.7 / Material 3 1.2.1, Room 2.6.1 with KSP 1.9.24-1.0.20, compileSdk 34, minSdk 24. No new Gradle dependencies.

## Device rule

Before sending any input to a connected device, check `mWakefulness` with `dumpsys power`. Wake with `input keyevent 26` **only** when the device reports `Asleep`. If a lockscreen appears, ask the user to unlock it — never guess a PIN or pattern. Restore changed device settings (for example `accelerometer_rotation`) when finished, and leave the launcher on a clean HOME.

## Subagent roles

- **Lead:** owns `MainActivity.kt`, `LauncherPrefs.kt`, Gradle/manifest/resources, shared repository/database files, integration, builds, commits and pushes.
- **Subagents:** own only the disjoint directories named in the phase contracts; never edit shared files, Gradle, device state or other agents' files. Commit coherent checkpoints with a clear message and report the hash.
- **Docs-only subagents:** read anything, but write only the files explicitly assigned to them.

## Done

- Typed pages, built-in widgets (`APP LIST`, `CLOCK`) and the overview map; interactive placement, drag and resize; theme/orientation/background work; direct canvas editing.
- Canonical `DEFAULT` layout, `RESET ALL` with confirmation and transactional clear/reseed, and AppWidget host cleanup on reset.
- v0.6.0 release build and 35/35 JVM tests green; versioned JSON backup v1 with backwards-compatible defaults.
- In progress for this revision: bidirectional link helpers, navigation history, page rename, sidebar navigation, picker/dialog theme fix, gesture model + DataStore bindings.

## Deferred

- Gesture recognition and dispatch (trigger detection in Compose, action execution, gesture settings wiring into the live UI). Only the model, storage and settings surface are the foundation.
- New Android system widget providers in the picker, provider-specific sizing, store-grade release signing, and accessibility gesture-zone expansion.
