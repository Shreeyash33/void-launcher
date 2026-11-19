# P8–P11 frozen contracts

Status: **frozen for implementation**. Lead owns integration, Gradle, builds, commits, and every shared file. Subagents own only the directories listed below and must not edit anything else. Toolchain stays pinned (Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, Compose UI 1.6.7 / Material 3 1.2.1, JDK 17, compileSdk 34). No new Gradle dependencies. No experimental/opt-in APIs. All UI colors must come from `MaterialTheme.colorScheme` or the new `LocalVoidStyle` tokens — no hardcoded `Color(0x…)` in composables.

---

## Ownership map

| Agent | Owns (exclusive) | Must not touch |
|---|---|---|
| Lead | `LauncherPrefs.kt`, `domain/CustomPalette.kt`, `domain/OrientationMode.kt`, `MainActivity.kt`, `ui/settings/**`, `data/db/VoidDatabase.kt`, `data/repository/**`, `data/backup/**`, `AndroidManifest.xml`, `res/**`, `app/build.gradle.kts`, all docs | — |
| P8a (theme system) | `ui/theme/**` | everything else |
| P8b (custom theme editor) | `ui/customtheme/**` (new directory) | everything else |
| P10a (background data) | `domain/background/**`, `data/background/**` (new directories) | everything else |
| P10b (background UI) | `ui/background/**` (new directory) | everything else |

Agents must commit their own coherent checkpoints on their branch/worktree with a clear message and report the commit hash. Never leave large uncommitted batches (hard-restart warning).

---

## P8 — Theme system

### Goal
- Make light mode genuinely good (soft, low-glare, correct tonal relationships), not a bare white swap.
- Make custom themes real: editable, persisted, selectable, and exportable.
- Establish glassmorphic + skeuomorphic design tokens that P11 will apply across the system UI.

### Frozen domain types (lead-owned, `domain/CustomPalette.kt`)

```kotlin
package com.ark.voidlauncher.domain

/** All colours are ARGB hex strings, e.g. "#FF121212", so this type is pure JVM (no Compose). */
data class CustomPalette(
    val background: String,
    val surface: String,
    val surfaceVariant: String,
    val onBackground: String,
    val onSurface: String,
    val primary: String,
    val secondary: String,
    val outline: String,
) {
    companion object {
        fun darkDefault(): CustomPalette
        fun lightDefault(): CustomPalette
        fun isValidHex(value: String): Boolean
    }
}
```

### Frozen theme API (`ui/theme/Theme.kt`, P8a)

```kotlin
@Composable
fun VoidTheme(
    themeMode: ThemeMode,
    customPalette: CustomPalette? = null,
    content: @Composable () -> Unit,
)
```
- `themeMode` gains a fourth value `CUSTOM` (lead adds it to the enum in `LauncherPrefs.kt`).
- When `themeMode == CUSTOM` and `customPalette != null`, parse each hex into `Color` and build a full `ColorScheme` by copying the dark or light base (chosen by `isSystemInDarkTheme()` when `CUSTOM`, so secondary tokens stay coherent) and overriding **all eight** palette slots. Never crash on an invalid hex — fall back to the base scheme.
- Keep `SYSTEM`/`DARK`/`LIGHT` behaviour; replace `LightColors` with a soft, warm-neutral low-glare palette that keeps WCAG-legible `on*` contrast. Strengthen `DarkColors` secondary/surface-variant tokens for depth.
- Provide design tokens as a Compose `CompositionLocal` so any surface can consume them:

```kotlin
data class VoidStyle(
    val glassTint: Color,          // translucent panel fill
    val glassBorder: Color,        // hairline stroke
    val glassBlurRadius: androidx.compose.ui.unit.Dp,
    val panelShape: androidx.compose.ui.graphics.Shape,
    val raisedElevation: androidx.compose.ui.unit.Dp,
    val skeuoHighlight: Color,     // top inner highlight
    val skeuoShadow: Color,        // bottom inner shadow
)
val LocalVoidStyle: androidx.compose.runtime.ProvidableCompositionLocal<VoidStyle>
```
  `VoidTheme` must `CompositionLocalProvider(LocalVoidStyle provides …)` around `MaterialTheme`. Derive light and dark variants from the active scheme's tokens (no new hardcoded literal colors beyond alpha-adjusted scheme colors).
- Provide a reusable panel composable (`ui/theme/GlassPanel.kt`, P8a) used by P11:

```kotlin
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```
  Frosted, translucent, hairline-bordered, softly raised. On API < 31 where `RenderEffect` blur is unavailable, degrade gracefully to a translucent tinted panel (no crash, no new dependency).

### Frozen theme picker API (`ui/theme/ThemePickerSheet.kt`, P8a)

```kotlin
@Composable
fun ThemePickerSheet(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onEditCustom: () -> Unit,
    onDismiss: () -> Unit,
)
```
- Rows are `SYSTEM`, `DARK`, `LIGHT`, `CUSTOM` (`ThemeMode.entries` order). Selecting `CUSTOM` uses the saved palette.
- Show an `EDIT CUSTOM THEME` action that calls `onEditCustom`.

