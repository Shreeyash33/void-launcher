# P1 / P2 / P3 Contracts — fixed APIs, disjoint ownership

Frozen toolchain: Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, compileSdk 34, Compose 1.6.7 / Material3 1.2.1. Do not change Gradle files, SDK versions, or dependency versions.

These contracts let P1, P2 and P3 be built in parallel worktrees. **Exact public signatures below are binding** so the lead can integrate without rewriting agent code.

Lead owns (agents must NOT edit):
- `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle/**`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ark/voidlauncher/MainActivity.kt`
- `app/src/main/res/**`, `docs/**`, `domain/Model.kt`, `data/**`

---

## P1 — Widget host (`system/widgets/`)

Owner: P1 agent, inside its worktree. Owns ONLY `app/src/main/java/com/ark/voidlauncher/system/widgets/**`.

Use framework `AppWidgetHost` / `AppWidgetManager`. No new Gradle deps. Implement exactly:

```kotlin
package com.ark.voidlauncher.system.widgets

class WidgetHostController(context: Context) {
    fun startListening()
    fun stopListening()
    fun allocateAppWidgetId(): Int
    fun bindAllowed(appWidgetId: Int, provider: ComponentName): Boolean
    fun createPickIntent(appWidgetId: Int): Intent          // ACTION_APPWIDGET_PICK + EXTRA_APPWIDGET_ID
    fun configureIntentFor(appWidgetId: Int): Intent?       // provider configure activity, if any
    fun deleteWidget(appWidgetId: Int)
    fun updateOptions(appWidgetId: Int, options: Bundle)
    fun getOptions(appWidgetId: Int): Bundle?
    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo?
    fun installedProviders(): List<AppWidgetProviderInfo>
}

@Composable
fun WidgetHostView(
    controller: WidgetHostController,
    appWidgetId: Int,
    modifier: Modifier = Modifier,
    onOptionsChanged: (Bundle) -> Unit = {},
)

object WidgetEvents {
    val deletedIds: MutableSharedFlow<Int>   // emit when a widget instance is deleted externally
}

class WidgetDeleteReceiver : BroadcastReceiver()  // forwards ACTION_APPWIDGET_DELETED ids to WidgetEvents
```

Rules:
- Fixed host id (e.g. 101). `startListening()` / `stopListening()` are lifecycle-safe and idempotent.
- Bind flow: allocate id → `bindAppWidgetIdIfAllowed` → if false, caller launches `createPickIntent`; after result, caller calls `bindAllowed`; if provider has configure activity, caller launches `configureIntentFor`. Do not launch activities from the controller.
- `WidgetHostView` renders `AppWidgetHostView` through `AndroidView` and reapplies stored options.
- Do not persist anything to Room; the lead writes `GridItemEntity(referenceId = widgetId.toString())`.
- Manifest entries are added by the lead: `BIND_APPWIDGET` permission and a receiver for `.system.widgets.WidgetDeleteReceiver` with action `android.appwidget.action.APPWIDGET_DELETED`.

---

## P2 — Spatial canvas + grid (`ui/spatial/`, `ui/grid/`, `domain/placement/`)

Owner: P2 agent, inside its worktree. Owns ONLY:
- `app/src/main/java/com/ark/voidlauncher/ui/spatial/**`
- `app/src/main/java/com/ark/voidlauncher/ui/grid/**`
- `app/src/main/java/com/ark/voidlauncher/domain/placement/**`
- `app/src/test/java/com/ark/voidlauncher/domain/placement/**`

Implement exactly:

```kotlin
package com.ark.voidlauncher.ui.spatial

@Composable
fun SpatialPageCanvas(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    currentPageId: Long,
    homePageId: Long,
    onPageChanged: (Long) -> Unit,
    onHomeRequested: () -> Unit,
    onAppClick: (packageName: String) -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
    modifier: Modifier = Modifier,
)

package com.ark.voidlauncher.ui.grid

@Composable
fun PageGrid(
    cols: Int,
    rows: Int,
    items: List<GridItemEntity>,
    onAppClick: (packageName: String) -> Unit,
    widgetContent: @Composable (GridItemEntity) -> Unit,
    modifier: Modifier = Modifier,
)

package com.ark.voidlauncher.domain.placement

object PlacementResolver {
    fun resolve(
        candidate: GridItemEntity,
        existing: List<GridItemEntity>,
        cols: Int,
        rows: Int
    ): PlacementResult
}

sealed interface PlacementResult {
    data object Ok : PlacementResult
    data class Rejected(val reason: String) : PlacementResult
    data class MoveToNeighbor(val direction: SwipeDirection) : PlacementResult
}
```

Rules:
- Swipe: `pointerInput` drag detection, dominant axis, threshold ~80.dp. Look up ONE outgoing edge for `(currentPageId, direction)`; if present, animate with `Animatable` and call `onPageChanged(target)`. If absent, spring rubber-band back + haptic.
- No nested Pagers. Only 4 directions. Double-tap invokes `onHomeRequested`.
- `PageGrid` uses `BoxWithConstraints` to size cells from page `cols`/`rows`, places items at `col`/`row` with `colSpan`/`rowSpan`.
- Overlap: APP_SHORTCUT never overlaps anything. WIDGET may overlap other WIDGETs only when `allowOverlap = true`. WIDGET never overlaps APP_SHORTCUT.
- Spans must fit; overflow crosses right → `RIGHT`, bottom → `DOWN`, negative col → `LEFT`, negative row → `UP`. Only one hop; `MoveToNeighbor` tells the caller to use that directed edge.
- `PlacementResolver` is pure Kotlin with JUnit 4 tests covering: free placement, shortcut/shortcut conflict, widget/widget allowed with flag, widget/shortcut conflict, each overflow direction, and one-hop rejection.

---

## P3 — Editor (`ui/editor/`)

Owner: P3 agent, inside its worktree. Owns ONLY `app/src/main/java/com/ark/voidlauncher/ui/editor/**`.

P3 is deliberately decoupled from P2: all composables are self-contained, receive data as parameters, and return intent through callbacks. Do not import `ui.spatial` or `ui.grid`.

Implement exactly:

```kotlin
package com.ark.voidlauncher.ui.editor

@Composable
fun PageEditorSheet(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    homePageId: Long,
    onAddPage: (coordX: Int, coordY: Int) -> Unit,
    onDeletePage: (pageId: Long) -> Unit,
    onSetEdge: (fromPageId: Long, direction: SwipeDirection, toPageId: Long) -> Unit,
    onRemoveEdge: (fromPageId: Long, direction: SwipeDirection) -> Unit,
    onSetHomePage: (pageId: Long) -> Unit,
    onDismiss: () -> Unit,
)

@Composable
fun ItemEditorSheet(
    page: PageInfo,
    items: List<GridItemEntity>,
    apps: List<AppInfo>,                 // com.ark.voidlauncher.AppInfo
    onAddShortcut: (packageName: String, col: Int, row: Int) -> Unit,
    onAddWidget: () -> Unit,
    onMove: (itemId: Long, col: Int, row: Int) -> Unit,
    onResize: (itemId: Long, colSpan: Int, rowSpan: Int) -> Unit,
    onRemove: (itemId: Long) -> Unit,
    onDismiss: () -> Unit,
)

@Composable
fun AppTypographyEditorSheet(
    customizations: List<AppCustomizationEntity>,
    apps: List<AppInfo>,
    onCustomize: (AppCustomizationEntity) -> Unit,
    onDismiss: () -> Unit,
)

sealed interface EdgeChange {
    data class SetEdge(val edge: EdgeInfo) : EdgeChange
    data class RemoveEdge(val fromPageId: Long, val direction: SwipeDirection) : EdgeChange
}

object EditorValidation {
    fun edgeChangeIsSafe(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long,
        change: EdgeChange
    ): Boolean
}
```

Rules:
- Reuse `com.ark.voidlauncher.domain.PageGraphValidator` for validation. `edgeChangeIsSafe` returns false if applying the change would leave any page with degree 0 or without a directed path to home. Home is always reachable.
- Coordinate inputs must enforce `(-10,-10)..(10,10)` and reject duplicate `(x,y)`.
- UI style: text-first, uppercase labels, no icons — match existing `MainActivity` look. Use Material3 `ModalBottomSheet` or a simple full-screen composable; no new dependencies.
- Do not write to Room; callbacks only. Do not touch `MainActivity`.

---

## Integration (lead, after all three report)

- Add manifest permission + receiver from P1.
- Rewrite `MainActivity` to collect Room Flows and wire P2 canvas + P3 sheets + P1 widget content.
- Resolve `PlacementResolver.MoveToNeighbor` with the directed-edge table.
- Run `testDebugUnitTest` + `assembleDebug`, fix conflicts, commit each phase separately.
