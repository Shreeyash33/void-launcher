# Contributor instructions

Read `agent.md` first. Back up before editing, keep edits inside this workspace, and let the lead run Gradle and device checks.

## Where to edit

| Change | Location |
|---|---|
| Pure rules, enums, link/history/gesture models, validators | `app/src/main/java/com/ark/voidlauncher/domain/` |
| Tables, DAOs, migrations, repository transactions | `data/db/`, `data/repository/`, `data/background/`, `data/migration/` |
| Preferences (theme, home, orientation, gesture bindings) | `LauncherPrefs.kt` |
| Widgets, accessibility, SAF backup codec | `system/widgets/`, `system/accessibility/`, `data/backup/` |
| Screens and composables | `ui/grid/`, `ui/spatial/`, `ui/overview/`, `ui/editor/`, `ui/settings/`, `ui/gestures/`, `ui/theme/`, `ui/drawer/`, `ui/hud/`, `ui/background/`, `ui/customtheme/`, `ui/backup/` |
| Activity wiring, shared state, SAF launchers | `MainActivity.kt` — lead only |
| Gradle, manifest, resources, `docs/` contracts | Lead only; append decisions to `docs/INTERACTIVE_WIDGET_UX.md` only when asked |

Phase contracts define exclusive ownership. Do not edit another agent's files, Gradle, backups, scripts, device settings or existing docs outside your assignment.

## Naming conventions

- Types/functions `PascalCase` / `camelCase`; enum values `UPPER_SCREAMING`; files match the main declared type; tests end in `Test.kt`.
- DataStore keys are snake_case strings (`show_coordinate_detail`, `gesture_bindings_global`). Encodings use `|`-separated enum names so invalid values can be ignored on decode.
- UI labels are short uppercase words (`OPEN MAP`, `RESET ALL`); status messages use uppercase words too (`THAT WOULD STRAND A PAGE`).
- Commit messages use `feat:`, `fix:`, `docs:`, `wip:` prefixes; keep checkpoints small. Product decisions are numbered and appended in `docs/INTERACTIVE_WIDGET_UX.md`.

## Adding a page role, action or gesture

1. **Page role:** add the enum value in `domain/Model.kt`; update `effectivePageType`, `DefaultLayout`, backup encode/decode with a safe default, a migration if stored rows need backfilling, and any editor UI that offers the role. `APP_DRAWER` pages cannot become HOME; widgets are not placed on them.
2. **Gesture action:** add `GestureAction` in `domain/GestureModel.kt`; add pure tests for resolution; define the eventual dispatch target in `MainActivity` (lead) and keep the action available to `ui/gestures/GestureSettingsSheet.kt`. `NONE` always means unbound.
3. **Gesture trigger:** add `GestureTrigger` (e.g. `SWIPE_2_UP`, `SWIPE_3_LEFT`); expose it in the gesture settings list and keep the DataStore encoding backward-compatible — unknown enum names on decode are skipped, not crashed on.
4. Page-scoped bindings override global ones for the same trigger; the same action may be bound to several triggers. Recognition/dispatch are deferred, so do not wire live pointer handling until that stage is assigned.

## Tests and builds

```powershell
.\gradlew --stop
.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease
```

Add JVM tests under `app/src/test/java/...` for pure rules. A green build is necessary but not sufficient: swipe feel, widget binding and OEM behavior need a real device.

## Avoid screenshots

Do not take or keep screenshots or test images. Verify UI with text-based evidence:

```powershell
adb -s <device> shell uiautomator dump /sdcard/window.xml
adb -s <device> shell cat /sdcard/window.xml
adb -s <device> shell uiautomator dump /dev/tty
adb -s <device> logcat -d -t 300
adb -s <device> shell dumpsys activity activities | Select-String mResumedActivity
```

`uiautomator` bounds can be misleading (some Compose text reports 4px height); trust node text and `content-desc` plus pixel-free checks such as Canvas arrow counts in tests. Remove any temporary verification files from the device and temp folders when done.

## Device validation

Check wakefulness before input; wake only when `Asleep`, and ask the user to unlock a lockscreen:

```powershell
adb -s <device> shell dumpsys power | Select-String mWakefulness
adb -s <device> shell input keyevent 26          # only when Asleep
adb -s <device> shell am start -n com.ark.voidlauncher/.MainActivity
adb -s <device> shell input swipe x1 y1 x2 y2 200
adb -s <device> shell input keyevent 4           # BACK
```

Check `logcat` for app exceptions after each flow and restore changed settings (e.g. `settings put system accelerometer_rotation 1`) before finishing.

## Commits and push

The lead commits coherent file sets as soon as they are written (hard-restart risk), builds centrally, and pushes only green, reviewable states to `origin/main`. Subagents commit their own checkpoints when instructed and report hashes; they do not push, force-push or rewrite shared history. Backups, APKs, logs and scratch files are gitignored and never enter the repository.