### Frozen custom theme editor API (`ui/customtheme/**`, P8b)

```kotlin
package com.ark.voidlauncher.ui.customtheme

@Composable
fun CustomThemeEditorSheet(
    palette: CustomPalette,
    isDarkBase: Boolean,
    onSave: (CustomPalette) -> Unit,
    onDismiss: () -> Unit,
)
```
- Eight labelled hex-colour fields (one per `CustomPalette` property) with live validation via `CustomPalette.isValidHex`; save disabled while any value is invalid.
- Two preset rows: `DARK PRESET` / `LIGHT PRESET` from `CustomPalette.darkDefault()` / `lightDefault()`.
- `onSave` returns the complete edited palette. No persistence here — lead persists it.
- Theme tokens only (`MaterialTheme.colorScheme`, `LocalVoidStyle` if desired), no hardcoded colors.

### Lead integration (P8)
- `ThemeMode { SYSTEM, DARK, LIGHT, CUSTOM }`; DataStore key `"theme"` maps `"custom"` → `CUSTOM`, `setTheme` writes `"custom"`.
- Persist the palette under eight new private DataStore keys (`custom_bg`, `custom_surface`, `custom_surface_variant`, `custom_on_bg`, `custom_on_surface`, `custom_primary`, `custom_secondary`, `custom_outline`) with dark defaults.
  ```kotlin
  val customPalette: Flow<CustomPalette>
  suspend fun setCustomPalette(palette: CustomPalette)
  ```
- MainActivity collects `customPalette`, passes it to `VoidTheme`, adds `customThemeEditor` state, wires `onEditCustom` from both the picker and settings to it, and renders `CustomThemeEditorSheet` with `isDarkBase = isSystemInDarkTheme()` (or the active dark choice).
- Backup v1 gains optional fields so custom themes survive export/import (lead; backward-compatible decode with defaults).

---

## P9 — Orientation

### Goal
Portrait by default so users are not confused by tilting; a setting for portrait / landscape / auto remains available. The grid already supports any orientation.

### Frozen domain type (lead-owned, `domain/OrientationMode.kt`)

```kotlin
package com.ark.voidlauncher.domain

enum class OrientationMode { PORTRAIT, LANDSCAPE, AUTO }
```

### Lead integration
- `LauncherPrefs`: key `"orientation"`, default `"portrait"`;
  ```kotlin
  val orientationMode: Flow<OrientationMode>
  suspend fun setOrientation(mode: OrientationMode)
  ```
- `SettingsScreen` signature gains two parameters (appended at the end, before any future ones):
  ```kotlin
  orientationMode: OrientationMode,
  onOrientationChange: (OrientationMode) -> Unit,
  ```
  with a `SectionLabel("ORIENTATION")` row of three choices (`PORTRAIT`, `LANDSCAPE`, `AUTO`) mirroring the THEME choice row. Selected = `primary`, others = `onBackground.copy(alpha = 0.45f)`.
- MainActivity collects `orientationMode` and applies it with `requestedOrientation` in a `DisposableEffect`:
  - `PORTRAIT` → `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`
  - `LANDSCAPE` → `ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE`
  - `AUTO` → `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED`
- `AndroidManifest.xml`: `android:screenOrientation="portrait"` on `MainActivity` so the very first frame is already portrait before the pref flow emits. No `configChanges` added (rotation recreates the activity, which is fine).
- Backup gains an optional `orientation` string with default `PORTRAIT`.

---

## P10 — Backgrounds

### Goal
Per-page or global backgrounds with three kinds — plain colour, multi-stop gradient, or image. A page-specific background overrides the global one; any page can mix kinds. No new Gradle dependency (decode with `BitmapFactory`).

### Frozen domain types (lead-owned, `domain/background/**` is instead P10a-owned)

P10a creates the entire `domain/background` package:

```kotlin
package com.ark.voidlauncher.domain.background

enum class BackgroundKind { COLOR, GRADIENT, IMAGE }
enum class BackgroundScope { GLOBAL, PAGE }

data class GradientStop(val colorHex: String, val position: Float) // 0f..1f

data class PageBackground(
    val id: Long = 0L,
    val scope: BackgroundScope,
    val pageId: Long? = null,                 // non-null iff scope == PAGE
    val kind: BackgroundKind,
    val colorHex: String? = null,             // COLOR
    val gradientStops: List<GradientStop> = emptyList(), // 2..5, sorted by position
    val gradientAngle: Float = 0f,            // degrees, 0 = left-to-right
    val imageToken: String? = null,           // IMAGE; app-internal filename (no content:// URI)
)
```
- P10a also owns a pure codec for `gradientStops` (e.g. `"#AARRGGBB@0.0|#AARRGGBB@1.0"`) and `parse/format` helpers, plus validation (`isValidHex`, stop count/position checks).
- `BackgroundResolver` helper (P10a): given `pageId` and the full background list, return the page-specific entry if present, else the GLOBAL entry, else `null`.

