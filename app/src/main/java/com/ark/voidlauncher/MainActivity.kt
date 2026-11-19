package com.ark.voidlauncher

import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.backup.BackupManager
import com.ark.voidlauncher.data.backup.BackupPayload
import com.ark.voidlauncher.data.backup.BackedUpBackground
import com.ark.voidlauncher.data.backup.BackedUpItem
import com.ark.voidlauncher.data.background.BackgroundRepository
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.data.migration.DataStoreToRoomMigration
import com.ark.voidlauncher.data.repository.LauncherRepository
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.OrientationMode
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.background.BackgroundKind
import com.ark.voidlauncher.domain.background.BackgroundResolver
import com.ark.voidlauncher.domain.background.BackgroundScope
import com.ark.voidlauncher.domain.background.PageBackground
import com.ark.voidlauncher.domain.background.decodeGradientStops
import com.ark.voidlauncher.domain.background.encodeGradientStops
import com.ark.voidlauncher.domain.placement.PlacementResolver
import com.ark.voidlauncher.domain.placement.PlacementResult
import com.ark.voidlauncher.system.accessibility.AccessibilityBridge
import com.ark.voidlauncher.system.widgets.WidgetEvents
import com.ark.voidlauncher.system.widgets.WidgetHostController
import com.ark.voidlauncher.system.widgets.WidgetHostView
import com.ark.voidlauncher.ui.backup.BackupScreen
import com.ark.voidlauncher.ui.background.BackgroundEditorSheet
import com.ark.voidlauncher.ui.background.PageBackgroundRenderer
import com.ark.voidlauncher.ui.customtheme.CustomThemeEditorSheet
import com.ark.voidlauncher.ui.drawer.AppDrawerOverlay
import com.ark.voidlauncher.ui.editor.AppTypographyEditorSheet
import com.ark.voidlauncher.ui.hud.NavigationHud
import com.ark.voidlauncher.ui.overview.PageOverview
import com.ark.voidlauncher.ui.editor.EdgeChange
import com.ark.voidlauncher.ui.editor.EditorValidation
import com.ark.voidlauncher.ui.editor.ItemEditorSheet
import com.ark.voidlauncher.ui.editor.PageEditorSheet
import com.ark.voidlauncher.ui.settings.SettingsScreen as SettingsSurface
import com.ark.voidlauncher.ui.spatial.SpatialPageCanvas
import com.ark.voidlauncher.ui.theme.ThemePickerSheet
import com.ark.voidlauncher.ui.theme.VoidTheme
import kotlinx.coroutines.launch
import java.util.UUID

