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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
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
import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.BuiltInWidgets
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.OrientationMode
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageType
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
import com.ark.voidlauncher.ui.overview.PageOverview
import com.ark.voidlauncher.ui.editor.EdgeChange
import com.ark.voidlauncher.ui.editor.EditorValidation
import com.ark.voidlauncher.ui.editor.ItemEditorSheet
import com.ark.voidlauncher.ui.editor.PageEditorSheet
import com.ark.voidlauncher.ui.grid.GridGeometry
import com.ark.voidlauncher.ui.grid.ItemResizeDialog
import com.ark.voidlauncher.ui.settings.SettingsScreen as SettingsSurface
import com.ark.voidlauncher.ui.spatial.SpatialPageCanvas
import com.ark.voidlauncher.ui.theme.GlassPanel
import com.ark.voidlauncher.ui.theme.ThemePickerSheet
import com.ark.voidlauncher.ui.theme.ThemedSystemBars
import com.ark.voidlauncher.ui.theme.VoidTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
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
            val showCoordinateDetail by prefs.showCoordinateDetail.collectAsState(initial = true)
            val backgroundRepository = remember { BackgroundRepository(database, context) }
            val backgrounds by backgroundRepository.backgrounds.collectAsState(initial = emptyList())
            val pages by repository.pages.collectAsState(initial = emptyList())
            val edges by repository.edges.collectAsState(initial = emptyList())
            val items by repository.items.collectAsState(initial = emptyList())
            val customizations by repository.customizations.collectAsState(initial = emptyList())
            val homeCoord by repository.homeCoord.collectAsState(initial = Pair(0, 0))
            val homePageId by repository.homePageId.collectAsState(initial = VoidDatabase.ORIGIN_PAGE_ID)
            val appDrawerPageId by repository.appDrawerPageId.collectAsState(initial = null)

            var currentPageId by remember { mutableLongStateOf(VoidDatabase.ORIGIN_PAGE_ID) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            var editor by remember { mutableStateOf(Editor.NONE) }
            var canvasEditing by remember { mutableStateOf(false) }
            var selectedCanvasItemId by remember { mutableStateOf<Long?>(null) }
            var resizeItemId by remember { mutableStateOf<Long?>(null) }
            var pendingWidgetKind by remember { mutableStateOf<BuiltInWidgetKind?>(null) }
            var floatingPoint by remember { mutableStateOf<Offset?>(null) }
            var floatingPointerId by remember { mutableStateOf<Long?>(null) }
            var rootSize by remember { mutableStateOf(IntSize.Zero) }
            var appPickerItemId by remember { mutableStateOf<Long?>(null) }
            var message by remember { mutableStateOf<String?>(null) }
            var overview by remember { mutableStateOf(false) }
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
                                referenceId = if (
                                    item.itemType == GridItemType.WIDGET &&
                                    item.widgetKind == null
                                ) {
                                    item.referenceId.removePrefix("placeholder:")
                                } else {
                                    item.referenceId
                                },
                                allowOverlap = item.allowOverlap,
                                widgetKind = item.widgetKind,
                                appListPackages = item.appListPackages,
                            )
                        },
                        customizations = customizations,
                        homeCoordX = homeCoord.first,
                        homeCoordY = homeCoord.second,
                        theme = themeMode.name,
                        orientation = orientationMode.name,
                        customPalette = customPalette,
                        backgrounds = backedUpBackgrounds,
                        showCoordinateDetail = showCoordinateDetail,
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
                                prefs.setShowCoordinateDetail(payload.showCoordinateDetail)
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

            LaunchedEffect(currentPageId) {
                canvasEditing = false
                selectedCanvasItemId = null
                resizeItemId = null
                pendingWidgetKind = null
                appPickerItemId = null
            }

            // Back, page changes, and successful drops all clear the pending
            // widget; that also ends any in-flight pointer tracking.
            LaunchedEffect(pendingWidgetKind) {
                if (pendingWidgetKind == null) {
                    floatingPointerId = null
                    floatingPoint = null
                }
            }

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
                    resizeItemId != null || pendingWidgetKind != null ||
                    appPickerItemId != null || canvasEditing ||
                    screen != Screen.HOME || editor != Editor.NONE,
            ) {
                when {
                    pendingWidgetKind != null -> {
                        pendingWidgetKind = null
                        canvasEditing = false
                    }
                    appPickerItemId != null -> appPickerItemId = null
                    resizeItemId != null -> resizeItemId = null
                    customThemeEditor -> customThemeEditor = false
                    backgroundEditor -> {
                        backgroundEditor = false
                        backgroundDraft = null
                    }
                    themePicker -> themePicker = false
                    overview -> overview = false
                    canvasEditing -> {
                        canvasEditing = false
                        selectedCanvasItemId = null
                    }
                    editor != Editor.NONE -> editor = Editor.NONE
                    screen != Screen.HOME -> screen = Screen.HOME
                }
            }

            fun exitCanvasEditor() {
                canvasEditing = false
                selectedCanvasItemId = null
                resizeItemId = null
            }

            fun selectCanvasItem(item: GridItemEntity) {
                canvasEditing = true
                selectedCanvasItemId = item.itemId
            }

            fun commitCanvasPlacement(
                item: GridItemEntity,
                col: Int,
                row: Int,
                colSpan: Int = item.colSpan,
                rowSpan: Int = item.rowSpan,
            ): Boolean {
                val page = pages.firstOrNull { it.pageId == item.pageId }
                if (page == null) {
                    message = "ITEM PAGE NOT FOUND"
                    return false
                }
                val candidate = item.copy(
                    pageId = page.pageId,
                    col = col,
                    row = row,
                    colSpan = colSpan,
                    rowSpan = rowSpan,
                )
                val placed = resolvePlacement(candidate, page, pages, edges, items)
                if (placed == null) {
                    message = "MOVE BLOCKED BY GRID RULES"
                    return false
                }
                scope.launch {
                    repository.placeItem(
                        itemId = item.itemId,
                        pageId = placed.first,
                        col = placed.second.col,
                        row = placed.second.row,
                        colSpan = colSpan,
                        rowSpan = rowSpan,
                    )
                    if (placed.first != item.pageId) {
                        canvasEditing = false
                        selectedCanvasItemId = null
                        resizeItemId = null
                        currentPageId = placed.first
                    }
                }
                return true
            }

            fun removeCanvasItem(item: GridItemEntity) {
                scope.launch {
                    if (item.itemType == GridItemType.WIDGET) {
                        item.referenceId.toIntOrNull()?.let { widgetController.deleteWidget(it) }
                    }
                    repository.removeItem(item.itemId)
                    selectedCanvasItemId = null
                    resizeItemId = null
                }
            }

            /** Commits a floating built-in widget. Failure leaves it on the finger. */
            fun dropPendingWidget(col: Int, row: Int): Boolean {
                val kind = pendingWidgetKind ?: return false
                val page = pages.firstOrNull { it.pageId == currentPageId }
                if (page == null || page.pageType == PageType.APP_DRAWER) {
                    message = "WIDGETS CANNOT BE PLACED HERE"
                    return false
                }
                val candidate = GridItemEntity(
                    pageId = page.pageId,
                    itemType = GridItemType.WIDGET,
                    col = col,
                    row = row,
                    referenceId = BuiltInWidgets.reference(kind),
                    allowOverlap = true,
                    widgetKind = kind,
                )
                val placed = resolvePlacement(candidate, page, pages, edges, items)
                if (placed == null) {
                    message = "DROP BLOCKED BY GRID RULES"
                    return false
                }
                // Clear the pending state synchronously so a rapid next touch
                // cannot be mistaken for a retry of the widget just dropped.
                pendingWidgetKind = null
                canvasEditing = false
                selectedCanvasItemId = null
                floatingPointerId = null
                floatingPoint = null
                scope.launch {
                    repository.addBuiltInWidget(
                        kind = kind,
                        pageId = placed.first,
                        col = placed.second.col,
                        row = placed.second.row,
                    )
                }
                return true
            }

            // Converts a root-level pointer position into the page's lattice.
            // The root box and the page grid share the same origin and extent.
            val dropFloatingAt = rememberUpdatedState { point: Offset ->
                val page = pages.firstOrNull { it.pageId == currentPageId }
                if (
                    page == null || page.gridCols <= 0 || page.gridRows <= 0 ||
                    rootSize.width <= 0 || rootSize.height <= 0
                ) {
                    message = "WIDGETS CANNOT BE PLACED HERE"
                    false
                } else {
                    val cellWidthPx = (rootSize.width.toFloat() / page.gridCols)
                        .roundToInt().coerceAtLeast(1)
                    val cellHeightPx = (rootSize.height.toFloat() / page.gridRows)
                        .roundToInt().coerceAtLeast(1)
                    val col = (point.x / cellWidthPx - 0.5f).roundToInt()
                    val row = (point.y / cellHeightPx - 0.5f).roundToInt()
                    dropPendingWidget(col, row)
                }
            }

            fun resizeCanvasItem(geometry: GridGeometry): Boolean {
                val item = items.firstOrNull { it.itemId == selectedCanvasItemId } ?: return false
                return commitCanvasPlacement(
                    item = item,
                    col = geometry.col,
                    row = geometry.row,
                    colSpan = geometry.spanX,
                    rowSpan = geometry.spanY,
                )
            }

            fun toggleAppInPicker(packageName: String) {
                val item = items.firstOrNull { it.itemId == appPickerItemId } ?: return
                val current = BuiltInWidgets.decodePackages(item.appListPackages).toMutableSet()
                if (!current.add(packageName)) current.remove(packageName)
                scope.launch { repository.setAppListPackages(item.itemId, current.toList()) }
            }

            fun openWidgetPicker() {
                screen = Screen.WIDGET_PICKER
            }

            fun acceptBuiltInWidget(kind: BuiltInWidgetKind) {
                val page = pages.firstOrNull { it.pageId == currentPageId }
                if (page == null || page.pageType == PageType.APP_DRAWER) {
                    message = "WIDGETS CANNOT BE PLACED HERE"
                    return
                }
                pendingWidgetKind = kind
                if (floatingPoint == null) {
                    floatingPoint = Offset(rootSize.width / 2f, rootSize.height / 2f)
                }
                overview = false
                screen = Screen.HOME
                canvasEditing = true
                selectedCanvasItemId = null
                resizeItemId = null
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

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM, ThemeMode.CUSTOM -> systemDark
            }
            VoidTheme(themeMode, customPalette) {
                ThemedSystemBars(darkTheme)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .onGloballyPositioned { rootSize = it.size }
                        .pointerInput(Unit) {
                            // The picker's long press swaps screens while the
                            // finger is still down. A root-level observer keeps
                            // that same pointer stream alive across the swap,
                            // so the preview follows the finger instead of
                            // waiting for a fresh drag on the new page.
                            awaitPointerEventScope {
                                val active = mutableMapOf<Long, Offset>()
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    var released: Pair<Long, Offset>? = null
                                    for (change in event.changes) {
                                        val id = change.id.value
                                        if (change.pressed) {
                                            active[id] = change.position
                                        } else {
                                            active.remove(id)
                                            released = id to change.position
                                        }
                                    }

                                    if (pendingWidgetKind == null) {
                                        floatingPointerId = null
                                        floatingPoint = null
                                        continue
                                    }

                                    val trackingId = floatingPointerId
                                    if (trackingId == null) {
                                        val entry = active.entries.lastOrNull()
                                        if (entry != null) {
                                            floatingPointerId = entry.key
                                            floatingPoint = entry.value
                                        } else if (released != null) {
                                            // Long-press then immediate lift: the
                                            // picker closed on the same event.
                                            floatingPoint = released.second
                                            dropFloatingAt.value(released.second)
                                        }
                                    } else {
                                        val point = active[trackingId]
                                        if (point != null) {
                                            floatingPoint = point
                                        } else {
                                            val finalPoint = released
                                                ?.takeIf { it.first == trackingId }
                                                ?.second
                                                ?: floatingPoint
                                            floatingPointerId = null
                                            floatingPoint = finalPoint
                                            if (finalPoint != null) dropFloatingAt.value(finalPoint)
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    when (screen) {
                        Screen.HOME -> {
                            val currentPage = pages.firstOrNull { it.pageId == currentPageId }
                            if (currentPage?.pageType == PageType.APP_DRAWER) {
                                AppDrawerPage(
                                    apps = apps,
                                    pinned = pinned,
                                    favorites = customizations.filter { it.isFavorite }
                                        .map { it.packageName }.toSet(),
                                    hidden = customizations.filter { it.isHidden }
                                        .map { it.packageName }.toSet(),
                                    coordX = currentPage.coordX,
                                    coordY = currentPage.coordY,
                                    showCoordinateDetail = showCoordinateDetail,
                                    onBack = { currentPageId = homePageId },
                                    onLaunch = { appRepository.launch(it) },
                                    onTogglePin = { scope.launch { prefs.togglePinned(it) } },
                                    onCustomize = { editor = Editor.TYPOGRAPHY },
                                )
                            } else {
                                HomeScreen(
                                    pages = pages,
                                    edges = edges,
                                    items = items,
                                    currentPageId = currentPageId,
                                    widgetController = widgetController,
                                    onPageChanged = { currentPageId = it },
                                    onHomeRequested = { currentPageId = homePageId },
                                    onAppClick = { appRepository.launch(it) },
                                    onOpenDrawerPage = {
                                        exitCanvasEditor()
                                        appDrawerPageId?.let { currentPageId = it }
                                    },
                                    onOpenSettings = {
                                        exitCanvasEditor()
                                        screen = Screen.SETTINGS
                                    },
                                    onEditItems = {
                                        exitCanvasEditor()
                                        editor = Editor.ITEMS
                                    },
                                    onOpenWidgetPicker = {
                                        exitCanvasEditor()
                                        overview = true
                                    },
                                    onLongPress = { overview = true },
                                    showCoordinateDetail = showCoordinateDetail,
                                    canvasEditing = canvasEditing,
                                    selectedCanvasItemId = selectedCanvasItemId,
                                    onSelectCanvasItem = ::selectCanvasItem,
                                    onMoveCanvasItem = { item, col, row ->
                                        commitCanvasPlacement(item, col, row)
                                    },
                                    onResizeCanvasItem = { resizeItemId = it.itemId },
                                    onResizeCanvasGeometry = ::resizeCanvasItem,
                                    onWidgetAction = { item ->
                                        if (
                                            item.widgetKind == BuiltInWidgetKind.APP_LIST ||
                                            BuiltInWidgets.fromReference(item.referenceId) ==
                                            BuiltInWidgetKind.APP_LIST
                                        ) {
                                            appPickerItemId = item.itemId
                                        }
                                    },
                                    apps = apps,
                                    onRemoveCanvasItem = ::removeCanvasItem,
                                    onExitCanvasEditor = ::exitCanvasEditor,
                                    floatingKind = pendingWidgetKind,
                                    floatingPoint = floatingPoint,
                                    pageBackground = {
                                        PageBackgroundRenderer(
                                            background = effectiveBackground,
                                            image = backgroundImage,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    },
                                )
                            }
                        }

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
                            onOrientationChange = { mode ->
                                // Apply immediately on the live activity. Relying only on the
                                // DataStore flow means the value can arrive during the
                                // configuration change that rotation itself triggers, and the
                                // assignment is then ignored on the outgoing instance.
                                requestedOrientation = when (mode) {
                                    OrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    OrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    OrientationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                                scope.launch { prefs.setOrientation(mode) }
                            },
                            onEditCustomTheme = { customThemeEditor = true },
                            onEditBackgrounds = {
                                backgroundDraft = effectiveBackground
                                backgroundEditor = true
                            },
                            showCoordinateDetail = showCoordinateDetail,
                            onShowCoordinateDetailChange = { show ->
                                scope.launch { prefs.setShowCoordinateDetail(show) }
                            },
                        )

                        Screen.BACKUP -> BackupScreen(
                            status = message,
                            onExport = { exportLauncher.launch("voidlauncher-backup.json") },
                            onImport = { importLauncher.launch(arrayOf("application/json")) },
                            onBack = { screen = Screen.SETTINGS },
                        )

                        Screen.WIDGET_PICKER -> BuiltInWidgetPickerScreen(
                            onLongPress = ::acceptBuiltInWidget,
                            onBack = { screen = Screen.HOME },
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
                            onAddPage = { x, y ->
                                scope.launch {
                                    val id = repository.addPage(x, y)
                                    // Adding a page keeps the map open so several pages can
                                    // be laid out in one pass. HERE only changes when the
                                    // user explicitly selects a page.
                                    if (id == null) {
                                        message = "PAGE ALREADY EXISTS OR OUT OF RANGE"
                                    }
                                }
                            },
                            onToggleEdge = { childId, parentId, direction, enable ->
                                val change = if (enable) {
                                    EdgeChange.SetEdge(EdgeInfo(childId, direction, parentId))
                                } else {
                                    EdgeChange.RemoveEdge(childId, direction)
                                }
                                if (!EditorValidation.edgeChangeStrandsAdditionalPages(
                                        pages, edges, homePageId, change,
                                    )
                                ) {
                                    scope.launch {
                                        if (enable) {
                                            repository.setEdge(childId, direction, parentId)
                                        } else {
                                            repository.removeEdge(childId, direction)
                                        }
                                    }
                                } else {
                                    message = "THAT WOULD STRAND A PAGE"
                                }
                            },
                            showCoordinateDetail = showCoordinateDetail,
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

                    val pickerItem = items.firstOrNull { it.itemId == appPickerItemId }
                    if (pickerItem != null) {
                        WidgetAppPicker(
                            apps = apps,
                            selected = BuiltInWidgets.decodePackages(
                                pickerItem.appListPackages,
                            ).toSet(),
                            onToggle = { packageName -> toggleAppInPicker(packageName) },
                            onDone = { appPickerItemId = null },
                        )
                    }
                }

                // Deleting the page you are looking at (or a stale id after a restore)
                // must never leave a blank canvas.
                LaunchedEffect(pages, currentPageId, homePageId) {
                    if (pages.isNotEmpty() && pages.none { it.pageId == currentPageId }) {
                        currentPageId = pages.firstOrNull { it.pageId == homePageId }?.pageId
                            ?: pages.first().pageId
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
                        onDeletePage = { id ->
                            if (id == currentPageId) {
                                currentPageId = pages.firstOrNull { it.pageId != id }?.pageId
                                    ?: homePageId
                            }
                            scope.launch { repository.deletePage(id) }
                        },
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
                            onMove = { id, col, row ->
                                val item = items.firstOrNull { it.itemId == id }
                                if (item != null) commitCanvasPlacement(item, col, row)
                            },
                            onResize = { id, cols, rows ->
                                val item = items.firstOrNull { it.itemId == id }
                                if (item != null) commitCanvasPlacement(item, item.col, item.row, cols, rows)
                            },
                            onRemove = { id ->
                                val item = items.firstOrNull { it.itemId == id }
                                if (item != null) removeCanvasItem(item)
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
                                runCatching { backgroundRepository.save(edited, previous = draft) }
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

                val resizeItem = items.firstOrNull { it.itemId == resizeItemId }
                val resizePage = pages.firstOrNull { it.pageId == currentPageId }
                if (resizeItem != null && resizePage != null && resizeItem.pageId == currentPageId) {
                    ItemResizeDialog(
                        initialCols = resizeItem.colSpan,
                        initialRows = resizeItem.rowSpan,
                        maxCols = resizePage.gridCols,
                        maxRows = resizePage.gridRows,
                        onApply = { cols, rows ->
                            if (commitCanvasPlacement(
                                    item = resizeItem,
                                    col = resizeItem.col,
                                    row = resizeItem.row,
                                    colSpan = cols,
                                    rowSpan = rows,
                                )
                            ) {
                                resizeItemId = null
                            }
                        },
                        onDismiss = { resizeItemId = null },
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
    widgetController: WidgetHostController,
    onPageChanged: (Long) -> Unit,
    onHomeRequested: () -> Unit,
    onAppClick: (String) -> Unit,
    onOpenDrawerPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditItems: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onLongPress: () -> Unit = {},
    showCoordinateDetail: Boolean = true,
    canvasEditing: Boolean = false,
    selectedCanvasItemId: Long? = null,
    onSelectCanvasItem: (GridItemEntity) -> Unit = {},
    onMoveCanvasItem: (GridItemEntity, Int, Int) -> Boolean = { _, _, _ -> false },
    onResizeCanvasItem: (GridItemEntity) -> Unit = {},
    onResizeCanvasGeometry: (GridGeometry) -> Boolean = { false },
    onWidgetAction: (GridItemEntity) -> Unit = {},
    apps: List<AppInfo> = emptyList(),
    onRemoveCanvasItem: (GridItemEntity) -> Unit = {},
    onExitCanvasEditor: () -> Unit = {},
    floatingKind: BuiltInWidgetKind? = null,
    floatingPoint: Offset? = null,
    pageBackground: @Composable () -> Unit = {},
) {
    val page = pages.firstOrNull { it.pageId == currentPageId } ?: return
    val selectedCanvasItem = items.firstOrNull { it.itemId == selectedCanvasItemId }
    var lastItemTouch by remember { mutableStateOf(0L) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(canvasEditing) {
        if (!canvasEditing) menuExpanded = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SpatialPageCanvas(
            pages = pages,
            edges = edges,
            items = items,
            currentPageId = currentPageId,
            onPageChanged = onPageChanged,
            onHomeRequested = onHomeRequested,
            onAppClick = onAppClick,
            onLongPress = {
                if (System.nanoTime() - lastItemTouch > 300_000_000L) onLongPress()
            },
            onWallHit = {},
            editing = canvasEditing,
            selectedItemId = selectedCanvasItemId,
            onSelectItem = onSelectCanvasItem,
            onMoveItem = onMoveCanvasItem,
            onResizeItem = onResizeCanvasGeometry,
            onWidgetAction = onWidgetAction,
            onItemTouched = { lastItemTouch = System.nanoTime() },
            onEmptyTap = onExitCanvasEditor,
            floatingKind = floatingKind,
            floatingPoint = floatingPoint,
            widgetContent = { item ->
                val builtIn = item.widgetKind ?: BuiltInWidgets.fromReference(item.referenceId)
                when (builtIn) {
                    BuiltInWidgetKind.CLOCK -> ClockWidgetContent()
                    BuiltInWidgetKind.APP_LIST -> AppListWidgetContent(
                        packages = BuiltInWidgets.decodePackages(item.appListPackages),
                        apps = apps,
                    )
                    null -> {
                        val widgetId = item.referenceId.toIntOrNull()
                        if (widgetId != null) {
                            WidgetHostView(
                                controller = widgetController,
                                appWidgetId = widgetId,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                "WIDGET NEEDS REBIND",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            },
            pageBackground = pageBackground,
            modifier = Modifier.fillMaxSize(),
        )

        // A normal page shows nothing but its coordinates when the setting is on.
        // Everything else is reachable by long-press gestures or the system Home button.
        if (showCoordinateDetail) {
            GlassPanel(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
            ) {
                Text(
                    text = "${page.coordX},${page.coordY}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        if (canvasEditing) {
            GlassPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "MENU",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .clickable { menuExpanded = !menuExpanded }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    if (menuExpanded) {
                        EditorMenuItem("APPS", onOpenDrawerPage)
                        EditorMenuItem("EDIT", onEditItems)
                        EditorMenuItem("SETTINGS", onOpenSettings)
                        EditorMenuItem("WIDGETS", onOpenWidgetPicker)
                    }
                }
            }

            GlassPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedCanvasItem == null) {
                            "LONG-PRESS AN ITEM · TAP EMPTY SPACE TO EXIT"
                        } else {
                            "DRAG TO MOVE"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (selectedCanvasItem != null) {
                        Text(
                            text = "RESIZE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onResizeCanvasItem(selectedCanvasItem) }
                                .padding(8.dp),
                        )
                        Text(
                            text = "REMOVE",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable { onRemoveCanvasItem(selectedCanvasItem) }
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorMenuItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun AppDrawerPage(
    apps: List<AppInfo>,
    pinned: Set<String>,
    favorites: Set<String>,
    hidden: Set<String>,
    coordX: Int,
    coordY: Int,
    showCoordinateDetail: Boolean = true,
    onBack: () -> Unit,
    onLaunch: (packageName: String) -> Unit,
    onTogglePin: (packageName: String) -> Unit,
    onCustomize: (packageName: String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AppDrawerOverlay(
            apps = apps,
            pinned = pinned,
            favorites = favorites,
            hidden = hidden,
            onBack = onBack,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
            onCustomize = onCustomize,
        )
        if (showCoordinateDetail) {
            Text(
                text = "$coordX,$coordY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp),
            )
        }
    }
}

@Composable
private fun BuiltInWidgetPickerScreen(
    onLongPress: (BuiltInWidgetKind) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "BACK",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text("CHOOSE A WIDGET", style = MaterialTheme.typography.labelMedium)
        Text(
            text = "LONG-PRESS TO PICK UP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(BuiltInWidgetKind.entries, key = { it.name }) { kind ->
                Text(
                    text = BuiltInWidgets.label(kind),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(kind) {
                            detectTapGestures(onLongPress = { onLongPress(kind) })
                        }
                        .padding(vertical = 16.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                )
            }
        }
    }
}

@Composable
private fun WidgetAppPicker(
    apps: List<AppInfo>,
    selected: Set<String>,
    onToggle: (packageName: String) -> Unit,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DONE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onDone).padding(8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text("ADD APPS", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(app.packageName) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (app.packageName in selected) "[x]" else "[ ]",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ClockWidgetContent() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val time = remember(now) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(now))
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = time,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AppListWidgetContent(packages: List<String>, apps: List<AppInfo>) {
    val labels = remember(packages, apps) {
        packages.map { pkg ->
            apps.firstOrNull { it.packageName == pkg }?.label ?: pkg.substringAfterLast('.')
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "+",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        if (labels.isEmpty()) {
            Text(
                text = "TAP + TO ADD APPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(6.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, top = 28.dp, end = 6.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                labels.take(8).forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
