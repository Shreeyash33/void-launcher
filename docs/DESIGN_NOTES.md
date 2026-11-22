# Void Launcher — Design Notes

**Last updated:** 2026-11-22  
**Status:** Implementation complete through P11 and v0.6.0, plus map/backup/gesture/modular-widget work through commit `fed10cc`. A new interaction batch is queued in §5b. Toolchain unchanged; see §9 for the full session log. These notes also exist so the design survives session compaction.

---

## 1. Hard constraints (do not violate)

- **Sudden restarts are expected on this laptop (user warning, 2026-11-19; reiterated 2026-11-22).** Treat every file write as potentially the last before a hard reboot. Commit small, frequent checkpoints; never leave a large batch of work uncommitted. All agents must commit as soon as a coherent file set is written, not only at the end of a phase. Keep notes in this file updated at each checkpoint. Prefer `write` (atomic replace) over long sequences of fragile edits when a restart could land mid-edit.
- **Memory must stay below 80% (user warning, 2026-11-22).** If usage crosses 80%, stop every active task immediately — Gradle, ADB, subagents, file scans — until it drops. Prefer low-memory serial work over parallelism when the laptop is under load.
- **Source backup before every revision (user instruction).** Create `backups\void-launcher-source-YYYYMMDD-HHMMSS.zip` with `scripts\create-backup.ps1` before editing source, notes, or contract files. Latest pre-batch backup: `backups\void-launcher-source-20261122-113450.zip`.
- **Serialized low-memory build (user instruction).** Before declaring a revision green, run `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease`. No new Gradle dependencies; `minSdk = 24` and the pinned toolchain stay unchanged.
- **Use subagents for disjoint slices (user instruction, reiterated 2026-11-22).** Keep ownership non-overlapping; the lead owns integration, verification and commits.
- **Do not set Void Launcher as the default HOME.** The user will do that later; never claim the default with an ADB or system command.
- **`web_editor/` is user-owned and untracked.** It must not be staged, committed or deleted. The only allowed in-place write is `web_editor/WEB_EDITOR_HANDOFF.md` when the data contract changes.
- **Device input safety.** Check `mWakefulness` before sending taps/swipes; wake with `input keyevent 26` only if `Asleep`. Hand off if a lockscreen appears.
- **No screenshots or test images may be left behind.** Text/XML/log/DB reports only.
- **Do not upgrade the toolchain.** Another OpenCode session shares it for a different app.
- Pinned stack:
  - JDK 17
  - Gradle wrapper 8.7
  - AGP 8.4.2
  - Kotlin 1.9.24
  - Compose compiler extension 1.5.14
  - Compose UI 1.6.7 / Material 3 1.2.1
  - `core-ktx` 1.13.1, `activity-compose` 1.9.0, `lifecycle` 2.7.0, `datastore-preferences` 1.0.0
  - SDK: `D:\AndroidSdk`, platform 34, build-tools 34.0.0
- **Do not use Compose BOM 2026.11.01, Room 2.8.x, Kotlin 2.x, AGP 9, Gradle 9, or compileSdk 37.**
- **Android support:** must run on at least Android 10 (API 29). Current `minSdk = 24` already satisfies this. User's phone is Android 12 (API 31/32); `targetSdk = 34` is fine.
- **Room version note:** Room 2.8.x requires Kotlin 2.0+. Use **Room 2.6.1** with **KSP 1.9.24-1.0.20**. Room 2.6.1 artifacts are already in the local Gradle cache. Adding KSP is an addition, not a toolchain upgrade. If KSP causes trouble, fall back to JSON/DataStore persistence rather than upgrading Kotlin.

---

## 2. Product definition

Void Launcher is **widget-first**, not app-first. The home screen is a 2D coordinate map of pages whose main residents are widgets. The app drawer is a text list used when something cannot be done from a widget. The point is that the user should often not need to open an app at all.

Secondary goals: be more accessible and convenient than stock/OEM launchers, and avoid the navigation/notification bugs found on skins such as XOS.

---

## 3. Locked design decisions

### 3.1 Page map and navigation

- Pages live at integer coordinates `(x, y)`.
- Default home page is `(0, 0)`.
- Only **4 swipe directions** exist: `UP`, `DOWN`, `LEFT`, `RIGHT`. No two-finger gestures.
- Edges are **directed**: `(fromX, fromY, direction) -> (toX, toY)`. There is at most one target per `(from page, direction)` pair.
- Geographic adjacency does **not** imply a swipe edge.
  - Example: an edge `(1,0) --DOWN--> (1,1)` does not automatically allow `(0,1) --RIGHT--> (1,1)`. The user explicitly controls which direction a page can be entered from.
- **Confirmed:** every page must connect to at least one other page in any direction (undirected degree >= 1) so it is accessible.
- **Confirmed:** every page must also have a directed path to the home page. The user's intent is simply: no stranded pages.
- The home coordinate is configurable in the editor; pressing Home animates back to it. Default `(0,0)`.
- Deleting a page deletes its grid items and all edges referencing it (foreign key cascade).
- **Confirmed:** bounds `(-10,-10)..(10,10)` are the cap: 21 x 21 = **441 possible pages**. User corrected the earlier ~100 estimate. No separate lower page-count cap needed; the coordinate range itself is the maximum.

### 3.2 Grid placement

- Each page has `gridCols` and `gridRows`, default `10 x 20`, customizable per page for phones/tablets/desktop-sized layouts.
- Grid items are `APP_SHORTCUT` or `WIDGET`, with `col`, `row`, `colSpan`, `rowSpan`.
- Overlap is an item-level toggle (`allowOverlap`):
  - App shortcuts **must not** overlap. **Confirmed** — even though overlap would not break much, do not allow it.
  - Widgets **may** overlap.
- Spans must fit inside page bounds. If a span overflows:
  1. Reject the placement, **or**
  2. If a valid neighbor page exists in the overflow direction, move the whole span to that page at `col = 0`, same `row`, same spans. Overflow direction is determined by which edge is crossed (right edge → RIGHT neighbor, bottom edge → DOWN neighbor, etc.).
  - Only one hop is attempted. If it still does not fit, reject.