private enum class Screen { HOME, DRAWER, SETTINGS, WIDGET_PICKER, BACKUP }
private enum class Editor { NONE, PAGES, ITEMS, TYPOGRAPHY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val prefs = remember { LauncherPrefs(context) }
            val database = remember { VoidDatabase.getInstance(context) }
            val repository = remember { LauncherRepository(database, prefs) }
            val appRepository = remember { AppRepository(context) }
            val widgetController = remember { WidgetHostController(context) }
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "0.0.0"
            }

            val apps = remember { appRepository.installedApps() }
            val pinned by prefs.pinnedApps.collectAsState(initial = emptySet())
            val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val customPalette by prefs.customPalette.collectAsState(initial = CustomPalette.darkDefault())
            val orientationMode by prefs.orientationMode.collectAsState(initial = OrientationMode.PORTRAIT)
            val backgroundRepository = remember { BackgroundRepository(database, context) }
            val backgrounds by backgroundRepository.backgrounds.collectAsState(initial = emptyList())
            val pages by repository.pages.collectAsState(initial = emptyList())
            val edges by repository.edges.collectAsState(initial = emptyList())
            val items by repository.items.collectAsState(initial = emptyList())
            val customizations by repository.customizations.collectAsState(initial = emptyList())
            val homeCoord by repository.homeCoord.collectAsState(initial = Pair(0, 0))
            val homePageId by repository.homePageId.collectAsState(initial = VoidDatabase.ORIGIN_PAGE_ID)

            var currentPageId by remember { mutableLongStateOf(VoidDatabase.ORIGIN_PAGE_ID) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            var editor by remember { mutableStateOf(Editor.NONE) }
            var message by remember { mutableStateOf<String?>(null) }
            var overview by remember { mutableStateOf(false) }
            var wallHit by remember { mutableStateOf<SwipeDirection?>(null) }
            var themePicker by remember { mutableStateOf(false) }
            var customThemeEditor by remember { mutableStateOf(false) }
            var backgroundEditor by remember { mutableStateOf(false) }
            var backgroundDraft by remember { mutableStateOf<PageBackground?>(null) }
            val accessibilityEnabled = remember { AccessibilityBridge.isEnabled(context) }

            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    // Encode P10 backgrounds with portable coordinates and image bytes.
                    val backedUpBackgrounds = backgrounds.mapNotNull { bg ->
                        val page = if (bg.scope == BackgroundScope.PAGE) {
                            pages.firstOrNull { it.pageId == bg.pageId }
                        } else {
                            null
                        }
                        if (bg.scope == BackgroundScope.PAGE && page == null) {
                            return@mapNotNull null
                        }
                        val encodedImage = bg.imageToken?.let { token ->
                            runCatching { backgroundRepository.imageBytes(token) }.getOrNull()
                        }?.takeIf { it.size <= MAX_BACKUP_IMAGE_BYTES }?.let {
                            Base64.encodeToString(it, Base64.DEFAULT)
                        }
                        BackedUpBackground(
                            scope = bg.scope.name,
                            pageCoordX = page?.coordX,
                            pageCoordY = page?.coordY,
                            kind = bg.kind.name,
                            colorHex = bg.colorHex,
                            gradientSpec = bg.gradientStops
                                .takeIf { it.isNotEmpty() }?.let(::encodeGradientStops),
                            gradientAngle = bg.gradientAngle,
                            imageToken = bg.imageToken,
                            imageBase64 = encodedImage,
                        )
                    }
                    val payload = BackupPayload(
                        pages = pages,
                        edges = edges,
                        items = items.mapNotNull { item ->
                            val page = pages.firstOrNull { it.pageId == item.pageId } ?: return@mapNotNull null
                            BackedUpItem(
                                pageCoordX = page.coordX,
                                pageCoordY = page.coordY,
                                itemType = item.itemType,
                                col = item.col,
                                row = item.row,
                                colSpan = item.colSpan,
                                rowSpan = item.rowSpan,
                                referenceId = if (item.itemType == GridItemType.WIDGET) {
                                    item.referenceId.removePrefix("placeholder:")
                                } else {
                                    item.referenceId
                                },
                                allowOverlap = item.allowOverlap,
                            )
                        },
                        customizations = customizations,
                        homeCoordX = homeCoord.first,
                        homeCoordY = homeCoord.second,
                        theme = themeMode.name,
                        orientation = orientationMode.name,
                        customPalette = customPalette,
                        backgrounds = backedUpBackgrounds,
                    )
                    message = runCatching {
                        BackupManager.writeToUri(context, uri, payload)
                        "BACKUP EXPORTED"
                    }.getOrElse { "EXPORT FAILED" }
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val payload = runCatching { BackupManager.readFromUri(context, uri) }
                        .getOrNull()
                    if (payload == null) {
                        message = "INVALID OR UNSUPPORTED BACKUP FILE"
                    } else {
                        val coreRestored = runCatching { repository.restore(payload) }.isSuccess
                        if (!coreRestored) {
                            message = "RESTORE FAILED"
                        } else {
                            // P8/P9 preferences are not touched by LauncherRepository.restore
                            // (it only restores theme). Apply them here with safe defaults.
                            runCatching {
                                val orientation = runCatching {
                                    OrientationMode.valueOf(payload.orientation)
                                }.getOrDefault(OrientationMode.PORTRAIT)
                                prefs.setOrientation(orientation)
                                payload.customPalette?.let { prefs.setCustomPalette(it) }
                            }
                            // P10 backgrounds are restored by fresh page coordinates because
                            // restore() regenerates all Room page ids.
                            val backgroundsRestored = runCatching {
                                restoreBackgrounds(payload.backgrounds, database, backgroundRepository)
                            }.isSuccess
                            message = if (backgroundsRestored) {
                                "BACKUP RESTORED"
                            } else {
                                "BACKUP RESTORED (SOME BACKGROUNDS SKIPPED)"
                            }
                        }
                    }
                }
            }

            val bindLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val id = result.data?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                if (result.resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    scope.launch {
                        placeWidget(
                            repository = repository,
                            widgetController = widgetController,
                            widgetId = id,
                            currentPageId = currentPageId,
                            pages = pages,
                            edges = edges,
                            items = items,
                            onError = { message = it },
                        )
                    }
                } else {
                    widgetController.deleteWidget(id)
                    message = "WIDGET BIND CANCELLED"
                }
            }

            val imageLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val bytes = runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                    if (bytes == null || bytes.isEmpty()) {
                        message = "IMAGE READ FAILED"
                    } else {
                        val token = UUID.randomUUID().toString()
                        val imageBytes = bytes
                        runCatching { backgroundRepository.importImage(token, imageBytes) }
                            .onSuccess {
                                val base = backgroundDraft
                                    ?: PageBackground(
                                        scope = BackgroundScope.PAGE,
                                        pageId = currentPageId,
                                        kind = BackgroundKind.IMAGE,
                                    )
                                backgroundDraft = base.copy(
                                    kind = BackgroundKind.IMAGE,
                                    imageToken = token,
                                )
                                message = "IMAGE READY - SAVE TO APPLY"
                            }
                            .onFailure { message = "IMAGE SAVE FAILED" }
                    }
                }
            }

            // A page-specific background wins; otherwise the global one is used.
            val effectiveBackground = remember(backgrounds, currentPageId) {
                BackgroundResolver.resolve(currentPageId, backgrounds)
            }
            val backgroundImage by produceState<ImageBitmap?>(
                initialValue = null,
                effectiveBackground?.imageToken,
            ) {
                val token = effectiveBackground?.imageToken
                value = if (token == null) {
                    null
                } else {
                    runCatching {
                        backgroundRepository.imageBytes(token)?.let { bytes ->
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            }

            // Collect the flow directly rather than reacting to collectAsState's
            // initial value: on an activity recreation that initial PORTRAIT would be
            // applied before the persisted value arrives, flipping the rotation back
            // and recreating the activity again in a loop. DataStore replays the
            // current value immediately, so this applies exactly the saved mode.
            LaunchedEffect(Unit) {
                prefs.orientationMode.collect { mode ->
                    requestedOrientation = when (mode) {
                        OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }

            LaunchedEffect(Unit) {
                DataStoreToRoomMigration(
                    context = context,
                    database = database,
                    dataStore = prefs.dataStore,
                ).migrate()
            }

            LaunchedEffect(homePageId) { currentPageId = homePageId }

            DisposableEffect(widgetController) {
                widgetController.startListening()
                onDispose { widgetController.stopListening() }
            }

            LaunchedEffect(widgetController) {
                WidgetEvents.deletedIds.collect { id ->
                    repository.removeWidget(id)
                    widgetController.deleteWidget(id)
                }
            }

            BackHandler(
                enabled = themePicker || customThemeEditor || backgroundEditor || overview ||
                    screen != Screen.HOME || editor != Editor.NONE,
            ) {
                when {
                    customThemeEditor -> customThemeEditor = false
                    backgroundEditor -> {
                        backgroundEditor = false
                        backgroundDraft = null
                    }
                    themePicker -> themePicker = false
                    overview -> overview = false
                    editor != Editor.NONE -> editor = Editor.NONE
                    screen != Screen.HOME -> screen = Screen.HOME
                }
            }

            fun openWidgetPicker() {
                screen = Screen.WIDGET_PICKER
            }

            fun beginAddWidget(provider: ComponentName) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    message = "WIDGETS REQUIRE ANDROID 8 OR NEWER"
                    return
                }
                val id = widgetController.allocateAppWidgetId()
                if (widgetController.bindAllowed(id, provider)) {
                    scope.launch {
                        placeWidget(
                            repository = repository,
                            widgetController = widgetController,
                            widgetId = id,
                            currentPageId = currentPageId,
                            pages = pages,
                            edges = edges,
                            items = items,
                            onError = { message = it },
                        )
                    }
                } else {
                    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                    bindLauncher.launch(intent)
                }
            }

            VoidTheme(themeMode, customPalette) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.HOME -> HomeScreen(
                            pages = pages,
                            edges = edges,
                            items = items,
                            currentPageId = currentPageId,
                            homePageId = homePageId,
                            widgetController = widgetController,
                            onPageChanged = { currentPageId = it },
                            onHomeRequested = { currentPageId = homePageId },
                            onAppClick = { appRepository.launch(it) },
                            onOpenDrawer = { screen = Screen.DRAWER },
                            onOpenSettings = { screen = Screen.SETTINGS },
                            onEditItems = { editor = Editor.ITEMS },
                            onLongPress = { overview = true },
                            onWallHit = { wallHit = it },
                            wallHit = wallHit,
                            onWallHitCleared = { wallHit = null },
                            onOpenOverview = { overview = true },
                            pageBackground = {
                                PageBackgroundRenderer(
                                    background = effectiveBackground,
                                    image = backgroundImage,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                        )

                        Screen.DRAWER -> AppDrawerOverlay(
                            apps = apps,
                            pinned = pinned,
                            favorites = customizations.filter { it.isFavorite }
                                .map { it.packageName }.toSet(),
                            hidden = customizations.filter { it.isHidden }
                                .map { it.packageName }.toSet(),
                            onBack = { screen = Screen.HOME },
                            onLaunch = { appRepository.launch(it) },
                            onTogglePin = { scope.launch { prefs.togglePinned(it) } },
                            onCustomize = { editor = Editor.TYPOGRAPHY },
                        )

                        Screen.SETTINGS -> SettingsSurface(
                            themeMode = themeMode,
                            versionName = versionName,
                            accessibilityEnabled = accessibilityEnabled,
                            onBack = { screen = Screen.HOME },
                            onThemeChange = { scope.launch { prefs.setTheme(it) } },
                            onEditPages = { editor = Editor.PAGES },
                            onEditItems = { editor = Editor.ITEMS },
                            onEditTypography = { editor = Editor.TYPOGRAPHY },
                            onAddWidget = ::openWidgetPicker,
                            onExport = { exportLauncher.launch("voidlauncher-backup.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json")) },
                            onAccessibilityToggle = { enabled ->
                                if (enabled) AccessibilityBridge.openSettings(context)
                            },
                            orientationMode = orientationMode,
                            onOrientationChange = { scope.launch { prefs.setOrientation(it) } },
                            onEditCustomTheme = { customThemeEditor = true },
                            onEditBackgrounds = {
                                backgroundDraft = effectiveBackground
                                backgroundEditor = true
                            },
                        )

                        Screen.BACKUP -> BackupScreen(
                            status = message,
                            onExport = { exportLauncher.launch("voidlauncher-backup.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json")) },
                            onBack = { screen = Screen.SETTINGS },
                        )

                        Screen.WIDGET_PICKER -> WidgetPickerScreen(
                            widgetController = widgetController,
                            onPick = ::beginAddWidget,
                            onBack = { screen = Screen.SETTINGS },
                        )
                    }

                    if (overview) {
                        PageOverview(
                            pages = pages,
                            edges = edges,
                            items = items,
                            currentPageId = currentPageId,
                            homePageId = homePageId,
                            onSelectPage = { id ->
                                currentPageId = id
                                overview = false
                            },
                            onClose = { overview = false },
                            onThemes = { themePicker = true },
                            onSettings = {
                                overview = false
                                screen = Screen.SETTINGS
                            },
                            onWidgets = {
                                overview = false
                                screen = Screen.WIDGET_PICKER
                            },
                            onPageManagement = {
                                overview = false
                                editor = Editor.PAGES
                            },
                        )
                    }

                    if (message != null) {
                        Text(
                            text = message.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .clickable { message = null },
                        )
                    }
                }

                val currentPage = pages.firstOrNull { it.pageId == currentPageId }
                when (editor) {
                    Editor.PAGES -> PageEditorSheet(
                        pages = pages,
                        edges = edges,
                        homePageId = homePageId,
                        onAddPage = { x, y ->
                            scope.launch {
                                val id = repository.addPage(x, y)
                                if (id == null) message = "PAGE ALREADY EXISTS OR OUT OF RANGE"
                            }
                        },
                        onDeletePage = { id -> scope.launch { repository.deletePage(id) } },
                        onSetEdge = { from, direction, to ->
                            val edge = EdgeInfo(from, direction, to)
                            if (EditorValidation.edgeChangeIsSafe(
                                    pages, edges, homePageId, EdgeChange.SetEdge(edge),
                                )
                            ) {
                                scope.launch { repository.setEdge(from, direction, to) }
                            } else {
                                message = "THAT WOULD STRAND A PAGE"
                            }
                        },
                        onRemoveEdge = { from, direction ->
                            if (EditorValidation.edgeChangeIsSafe(
                                    pages, edges, homePageId, EdgeChange.RemoveEdge(from, direction),
                                )
                            ) {
                                scope.launch { repository.removeEdge(from, direction) }
                            } else {
                                message = "THAT WOULD STRAND A PAGE"
                            }
                        },
                        onSetHomePage = { id ->
                            val page = pages.firstOrNull { it.pageId == id }
                            if (page != null) {
                                scope.launch { repository.setHomeCoord(page.coordX, page.coordY) }
                            }
                        },
                        onDismiss = { editor = Editor.NONE },
                    )

                    Editor.ITEMS -> if (currentPage != null) {
                        ItemEditorSheet(
                            page = currentPage,
                            items = items.filter { it.pageId == currentPage.pageId },
                            apps = apps,
                            onAddShortcut = { packageName, col, row ->
                                scope.launch {
                                    val candidate = GridItemEntity(
                                        pageId = currentPage.pageId,
                                        itemType = GridItemType.APP_SHORTCUT,
                                        col = col,
                                        row = row,
                                        referenceId = packageName,
                                    )
                                    val placed = resolvePlacement(candidate, currentPage, pages, edges, items)
                                    if (placed != null) {
                                        repository.addShortcut(
                                            packageName = packageName,
                                            pageId = placed.first,
                                            col = placed.second.col,
                                            row = placed.second.row,
                                        )
                                    } else {
                                        message = "NO FREE CELL ON THIS PAGE OR ITS NEIGHBOR"
                                    }
                                }
                            },
                            onAddWidget = ::openWidgetPicker,
                            onMove = { id, col, row -> scope.launch { repository.moveItem(id, col, row) } },
                            onResize = { id, cols, rows ->
                                scope.launch { repository.resizeItem(id, cols, rows) }
                            },
                            onRemove = { id ->
                                scope.launch {
                                    val item = items.firstOrNull { it.itemId == id }
                                    if (item?.itemType == GridItemType.WIDGET) {
                                        item.referenceId.toIntOrNull()?.let {
                                            widgetController.deleteWidget(it)
                                        }
                                    }
                                    repository.removeItem(id)
                                }
                            },
                            onDismiss = { editor = Editor.NONE },
                        )
                    }

                    Editor.TYPOGRAPHY -> AppTypographyEditorSheet(
                        customizations = customizations,
                        apps = apps,
                        onCustomize = { scope.launch { repository.customizeApp(it) } },
                        onDismiss = { editor = Editor.NONE },
                    )

                    Editor.NONE -> Unit
                }

                if (themePicker) {
                    ThemePickerSheet(
                        current = themeMode,
                        onSelect = { mode ->
                            scope.launch {
                                prefs.setTheme(mode)
                                if (mode == ThemeMode.CUSTOM) {
                                    customThemeEditor = true
                                }
                            }
                            themePicker = false
                        },
                        onEditCustom = {
                            themePicker = false
                            customThemeEditor = true
                        },
                        onDismiss = { themePicker = false },
                    )
                }

                if (customThemeEditor) {
                    CustomThemeEditorSheet(
                        palette = customPalette,
                        isDarkBase = themeMode != ThemeMode.LIGHT,
                        onSave = { palette ->
                            scope.launch { prefs.setCustomPalette(palette) }
                            customThemeEditor = false
                        },
                        onDismiss = { customThemeEditor = false },
                    )
                }

                if (backgroundEditor) {
                    val draft = backgroundDraft
                    val draftImage by produceState<ImageBitmap?>(
                        initialValue = null,
                        draft?.imageToken,
                    ) {
                        val token = draft?.imageToken
                        value = if (token == null) {
                            null
                        } else {
                            runCatching {
                                backgroundRepository.imageBytes(token)?.let { bytes ->
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                }
                            }.getOrNull()
                        }
                    }
                    BackgroundEditorSheet(
                        current = draft,
                        pageTitle = pages.firstOrNull { it.pageId == currentPageId }?.title ?: "HOME",
                        isGlobal = draft?.scope != BackgroundScope.PAGE,
                        onSave = { edited ->
                            scope.launch {
                                runCatching { backgroundRepository.save(edited) }
                                    .onSuccess { message = "BACKGROUND SAVED" }
                                    .onFailure { message = "BACKGROUND SAVE FAILED" }
                            }
                            backgroundEditor = false
                            backgroundDraft = null
                        },
                        onDelete = {
                            scope.launch {
                                draft?.id?.let { backgroundRepository.remove(it) }
                                draft?.imageToken?.let { backgroundRepository.deleteImage(it) }
                            }
                            backgroundEditor = false
                            backgroundDraft = null
                        },
                        onPickImage = { imageLauncher.launch(arrayOf("image/*")) },
                        onDismiss = {
                            backgroundEditor = false
                            backgroundDraft = null
                        },
                        pageId = currentPageId,
                        image = draftImage,
                    )
                }
            }
        }
    }
}

