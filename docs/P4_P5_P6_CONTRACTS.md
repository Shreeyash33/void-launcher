# P4 / P5 / P6 Contracts — fixed APIs, disjoint ownership

Frozen toolchain: Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, compileSdk 34, Compose 1.6.7 / Material3 1.2.1. Do not change Gradle files, SDK versions, or dependency versions. No new dependencies — use framework `org.json`, `Intent`, `AccessibilityService` APIs.

Exact public signatures below are binding so the lead can integrate without rewriting agent code. All UI must use `MaterialTheme.colorScheme` / `MaterialTheme.typography` tokens only (dark/light themes and the future custom theme hook). No hardcoded colors.

Lead owns (agents must NOT edit):
- all Gradle files, `AndroidManifest.xml`, `res/**`, `MainActivity.kt`, `docs/**`
- `domain/Model.kt`, `data/db/**`, `data/repository/**`, `domain/PageGraphValidator.kt`
- final integration, builds, tests, commits to `main`

---

## P4 — Text drawer + settings (`ui/drawer/`, `ui/settings/`)

Owner: P4 agent. Owns ONLY:
- `app/src/main/java/com/ark/voidlauncher/ui/drawer/**`
- `app/src/main/java/com/ark/voidlauncher/ui/settings/**`

```kotlin
package com.ark.voidlauncher.ui.drawer

@Composable
fun AppDrawerOverlay(
    apps: List<AppInfo>,                       // com.ark.voidlauncher.AppInfo
    pinned: Set<String>,
    favorites: Set<String>,
    hidden: Set<String>,
    onBack: () -> Unit,
    onLaunch: (packageName: String) -> Unit,
    onTogglePin: (packageName: String) -> Unit,
    onCustomize: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
)

package com.ark.voidlauncher.ui.settings

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,                      // com.ark.voidlauncher.ThemeMode
    versionName: String,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onEditPages: () -> Unit,
    onEditItems: () -> Unit,
    onEditTypography: () -> Unit,
    onAddWidget: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAccessibilityToggle: (Boolean) -> Unit,
)
```

Rules:
- Drawer: instant-focus text filter (search label and package), clean vertical text list, no icons. Favorites first, then the rest alphabetically. `hidden` apps are excluded from the list. PIN/REMOVE and EDIT (calls `onCustomize`) per row.
- Settings: text-first rows for theme (SYSTEM/DARK/LIGHT), map editors, add widget, backup/restore, accessibility opt-in with plain-language disclosure, SET AS HOME (lead handles the intent — expose the callback or use `Settings.ACTION_HOME_SETTINGS` directly), and version footer.
- Accessibility toggle: when enabling, call `onAccessibilityToggle(true)`; lead opens the system settings screen. Show honest text: "Off by default. Needed only for system actions." Do not enable anything silently.
- No new dependencies, no experimental opt-ins, no Room access.

---

## P5 — Backup / restore (`data/backup/`, `ui/backup/`)

Owner: P5 agent. Owns ONLY:
- `app/src/main/java/com/ark/voidlauncher/data/backup/**`
- `app/src/main/java/com/ark/voidlauncher/ui/backup/**`

```kotlin
package com.ark.voidlauncher.data.backup

data class BackedUpItem(
    val pageCoordX: Int,
    val pageCoordY: Int,
    val itemType: GridItemType,                // domain enum
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val referenceId: String,                   // package for shortcut; provider flattened name for widget
    val allowOverlap: Boolean,
)

data class BackupPayload(
    val version: Int = BackupManager.FORMAT_VERSION,
    val pages: List<PageInfo>,
    val edges: List<EdgeInfo>,
    val items: List<BackedUpItem>,
    val customizations: List<AppCustomizationEntity>,
    val homeCoordX: Int,
    val homeCoordY: Int,
    val theme: String,
)

object BackupManager {
    const val FORMAT_VERSION = 1
    fun toJson(payload: BackupPayload): String
    fun fromJson(json: String): BackupPayload?      // null on malformed JSON or unsupported version
    suspend fun writeToUri(context: Context, uri: Uri, payload: BackupPayload)
    suspend fun readFromUri(context: Context, uri: Uri): BackupPayload?
}

@Composable
fun BackupScreen(
    status: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
)
```

Rules:
- Use framework `org.json` only (no kotlinx.serialization dependency). Pretty-print is fine.
- Versioned: reject any file whose `version` != 1 by returning null. Defensive parsing: never throw on missing/wrong fields; skip bad rows and keep the rest. Use `runCatching` at boundaries.
- Widgets: store provider/package identity, NOT the live `appWidgetId`. Set `referenceId` to the widget provider's flattened component name (or `"placeholder:<provider>"`). Imported widgets are placeholders requiring rebinding; say so in `BackupScreen` text.
- `writeToUri` uses `context.contentResolver.openOutputStream(uri)`; `readFromUri` uses `openInputStream`. Close streams safely. Suspend functions may use `withContext(Dispatchers.IO)`.
- JUnit 4 tests under `app/src/test/java/com/ark/voidlauncher/data/backup/` for round-trip toJson/fromJson, version rejection, malformed JSON null, and skipping a bad row. Pure JVM — no Android imports in the JSON codec functions (`toJson`/`fromJson` must not call Android APIs).
- `BackupScreen` is self-contained text UI with EXPORT / IMPORT / BACK rows and a status line.

---

## P6 — Accessibility bridge (`system/accessibility/`)

Owner: P6 agent. Owns ONLY:
- `app/src/main/java/com/ark/voidlauncher/system/accessibility/**`

```kotlin
package com.ark.voidlauncher.system.accessibility

enum class GlobalAction { HOME, BACK, RECENTS, NOTIFICATIONS, LOCK_SCREEN }

class VoidAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: VoidAccessibilityService? = null
    }
    // onAccessibilityEvent / onInterrupt may be no-ops.
}

object AccessibilityBridge {
    fun isEnabled(context: Context): Boolean
    fun perform(action: GlobalAction): Boolean   // false when service not connected or action unsupported
    fun openSettings(context: Context)
}
```

Rules:
- Use only public APIs: `performGlobalAction(GLOBAL_ACTION_HOME/BACK/RECENTS/NOTIFICATIONS)` and `GLOBAL_ACTION_LOCK_SCREEN` guarded by `Build.VERSION.SDK_INT >= Build.VERSION_CODES.P` (API 28). No `StatusBarManager` reflection, no hidden APIs.
- `isEnabled` uses `Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0)` plus a scan of `ENABLED_ACCESSIBILITY_SERVICES` for this service's `ComponentName`. Must not crash on missing settings.
- `openSettings` launches `Settings.ACTION_ACCESSIBILITY_SETTINGS` with `FLAG_ACTIVITY_NEW_TASK`.
- Opt-in only: the service does nothing until the user enables it in system settings. No auto-start, no polling of UI events.
- Lead adds the manifest service declaration and `res/xml/accessibility_service_config.xml`. Do not touch manifest or res.

---

## Integration (lead, after all three report)

- Replace drawer/settings sections of `MainActivity` with P4 composables; add SAF `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` launchers and `BackupScreen` route for P5.
- Add `LauncherRepository.restore(payload)` mapping `BackedUpItem` coordinates to fresh page ids; widget items become placeholders.
- Add manifest service entry + accessibility config XML for P6; wire `AccessibilityBridge` state into P4 settings.
- Run `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug`, fix, commit each phase, push.