- Do not encode cell occupancy as a SQL unique constraint. Enforce it in the domain/ViewModel layer because spans and overlap make it contextual.

### 3.3 App customization and backup

- Per-package overrides: nickname, hidden, favorite, font family, font size, font weight, letter spacing, text color, alignment, text transform.
- Data is local only. On uninstall it is lost unless the user exported it.
- Export/import using Android Storage Access Framework (`ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT`) as a **versioned JSON** file containing pages, edges, grid items, app customizations, home coordinate, and settings.
- Widgets: back up provider/package and placement, **not** the live `appWidgetId`. App widget IDs are host-local and will not survive reinstall. Imported widgets become placeholders that need rebinding.
- No cloud sync, no accounts.

### 3.4 Accessibility

- Accessibility is a product goal, not merely a workaround.
- Use an **opt-in AccessibilityService**, off by default, with a plain-language disclosure and the required Play Store declaration if published.
- Actions: recents, home, back, notifications, lock screen. Gate lock screen behind API 28+ (`GLOBAL_ACTION_LOCK_SCREEN`).
- Use public APIs. Do **not** use `StatusBarManager` reflection: it is hidden/`@TestApi`, restricted by non-SDK rules, and fragile on Android 12+/OEM skins. Use `GLOBAL_ACTION_NOTIFICATIONS` for the shade.
- Configurable edge/corner swipe zones in the editor map to these global actions.

### 3.5 Widget-first hosting

- `AppWidgetHost` is core infrastructure and must be built early, not last.
- Use a fixed host ID; call `startListening()` / `stopListening()` with the Activity lifecycle.
- Render `AppWidgetHostView` inside Compose via `AndroidView`.
- Add flow: pick provider → `allocateAppWidgetId()` → `bindAppWidgetIdIfAllowed()` → if false, launch `ACTION_APPWIDGET_BIND` → run provider configure activity if needed → persist placement, provider/package, and widget ID.
- Declare `BIND_APPWIDGET` permission. Handle `ACTION_APPWIDGET_DELETED` to remove stale items.
- Persist widget options/size per placement and refresh options on resize and boot inflation.
- `themeId` / multi-skin widgets are deferred until basic hosting works.

---

## 4. What to build, how, and why

| # | Component | What / how | Why |
|---|-----------|-----------|-----|
| 1 | Domain graph model | Kotlin data classes + validators for degree, reachability, edge uniqueness, home coordinate | Keeps the dungeon-path rules in one place so the editor cannot create stranded pages |
| 2 | Room database (`data/db/`) | Room 2.6.1 + KSP 1.9.24-1.0.20, entities, FK cascade, reactive `Flow` DAOs | Offline source of truth that survives process death and drives Compose |
| 3 | DataStore → Room migration | One-time import of v0.1 pinned apps into the new customization/grid tables | The repo is not empty; preserve the existing user's setup |
| 4 | AppLoader (`system/`) | `PackageManager` + `LauncherApps` and `LauncherApps.Callback`, merged with customization rows | Reactive text drawer; app installs/uninstalls show up without restart |
| 5 | WidgetHost (`system/widgets/`) | `AppWidgetHost`, bind/configure flow, `AndroidView` bridge | Widget-first is the product; without this the app is just another text launcher |
| 6 | SpatialHomeCanvas (`ui/`) | `pointerInput` drag tracking + `Animatable` transition, 4-way edge lookup, spring rubber-band + haptic | Nested Pagers cannot represent directed graph navigation |
| 7 | PageGrid (`ui/`) | `BoxWithConstraints`, adaptive `gridCols x gridRows`, span placement, overlap rules, overflow-to-neighbor logic | Widgets need precise, adaptive placement across phone/tablet sizes |
| 8 | AppDrawerOverlay (`ui/`) | Instant-focus text filter + clean vertical text list | Text-based app access without icons; secondary to widgets |
| 9 | Customization editor | Bottom sheets for edges, home coordinate, item placement/size, app typography | Every rule above needs a user-facing editing surface |
| 10 | Backup/restore | Versioned JSON export/import via SAF | User-owned persistence; uninstall loss is explicit and accepted |
| 11 | Accessibility bridge | Opt-in service + configurable edge/corner gesture zones | Avoid OEM nav bugs and make the launcher more convenient |
| 12 | Settings | Theme, home coordinate, accessibility toggle, backup actions | Makes the powerful features discoverable |

---

## 5. Revised build order (frozen toolchain)

1. **Add Room 2.6.1 + KSP 1.9.24-1.0.20 to the existing stack** (or choose JSON fallback). Build green. Do not touch Kotlin/AGP/Gradle/Compose versions.
2. **Domain model + Room entities/DAOs + validators + DataStore migration.**
3. **Widget host spike:** place one real widget on a page and verify on Android 12. This is early because widgets are the core.
4. **Spatial pager + grid rendering:** apps first, then widgets.
5. **Customization editor:** edges, home coordinate, placement, app typography.
6. **Text app drawer + search.**
7. **Backup/restore.**
8. **Accessibility service + gesture zones.**
9. **Themes and polish.**

A green Gradle build is necessary but not sufficient; swipe feel, widget resizing, and OEM behavior need real-device testing.

---

## 5a. Queued work — do not forget

- **Theme foundation before homepage UI (user requirement, 2026-11-19):** When the homepage UI is assembled, dark and light themes must be in place **first**, with a deliberate slot reserved for a future custom theme editor. `ui/theme/Theme.kt` already has `SYSTEM` / `DARK` / `LIGHT` and two color schemes; before integrating the P2 canvas the lead must audit it so every homepage surface uses `MaterialTheme.colorScheme` / `MaterialTheme.typography` tokens (no hardcoded colors), and add an extension point for user-defined color palettes. The custom **editor** itself is deferred — do not build it in P1-P3. Homepage components built by subagents must consume theme tokens only.

- **Page-label widget (user request, 2026-11-21; decided 2026-11-22):** The fixed top-centre title/coordinate badge becomes a normal built-in `PAGE_INFO` widget, **default 2 columns × 2 rows**, seeded on HOME for fresh installs and RESET ALL. It renders `displayTitle` + coordinates + HOME tag from the current `PageInfo`. Direction indicators and wall flash stay in `NavigationHud`; the fixed badge is removed. Add `PAGE_INFO` to the picker, backup codec/validator tests and `web_editor/WEB_EDITOR_HANDOFF.md`. `showCoordinateDetail` controls its coordinate line. This is now part of the §5b batch.