/** Guard against huge base64 payloads that would bloat a backup file. */
private const val MAX_BACKUP_IMAGE_BYTES = 2 * 1024 * 1024

/**
 * Restores P10 backgrounds after the core restore has regenerated all page ids.
 * Page-scoped records are re-linked through their coordinates, matching how
 * [BackedUpItem] survives a restore. Image bytes are re-imported into internal
 * storage under their original stable token.
 */
private suspend fun restoreBackgrounds(
    entries: List<BackedUpBackground>,
    database: VoidDatabase,
    repository: BackgroundRepository,
) {
    if (entries.isEmpty()) return
    val restored = entries.mapNotNull { entry ->
        val scope = runCatching { BackgroundScope.valueOf(entry.scope) }.getOrNull()
            ?: return@mapNotNull null
        val kind = runCatching { BackgroundKind.valueOf(entry.kind) }.getOrNull()
            ?: return@mapNotNull null
        val pageId = if (scope == BackgroundScope.PAGE) {
            val x = entry.pageCoordX ?: return@mapNotNull null
            val y = entry.pageCoordY ?: return@mapNotNull null
            database.pageDao().getByCoordOnce(x, y)?.pageId ?: return@mapNotNull null
        } else {
            null
        }
        PageBackground(
            scope = scope,
            pageId = pageId,
            kind = kind,
            colorHex = entry.colorHex,
            gradientStops = entry.gradientSpec?.let(::decodeGradientStops) ?: emptyList(),
            gradientAngle = entry.gradientAngle,
            imageToken = entry.imageToken,
        )
    }
    repository.importAll(restored)
    entries.forEach { entry ->
        val token = entry.imageToken ?: return@forEach
        val encoded = entry.imageBase64 ?: return@forEach
        runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            repository.importImage(token, bytes)
        }
    }
}

