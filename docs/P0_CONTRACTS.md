# P0 Contracts — do not cross file ownership

Frozen toolchain: Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, compileSdk 34. Do not change Gradle files.

Shared types already exist in:
`app/src/main/java/com/ark/voidlauncher/domain/Model.kt`

## Agent A — data layer ONLY

Owns (create/edit only these):
- `app/src/main/java/com/ark/voidlauncher/data/db/**`
- `app/src/main/java/com/ark/voidlauncher/data/migration/**`

Must implement:
- `@Entity` tables: `PageEntity`, `PageEdgeEntity`, `AppCustomizationEntity`, `GridItemEntity`
- Foreign keys with cascade delete for edges/items pointing to pages
- Unique constraint on page `(coordX, coordY)`
- Composite PK on `PageEdgeEntity(fromPageId, swipeDirection)` (one target per direction)
- Reactive `Flow` DAOs: `PageDao`, `PageEdgeDao`, `AppCustomizationDao`, `GridItemDao`
- `VoidDatabase` (version 1, exportSchema true) with callback seeding origin page `(0,0)`
- `DataStoreToRoomMigration`: reads existing `pinned_packages` and `theme` from the current DataStore, writes favourite app customizations + APP_SHORTCUT grid items on origin, sets a one-time `migration_v1_done` flag. Do not delete DataStore dependency.

Mapping helpers should convert entities to/from `PageInfo` and `EdgeInfo` from the domain model.

Do not touch: Gradle files, `domain/`, UI, tests, MainActivity, or this doc.

## Agent B — validators + tests ONLY

Owns (create/edit only these):
- `app/src/main/java/com/ark/voidlauncher/domain/PageGraphValidator.kt`
- `app/src/test/java/com/ark/voidlauncher/domain/**`

Must implement pure Kotlin (no Android imports):
```kotlin
object PageGraphValidator {
    fun pagesWithoutConnection(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>
    ): List<Long>

    fun pagesWithoutPathToHome(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long
    ): List<Long>

    fun validate(
        pages: List<PageInfo>,
        edges: List<EdgeInfo>,
        homePageId: Long
    ): GraphValidationResult
}
```

Rules:
- "Connection" means degree >= 1 in either direction (incoming OR outgoing edge).
- "Path to home" follows directed edges from each page to `homePageId`. Home itself is always reachable.
- Results must be deterministic: sort by pageId ascending.
- Tests with JUnit 4 must cover: isolated page, one-way path, directed path failure, cycle without path to home, and a valid graph.

Do not touch: Gradle files, data layer, UI, MainActivity, or this doc.

## Lead (me)

Owns Gradle files, `domain/Model.kt`, MainActivity wiring, integration build, and notes. Do not edit these in subagents.
