# Changelog

## 0.6.0 — current release (2026-11-19)

- P8–P11 shipped: soft light/dark system with editable `CUSTOM` palette, portrait/default orientation setting, per-page/global colour/gradient/image backgrounds, themed system bars, glass HUD/nav and Canvas-drawn direction arrows.
- Typed pages, built-in widgets and direct on-canvas editing are live. Release APK: `downloadable\voidlauncher v0.6.0 - release.apk` (debug-signed; store-grade signing deferred).
- Verification: `testDebugUnitTest assembleDebug assembleRelease` green with `--no-daemon --max-workers=1`; 35/35 JVM tests, 0 failures.

## 2026-11-20 — typed pages, widgets and map

- Pages carry a persisted `pageType` (`HOME`, `WIDGET`, `APP_DRAWER`); grid items carry `widgetKind` and app-list membership.
- Widget picker limited to `CLOCK` and `APP LIST`; picker long-press hands the pointer to a floating widget that follows the finger until a validated drop. System widget placements keep rendering and are never deleted by this change.
- Overview map gained a dotted grid, 16:9 height:width page and `+` preview tiles, a coordinate-detail setting and themed prompts. Backup v1 carries the new fields with safe defaults for old files.

## 2026-11-20 — page types, drag, HOME and arrows

- `+` previews choose `WIDGET PAGE` or `APP DRAWER PAGE`; multiple drawer pages are allowed. Long-press selects a page for dragging within the in-bounds lattice, rejecting isolated cells.
- Moving or creating a page derives one default outgoing link in LEFT, RIGHT, UP, DOWN priority; the map exposes all four direction toggles with graph-safety checks.
- Long-press selection adds `SET HOME` / `HOME`; HOME follows the single home-coordinate preference anywhere on the map, and a drawer page cannot become HOME. Dialogs and prompts use theme surfaces, never hardcoded white.

## 2026-11-20 — default layout and RESET ALL

- Canonical `DEFAULT` state: exactly five pages — `HOME` at `(0,0)`, widgets at `(1,0)`, `(-1,0)`, `(0,-1)`, `APP_DRAWER` at `(0,1)` — with bidirectional links between HOME and each neighbour, and no other customization.
- Settings gained `RESET ALL` with confirmation. All Room tables are cleared and reseeded in one transaction; DataStore is cleared only after commit, so HOME can never point at a missing page.

## 2026-11-20 — AppWidget cleanup fix

- Reset now collects every launcher-owned `AppWidgetId` (host allocations plus referenced placements) and releases them after the Room transaction. Stale host widgets are no longer left behind after `RESET ALL`; background image tokens are cleaned up in the same ordered flow.

## 2026-11-20 — in-progress revision (not yet released)

- Bidirectional links: four map toggles create/remove both directed rows through `PageLinks`; the map shows one link, not two one-way entries, and a toggle that would strand a page is rejected.
- Navigation history: BACK and the reverse of the swipe used to enter a page return to the last page entered, including from `APP_DRAWER` pages; unmatched gestures fall back to normal link navigation.
- User-editable page titles; the launcher shows the page name first and coordinates secondarily.
- Map bottom nav removed; a top-right hamburger opens a sidebar with `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES`, `GESTURES`. Status/error messages render at the top.
- Widget picker and dialog text now use `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant`, fixing black-on-dark text.
- Gesture foundation: trigger/action/scope models, page-over-global precedence, many-to-one bindings, DataStore encoding, tests and a settings surface.

**Deferred to the next stage:** gesture recognition and dispatch (detecting triggers in Compose and executing actions), plus gesture settings wired into the live navigation flow.