/** Applies the one-hop overflow rule using the directed edge table. */
private fun resolvePlacement(
    candidate: GridItemEntity,
    page: PageInfo,
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
): Pair<Long, GridItemEntity>? {
    val onPage = items.filter { it.pageId == page.pageId }
    return when (val result = PlacementResolver.resolve(candidate, onPage, page.gridCols, page.gridRows)) {
        is PlacementResult.Ok -> page.pageId to candidate
        is PlacementResult.MoveToNeighbor -> {
            val neighborId = edges.firstOrNull {
                it.fromPageId == page.pageId && it.swipeDirection == result.direction
            }?.toPageId ?: return null
            val neighborPage = pages.firstOrNull { it.pageId == neighborId } ?: return null
            val moved = candidate.copy(pageId = neighborId, col = 0)
            val neighborItems = items.filter { it.pageId == neighborId }
            if (PlacementResolver.resolve(
                    moved, neighborItems, neighborPage.gridCols, neighborPage.gridRows,
                ) is PlacementResult.Ok
            ) {
                neighborId to moved
            } else {
                null
            }
        }
        is PlacementResult.Rejected -> null
    }
}

private suspend fun placeWidget(
    repository: LauncherRepository,
    widgetController: WidgetHostController,
    widgetId: Int,
    currentPageId: Long,
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    onError: (String) -> Unit,
) {
    val page = pages.firstOrNull { it.pageId == currentPageId }
    if (page == null) {
        onError("NO PAGE TO PLACE WIDGET")
        return
    }
    val candidate = GridItemEntity(
        pageId = page.pageId,
        itemType = GridItemType.WIDGET,
        col = 0,
        row = 0,
        colSpan = 4,
        rowSpan = 2,
        referenceId = widgetId.toString(),
        allowOverlap = true,
    )
    val placed = resolvePlacement(candidate, page, pages, edges, items)
    if (placed == null) {
        widgetController.deleteWidget(widgetId)
        onError("NO FREE CELL FOR WIDGET")
        return
    }
    repository.addWidget(
        appWidgetId = widgetId,
        pageId = placed.first,
        col = placed.second.col,
        row = placed.second.row,
        colSpan = placed.second.colSpan,
        rowSpan = placed.second.rowSpan,
    )
}

