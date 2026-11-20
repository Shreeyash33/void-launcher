# Architecture map

Compose UI over a single Activity, with Room as the source of truth for launcher content and DataStore for small preferences. Pure JVM helpers in `domain/` hold the rules; `MainActivity` wires everything together.

## State / controller — `MainActivity.kt`

- Single `ComponentActivity`; creates `LauncherPrefs`, `VoidDatabase`, `AppRepository`, `WidgetHostController`, `BackgroundRepository` and `LauncherRepository` once, then collects their flows into Compose state.
- Owns transient UI state: `Screen` (`HOME`, `DRAWER`, `SETTINGS`, `WIDGET_PICKER`, `BACKUP`), `Editor`, `currentPageId`, canvas selection, floating-widget pointer state, overlay flags and the single `message` status string.
- Hosts SAF launchers (export, import, bind, image), orientation replay, `BackHandler`, widget-host lifecycle, `WidgetEvents.deletedIds`, and a root-level pointer observer that keeps a floating widget attached to the finger across screen swaps.
- Calls validated domain helpers for placement (`PlacementResolver`) and graph safety (`EditorValidation`) before repository writes. Shared file: lead-owned.

## Data layer

- `data/repository/LauncherRepository.kt` — one write surface plus reactive reads (`pages` with derived roles, `edges`, `items`, `customizations`, `homeCoord`, `homePageId`, `appDrawerPageId`). Transactions cover add/move/set-home, links, rename, reset and restore. Preference writes happen only after Room commits.
- `data/db/VoidDatabase.kt` — Room v4 singleton with `MIGRATION_1_2`, `MIGRATION_2_3`, `MIGRATION_3_4` and `SeedCallback` for the connected starter map. Entities: `PageEntity` (unique coordinate, title, role, grid size), `PageEdgeEntity` (PK `fromPageId + swipeDirection`, FKs cascade both ways), `GridItemEntity`, `AppCustomizationEntity`, `BackgroundEntity`. DAOs expose `Flow` readers plus once/CRUD helpers; `Mappers.kt` converts rows to domain types.
- `LauncherPrefs.kt` / DataStore — theme, orientation, home coordinate, coordinate-detail flag, pinned packages, eight custom-palette hex keys, and gesture binding sets. `reset()` clears everything; raw string helpers support backup restore.
- `data/backup/` — `BackupPayload`, `BackedUpItem`, `BackedUpBackground`, `BackupManager`: versioned JSON v1 via SAF. Widgets keep provider identity, not host-local IDs; restored system widgets become placeholders. Backgrounds carry tokens plus capped base64 bytes.
- `data/migration/DataStoreToRoomMigration.kt` — one-time v0.1 pin import.

## Domain (pure JVM)

- `Model.kt` — `SwipeDirection`, `edgeForGesture` (carousel mirror), `swipeDestination` (X-right / Y-up), `PageInfo`, `EdgeInfo`, `PageType`, `GridItemType`.
- `PageGraphValidator.kt`, `PagePlacement.kt`, `placement/PlacementResolver.kt` — no-stranded-page rules, overlap/span checks, single-hop overflow through directed edges.
- `DefaultLayout.kt`, `ResetExecution.kt` — canonical five-page state and ordered clear/reseed with host/file cleanup.
- `PageLinks.kt` — bidirectional link helpers: `opposite`, `link` (outgoing then reverse), `withoutLink` (removes both rows, preserves unrelated edges).
- `PageHistory.kt` — one-step swipe history: `push`, `pop`, peeking `reverseTarget` and `dropPage`; entries remember the stored entry direction.
- `GestureModel.kt` — `GestureScope`, `GestureTrigger`, `GestureAction`, `GestureBinding`, `GestureBindings.actionFor` (page overrides global, `NONE` means unbound).
- `CustomPalette.kt`, `OrientationMode.kt`, `BuiltInWidgets.kt`, `background/` (codec, resolver, validation), `background/BackgroundModels.kt`.

## UI packages

- `ui/grid/` — `PageGrid`, `GridEditOverlay`: adaptive lattice, spans, live drag/resize handles.
- `ui/spatial/SpatialPageCanvas.kt` — four-way swipe paging, home double-tap, wall feedback, canvas editing, floating preview; consumes `edgeForGesture` so gesture and stored direction cannot drift.
- `ui/overview/` — `PageOverview`, `OverviewMapCanvas`, `OverviewCells`: 16:9 portrait tiles, dotted grid, `+` previews, drag to move, SET HOME and four link toggles; increasing Y renders upward.
- `ui/editor/` — page/item/typography sheets, `EditorValidation` and `EditorShared`; graph-safety checks now use `PageLinks` so a toggle removes both directed rows before validation.
- `ui/settings/SettingsScreen.kt`, `ui/gestures/GestureSettingsSheet.kt` — settings rows and gesture binding editor (foundation).
- `ui/hud/NavigationHud.kt`, `ui/drawer/AppDrawerOverlay.kt`, `ui/theme/`, `ui/background/`, `ui/customtheme/`, `ui/backup/`.
- All colors come from `MaterialTheme.colorScheme` or `LocalVoidStyle`; `GlassPanel` is visual-only and does not consume pointer input.

## System bridges

- `system/widgets/` — `WidgetHostController` (fixed host id, `startListening`/`stopListening`, bind/configure), `WidgetHostView` (`AndroidView`), `WidgetEvents`, `WidgetDeleteReceiver`.
- `system/accessibility/` — `GlobalAction`, `AccessibilityBridge`, `VoidAccessibilityService`. Opt-in and off by default; only public `performGlobalAction` APIs, no reflection and no UI-content reads. Lock screen is gated at API 28+.

## Bidirectional link representation

The map exposes one link between orthogonal neighbours; Room still stores two directed rows in `page_edges`, because swipe resolution is directional. `PageLinks.link(from, direction, to)` returns `(from --direction--> to, to --opposite--> from)`; `setLink` inserts both inside one transaction and `removeLink` deletes both. `PageLinks.withoutLink` feeds `EditorValidation` so a toggle is rejected when it would strand a page. Geographical adjacency alone never implies a link.

## Gesture binding storage

Bindings are preferences, not Room rows. `GestureBindings` holds a global set and a `byPageCoord` map keyed by `(x, y)`; `actionFor` resolves page scope first, then global. `LauncherPrefs` stores two string sets:

- `gesture_bindings_global`: `G|<TRIGGER>|<ACTION>`
- `gesture_bindings_pages`: `P|<x>|<y>|<TRIGGER>|<ACTION>`

`setGestureBinding` removes any prior entry for the same scope/coordinate/trigger before writing, and treats `null`/`NONE` as removal. Distinct trigger/action pairs allow one action to be bound to many gestures. Recognition in Compose and dispatch to actions are **deferred** to the next stage.
