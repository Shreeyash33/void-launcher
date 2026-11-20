# Workflow (root summary)

Condensed from `docs/WORKFLOW.md`, plus the staged-delivery approach for the current revision. Full contracts stay in `docs/`.

## Workspace boundary

The only workspace is `D:\projects\Void Launcher`. Every new file, scratch copy and archive stays inside it. No git worktrees, clones or sibling "temporary" folders. A copy belongs in `backups\`.

## Backup before every revision

Before changing source, configuration or docs:

```powershell
.\gradlew --stop
powershell -ExecutionPolicy Bypass -File .\scripts\create-backup.ps1
```

Confirm exactly one fresh, non-zero `backups\void-launcher-source-<yyyyMMdd-HHmmss>.zip`, then edit in place. `scripts\create-backup.ps1` excludes `.git\`, `.gradle\`, root `build\`, `app\build\` and `backups\`; the folder is gitignored. This revision already has `backups\void-launcher-source-20261120-175526.zip`.

To restore: pick an archive, extract it into the workspace root preserving relative paths, verify `MainActivity.kt`, `docs\` and `gradlew`, and continue in the same root.

## Staged delivery

1. **Backup** as above; never revise without one.
2. **Decide and freeze** the behavior in contracts/decisions (numbered items in `docs/INTERACTIVE_WIDGET_UX.md` or a phase contract) before implementation.
3. **Parallel subagents** implement only their assigned files. Strict ownership: lead owns shared files, Gradle, manifest, resources and integration; agents own disjoint directories and commit small coherent checkpoints.
4. **Serialize** build, review and device work through the lead. No parallel Gradle invocations, no agent-run builds, one device session at a time.
5. **Commit boundaries:** one coherent change per commit (`feat:`, `fix:`, `docs:`, `wip:`). Hard-restart risk means checkpoint early and often; never leave a large uncommitted batch.
6. **Push policy:** lead pushes only green, reviewed states to `origin/main`. No force pushes, no rebases of shared history, no pushes from subagents unless explicitly asked. Backups, APKs and logs stay untracked.

## Low-memory validation

```powershell
.\gradlew --stop
.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease
```

Toolchain stays pinned (Kotlin 1.9.24, KSP 1.9.24-1.0.20, Room 2.6.1, AGP 8.4.2, Gradle 8.7, Compose 1.6.7 / Material 3 1.2.1, JDK 17, compileSdk 34, minSdk 24); no new Gradle dependencies.

## Device checks

Before sending input, check `mWakefulness` with `dumpsys power`. Wake with `input keyevent 26` only when `Asleep`; ask the user to unlock a lockscreen instead of guessing. Verify with `uiautomator dump`, `logcat` and `dumpsys` rather than screenshots, restore any changed device settings, and leave the launcher on a clean HOME.