@Composable
private fun HomeScreen(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    currentPageId: Long,
    homePageId: Long,
    widgetController: WidgetHostController,
    onPageChanged: (Long) -> Unit,
    onHomeRequested: () -> Unit,
    onAppClick: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditItems: () -> Unit,
    onLongPress: () -> Unit = {},
    onWallHit: (SwipeDirection) -> Unit = {},
    wallHit: SwipeDirection? = null,
    onWallHitCleared: () -> Unit = {},
    onOpenOverview: () -> Unit = {},
    pageBackground: @Composable () -> Unit = {},
) {
    val page = pages.firstOrNull { it.pageId == currentPageId } ?: return
    val pageItems = items.filter { it.pageId == currentPageId }

    Box(modifier = Modifier.fillMaxSize()) {
        SpatialPageCanvas(
            pages = pages,
            edges = edges,
            items = items,
            currentPageId = currentPageId,
            homePageId = homePageId,
            onPageChanged = onPageChanged,
            onHomeRequested = onHomeRequested,
            onAppClick = onAppClick,
            onLongPress = onLongPress,
            onWallHit = onWallHit,
            widgetContent = { item ->
                val widgetId = item.referenceId.toIntOrNull()
                if (widgetId != null) {
                    WidgetHostView(
                        controller = widgetController,
                        appWidgetId = widgetId,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("WIDGET NEEDS REBIND", style = MaterialTheme.typography.labelSmall)
                }
            },
            pageBackground = pageBackground,
            modifier = Modifier.fillMaxSize(),
        )

        Text(
            text = page.title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(20.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text("APPS", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onOpenDrawer).padding(8.dp))
            Text("EDIT", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onEditItems).padding(8.dp))
            Text("SETTINGS", style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(8.dp))
        }

        if (pageItems.isEmpty()) {
            Text(
                text = "SWIPE TO MOVE · DOUBLE-TAP FOR HOME\nLONG-PRESS FOR MAP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        NavigationHud(
            page = page,
            edges = edges,
            wallHit = wallHit,
            onWallHitCleared = onWallHitCleared,
            onOpenOverview = onOpenOverview,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun WidgetPickerScreen(
    widgetController: WidgetHostController,
    onPick: (ComponentName) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val providers = remember { widgetController.installedProviders() }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("BACK", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        androidx.compose.foundation.layout.Spacer(
            Modifier.height(20.dp)
        )
        Text("CHOOSE A WIDGET", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
        if (providers.isEmpty()) {
            Text("No widget providers available.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(providers, key = { it.provider.flattenToString() }) { provider ->
                    val label = provider.loadLabel(context.packageManager).toString()
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onPick(provider.provider) }
                            .padding(vertical = 16.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                }
            }
        }
    }
}
