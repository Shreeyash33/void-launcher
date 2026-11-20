# Workspace and revision workflow

## Workspace boundary

The only project workspace is:

`D:\projects\Void Launcher`

All new files, scratch copies, archives, and subdirectories must stay inside that
folder. Do not create git worktrees, clones, or "temporary" project folders
outside it. If a copy is needed, put it in `backups\` and remove it when it is no
longer needed.

## Backup before every refactor or revision

Before changing source, configuration, or docs:

1. Stop Gradle if a build may be running:
   ```powershell
   .\gradlew --stop
   ```
2. Create a timestamped source backup:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\scripts\create-backup.ps1
   ```
3. Confirm exactly one fresh, non-zero archive exists in `backups\`, named
   `void-launcher-source-<yyyyMMdd-HHmmss>.zip`.
4. Only then edit or recreate the project in place under the workspace root.

`scripts\create-backup.ps1` writes only to `backups\` and excludes:

- `.git\`
- `.gradle\`
- root `build\`
- `app\build\`
- `backups\` itself

`backups\` is gitignored so archives never enter the repository. If an old or
corrupt archive is found, remove only that archive and recreate it inside
`backups\`.

## Restoring a backup

1. Pick the desired `backups\void-launcher-source-*.zip`.
2. Extract it **into `D:\projects\Void Launcher`**, preserving relative paths.
3. Verify that `app\src\main\java\com\ark\voidlauncher\MainActivity.kt`,
   `docs\`, and `gradlew` exist at the expected locations.
4. Continue work in the same root. Never extract to a sibling folder.

## Low-memory validation

The laptop recently crashed, so keep builds serialized and low-memory:

```powershell
.\gradlew --stop
.\gradlew --no-daemon --max-workers=1 testDebugUnitTest assembleDebug assembleRelease
```

Do not run parallel builds or spawn extra Gradle daemons.

## Device checks

Before sending input to a connected device, check `mWakefulness`. Wake with
`input keyevent 26` only when the device reports `Asleep`. If a lockscreen is
shown, ask the user to unlock it instead of guessing a PIN or pattern.