### Frozen persistence (P10a data, lead DB integration)

P10a creates `data/background/`:
```kotlin
@Entity(tableName = "backgrounds")
data class BackgroundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val scope: String,        // BackgroundScope.name
    val pageId: Long?,        // nullable; FK to pages with ON DELETE CASCADE where possible
    val kind: String,         // BackgroundKind.name
    val colorHex: String?,
    val gradientSpec: String?,// codec-encoded stops
    val gradientAngle: Float,
    val imageToken: String?,
)

@Dao
interface BackgroundDao {
    fun observeAll(): Flow<List<BackgroundEntity>>
    suspend fun upsert(entity: BackgroundEntity): Long
    suspend fun deleteById(id: Long)
    suspend fun deleteForPage(pageId: Long)
    suspend fun clearGlobal()
    suspend fun getAllOnce(): List<BackgroundEntity>
    suspend fun deleteAll()
}

class BackgroundRepository(private val database: VoidDatabase, private val context: Context) {
    val backgrounds: Flow<List<PageBackground>>
    suspend fun save(background: PageBackground): Long
    suspend fun remove(id: Long)
    suspend fun clearPage(pageId: Long)
    suspend fun importAll(items: List<PageBackground>)   // replaces all
    suspend fun importImage(token: String, bytes: ByteArray)
    suspend fun imageBytes(token: String): ByteArray?
    suspend fun deleteImage(token: String)
}
```
- Lead adds `backgroundDao()` to `VoidDatabase`, bumps the DB to **version 3**, and adds `MIGRATION_2_3` that only `CREATE TABLE backgrounds (...)` (no seeding). The schema JSON `3.json` is generated by KSP.
- Images are copied from SAF into `context.filesDir/backgrounds/<token>.img` by `BackgroundRepository`; no raw `content://` URI is ever persisted or backed up.
- `BackgroundRepository` is instantiated by lead in `onCreate` and exposed from `MainActivity`; P10a must not edit `MainActivity`.

### Frozen background UI API (`ui/background/**`, P10b)

```kotlin
package com.ark.voidlauncher.ui.background

@Composable
fun PageBackgroundRenderer(
    background: PageBackground?,
    image: ImageBitmap?,              // loaded by lead from imageToken; null while loading
    modifier: Modifier = Modifier,
)
```
- COLOR → solid fill. GRADIENT → `Brush.linearGradient` using the stops + angle. IMAGE → `Image(bitmap, contentScale = ContentScale.Crop)`, with a subtle scheme-derived top/bottom scrim so HUD text stays legible. `null` → `MaterialTheme.colorScheme.background`. No hardcoded colours beyond scheme-derived alpha overlays.

```kotlin
@Composable
fun BackgroundEditorSheet(
    current: PageBackground?,
    pageTitle: String,
    isGlobal: Boolean,                // initial toggle state
    onSave: (PageBackground) -> Unit,
    onDelete: () -> Unit,
    onPickImage: () -> Unit,          // lead launches SAF picker
    onDismiss: () -> Unit,
)
```
- Scope selector `GLOBAL` / `THIS PAGE`; kind selector `COLOR` / `GRADIENT` / `IMAGE`.
- COLOR: one validated hex field + preset swatches. GRADIENT: 2–5 stop rows (hex + 0..1 position) + angle field + add/remove stop (disabled outside 2..5). IMAGE: `PICK IMAGE` button calling `onPickImage`, preview from `image`, clear button. Save disabled on invalid input. Theme tokens only.

### Lead integration (P10)
- Instantiate `BackgroundRepository`; collect `backgrounds`; for the current page call `BackgroundResolver`, load image via `imageBytes(token)` into `ImageBitmap` (`BitmapFactory` + `asImageBitmap`, downsample safely), pass to `PageBackgroundRenderer`.
- Add a `pageBackground` slot to `SpatialPageCanvas` (lead owns that file) rendered as the first child of the stationary outer Box, so backgrounds do not translate during swipes.
- Add `EDIT BACKGROUNDS` row to `SettingsScreen` wired to `BackgroundEditorSheet` for the current page (scope toggle lets the user switch to global). No change to the P7 overview bottom-nav contract.
- SAF image launcher: `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())` with `arrayOf("image/*")`; copy bytes into `BackgroundRepository` and keep the returned token in the editor state.
- Backup: add an optional `"backgrounds"` section to the v1 format (backward-compatible; old files still load). Encode each background with `(coordX, coordY)` or scope; encode image bytes as base64 with a sane size cap. Wire into export builder and `LauncherRepository.restore`/lead restore path. Update `BackupManagerTest` round-trip expectations.

---

## P11 — Visual polish

_Uses `LocalVoidStyle` + `GlassPanel` from P8a. Lead schedules this after P8/P9/P10 are integrated and green. Applies frosted panels, hairline borders, soft raised depth, and tactile pressed states to the HUD, drawer, settings, overview, and editor surfaces while preserving token-only colours and low-glare contrast._