- **Modular widget architecture (user decision, 2026-11-21):** Widgets are first-class, user-extensible items, not a closed enum. The layout file references them (`builtin:`, flattened provider, `custom:<id>`, or an opaque future scheme); web-authored definitions live in a sidecar `voidlauncher-widgets.json`, and a `voidlauncher-inventory.json` export lets the web editor mimic the device's apps/widgets. Custom widgets run as sandboxed WebView widgets with no network, file access, navigation, bridges or external resources. Missing custom widgets render as placeholders and are never dropped on import/export; unknown reference schemes round-trip verbatim. Authoritative spec is `web_editor/WEB_EDITOR_HANDOFF.md` §5a, §9a, §9b and §11.

## 5b. Active interaction batch (user requests, 2026-11-22)

- **Long-press empty page → edit that page's grid, not the modal map.** The default unbound `LONG_PRESS` fallback must enter `canvasEditing` on the current page so widgets can be dragged immediately. The full-screen PAGE MAP is reachable only via an explicit gesture binding or a visible MAP button; the editor gets a MAP button that exits editing and opens the overview. App-drawer pages have no canvas editor, so their unbound long-press is a no-op; explicit `OPEN_MAP` bindings still work.
- **Map page cards must show their type.** In addition to HOME/HERE tags, each card shows the stored/effective `PageInfo.pageType` name: `HOME`, `WIDGET`, or `APP_DRAWER`. Never recompute the type from coordinates in the UI.
- **Editor mode shows every widget's boundary.** While `canvasEditing`, draw a faint, contrasting outline around all placed items' stored `col/row/colSpan/rowSpan`, not just the selected item. Keep the stronger selected-item border. This makes blocked drop targets understandable.
- **APP_LIST widget is a miniature app drawer.** Default size **4 columns × 3 rows**, resizable. One app name per row, label fills the available width, and tapping a row launches that app. In editor mode a MENU affordance opens a modal for app membership, per-app nicknames inside the list, and widget colour/appearance. The external floating `+` add-widget flow stays outside that modal and keeps working.
- **Drawer app edit is per-app and scoped.** Tapping EDIT on one app in the app drawer opens a modal for that app only, never all apps. The user chooses whether the nickname applies **globally** (existing `AppCustomizationEntity.customNickname`, visible wherever the app is labelled) or **only in the app drawer** (new DataStore keyed by package). No Room migration. Drawer PIN stays on its existing DataStore path.
- **PAGE_INFO widget ships in this batch.** See §5a; default 2×2, seeded on HOME for fresh installs and RESET ALL, fixed badge removed.
- **Data-scope caveat to document:** drawer-only nicknames and APP_LIST appearance are DataStore-only and are not part of backup v1, like gesture bindings. Global nicknames remain in the backup via `customizations`.
- **Future, not this batch:** user plans dedicated gesture-zone widgets later — visually distinctive, technically difficult. Do not start them here.

---

## 6. Resolved and remaining questions

**Resolved 2026-11-19:**
- Coordinate bounds: `(-10,-10)..(10,10)` = 441 possible pages. This is the maximum. Not unlimited, but much larger than the earlier ~100 estimate.
- "Connected" means: degree >= 1 in any direction **plus** a directed path to home. Goal: no stranded pages.
- App shortcuts must never overlap. Widgets may overlap.

**Still open:**
- Should the launcher keep a transient in-memory back stack, or should Home always reset to the configured home coordinate?
- Should imported widgets remain placeholders, or should we attempt automatic rebind? (Recommendation: placeholders.)

---

## 7. Time estimate for first testable APK (discussion only)

Scope of "test package" used for this estimate:
- Coordinate pages with 4-way directed edges, home coordinate, no-stranded-page validator
- 10x20 adaptive grid, app shortcuts (no overlap) and at least one real AppWidget
- Text drawer with search and pin
- Minimal editor: edges, home coordinate, item placement/size, nickname
- Basic theme; **no** backup/restore, accessibility service, or multi-skin widgets yet

Rough wall-clock estimate with 2-3 parallel subagents:
- Contract/interface definition by lead (serial): 0.5-1h
- Data/db + domain validators + DataStore migration: 1.5-2h
- Widget host spike + AppLoader: 2-3h
- Spatial canvas + grid skeleton: 2-3h
- Editor + drawer/settings: 2-3h
- Integration, build fixes, APK assembly: 1.5-3h
- **Total: ~6-9 hours wall clock**, likely across 2-3 sessions. Add 2-4h if backup/restore and accessibility are included in the first test build.

Caveats: subagents cannot safely edit shared Gradle/theme files, so those stay with the lead; widget binding/configure flow is the highest-uncertainty piece; no device is currently attached, so final gesture/widget feel needs the user to install and test on the Android 12 phone.

---

## 8. Phase tickets (agile batches)

Each phase is independently reviewable. Ask for one or two at a time.

| ID | Phase | Deliverable / acceptance | Depends on |
|----|-------|--------------------------|------------|
| P0 ✅ | Foundation | KSP 1.9.24-1.0.20 + Room 2.6.1 build green; entities, DAOs, validators, DataStore migration; unit tests for degree/path rules | — |
| P1 ✅ | Widget host | One real AppWidget renders via `AppWidgetHost`; bind/configure/delete handled; lifecycle start/stop | P0 |
| P2 ✅ | Spatial canvas + grid | 4-way swipe pager, 10x20 grid, non-overlapping app shortcuts; bounce + haptic on no edge | P0 |
| P2a ✅ | Theme foundation | Dark/light/system tokens audited; all homepage surfaces use MaterialTheme tokens; `CustomPalette` extension point reserved (editor itself deferred) | P0 |
| P3 ✅ | Editor | Wire/remove edges, set home coordinate, place/resize items, edit nickname/typography; validation prevents stranded pages | P0, P2 |
| P4 ✅ | Drawer + settings | Text drawer with search and pin; theme switch; home/settings navigation | P0 |
| P5 ✅ | Backup/restore | Versioned JSON export/import via SAF, including widget placeholders | P0, P3 |
| P6 ✅ | Accessibility | Opt-in service, gesture zones, global actions; no reflection | P2 |
| P7 ✅ | Polish | Themes, transitions, empty states, device-test fixes | P1-P6 |
| P8 ✅ | Themes | Soft light/dark schemes, CUSTOM mode, editable persisted palette, glass/skeuo tokens | P7 |
| P9 ✅ | Orientation | PORTRAIT default; portrait/landscape/auto setting; manifest first frame portrait | P7 |
| P10 ✅ | Backgrounds | Per-page/global COLOR, GRADIENT, IMAGE backgrounds; Room v3; backup fields | P8, P0 |
| P11 ✅ | Visual polish | Glass HUD/nav/settings, themed system bars, Canvas-drawn direction arrows | P8 |
| P12 ✅ | Direct canvas editing | Long-press to edit on canvas; dotted grid guide; drag, resize, remove; empty/nav exit | P2, P7 |

