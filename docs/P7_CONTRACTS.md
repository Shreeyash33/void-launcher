# P7 Contracts — Spatial Awareness & Overview

Frozen toolchain: Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, compileSdk 34, Compose 1.6.7 / Material3 1.2.1. Do not change Gradle files, SDK versions, or dependency versions. No new dependencies.

All visuals must use `MaterialTheme.colorScheme` / `MaterialTheme.typography` tokens only. No hardcoded colors. No experimental opt-ins.

Lead owns (agents must NOT edit): Gradle files, `AndroidManifest.xml`, `res/**`, `MainActivity.kt`, `docs/**`, `domain/Model.kt`, `data/**`, `ui/spatial/SpatialPageCanvas.kt`, `ui/grid/**`, `ui/editor/**`, `ui/drawer/**`, `ui/settings/**`, `ui/backup/**`. Final integration, builds, tests, ADB verification, and commits to `main` are lead-only.

---

## P7a — Page Overview map (`ui/overview/**`)

Owner: P7a agent. Owns ONLY `app/src/main/java/com/ark/voidlauncher/ui/overview/**`.

```kotlin
package com.ark.voidlauncher.ui.overview

@Composable
fun PageOverview(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    currentPageId: Long,
    homePageId: Long,
    onSelectPage: (Long) -> Unit,
    onClose: () -> Unit,
    onThemes: () -> Unit,
    onSettings: () -> Unit,
    onWidgets: () -> Unit,
    onPageManagement: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Requirements:
- Full-screen "zoomed out" bird's-eye view. Render each page as a cell positioned by its `(coordX, coordY)` on a 2D grid (X right, Y down). Cell size ~140dp at 1x scale.
- Pan by drag and zoom by pinch using `detectTransformGestures` (Compose foundation 1.6.7). Clamp scale to a sensible range (e.g. 0.35x..2.5x). Center the map on the current page on first composition.
- Draw directed edges as lines/arrows between page cells (use `Canvas`/`DrawScope` with `MaterialTheme.colorScheme` colors). Highlight edges whose source is the current page.
- Each page cell shows: coordinate label `"x,y"`, a `HOME` marker for the home page, a `HERE` marker for the current page, and an item count (e.g. `"3 items"`). Also show small direction glyphs for available outgoing swipes.
- Tapping a cell calls `onSelectPage(page.pageId)`. Provide a visible `BACK`/close control calling `onClose`.
- Bottom nav row with exactly four text buttons: `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES`. Wire them to the four callbacks. They are entry points; lead decides destinations.
- Handle empty/one-page maps gracefully. Keep it stateless except local pan/zoom state.

Existing types: `com.ark.voidlauncher.domain.PageInfo` (pageId, coordX, coordY, title, gridCols, gridRows, isSecret; MIN_COORD/MAX_COORD), `EdgeInfo` (fromPageId, swipeDirection: SwipeDirection, toPageId), `com.ark.voidlauncher.data.db.GridItemEntity` (itemId, pageId, itemType, ...), `SwipeDirection` enum {UP, DOWN, LEFT, RIGHT}.

---

## P7b — Navigation HUD (`ui/hud/**`)

Owner: P7b agent. Owns ONLY `app/src/main/java/com/ark/voidlauncher/ui/hud/**`.

```kotlin
package com.ark.voidlauncher.ui.hud

@Composable
fun NavigationHud(
    page: PageInfo,
    edges: List<EdgeInfo>,
    wallHit: SwipeDirection?,
    onWallHitCleared: () -> Unit,
    onOpenOverview: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Requirements:
- Overlay for the home canvas. Must not block gestures: the root modifier must be `Modifier.matchParentSize()` with `Box` children that are non-clickable except the small `MAP` affordance.
- Prominent **coordinate badge** near the top: show `"PAGE x,y"` and a `HOME` tag when this is the home page. This is the primary "where am I" indicator.
- **Direction indicators**: four arrows (`↑`, `↓`, `←`, `→`) positioned at top/bottom/left/right edges. An arrow is bright/highlighted when `edges` contains an outgoing edge from `page.pageId` in that direction, and dim when it's a wall. Include a tiny target label (neighbour coordinate) when available.
- **Wall feedback**: when `wallHit != null`, briefly show a strong visual cue on that side (e.g. a thick colored bar / flash) and call `onWallHitCleared()` after ~450ms via `LaunchedEffect`. Use `MaterialTheme.colorScheme.error` or primary tokens.
- A small `MAP` text affordance calling `onOpenOverview()` — a tappable alternative to long-press, for accessibility.
- Theme tokens only, no hardcoded colors.

---

## Lead — shared gesture + integration (not for agents)

- Add `onLongPress` and `onWallHit(direction)` callbacks to `SpatialPageCanvas`; wire `detectTapGestures(onLongPress = ...)`.
- Improve page transition: animate out in the swipe direction, then animate in from the opposite side instead of snapping to zero. Keep `onPageChanged` invocation.
- Strengthen wall feedback: haptic + `onWallHit` callback so the HUD can show the visual cue.
- In `MainActivity`: add `overview` boolean and `wallHit` state; long-press → overview; wire overview bottom nav (THEMES → theme chooser or Settings, SETTINGS → Settings, WIDGETS → widget picker, PAGES → page editor); render `NavigationHud` on HOME.
- Build/test with `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug`, bump version to 0.5.0, produce release APK, install via ADB, and drive UI with `input swipe/tap` + `uiautomator dump`.
