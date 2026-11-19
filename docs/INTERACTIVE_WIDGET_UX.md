# Interactive homescreen widget UX

Status: **frozen for implementation**. This supersedes the numeric-first placement/resize flow added during v0.6.0 closeout. The toolchain stays pinned and no new Gradle dependencies are allowed.

## Decisions (confirmed with user, 2026-11-19)

1. The homescreen contains **widgets only**. The app drawer remains the separate text list used to launch apps. New `APP_SHORTCUT` placements are no longer part of the primary flow.
2. For now the widget picker contains exactly two built-in widgets:
   - `APP LIST` — lists apps inside the widget.
   - `CLOCK` — live clock.
   Android/system `AppWidgetProvider` widgets are hidden from the picker for now. Existing system widget placements must keep rendering until the user removes them; do not delete user data.
3. `APP LIST` can be resized from **1×1 up to the maximum grid size** of its page. It can hold **zero or more** apps. A `+` control inside the widget opens an app picker; tapping an app toggles it into/out of that widget. With zero apps the widget shows a short hint such as `TAP + TO ADD APPS`.
4. `CLOCK` can be resized from **1×1 up to the maximum grid size** of its page and needs no configuration.
5. Placement must be fully interactive. Users must not have to type grid coordinates in the primary flow.
6. Resizing must be interactive: the selected widget gets visible **corner and edge drag handles**. Dragging a handle changes the span live, clamped and validated by the existing placement rules. The existing numeric `RESIZE` dialog remains available as a fallback for users who want to type values.
7. Add-widget flow:
   1. Long-press empty canvas opens the overview map (existing behaviour).
   2. Tap `WIDGETS` in the map bottom nav.
   3. Scroll the widget list and **long-press** the desired widget.
   4. The picker closes, the app returns to the **current HERE page**, enters canvas editor mode, and the new widget is attached to the finger as a floating preview.
   5. Moving the finger moves the preview over the dotted grid. Releasing commits the widget to the nearest valid cell through `PlacementResolver`. Invalid releases show a message and keep the widget floating for another attempt. Back cancels the pending widget.
8. App-list app selection uses the same app list source as the drawer. Selecting an app in the widget's app picker toggles membership; no drag-from-drawer interaction is required for this pass.

## Data implications

- Widget kind and app-list membership must be persisted on the grid item (or an associated table), not held only in composable state.
- Export/import must carry the new fields with safe defaults so old version-1 backups still restore.
- `referenceId` remains valid for legacy numeric system widget IDs and package-name app shortcuts. Built-ins must be distinguishable without colliding with either.

## Exit and safety

- While a pending widget is floating, empty-space tap and bottom-nav taps must not silently discard it; show the grid and require a valid drop or explicit Back/cancel.
- Once placed, empty-space tap or any bottom-nav tap exits editor mode as before.
- All placement, overlap, and single-hop overflow rules remain enforced. Built-in widgets use the same resolver as every other item.

## Out of scope for this pass

- New Android system widget providers, provider-specific sizing, rebind flows.
- Drag apps from the drawer into an app-list widget.
- App shortcuts on the canvas.
- Gesture zones / accessibility changes.
- Store-grade signing or dependency upgrades.

## Acceptance checks

- No numeric coordinate entry is required to add, move, or resize a widget.
- `APP LIST` and `CLOCK` survive process restart and backup restore.
- App-list membership survives process restart and backup restore.
- Long-press in the widget list returns to the HERE page with a floating widget that follows the finger and drops onto the visible grid.
- Corner/edge handles resize live; the numeric dialog still works as a fallback.
- Existing system widgets are not deleted by this change.