Parallelism: after P0, **P1, P2, and P4 can run together**. Then **P3, P5, and P6** can overlap. P7 integrates and polishes.

---

## 9. Session log

- **2026-11-19 (design):** Design discussion after user clarified constraints and product direction. No source files changed. This notes file was created. Repo remains at commit `f84fc03`.
- **2026-11-19 (P0 started):** Toolchain unchanged. Added KSP 1.9.24-1.0.20 + Room 2.6.1 to Gradle; `assembleDebug` passed. Created shared `domain/Model.kt` and `docs/P0_CONTRACTS.md`. Spawned two background subagents with disjoint ownership:
  - Data layer + DataStore→Room migration: session `ses_e0da81f54ffey3BATa9KqH9vWz`
  - Graph validators + JUnit tests: session `ses_e0da81f53ffeRQrgBZDiRbJd3G`
  - Status: **in progress**.
  - 2026-11-19: Validator agent completed. Reviewed `PageGraphValidator.kt` and its JUnit tests — reverse-BFS reachability, degree check, deterministic sorted output, 9 test cases including cycles and missing home. Looks correct.
  - 2026-11-19: Data agent completed. Reviewed and corrected contract mismatches: added missing customization fields (`isHidden`, `fontSizeSp`, `letterSpacingSp`, `textColorHex`, `customNickname`), replaced grid unique-cell index with overlap-friendly schema (`col/row/colSpan/rowSpan/referenceId/themeId/allowOverlap`), wired migration to the existing DataStore instance, added index on `page_edges.toPageId`.
  - **P0 COMPLETE:** `assembleDebug` + `testDebugUnitTest` both pass. 9/9 validator tests green, 0 failures/errors. APK at `app\build\outputs\apk\debug\app-debug.apk`.
  - **2026-11-19 (P0 committed/pushed):** `77a2f17` P0 foundation; `fb46374` fixed P1/P2/P3 contracts. Remote `origin/main` is in sync.
  - **2026-11-19 (P1/P2/P3 started):** Three worktrees created from `fb46374` on branches `p1-widgets`, `p2-canvas`, `p3-editor` (`D:\projects\void-launcher-p1|p2|p3`). Sessions: P1 `ses_e0d9977faffdYn3CwwFv9KBmQK`, P2 `ses_e0d9977f9ffeMV4CZJb4QQ3yZ0`, P3 `ses_e0d9977f5ffe3xTz16xy50wLh1`. Lead is doing P2a theme foundation in main while agents work. Status: **in progress**.
  - **2026-11-19 (P2a):** User required themes first for homepage UI. `ui/theme/Theme.kt` now has explicit dark/light schemes, `VoidTypography` text-first tokens, and `CustomPalette` + optional `customPalette` param as the future custom-theme hook. No theme editor built. Added queued-work note and P2a phase row.
  - **2026-11-19 (restart policy):** User warned the laptop can hard-restart at any moment. All work now uses small checkpoint commits; agents were instructed to do the same. `1b4488a` = P2a + notes, `7f168d1` = home coordinate prefs + `LauncherRepository` write surface. Remote pushed through `1b4488a`.
  - **Lead integration prep:** `LauncherRepository` exposes reactive pages/edges/items/customizations/home flows and all editor write callbacks. Next: add manifest entries from P1 contract, rewrite `MainActivity` against the fixed P1/P2/P3 APIs, merge the three branches, then run full build + tests.
  - **2026-11-19 (P1/P2 merged):** P1 merged at `8bde55f`, P2 merged at `59010ec`; both pushed. P1: `WidgetHostController`/`WidgetHostView`/delete receiver, compile green. P2: placement resolver + 14 tests, spatial canvas + adaptive grid, theme-token-only. Integrated `MainActivity` staged on branch `integration` at `b696606`; it wires repository flows, P1 widgets, P2 canvas, P3 editor callbacks, widget bind/picker flow, and one-hop overflow via directed edges.
  - **Gradle daemon incident:** `testDebugUnitTest` failed because "Gradle build daemon disappeared unexpectedly" during multi-worktree parallel builds — consistent with the restart/memory warning. No source corruption; all worktrees clean and `git fsck` reports only dangling checkpoint commits. Mitigation for final verification: stop stray daemons (`.\gradlew --stop`) and run `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug` after agents finish.
  - **2026-11-19 (P1-P3 COMPLETE):** P3 merged at `f0be547`, integration at `28cd778`, all pushed. Final verification with `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**. Tests: `PageGraphValidatorTest` 9/9, `PlacementResolverTest` 14/14 — 23 total, 0 failures/errors. APK: `app\build\outputs\apk\debug\app-debug.apk` (~9.4 MB).
  - **Integration summary:** `MainActivity` now hosts the P2 spatial canvas as the home surface, P3 editor sheets from SETTINGS/EDIT, and P1 live widget views. Added home-coordinate persistence, one-hop overflow resolution through directed edges, widget provider picker + `ACTION_APPWIDGET_BIND` consent flow, stale-widget cleanup, and graph validation before persisting edge changes. App drawer from v0.1 is preserved; P4 will replace/extend it.
  - **Known gap:** widgets are placed with a default 4x2 span at the first free cell; provider-specific sizing and resize persistence are deferred. Real-device testing on the Android 12 phone is still required for swipe feel, widget binding, and OEM behavior.
  - **2026-11-19 (release build):** Version bumped to `versionName = 0.2.0`, `versionCode = 2`; release build type signed with the debug key so it installs on a phone (store-grade signing deferred). `assembleRelease` succeeded with `--no-daemon --max-workers=1`. Copy at `D:\projects\Void Launcher\downloadable\voidlauncher v0.2.0 - release.apk` (~6.4 MB), signature verified with `apksigner` (Android Debug certificate). `downloadable/` is git-ignored so APKs never enter version control. This APK contains P0-P3 + themes; it does NOT include P4-P6, which were started afterward.
  - **2026-11-19 (P4/P5/P6 started):** Contracts in `docs/P4_P5_P6_CONTRACTS.md`. Worktrees `D:\projects\void-launcher-p4|p5|p6` on branches `p4-drawer`, `p5-backup`, `p6-access`. Sessions: P4 `ses_e0d889c45ffeju1XTvkfdPI1V9`, P5 `ses_e0d889c42ffeky9YgnEJqR5idX`, P6 `ses_e0d889c3affevD8WXM4DcNYybv`. Agents were told not to run Gradle (lead builds centrally) and to commit checkpoints. Lead added accessibility manifest service, config XML and disclosure string at `202708a`; staging worktree `integration-next` created for the final wiring.
  - **2026-11-19 (P4/P5/P6 COMPLETE):** All three agents delivered on contract. P4 merged at `562af30` (text drawer with instant text search/favorites-first/hidden filtering, text settings with theme/map/backup/accessibility rows). P5 merged at `e15760c` (versioned JSON v1 backup format via framework `org.json`, SAF export/import, 6 pure-JVM tests; widgets store provider identity only and restore as placeholders needing rebinding). P6 merged via `9fd3194` (opt-in `VoidAccessibilityService` global-actions bridge using only public APIs, zero accessibility events, no UI-content reads). Lead added `LauncherRepository.restore(payload)` remapping old page ids through a fresh id table, `deleteAll()` DAO hooks, test-only `org.json:json:20231013`, and P6 manifest refinements (label + zero-event XML).
  - **2026-11-19 (final integration):** `MainActivity` rewritten on `integration-next` (`2219cb0`) to wire P4 `AppDrawerOverlay`/`SettingsSurface`, P5 SAF export/import launchers + `BackupScreen`, P6 accessibility state, and favorites/hidden from `AppCustomizationEntity`. Fixed two integration defects before committing: duplicate `repository.restore(payload)` call on import, and missing `layout.height` import. Merged to `main` at `d3b6a04`. Full verification with `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**, 29/29 tests green (9 graph + 14 placement + 6 backup), 0 failures/errors. Cross-checked every shared API used by the integration (prefs, repository, widget host, editor sheets, spatial canvas, theme) — all signatures matched.
  - **2026-11-19 (v0.3.0 release):** Version bumped to `versionName = 0.3.0`, `versionCode = 3` at `40d7295`. This release contains all phases P0-P6 plus dark/light themes and the custom-theme hook. Release build uses the debug signing config (store-grade signing deferred). Copy at `D:\projects\Void Launcher\downloadable\voidlauncher v0.3.0 - release.apk`; `downloadable/` remains git-ignored.
  - **2026-11-19 (launcher icon fix):** User reported v0.2.0 installed but had no drawer icon and no "Open" button in Settings. Root cause: `MainActivity`'s only intent-filter was `MAIN` + `HOME` + `DEFAULT`; a HOME-only main activity is intentionally hidden from the drawer. Fixed at `bfede8f` by adding a second `MAIN` + `LAUNCHER` intent-filter (HOME registration preserved). v0.3.0 is being rebuilt with this fix. Interim workaround for an already-installed build: press Home and choose Void Launcher in the resolver (use "Just once" to avoid switching default home).
  - **2026-11-19 (v0.4.0 — first real-device feedback):** User could not install v0.3.0: Play Protect blocked it for "accessing sensitive data". Likely triggers: broad `QUERY_ALL_PACKAGES`, accessibility service declaration, debug signature. Removed `QUERY_ALL_PACKAGES` in favour of a scoped `<queries>` MAIN/LAUNCHER intent entry — `AppRepository` uses `queryIntentActivities(MAIN/LAUNCHER)`, so drawer behavior is unchanged. Accessibility service kept (opt-in feature); user can bypass Play Protect with "Install anyway" or by temporarily disabling "Scan apps with Play Protect".
  - **2026-11-19 (gesture diagnosis & fix):** User reported on v0.2.0 that swipe and double-tap "didn't work", while Back/Home/Recents/notification panel did. Root cause was NOT the gesture code: the DB seeded only the origin page and zero edges, so every swipe found no outgoing edge and sprang back; double-tap calls `onHomeRequested`, a silent no-op when already on home. Back/Home/Recents/notifications are Android system navigation and unrelated. Fixed: `VoidDatabase` bumped to v2 with `MIGRATION_1_2`, seeding a connected starter map (Home + 4 neighbours, 8 directed edges, every page has a path home); double-tap now emits a haptic for feedback. `onOpen` only re-seeds the origin page as a safety net, so user-removed edges are never restored.
  - **2026-11-19 (v0.4.0 verification):** `testDebugUnitTest assembleRelease` with `--no-daemon --max-workers=1`: **BUILD SUCCESSFUL**, 29/29 tests, 0 failures. APK at `D:\projects\Void Launcher\downloadable\voidlauncher v0.4.0 - release.apk` (~6.4 MB), debug-signed, signature verified. `aapt` confirms `versionCode=4`, `versionName=0.4.0`, `launchable-activity=MainActivity`, and NO `QUERY_ALL_PACKAGES` permission. Committed at `75004bd`.
  - **2026-11-19 (real-device test, lead-driven via ADB):** Installed v0.4.0 on the user's Infinix X669C (Android 12) with `adb -s 192.168.1.69:34763 install -r -t`, launched via `am start -n com.ark.voidlauncher/.MainActivity`, and drove the UI with `input swipe/tap/keyevent` plus `uiautomator dump` for verification. All flows PASSED: (1) HOME renders with bottom bar; (2) swipe DOWN from PAGE 0,-1 → HOME, swipe RIGHT from HOME → PAGE 1,0; (3) double-tap → HOME; (4) drawer lists 35 apps with search/EDIT/PIN and launches apps; (5) Settings shows all rows incl. version 0.4.0; (6) page editor shows the 5 seeded pages; (7) SAF export wrote valid JSON v1 (5 pages, 8 directed edges) to `Download/voidlauncher-backup.json` with "BACKUP EXPORTED" status; (8) SAF import showed confirmation, restored with "BACKUP RESTORED" status, and ID remapping preserved navigation; (9) widget picker listed real providers, `ACTION_APPWIDGET_BIND` consent dialog appeared, CREATE placed a "Chrome Dino" widget that rendered on PAGE 1,0. Logcat showed no app errors/crashes (only benign OEM ClassLoader and GC finalizer warnings); a `uiautomator` NPE in `AccessibilityNodeInfoDumper` was a tool-side crash, not the app. Note: BACK from HOME exits the app by design (host BackHandler is disabled on HOME), so use the on-screen APPS/SETTINGS rows or relaunch with `am start`.
  - **Remaining work / known gaps:** provider-specific widget sizing and resize persistence deferred; custom-theme editor deferred (hook only); store-grade release signing deferred. Accessibility remains opt-in and off by default. If Play Protect still blocks v0.4.0, offer a test build with the accessibility service removed.
  - **2026-11-19 (P7 — Spatial Awareness & Overview, IMPLEMENTED):** User reported they could not tell which coordinate page they were on, what pages existed, or where they could swipe. Added a formal phase P7 with frozen contracts in `docs/P7_CONTRACTS.md` (`c012704`) and two disjoint worktrees/agents. Delivered: (1) **long-press on the canvas** (and a tappable `MAP` affordance in the HUD) opens a full-screen bird's-eye **page map** — pannable by drag and zoomable by pinch (`detectTransformGestures`, clamped 0.35x..2.5x), showing every page at its `(x,y)` coordinate with HOME/HERE tags, item counts, direction glyphs, and directed arrow edges that highlight edges leaving the current page; tapping a cell navigates there. The map has a bottom nav with exactly `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES`, each wired to a real destination (theme dialog, Settings, widget picker, page editor). (2) A non-blocking **Navigation HUD** overlays the canvas showing `PAGE x,y`, a HOME tag, four direction arrows bright/dim depending on whether an outgoing edge exists (with neighbour coordinate labels), and a wall-hit flash on the blocked side. (3) **Swipe animations**: the page animates off in the swipe direction and the new page slides in from the opposite side (tween 200/220ms). (4) **Wall haptic**: `HapticFeedbackConstants.REJECT` on API 30+ (LONG_PRESS fallback), plus the HUD visual flash, cleared after 450 ms. Commits: P7a overview `71fe557`, `8fabcd9`, `051ff69` merged at `e229eed`; P7b HUD `29b6662`, `58fdf89` merged at `e9492db`; lead canvas `2c2b79a`, theme picker `251955a`, integration `7f8d6be`; fixed a compile error (`Int * Dp` ordering in map sizing) at `a773e7a`. All pushed.
  - **2026-11-19 (v0.5.0 release):** Version bumped to `versionCode = 5`, `versionName = "0.5.0"`. Full verification with `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug`: **BUILD SUCCESSFUL**, 29/29 tests green (9 graph + 14 placement + 6 backup), 0 failures/errors. `assembleRelease` also **BUILD SUCCESSFUL**. APK at `D:\projects\Void Launcher\downloadable\voidlauncher v0.5.0 - release.apk` (~6.47 MB), debug-signed (Android Debug certificate verified with `apksigner`); `aapt` confirms `versionCode=5`, `versionName=0.5.0`, launchable `MainActivity`, minSdk 24.
  - **2026-11-19 (P7 real-device test, in progress):** Installed v0.5.0 on the Infinix X669C via `adb install -r -t` and launched it. Confirmed the HUD renders correctly on HOME: `PAGE 0,0` with HOME tag, all four neighbour labels (`0,-1`, `0,1`, `-1,0`, `1,0`), and the `MAP` affordance. Test interrupted by device-side issues: an Infinix "display overlay on lock screen" prompt appeared when launching via `am start` while the screen was asleep (dismissed with BACK), the screen auto-rotated to landscape on unlock (auto-rotate temporarily locked to portrait for consistent coordinate-based ADB input), and an adb/server restart dropped the wireless connection (reconnected with `adb connect 192.168.1.69:34763`). Remaining live checks: overview map open/pan/select, bottom-nav destinations, theme dialog, wall flash, and logcat error scan.
   - **2026-11-19 (P7 real-device test, COMPLETE):** Finished v0.5.0 device verification on the Infinix X669C (Android 12). Confirmed: map opens on long-press/`MAP` tap, cells render at their coordinates, bottom-nav labels `THEMES`/`SETTINGS`/`WIDGETS`/`PAGES` are all visible (pixel scan found bright text at y≈1468–1484; `uiautomator` reported misleading 4px-tall bounds), wall-hit flash and haptic fire on blocked swipes, and logcat showed only vendor/system noise (`QT`, `ion`, `OpenGLRenderer`, `BLASTBufferQueue`), no app exceptions. BACK from HOME exits the app by design (host `BackHandler` disabled on HOME), so use the on-screen nav or relaunch with `am start`.
   - **2026-11-19 (v0.5.1 — swipe/Y-axis bug fixes):** User reported swipe directions were mirrored and the map Y axis inverted. Root cause: `SpatialPageCanvas` looked up edges using the gesture direction directly (right→`RIGHT`→`(1,0)`), and `PageOverview` laid out increasing `coordY` downward. Fixed by centralising the convention in `domain/Model.kt`: `SwipeDirection.edgeForGesture` (carousel paging — X mirrored in the stored-edge lookup, Y unchanged) and `swipeDestination` (X-right / Y-up), now consumed by the canvas, HUD, and overview so they cannot drift. Also fixed invisible overview nav labels (`background()` does not set `LocalContentColor`; added explicit `onSurface`), and gave nav buttons an explicit 56dp touch target. Added `SpatialNavigationTest` (6 tests) locking the convention. Commits `8760fc5`, `7147410`, `82862f4`, all pushed. No DB migration: stored directed edges and schema are unchanged — only gesture→edge lookup changed.
   - **2026-11-19 (v0.5.2 final):** Version bumped to `versionCode = 7`, `versionName = "0.5.2"`. Full verification with `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease`: **BUILD SUCCESSFUL**, **35/35 tests** (9 graph + 14 placement + 6 backup + 6 spatial), 0 failures/errors. APK at `D:\projects\Void Launcher\downloadable\voidlauncher v0.5.2 - release.apk` (~6.47 MB), debug-signed (Android Debug certificate verified with `apksigner`); `aapt` confirms `versionCode='7'`, `versionName='0.5.2'`.
   - **2026-11-19 (v0.5.2 real-device verification, FINAL):** Installed and drove the UI on the Infinix X669C via ADB. Verified HUD labels: top `0,-1`, bottom `0,1`, left `1,0`, right `-1,0`. Verified swipes: RIGHT→`PAGE -1,0`, LEFT→`PAGE 0,0`, UP→`PAGE 0,-1`, DOWN from `(0,-1)`→`PAGE 0,0`, and DOWN from HOME→`PAGE 0,1`, UP back→`PAGE 0,0`. Map renders `(0,1)` above `(0,0)` and `(0,-1)` below. Final logcat scan: no app errors. Phone settings restored (`accelerometer_rotation` back to `1`); theme left on SYSTEM; app left on a clean HOME. Working tree clean, all commits pushed to `origin/main`.

