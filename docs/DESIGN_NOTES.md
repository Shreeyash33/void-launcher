# Void Launcher — Design Notes

**Last updated:** 2026-11-19  
**Status:** Discussion only. No app source was changed in this session. These notes exist so the design survives session compaction.

---

## 1. Hard constraints (do not violate)

- **Sudden restarts are expected on this laptop (user warning, 2026-11-19).** Treat every file write as potentially the last before a hard reboot. Commit small, frequent checkpoints; never leave a large batch of work uncommitted. All agents must commit as soon as a coherent file set is written, not only at the end of a phase. Keep notes in this file updated at each checkpoint. Prefer `write` (atomic replace) over long sequences of fragile edits when a restart could land mid-edit.
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
| P4 | Drawer + settings | Text drawer with search and pin; theme switch; home/settings navigation | P0 |
| P5 | Backup/restore | Versioned JSON export/import via SAF, including widget placeholders | P0, P3 |
| P6 | Accessibility | Opt-in service, gesture zones, global actions; no reflection | P2 |
| P7 | Polish | Themes, transitions, empty states, device-test fixes | P1-P6 |

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
  - **Remaining work / known gaps:** real-device testing on the Android 12 phone (swipe feel, widget binding, OEM behavior); provider-specific widget sizing and resize persistence deferred; custom-theme editor deferred (hook only); store-grade release signing deferred. Accessibility remains opt-in and off by default. If Play Protect still blocks v0.4.0, offer a test build with the accessibility service removed.
