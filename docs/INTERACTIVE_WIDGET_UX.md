# Interactive homescreen widget UX

Status: **frozen for implementation**. This supersedes the numeric-first placement/resize flow and the v0.6.0 HUD/nav treatment. The toolchain stays pinned and no new Gradle dependencies are allowed.

## Confirmed product decisions (2026-11-19)

### Page model

1. There are three page types:
   - `HOME` — the configured home page, `(0,0)` by default but still configurable via the existing home coordinate.
   - `WIDGET` — ordinary widget pages.
   - `APP_DRAWER` — a dedicated page that hosts the app drawer.
2. All other pages are widget pages. Widgets are never placed on an `APP_DRAWER` page.
3. A default `APP_DRAWER` page is seeded at `(0,1)` and reached by the existing HOME→DOWN edge. Its page type is persisted so the user cannot accidentally turn it into a widget page.
4. Existing pages without a stored type are treated as `WIDGET`, except the configured home coordinate, which is treated as `HOME`.

### Normal page UI

5. A normal HOME or WIDGET page shows **only the page coordinates** as a small, unobtrusive label.
6. Remove from the normal page overlay:
   - the in-app `HOME` tag/button (the Android system Home button is the way home);
   - the `MAP` button (long-press empty canvas still opens the map);
   - all four direction arrows;
   - the bottom `APPS` / `EDIT` / `SETTINGS` nav.
7. The overview map keeps its existing bottom nav: `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES`.

### Editor mode and menus

8. Long-pressing a widget enters editor mode for that widget. Editor mode shows:
   - the dotted grid guide;
   - the selected widget border and corner/edge resize handles;
   - contextual `RESIZE` and `REMOVE` actions for the selected widget;
   - a popup menu containing `APPS`, `EDIT`, `SETTINGS`, `WIDGETS`.
9. `APPS` in the editor menu navigates to the `APP_DRAWER` page. It does not open an overlay drawer on top of the current page.
10. `EDIT` and `SETTINGS` are available only from editor mode or from the map nav. They are never shown on a normal page.
11. Tapping empty canvas space exits editor mode. Bottom-nav taps are gone from normal pages; the editor popup menu closes with Back or by choosing an action.

### Widgets

12. The primary homescreen contains widgets only. No new `APP_SHORTCUT` placements are created.
13. For now the widget picker contains exactly two built-in widgets:
    - `APP LIST` — lists apps inside the widget.
    - `CLOCK` — live clock.
    Android/system `AppWidgetProvider` widgets are hidden from the picker for now. Existing system widget placements must keep rendering until removed; do not delete user data.
14. `APP LIST` and `CLOCK` can be resized from 1×1 up to the maximum grid size of their page. `APP LIST` can hold zero or more apps. A `+` control inside the widget opens an app picker; tapping an app toggles it into/out of that widget. With zero apps the widget shows `TAP + TO ADD APPS`.
15. `CLOCK` needs no configuration.

### Placement and resizing

16. Users must never type grid coordinates in the primary flow.
17. Resizing is interactive: the selected widget gets visible corner and edge drag handles. Dragging a handle changes the span live, clamped and validated by the existing placement rules. The numeric `RESIZE` dialog remains available as a fallback.
18. Add-widget flow:
    1. Long-press empty canvas opens the overview map.
    2. Tap `WIDGETS` in the map bottom nav.
    3. Scroll the widget list and **long-press** the desired widget.
    4. The picker closes, the app returns to the current HERE page, enters canvas editor mode, and the new widget is attached to the finger as a floating preview.
    5. Moving the finger moves the preview over the dotted grid. Releasing commits the widget to the nearest valid cell through `PlacementResolver`. Invalid releases show a message and keep the widget floating for another attempt. Back cancels the pending widget.
    6. If HERE is the `APP_DRAWER` page, placement is rejected with a clear message; switch to a HOME/WIDGET page first.
19. All placement, overlap, and single-hop overflow rules remain enforced. Built-in widgets use the same resolver as every other item.

## Data implications

- Add a persisted `pageType` to pages and a persisted `widgetKind` plus app-list membership to grid items.
- `referenceId` keeps its legacy meaning for numeric system widgets and package-name app shortcuts. Built-ins use a `builtin:` sentinel and cannot collide.
- Export/import must carry the new fields with safe defaults so old version-1 backups still restore. Old backups get a default `APP_DRAWER` page if none exists.

## Exit and safety

- While a pending widget is floating, empty-space tap must not silently discard it; show the grid and require a valid drop or explicit Back/cancel.
- Once placed, empty-space tap exits editor mode.
- Home is the system Home button; there is no in-app home affordance on a normal page.

## Out of scope for this pass

- New Android system widget providers, provider-specific sizing, rebind flows.
- Drag apps from the drawer into an app-list widget.
- App shortcuts on the canvas.
- Gesture zones / accessibility changes.
- Store-grade signing or dependency upgrades.

## Acceptance checks

- Normal pages show only page coordinates — no HOME, MAP, arrows, or bottom nav.
- Editor mode exposes APPS / EDIT / SETTINGS / WIDGETS and contextual widget actions.
- APPS navigates to the dedicated app drawer page.
- No numeric coordinate entry is required to add, move, or resize a widget.
- Page type, widget kind, and app-list membership survive process restart and backup restore.
- Long-press in the widget list returns to the HERE page with a floating widget that follows the finger and drops onto the visible grid.
- Corner/edge handles resize live; the numeric dialog still works as a fallback.
- Existing system widgets are not deleted by this change.