---

## 10. P8–P11 / v0.6.0 results

Frozen contracts live in `docs/P8_P11_CONTRACTS.md` (`62a7c99`). The pinned toolchain and dependency set did not change: Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, compileSdk 34, Compose UI 1.6.7 / Material 3 1.2.1, JDK 17. Android 10 (API 29) remains supported; `minSdk` is still 24.

### 10.1 P8 — Theme system

- `ThemeMode` gained `CUSTOM`; system/dark/light remain. The light scheme is a soft warm paper palette (no pure white/black) and the dark scheme has stronger surface/secondary tokens for depth.
- `CustomPalette` persists as eight DataStore hex keys (`custom_bg`, `custom_surface`, `custom_surface_variant`, `custom_on_bg`, `custom_on_surface`, `custom_primary`, `custom_secondary`, `custom_outline`). `CustomThemeEditorSheet` validates all fields and offers dark/light presets. Theme selection and editing are reachable from the theme picker and settings.
- `VoidStyle` / `LocalVoidStyle` and `GlassPanel` provide scheme-derived glass/skeuo tokens. `GlassPanel` is visual-only (no pointer-input consumption), so child taps still work; no `RenderEffect` blur is used, which avoids API 31+ requirements and CRASH risk on older devices.
- **HOME badge contrast fix (`56a546d`):** `Theme.kt` derives `onPrimary`, `onSecondary`, and `onSurfaceVariant` from the relative luminance of the matching user-chosen fill (`readableOn`). `NavigationHud` renders the HOME tag with `onPrimary` on `primary`; custom fills can no longer leave the badge with the opposite theme's ink.
- Backup v1 gained optional `theme` and `customPalette` fields with safe defaults for older files.

