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

5. A normal HOME or WIDGET page shows the **page title first**, with coordinates as a small, secondary label when detail is enabled. Superseded by decision 44 below.
6. Remove from the normal page overlay:
   - the in-app `HOME` tag/button (the Android system Home button is the way home);
   - the `MAP` button (long-press empty canvas still opens the map);
   - all four direction arrows;
   - the bottom `APPS` / `EDIT` / `SETTINGS` nav.
7. The overview map is reached by long-press; its top-right hamburger opens a sidebar with `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES` and `GESTURES`. Superseded by decision 45 below.

### Editor mode and menus

8. Long-pressing a widget enters editor mode for that widget. Editor mode shows:
   - the dotted grid guide;
   - the selected widget border and corner/edge resize handles;
   - contextual `RESIZE` and `REMOVE` actions for the selected widget;
   - a popup menu containing `APPS`, `EDIT`, `SETTINGS`, `WIDGETS`.
9. `APPS` in the editor menu navigates to the `APP_DRAWER` page. It does not open an overlay drawer on top of the current page.
10. `EDIT` and `SETTINGS` are available only from editor mode or from the map sidebar. They are never shown on a normal page.
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
    2. Open the map's top-right hamburger sidebar and tap `WIDGETS`.
    3. Scroll the widget list and **long-press** the desired widget.
    4. The picker closes, the app returns to the current HERE page, enters canvas editor mode, and the new widget is attached to the finger as a floating preview.
    5. Moving the finger moves the preview over the dotted grid. Releasing commits the widget to the nearest valid cell through `PlacementResolver`. Invalid releases show a message and keep the widget floating for another attempt. Back cancels the pending widget.
    6. If HERE is the `APP_DRAWER` page, placement is rejected with a clear message; switch to a HOME/WIDGET page first.
19. All placement, overlap, and single-hop overflow rules remain enforced. Built-in widgets use the same resolver as every other item.

### Map and page creation (2026-11-20)

20. Long-press empty canvas continues to open the overview map.
21. The map draws a faint dotted grid behind the page tiles so the extent and edges of the map are visible.
22. Every coordinate where the next page could validly be placed shows a dotted-border preview tile with a `+` icon. The preview uses a 16:9 **height:width** portrait aspect ratio (taller than wide) instead of a square, so it reads like a phone screen.
23. Tapping a `+` preview creates a `WIDGET` page at that coordinate, exits the map, and makes the new page the current HERE page.
24. A setting controls whether the page coordinate detail is shown or hidden. It defaults to visible and affects the normal-page label and the map tiles.
25. Resize handle visuals are smaller than the current implementation; the usable drag targets remain large enough to grab reliably.

### Map and theme refinements (2026-11-20)

26. Existing page tiles on the map use the same portrait 16:9 height:width ratio as the `+` previews.
27. The map grid lines use a clearly visible dotted stroke rather than a barely perceptible one.
28. Tapping a `+` preview creates the page but **stays on the map**; it does not change the current HERE page.
29. A small faint arrow on a page toggles the navigation link to the neighbour it was spawned from. Disabled means the page has no navigation access; enabling creates the swipe edge, disabling removes it. Graph safety still applies.
30. The custom theme editor gets a color modal with hex input, RGBA numeric input and a hue wheel, opened by a small toggle button and closed with a cross.
31. Modals on the settings surface use the active theme's surface colour (no white background in dark theme).

### Page creation, movement and home (2026-11-20)

