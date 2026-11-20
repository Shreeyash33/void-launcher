# Product and design rules

Status: these rules supersede earlier HUD/nav treatments. Product decisions are numbered in `docs/INTERACTIVE_WIDGET_UX.md` (currently 1–41, with 42–48 appended for the in-progress revision).

## Widget-first homescreen

- Void Launcher is a quiet, text-first 2D map of pages whose main residents are widgets; the app drawer is the fallback when something cannot be done from a widget.
- No new `APP_SHORTCUT` placements. The picker contains `CLOCK` and `APP LIST`; system `AppWidgetProvider` providers stay hidden, and existing system widgets keep rendering until removed.
- `APP LIST` holds zero or more chosen apps and shows `TAP + TO ADD APPS` when empty; `CLOCK` needs no configuration. Both resize from 1×1 to the page maximum through drag handles, with the numeric dialog as fallback.
- Coordinates are never typed: placement uses the visible grid, `PlacementResolver` validates overlap/spans and allows one hop to a directed neighbour on overflow. Widgets may overlap when allowed; app shortcuts never do.

## Page roles

- `HOME` is derived from the single home-coordinate preference (default `(0,0)`), can be anywhere on the map, and is what the Android Home button opens. `APP_DRAWER` pages host the drawer; multiple drawers are allowed and none can become HOME. Every other page is `WIDGET`.
- Unstored roles default to `WIDGET`, except the configured home coordinate. A default drawer is seeded at `(0,1)`; restore guarantees a drawer for old backups.
- The canonical `DEFAULT` state has exactly five pages and bidirectional links between HOME and each neighbour. `RESET ALL` applies it transactionally and returns to HOME.

## Map geometry and inverted Y

- Coordinates range `(-10,-10)..(10,10)` (441 cells). The model is X-right / Y-up; the map renders increasing Y **upward**, so `(0,1)` appears above `(0,0)` and `(0,-1)` below.
- Paging is a carousel: `SwipeDirection.edgeForGesture` mirrors X for the stored-edge lookup and leaves Y unchanged; `swipeDestination` gives the revealed coordinate. Canvas, HUD and overview all consume these helpers so they cannot drift.
- Page and `+` preview tiles are 16:9 **height:width** portraits over a dotted grid. A `+` creates a page but keeps the map open; long-press selects a tile for dragging to an in-bounds cell that has at least one orthogonal neighbour.
- New and moved pages get one default outgoing link in LEFT, RIGHT, UP, DOWN priority; additional directions are user-configured. Arrow glyphs are drawn on Canvas, never Unicode.

## Bidirectional links

- Every page exposes four toggles (LEFT / RIGHT / UP / DOWN). One toggle creates or removes **both** directed rows between the two neighbouring pages; a map shows one link, not two one-way entries.
- Directed rows remain the storage format because swipe resolution is directional. `PageLinks.link` / `withoutLink` keep them in sync, and `LauncherRepository.setLink` / `removeLink` write both rows in one transaction.
- Geographical adjacency does not imply a link. Graph safety still applies: a toggle that would strand a page is rejected with a top-of-screen message.

## Navigation history and BACK

- BACK and the reverse of the swipe used to enter a page navigate to the last page entered, including on `APP_DRAWER` pages. If no history entry matches, normal link navigation applies.
- History is one-step and pure (`PageHistory`): it records the page left behind plus the stored entry direction, peeks before consuming so a reverse swipe does not mutate state on a miss, and drops steps referencing a deleted page.

## Sidebar layout

- The map bottom navigation is removed. A top-right hamburger opens a sidebar containing `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES` and `GESTURES`.
- Status and error messages render at the top so bottom UI cannot hide them. Normal pages remain minimal; destination actions live in the sidebar or behind long-press, and the Android system Home button is the way home.

## Gesture model

- Gestures are assignable globally or per page; page-scoped bindings override global bindings for the same trigger, and the same action may be assigned to multiple gestures. `NONE` means unbound.
- Initial triggers: `LONG_PRESS`, `DOUBLE_TAP`, `TRIPLE_TAP`, `PINCH_ZOOM_IN`, `PINCH_ZOOM_OUT`, `SWIPE_2_*`, `SWIPE_3_*`.
- Initial actions: `ZOOM_IN`, `ZOOM_OUT`, `LOCK_SCREEN`, `LOCK_LAUNCHER`, `OPEN_EDITOR`, `OPEN_MAP`, `GO_HOME`, `OPEN_SETTINGS`, `OPEN_APP_DRAWER`, `OPEN_WIDGET_PICKER`, `TOGGLE_COORDINATES`, `SYSTEM_BACK`, `SYSTEM_RECENTS`, `SYSTEM_NOTIFICATIONS`.
- Recognition and dispatch are **deferred** to the next stage; this revision delivers the model, storage, tests and settings surface only.

## Theming

- Every surface uses `MaterialTheme.colorScheme` or `LocalVoidStyle` tokens. No hardcoded white dialogs on dark themes, no pure white/black palette values.
- Widget picker and dialog text always use `onSurface` / `onSurfaceVariant` so dark theme never shows black text on dark surfaces. Readable ink for custom fills is derived from luminance (`readableOn`).
- `GlassPanel` supplies scheme-derived translucency, hairline borders and depth without consuming pointer input; `SYSTEM`, `DARK`, `LIGHT` and editable `CUSTOM` modes remain supported.

## Accessibility constraints

- Accessibility is opt-in and off by default, with plain-language disclosure and a Play Store declaration if published.
- Use only public `performGlobalAction` APIs through `AccessibilityBridge`; no `StatusBarManager` reflection, no hidden APIs, no accessibility events and no reading of UI content. Lock screen is gated at API 28+.
- Gesture zones must not silently bypass these rules: page-scoped bindings still require the user's explicit choice.