### 10.2 P9 — Orientation

- `OrientationMode { PORTRAIT, LANDSCAPE, AUTO }` persists under DataStore key `"orientation"`; PORTRAIT is the default.
- `MainActivity` applies `requestedOrientation` immediately on a setting change and also replays it from the flow (`7c8bb25`) so a DataStore emission during a configuration change cannot be lost. `AndroidManifest.xml` starts `MainActivity` in portrait for the first frame; no `configChanges` were added.
- Settings exposes the three-way ORIENTATION row; backup carries an optional `orientation` string defaulting to `PORTRAIT`.

### 10.3 P10 — Backgrounds

- Room v3 added the `backgrounds` table via `MIGRATION_2_3` (create only, no seeding). `BackgroundRepository` owns GLOBAL/PAGE scopes, COLOR/GRADIENT/IMAGE kinds, gradient-stop codec/validation, image bytes, and internal image storage under `filesDir/backgrounds/<token>.img`; raw `content://` URIs are never persisted.
- `BackgroundResolver` picks a page-specific background before the global one. `PageBackgroundRenderer` paints solid fill, multi-stop linear gradient, or cropped image with a theme-derived scrim to keep HUD text legible. `BackgroundEditorSheet` supports scope/kind selectors, hex fields, gradient stops/angle, SAF image picking, and delete.
- The renderer is a stationary first child of `SpatialPageCanvas`, so backgrounds do not slide with the translating page during swipes.
- Fixes included gradient defaults and moving backgrounds between GLOBAL and THIS PAGE scopes (`ed9a0f5`) and image-byte handling (`1b7f267`).
- Backup v1 gained an optional `backgrounds` array. Image backgrounds carry `imageToken` plus base64 bytes with a size cap; restore remaps page coordinates to regenerated Room page IDs.