32. Tapping a `+` placement preview on the map opens a small themed prompt asking `WIDGET PAGE` or `APP DRAWER PAGE`. There is no coordinate typing. The chosen type is stored on the created page. Multiple `APP_DRAWER` pages are allowed.
33. Long-pressing a page tile on the map selects it for dragging. Dragging moves the page to any empty in-bounds lattice cell that has at least one existing page in one of the four orthogonal directions. Dropping in an isolated cell is rejected.
34. When a page is moved, all edges where it is the source or destination are removed, then one default outgoing edge is created from the moved page to the first existing neighbour in priority order: **LEFT**, **RIGHT**, **TOP/UP**, **BOTTOM/DOWN**. The user must configure any additional swipe directions afterward.
35. A newly created page also gets one default outgoing edge using the same LEFT, RIGHT, UP, DOWN priority. This replaces the old UP, RIGHT, DOWN, LEFT spawn priority.
36. The swipe-direction control on the map exposes all four arrows for every page, superseding the single spawn-neighbour arrow in decision 29. An arrow is disabled and non-toggleable when there is no page in that orthogonal direction; otherwise it toggles the outgoing edge in that direction.
37. While a page is selected by long-press, the map sidebar gains a `SET HOME` / `HOME` action. Tapping it makes that page the HOME page at its current coordinates; the previous HOME page's stored type becomes `WIDGET`. HOME is derived from the single home-coordinate preference, can be anywhere on the map, and the Android Home button therefore opens the selected page. An `APP_DRAWER` page cannot become HOME.
38. The prompt, drag selection, and home toggle use theme surface/content colours; never a hardcoded white dialog on a dark theme.
39. The app has a canonical `DEFAULT` state: exactly five pages — `HOME` at `(0,0)`, `WIDGET` pages at `(1,0)`, `(-1,0)` and `(0,-1)`, and an `APP_DRAWER` at `(0,1)` — with bidirectional edges between `HOME` and each neighbour. No other pages, grid items, backgrounds, app customizations, pinned apps, or custom theme values exist in the default state.
40. Settings gains a `RESET ALL` action with a confirmation dialog. Confirming deletes all customization and applies the `DEFAULT` state, then returns the user to the default `HOME` page. This replaces manual page/edge cleanup as the supported way to return to a clean launcher.
41. Reset must be transactional: all Room tables are cleared and reseeded in one transaction, and DataStore preferences are cleared only after that transaction commits, so a failed reset can never leave `HOME` pointing at a page that no longer exists.

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

- Normal pages show the page title first and coordinates secondarily — no HOME, MAP, arrows, or bottom nav.
- Editor mode exposes APPS / EDIT / SETTINGS / WIDGETS and contextual widget actions.
- APPS navigates to the dedicated app drawer page.
- No numeric coordinate entry is required to add, move, or resize a widget.
- Page type, widget kind, and app-list membership survive process restart and backup restore.
- Long-press in the widget list returns to the HERE page with a floating widget that follows the finger and drops onto the visible grid.
- Corner/edge handles resize live; the numeric dialog still works as a fallback.
- Existing system widgets are not deleted by this change.
- The map shows faint dotted grid lines and `+` preview tiles at every valid next-page coordinate.
- A `+` preview creates a page at that coordinate and opens it; the preview aspect ratio is 16:9 height:width.
- The coordinate-detail setting hides and shows coordinate labels without changing placement behavior.
- Resize handles are visually smaller but still draggable.

## Confirmed product decisions (2026-11-20, continued)

Decisions 42–48 supersede decisions 5, 7, 10 and 37, plus the map-bottom-nav wording in decision 18 and the acceptance check at line 114. Where this section conflicts with earlier text, this section wins.

### Links, navigation and identity

42. Each page has four bidirectional link toggles (`LEFT`, `RIGHT`, `UP`, `DOWN`). One toggle creates or removes BOTH directed edge rows between the two neighbouring pages. Internal directed rows remain for swipe resolution, but the map exposes one link, not two one-way entries. A toggle is rejected if it would strand a page.
43. `BACK` and the reverse of the swipe used to enter a page navigate to the last page entered. This applies to `APP_DRAWER` pages too. If no history entry matches, normal link navigation applies.
44. Pages have user-editable titles. The launcher displays the page name first and coordinates secondarily.

### Chrome and gestures

45. The map bottom navigation is removed. A top-right hamburger opens a sidebar containing `THEMES`, `SETTINGS`, `WIDGETS`, `PAGES` and `GESTURES`. Status/error messages render at the top so bottom UI cannot hide them.
46. Gestures are user-assignable globally or per page. Page-scoped bindings override global bindings. The same action may be assigned to multiple gestures.
47. Initial gesture triggers: `LONG_PRESS`, `DOUBLE_TAP`, `TRIPLE_TAP`, `PINCH_ZOOM_IN`, `PINCH_ZOOM_OUT`, `SWIPE_2_*`, `SWIPE_3_*`. Initial actions: `ZOOM_IN`, `ZOOM_OUT`, `LOCK_SCREEN`, `LOCK_LAUNCHER`, `OPEN_EDITOR`, `OPEN_MAP`, `GO_HOME`, `OPEN_SETTINGS`, `OPEN_APP_DRAWER`, `OPEN_WIDGET_PICKER`, `TOGGLE_COORDINATES`, `SYSTEM_BACK`, `SYSTEM_RECENTS`, `SYSTEM_NOTIFICATIONS`.

### Theming

48. Widget picker and dialog text always use `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant` so dark theme never shows black text on dark surfaces.