### 10.4 P11 — Visual polish

- Navigation HUD, home bottom nav, settings, and system bars use `GlassPanel` / theme-derived surfaces (`51994a4`, `81b0ab2`, `20dd059`). Themed status/navigation bars follow the active scheme.
- Direction arrows are drawn on `Canvas` instead of Unicode arrow glyphs (`b55f68d`); device verification counts `content-desc="SWIPE"` arrows (4 on HOME, 20 on MAP) with zero Unicode arrow glyphs.

### 10.5 Direct on-canvas editing

- Long-pressing a grid item enters editor mode for that item. A `GridGuide` draws dotted lattice lines and dots at intersections; page swipes, double-tap, and overview long-press are suspended while editing.
- The selected item gets a themed selection border. Dragging computes a target `(col,row)` from the pointer delta and commits through `PlacementResolver.resolve` / `resolvePlacement`, preserving all placement rules (in-bounds positive spans, app-shortcut non-overlap, widget overlap only when allowed, and single-hop overflow through directed edges). One-hop page changes use `LauncherRepository.placeItem`.
- A contextual bar offers `RESIZE` and `REMOVE` while an item is selected and `DRAG TO MOVE` otherwise. `ItemResizeDialog` validates column/row spans against the page dimensions. The numeric `ItemEditorSheet` callbacks were routed through the same validated helpers, so the two editors cannot disagree.
- Tapping empty canvas space or any of the three bottom nav buttons exits the editor without opening that destination. Android Back closes the resize dialog first, then the editor.
- On-device verification on the Infinix X669C: long-press showed `DRAG TO MOVE`, `RESIZE`, `REMOVE`; dragging moved a widget from `AT 0,0` to `AT 2,2`; applying a 5×3 resize changed its bounds to `[144,218][504,437]`; empty-space tap and `APPS` both exited; the widget was restored to `AT 0,0`, `[0,72][288,218]`, span 4×2. A temporary screenshot pixel comparison found average grid-line/intersection difference 4.3 versus 0.72 at control points, confirming the guide is rendered.

### 10.6 Verification and closeout

- Compile/unit check after the editor work: `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug` — **BUILD SUCCESSFUL**.
- P10 backup import was verified on the device from `Download/voidlauncher-backup (1).json` (3084 bytes; includes `version=1`, `theme=CUSTOM`, `orientation=PORTRAIT`, `customPalette`, and one IMAGE background with base64 bytes). Settings reported **BACKUP RESTORED**.
- Final low-memory verification: `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease` — **BUILD SUCCESSFUL in 1m 31s**. Test reports: **35/35 tests, 0 failures, 0 errors** (9 graph + 14 placement + 6 backup + 6 spatial).
- `app/build.gradle.kts` is at `versionCode = 8`, `versionName = "0.6.0"`. The release APK is `D:\projects\Void Launcher\downloadable\voidlauncher v0.6.0 - release.apk` (6,567,398 bytes), debug-signed as before. `aapt` confirms package `com.ark.voidlauncher`, `versionCode='8'`, `versionName='0.6.0'`, `minSdk=24`, `targetSdk=34`, launchable `MainActivity`; `apksigner` verifies the Android Debug certificate. `downloadable/` remains git-ignored.
- Final release APK was installed on the Infinix X669C and driven via ADB. Verified: HOME renders `PAGE 0,0` with 4 Canvas `SWIPE` arrows; MAP renders 20 Canvas `SWIPE` arrows and 0 Unicode arrow glyphs; long-press shows `DRAG TO MOVE` / `RESIZE` / `REMOVE`; dragging moved the item `AT 0,0` → `AT 2,2` with bounds `[144,218][432,364]`; applying 5×3 resize changed bounds to `[144,218][504,437]`; item was restored to `AT 0,0`, `[0,72][288,218]`, span 4×2; empty-space tap and bottom-nav `APPS` both exited the editor without opening the drawer. App was left on a clean HOME after navigating back from MAP. Because restore intentionally converts widgets to placeholders, the verification placeholder was then removed and a live Chrome Dino widget was rebound; final HOME shows `GRID ITEM WIDGET 52 AT 0,0`.
- The imported custom palette uses `primary=#FF43382E`; `readableOn()` selects light ink `#FFEDE9E3`, giving a computed contrast ratio of about **9.42:1** for the HOME badge, so it never inherits the opposite theme's ink.
- Temporary verification files were removed from the device and from `C:\Users\Shreeyash\AppData\Local\Temp\opencode`; no screenshots or test images remain.

---

## 11. 2026-11-22 interaction batch — completion

- Source backup before the final revisions: `backups\void-launcher-source-20261122-122659.zip` (git archive of HEAD). Malformed earlier archives were deleted; `web_editor/` stays user-owned/untracked.
- Checkpoints: `6228337` (batch integration), `58c4307` (import/scope compile fixes), `ff49fd9` (outlined action buttons), `62b816f` (non-consuming page taps), `1fb3c75` (APP_LIST rows own taps and long-press), plus the notes/changelog commit `b58a887`.
- Same-line action buttons use Material 3 `OutlinedButton` with `BorderStroke(1.dp, ...)`, compact padding and theme-derived colours; `Arrangement.spacedBy(8.dp)` separates them. Device bounds confirmed: editor `CLEAR [68,880][144,912]`, `SAVE [208,880][269,912]`; drawer `EDIT [462,468][519,500]`, `PIN [601,468][644,500]`.
- Root cause of APP_LIST row taps doing nothing: a transparent full-size `pointerInput` sibling drawn after `widgetContent` was the only eligible hit target, so `LazyColumn` rows never received pointer events. Fix removes that overlay in normal mode; `AppListWidgetContent` rows use `combinedClickable(onClick, onLongClick)` and update `lastItemTouch`, while editing keeps the selection/MENU action-zone overlay. A subagent diagnosis confirmed the sibling hit-testing cause and ruled out R8/log stripping (minification is off).
- Final serialized verification: `.\gradlew --stop` then `.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease` — **BUILD SUCCESSFUL in 2m 48s**. Debug APK SHA-256 `B3C239171A42CE42AB46AB31A8B8DE97799CA172B25C1BB063F5200AA07D6F52`.
- Device verification on the Infinix X669C (`adb-104873739I004203-8S8Hxg._adb-tls-connect._tcp`): APP_LIST row tap at `[220,310]` launched `com.JindoBlu.FourPlayers`; row long-press opened editor with `MENU`/`RESIZE`/`REMOVE`; app shortcut tap at `[280,830]` still launched; double-tap returned HOME. Drawer nickname GLOBAL `GTEST` and DRAWER ONLY `DTEST` saved/displayed mutually exclusively, then both cleared to `NOT SET`. RESET ALL confirmation reseeded HOME with `GRID ITEM WIDGET builtin:PAGE_INFO AT 1,1`, bounds `[72,145][216,291]` (2×2), and no fixed coordinate badge.
- Wakefulness was checked before input; the keyguard appeared once during a relaunch and cleared after wake. Phone left awake, unlocked, focused on `com.ark.voidlauncher/.MainActivity`, clean HOME after RESET ALL. Void Launcher was **not** set as default HOME. No screenshots or temp DB/UI dump files remain.
