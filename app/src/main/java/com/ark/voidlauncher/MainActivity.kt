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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.backup.BackupDocumentClassifier
import com.ark.voidlauncher.data.backup.BackupDocumentKind
import com.ark.voidlauncher.data.backup.BackupManager
import com.ark.voidlauncher.data.backup.BackupPayload
import com.ark.voidlauncher.data.backup.BackupValidator
import com.ark.voidlauncher.data.backup.BackedUpBackground
import com.ark.voidlauncher.data.backup.BackedUpItem
import com.ark.voidlauncher.data.backup.CustomWidgetBundleManager
import com.ark.voidlauncher.data.backup.CustomWidgetBundleValidator
import com.ark.voidlauncher.data.backup.DeviceInventory
import com.ark.voidlauncher.data.backup.DeviceInventoryManager
import com.ark.voidlauncher.data.backup.InventoryApp
import com.ark.voidlauncher.data.backup.InventorySystemWidget
import com.ark.voidlauncher.data.background.BackgroundRepository
import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.data.migration.DataStoreToRoomMigration
import com.ark.voidlauncher.data.repository.LauncherRepository
import com.ark.voidlauncher.domain.AppListAppearance
import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.BuiltInWidgets
import com.ark.voidlauncher.domain.CUSTOM_WIDGET_RUNTIME_WEBVIEW
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.CustomWidgetBundle
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.displayTitle
import com.ark.voidlauncher.domain.GestureAction
import com.ark.voidlauncher.domain.GestureBindings
import com.ark.voidlauncher.domain.GestureDispatch
import com.ark.voidlauncher.domain.GestureScope
import com.ark.voidlauncher.domain.GestureTarget
import com.ark.voidlauncher.domain.GestureTrigger
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.OrientationMode
import com.ark.voidlauncher.domain.PageHistory
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.WidgetReference
import com.ark.voidlauncher.domain.background.BackgroundKind
import com.ark.voidlauncher.domain.edgeForGesture
import com.ark.voidlauncher.domain.background.BackgroundResolver
import com.ark.voidlauncher.domain.background.BackgroundScope
import com.ark.voidlauncher.domain.background.PageBackground
import com.ark.voidlauncher.domain.background.decodeGradientStops
import com.ark.voidlauncher.domain.background.encodeGradientStops
import com.ark.voidlauncher.domain.placement.PlacementResolver
import com.ark.voidlauncher.domain.placement.PlacementResult
import com.ark.voidlauncher.system.accessibility.AccessibilityBridge
import com.ark.voidlauncher.system.accessibility.GlobalAction
import com.ark.voidlauncher.system.widgets.WidgetEvents
import com.ark.voidlauncher.system.widgets.WidgetHostController
import com.ark.voidlauncher.system.widgets.WidgetHostView
import com.ark.voidlauncher.ui.backup.BackupScreen
import com.ark.voidlauncher.ui.background.BackgroundEditorSheet
import com.ark.voidlauncher.ui.background.PageBackgroundRenderer
import com.ark.voidlauncher.ui.customtheme.CustomThemeEditorSheet
import com.ark.voidlauncher.ui.drawer.AppDrawerOverlay
import com.ark.voidlauncher.ui.editor.AppAppEditModal
import com.ark.voidlauncher.ui.editor.AppEditScope
import com.ark.voidlauncher.ui.editor.AppListWidgetMenuModal
import com.ark.voidlauncher.ui.editor.AppTypographyEditorSheet
import com.ark.voidlauncher.ui.overview.PageOverview
import com.ark.voidlauncher.ui.editor.EditorValidation
import com.ark.voidlauncher.ui.editor.ItemEditorSheet
import com.ark.voidlauncher.ui.editor.LinkChange
import com.ark.voidlauncher.ui.editor.PageEditorSheet
import com.ark.voidlauncher.ui.gestures.GestureSettingsSheet
import com.ark.voidlauncher.ui.grid.GridGeometry
import com.ark.voidlauncher.ui.grid.ItemResizeDialog
import com.ark.voidlauncher.ui.settings.SettingsScreen as SettingsSurface
import com.ark.voidlauncher.ui.spatial.SpatialPageCanvas
import com.ark.voidlauncher.ui.theme.GlassPanel
import com.ark.voidlauncher.ui.theme.ThemePickerSheet
import com.ark.voidlauncher.ui.theme.ThemedSystemBars
import com.ark.voidlauncher.ui.theme.VoidTheme
import com.ark.voidlauncher.ui.widgets.CustomWebViewWidget
import com.ark.voidlauncher.ui.widgets.MissingCustomWidget
import com.ark.voidlauncher.ui.widgets.UnknownWidgetReference
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

private enum class Screen { HOME, SETTINGS, WIDGET_PICKER, BACKUP }
private enum class Editor { NONE, PAGES, ITEMS, TYPOGRAPHY }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val prefs = remember { LauncherPrefs(context) }
            val database = remember { VoidDatabase.getInstance(context) }
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
            // Start as seen so returning users never get a hint flash before the
            // persisted value arrives.
            val editorHintSeen by prefs.editorHintSeen.collectAsState(initial = true)
            val gestureBindings by prefs.gestureBindings.collectAsState(
                initial = GestureBindings(),
            )
            val drawerNicknames by prefs.drawerNicknames.collectAsState(initial = emptyMap())
            val appListAppearances by prefs.appListAppearances.collectAsState(initial = emptyMap())
            val customWidgets by prefs.customWidgetBundle.collectAsState(
                initial = CustomWidgetBundle(),
            )
            val backgroundRepository = remember { BackgroundRepository(database, context) }
            val repository = remember {
                LauncherRepository(
                    database = database,
                    prefs = prefs,
                    releaseAppWidget = { widgetController.deleteWidget(it) },
                    deleteBackgroundImage = { backgroundRepository.deleteImage(it) },
                    readAppWidgetIds = { widgetController.allocatedWidgetIds() },
                )
            }
            val backgrounds by backgroundRepository.backgrounds.collectAsState(initial = emptyList())
            val pages by repository.pages.collectAsState(initial = emptyList())
            val edges by repository.edges.collectAsState(initial = emptyList())
            val items by repository.items.collectAsState(initial = emptyList())
            val customizations by repository.customizations.collectAsState(initial = emptyList())
            // Global nicknames replace the package label everywhere. Drawer-only
            // nicknames layer on top for the drawer alone; both are DataStore-safe.
            val launcherApps = remember(apps, customizations) {
                val nicknames = customizations.associate { it.packageName to it.customNickname }
                apps.map { app ->
                    app.copy(
                        label = nicknames[app.packageName]?.takeIf { it.isNotBlank() }
                            ?: app.label,
                    )
                }
            }
            val drawerApps = remember(launcherApps, drawerNicknames) {
                launcherApps.map { app ->
                    app.copy(
                        label = drawerNicknames[app.packageName]?.takeIf { it.isNotBlank() }
                            ?: app.label,
                    )
                }
            }
            val homeCoord by repository.homeCoord.collectAsState(initial = Pair(0, 0))
            val homePageId by repository.homePageId.collectAsState(initial = VoidDatabase.ORIGIN_PAGE_ID)
            val appDrawerPageId by repository.appDrawerPageId.collectAsState(initial = null)

            var currentPageId by remember { mutableLongStateOf(VoidDatabase.ORIGIN_PAGE_ID) }
            val pageHistory = remember { PageHistory() }
            var screen by remember { mutableStateOf(Screen.HOME) }
            val screenHistory = remember { mutableStateListOf<Screen>() }
            var editor by remember { mutableStateOf(Editor.NONE) }
            var canvasEditing by remember { mutableStateOf(false) }
            var showEditorHint by remember { mutableStateOf(false) }
            var selectedCanvasItemId by remember { mutableStateOf<Long?>(null) }
            var resizeItemId by remember { mutableStateOf<Long?>(null) }
            var pendingWidgetKind by remember { mutableStateOf<BuiltInWidgetKind?>(null) }
            var floatingPoint by remember { mutableStateOf<Offset?>(null) }
            var floatingPointerId by remember { mutableStateOf<Long?>(null) }
            var rootSize by remember { mutableStateOf(IntSize.Zero) }
            var widgetMenuItemId by remember { mutableStateOf<Long?>(null) }
            var drawerAppEditPackage by remember { mutableStateOf<String?>(null) }
            var message by remember { mutableStateOf<String?>(null) }
            var overview by remember { mutableStateOf(false) }
            var themePicker by remember { mutableStateOf(false) }
            var gestureEditor by remember { mutableStateOf(false) }
            var customThemeEditor by remember { mutableStateOf(false) }
            var backgroundEditor by remember { mutableStateOf(false) }
            var backgroundDraft by remember { mutableStateOf<PageBackground?>(null) }
            val pendingRebindItemId = remember { mutableStateOf<Long?>(null) }
            val accessibilityEnabled = remember { AccessibilityBridge.isEnabled(context) }

            /** Snapshot of apps/providers this device can actually render. */
            fun buildDeviceInventory(): DeviceInventory {
                val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .format(Date())
                val systemWidgets = widgetController.installedProviders().mapNotNull { info ->
                    val provider = info.provider?.flattenToString() ?: return@mapNotNull null
                    val label = runCatching {
                        info.loadLabel(context.packageManager).toString()
                    }.getOrDefault(provider).ifBlank { provider }
                    InventorySystemWidget(
                        provider = provider,
                        label = label,
                        minColSpan = maxOf(1, ceil(info.minWidth / 40f).toInt()),
                        minRowSpan = maxOf(1, ceil(info.minHeight / 40f).toInt()),
                    )
                }.sortedBy { it.label.lowercase(Locale.US) }
                return DeviceInventory(
                    generatedAt = timestamp,
                    builtInWidgets = BuiltInWidgetKind.entries.map { it.name },
                    apps = apps.map { InventoryApp(it.packageName, it.label) },
                    systemWidgets = systemWidgets,
                )
            }

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
                                referenceId = run {
                                    val customId = WidgetReference.customId(item.referenceId)
                                    when {
                                        customId != null -> WidgetReference.customReference(customId)
                                        item.itemType == GridItemType.WIDGET &&
                                            item.widgetKind == null -> {
                                            val raw = WidgetReference.unwrap(item.referenceId)
                                            raw.toIntOrNull()?.let { id ->
                                                widgetController.providerInfo(id)?.provider?.flattenToString()
                                            } ?: raw
                                        }
                                        else -> item.referenceId
                                    }
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
                        if (BackupManager.writeToUri(context, uri, payload)) {
                            "BACKUP EXPORTED"
                        } else {
                            "EXPORT FAILED"
                        }
                    }.getOrDefault("EXPORT FAILED")
                }
            }

            val exportWidgetBundleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    message = runCatching {
                        if (CustomWidgetBundleManager.writeToUri(context, uri, customWidgets)) {
                            "WIDGET BUNDLE EXPORTED (${customWidgets.widgets.size} WIDGETS)"
                        } else {
                            "EXPORT FAILED"
                        }
                    }.getOrDefault("EXPORT FAILED")
                }
            }

            val exportInventoryLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/json"),
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val inventory = buildDeviceInventory()
                    message = runCatching {
                        if (DeviceInventoryManager.writeToUri(context, uri, inventory)) {
                            "DEVICE INVENTORY EXPORTED (" +
                                "${inventory.apps.size} APPS, ${inventory.systemWidgets.size} WIDGETS)"
                        } else {
                            "EXPORT FAILED"
                        }
                    }.getOrDefault("EXPORT FAILED")
                }
            }

            val importLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetMultipleContents(),
            ) { uris ->
                if (uris.isEmpty()) return@rememberLauncherForActivityResult
                scope.launch {
                    val documents = withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri ->
                            val text = runCatching {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    input.readBytes().toString(Charsets.UTF_8)
                                }
                            }.getOrNull()
                            text?.let { BackupDocumentClassifier.classify(it) to it }
                        }
                    }
                    if (documents.isEmpty()) {
                        message = "NO READABLE DOCUMENTS"
                        return@launch
                    }
                    if (documents.any { it.first == BackupDocumentKind.UNKNOWN }) {
                        message = "UNKNOWN BACKUP DOCUMENT"
                        return@launch
                    }

                    val inventoryNote = if (documents.any {
                            it.first == BackupDocumentKind.DEVICE_INVENTORY
                        }
                    ) {
                        "INVENTORY FILES BELONG TO THE WEB EDITOR"
                    } else {
                        null
                    }

                    // Validate every selected document before mutating either store.
                    var pendingBundle: CustomWidgetBundle? = null
                    var pendingBundleNote: String? = null
                    val bundleDocuments = documents.filter { it.first == BackupDocumentKind.WIDGET_BUNDLE }
                    if (bundleDocuments.size > 1) {
                        message = "MULTIPLE WIDGET BUNDLES SELECTED"
                        return@launch
                    }
                    if (bundleDocuments.size == 1) {
                        val bundle = CustomWidgetBundleManager.fromJson(bundleDocuments[0].second)
                        if (bundle == null) {
                            message = "INVALID OR UNSUPPORTED WIDGET BUNDLE FILE"
                            return@launch
                        }
                        val validation = CustomWidgetBundleValidator.validate(bundle)
                        if (!validation.isValid) {
                            message = "INVALID WIDGET BUNDLE: ${validation.errors.first()}"
                            return@launch
                        }
                        pendingBundle = bundle
                        pendingBundleNote = "WIDGET BUNDLE IMPORTED (${bundle.widgets.size} WIDGETS)"
                    }

                    val layoutDocuments = documents.filter { it.first == BackupDocumentKind.LAYOUT }
                    if (layoutDocuments.size > 1) {
                        message = "MULTIPLE LAYOUT FILES SELECTED"
                        return@launch
                    }
                    val payload = if (layoutDocuments.isEmpty()) {
                        null
                    } else {
                        val parsed = BackupManager.fromJson(layoutDocuments[0].second)
                        if (parsed == null) {
                            message = "INVALID OR UNSUPPORTED BACKUP FILE"
                            return@launch
                        }
                        val validation = BackupValidator.validate(parsed)
                        if (!validation.isValid) {
                            message = "INVALID BACKUP DATA: ${validation.errors.first()}"
                            return@launch
                        }
                        parsed
                    }

                    var layoutNote: String? = null
                    if (payload != null) {
                        val restored = runCatching { repository.restore(payload) }.getOrNull()
                        if (restored == null) {
                            message = "RESTORE FAILED"
                            return@launch
                        }
                        pageHistory.clear()
                        currentPageId = repository.homePageId.first()
                        // P8/P9 preferences are not touched by LauncherRepository.restore
                        // (it only restores theme). Apply them here with safe defaults.
                        runCatching {
                            val orientation = runCatching {
                                OrientationMode.valueOf(payload.orientation)
                            }.getOrDefault(OrientationMode.PORTRAIT)
                            prefs.setOrientation(orientation)
                            prefs.setShowCoordinateDetail(payload.showCoordinateDetail)
                            if (payload.customPalette != null) {
                                prefs.setCustomPalette(payload.customPalette)
                            } else {
                                prefs.clearCustomPalette()
                            }
                        }
                        // P10 backgrounds are restored by fresh page coordinates because
                        // restore() regenerates all Room page ids.
                        val skippedBackgrounds = runCatching {
                            restoreBackgrounds(payload.backgrounds, database, backgroundRepository)
                        }.fold(onSuccess = { it }, onFailure = { 1 })
                        val skipped = restored.skippedEdges + restored.skippedItems + skippedBackgrounds
                        layoutNote = when {
                            skipped > 0 -> "BACKUP RESTORED ($skipped ITEMS SKIPPED)"
                            restored.synthesizedPages > 0 -> "BACKUP RESTORED (PAGES SYNTHESIZED)"
                            else -> "BACKUP RESTORED (${restored.linkedPairs} LINKS)"
                        }
                    }

                    val bundleNote = pendingBundle?.let { bundle ->
                        val saved = runCatching { prefs.setCustomWidgetBundle(bundle) }
                        if (saved.isFailure) "WIDGET BUNDLE SAVE FAILED" else pendingBundleNote
                    }
                    message = listOfNotNull(layoutNote, bundleNote, inventoryNote)
                        .joinToString(" / ")
                        .ifBlank { "NOTHING TO IMPORT" }
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

            val rebindLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                val itemId = pendingRebindItemId.value
                pendingRebindItemId.value = null
                if (itemId == null) return@rememberLauncherForActivityResult
                val id = result.data?.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
                if (result.resultCode == RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    scope.launch {
                        runCatching { repository.rebindWidget(itemId, id) }
                            .onSuccess { message = "WIDGET REBOUND" }
                            .onFailure { message = "REBIND FAILED" }
                    }
                } else {
                    widgetController.deleteWidget(id)
                    message = "WIDGET REBIND CANCELLED"
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
                // Editor mode is sticky across page changes: only per-page state
                // is dropped. Exiting is an explicit user action.
                selectedCanvasItemId = null
                resizeItemId = null
                pendingWidgetKind = null
                widgetMenuItemId = null
                drawerAppEditPackage = null
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

            /** Record where a non-HOME screen was opened from so BACK can unwind it. */
            fun navigateTo(target: Screen) {
                if (target == screen) return
                if (target == Screen.HOME) {
                    screenHistory.clear()
                } else {
                    screenHistory.add(screen)
                }
                screen = target
            }

            fun goBackScreen() {
                screen = if (screenHistory.isEmpty()) {
                    Screen.HOME
                } else {
                    screenHistory.removeAt(screenHistory.lastIndex)
                }
            }

            BackHandler(
                enabled = themePicker || gestureEditor || customThemeEditor || backgroundEditor ||
                    overview || resizeItemId != null || pendingWidgetKind != null ||
                    widgetMenuItemId != null || drawerAppEditPackage != null || canvasEditing ||
                    screen != Screen.HOME || editor != Editor.NONE || pageHistory.size > 0,
            ) {
                when {
                    pendingWidgetKind != null -> {
                        pendingWidgetKind = null
                        canvasEditing = false
                    }
                    widgetMenuItemId != null -> widgetMenuItemId = null
                    drawerAppEditPackage != null -> drawerAppEditPackage = null
                    resizeItemId != null -> resizeItemId = null
                    customThemeEditor -> customThemeEditor = false
                    backgroundEditor -> {
                        backgroundEditor = false
                        backgroundDraft = null
                    }
                    themePicker -> themePicker = false
                    gestureEditor -> gestureEditor = false
                    overview -> overview = false
                    canvasEditing -> {
                        canvasEditing = false
                        selectedCanvasItemId = null
                    }
                    editor != Editor.NONE -> editor = Editor.NONE
                    screen != Screen.HOME -> goBackScreen()
                    pageHistory.size > 0 -> {
                        // Skip stale ids (e.g. a page deleted before history was pruned).
                        while (pageHistory.size > 0) {
                            val target = pageHistory.pop() ?: break
                            if (pages.any { it.pageId == target }) {
                                currentPageId = target
                                break
                            }
                        }
                    }
                }
            }

            fun exitCanvasEditor() {
                canvasEditing = false
                showEditorHint = false
                selectedCanvasItemId = null
                resizeItemId = null
            }

            /** Enters the canvas editor, showing the hint only for the first session. */
            fun enterCanvasEditor() {
                showEditorHint = !editorHintSeen
                if (!editorHintSeen) scope.launch { prefs.markEditorHintSeen() }
                editor = Editor.NONE
                canvasEditing = true
                selectedCanvasItemId = null
                resizeItemId = null
            }

            fun selectCanvasItem(item: GridItemEntity) {
                if (!canvasEditing) enterCanvasEditor()
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
                        // Moving an item to another page keeps editor mode active;
                        // the page-change effect clears its stale selection.
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
                val span = BuiltInWidgets.defaultSpan(kind)
                val candidate = GridItemEntity(
                    pageId = page.pageId,
                    itemType = GridItemType.WIDGET,
                    col = col,
                    row = row,
                    colSpan = span.colSpan,
                    rowSpan = span.rowSpan,
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
                        colSpan = span.colSpan,
                        rowSpan = span.rowSpan,
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
                    val span = pendingWidgetKind?.let(BuiltInWidgets::defaultSpan)
                    val col = (
                        point.x / cellWidthPx - (span?.colSpan?.minus(1)?.div(2f) ?: 0.5f)
                        ).roundToInt()
                    val row = (
                        point.y / cellHeightPx - (span?.rowSpan?.minus(1)?.div(2f) ?: 0.5f)
                        ).roundToInt()
                    dropPendingWidget(col, row)
                }
            }

            fun resizeCanvasItem(geometry: GridGeometry): Boolean {
                val item = items.firstOrNull { it.itemId == selectedCanvasItemId } ?: return false
                val definition = WidgetReference.customId(item.referenceId)?.let { id ->
                    customWidgets.widgets.firstOrNull { it.id == id }
                }
                if (definition != null && !definition.allowResize &&
                    (geometry.spanX != item.colSpan || geometry.spanY != item.rowSpan)
                ) {
                    message = "CUSTOM WIDGET IS FIXED SIZE"
                    return false
                }
                val spanX = maxOf(definition?.minColSpan ?: 1, geometry.spanX)
                val spanY = maxOf(definition?.minRowSpan ?: 1, geometry.spanY)
                return commitCanvasPlacement(
                    item = item,
                    col = geometry.col,
                    row = geometry.row,
                    colSpan = spanX,
                    rowSpan = spanY,
                )
            }

            fun toggleAppInWidgetMenu(packageName: String) {
                val item = items.firstOrNull { it.itemId == widgetMenuItemId } ?: return
                val current = BuiltInWidgets.decodePackages(item.appListPackages).toMutableSet()
                if (!current.add(packageName)) current.remove(packageName)
                scope.launch { repository.setAppListPackages(item.itemId, current.toList()) }
            }

            /** APP_LIST nicknames use the global scope so they stay portable. */
            fun setGlobalNickname(packageName: String, nickname: String?) {
                val existing = customizations.firstOrNull { it.packageName == packageName }
                val updated = existing?.copy(customNickname = nickname)
                    ?: AppCustomizationEntity(packageName = packageName, customNickname = nickname)
                scope.launch {
                    repository.customizeApp(updated)
                    prefs.setDrawerNickname(packageName, null)
                }
            }

            fun saveDrawerAppEdit(editScope: AppEditScope, nickname: String?) {
                val packageName = drawerAppEditPackage ?: return
                drawerAppEditPackage = null
                val existing = customizations.firstOrNull { it.packageName == packageName }
                scope.launch {
                    when (editScope) {
                        AppEditScope.GLOBAL -> {
                            repository.customizeApp(
                                existing?.copy(customNickname = nickname)
                                    ?: AppCustomizationEntity(
                                        packageName = packageName,
                                        customNickname = nickname,
                                    ),
                            )
                            prefs.setDrawerNickname(packageName, null)
                        }
                        AppEditScope.DRAWER_ONLY -> {
                            if (existing?.customNickname != null) {
                                repository.customizeApp(existing.copy(customNickname = null))
                            }
                            prefs.setDrawerNickname(packageName, nickname)
                        }
                    }
                }
            }

            fun openWidgetPicker() {
                navigateTo(Screen.WIDGET_PICKER)
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
                navigateTo(Screen.HOME)
                enterCanvasEditor()
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

            fun beginRebindWidget(item: GridItemEntity) {
                if (WidgetReference.isCustom(item.referenceId)) {
                    message = "CUSTOM WIDGETS REBIND FROM THEIR BUNDLE"
                    return
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    message = "WIDGETS REQUIRE ANDROID 8 OR NEWER"
                    return
                }
                val providerName = WidgetReference.portableProvider(item.referenceId)
                if (providerName != null && !providerName.contains('/')) {
                    message = "WIDGET CANNOT BE REBOUND"
                    return
                }
                val id = widgetController.allocateAppWidgetId()
                pendingRebindItemId.value = item.itemId
                if (providerName != null) {
                    val provider = ComponentName.unflattenFromString(providerName)
                    if (provider != null) {
                        if (widgetController.bindAllowed(id, provider)) {
                            pendingRebindItemId.value = null
                            scope.launch {
                                runCatching { repository.rebindWidget(item.itemId, id) }
                                    .onSuccess { message = "WIDGET REBOUND" }
                                    .onFailure { message = "REBIND FAILED" }
                            }
                        } else {
                            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                            rebindLauncher.launch(intent)
                        }
                        return
                    }
                }
                rebindLauncher.launch(widgetController.createPickIntent(id))
            }

            // Runs a configured gesture action. A missing binding keeps today's
            // hardcoded behavior; an explicit binding replaces it.
            fun runSystemGesture(target: GestureTarget) {
                if (!AccessibilityBridge.isEnabled(context)) {
                    message = "ACCESSIBILITY ACTION UNAVAILABLE"
                    return
                }
                val global = when (target) {
                    GestureTarget.SYSTEM_BACK -> GlobalAction.BACK
                    GestureTarget.SYSTEM_RECENTS -> GlobalAction.RECENTS
                    GestureTarget.SYSTEM_NOTIFICATIONS -> GlobalAction.NOTIFICATIONS
                    GestureTarget.LOCK_SCREEN -> GlobalAction.LOCK_SCREEN
                    else -> return
                }
                if (!AccessibilityBridge.perform(global)) {
                    message = "SYSTEM ACTION UNAVAILABLE"
                }
            }

            fun runGestureAction(action: GestureAction?, fallback: () -> Unit) {
                if (action == null) {
                    fallback()
                    return
                }
                val target = GestureDispatch.targetFor(action)
                when (target) {
                    GestureTarget.HOME -> {
                        if (homePageId != currentPageId) {
                            pageHistory.push(currentPageId, homePageId)
                        }
                        currentPageId = homePageId
                    }
                    GestureTarget.MAP -> overview = true
                    GestureTarget.SETTINGS -> {
                        exitCanvasEditor()
                        navigateTo(Screen.SETTINGS)
                    }
                    GestureTarget.APP_DRAWER -> {
                        exitCanvasEditor()
                        appDrawerPageId?.let { drawerId ->
                            if (drawerId != currentPageId) {
                                pageHistory.push(currentPageId, drawerId)
                            }
                            currentPageId = drawerId
                        }
                    }
                    GestureTarget.WIDGET_PICKER -> {
                        exitCanvasEditor()
                        openWidgetPicker()
                    }
                    GestureTarget.EDITOR -> enterCanvasEditor()
                    GestureTarget.TOGGLE_COORDINATES -> scope.launch {
                        prefs.setShowCoordinateDetail(!showCoordinateDetail)
                    }
                    GestureTarget.SYSTEM_BACK, GestureTarget.SYSTEM_RECENTS,
                    GestureTarget.SYSTEM_NOTIFICATIONS, GestureTarget.LOCK_SCREEN,
                    -> if (GestureDispatch.requiresAccessibility(target)) {
                        runSystemGesture(target)
                    }
                    GestureTarget.UNSUPPORTED -> message = "GESTURE ACTION NOT AVAILABLE"
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
                            val gestureCoord = (currentPage?.coordX ?: 0) to
                                (currentPage?.coordY ?: 0)
                            val doubleTapAction = gestureBindings.actionFor(
                                gestureCoord, GestureTrigger.DOUBLE_TAP,
                            )
                            val longPressAction = gestureBindings.actionFor(
                                gestureCoord, GestureTrigger.LONG_PRESS,
                            )
                            if (currentPage?.pageType == PageType.APP_DRAWER) {
                                AppDrawerPage(
                                    apps = drawerApps,
                                    pinned = pinned,
                                    favorites = customizations.filter { it.isFavorite }
                                        .map { it.packageName }.toSet(),
                                    hidden = customizations.filter { it.isHidden }
                                        .map { it.packageName }.toSet(),
                                    title = currentPage.displayTitle,
                                    coordX = currentPage.coordX,
                                    coordY = currentPage.coordY,
                                    showCoordinateDetail = showCoordinateDetail,
                                    onOpenMap = {
                                        val drawerPage = pages.firstOrNull {
                                            it.pageId == currentPageId
                                        }
                                        val drawerCoord = (drawerPage?.coordX ?: 0) to
                                            (drawerPage?.coordY ?: 0)
                                        runGestureAction(
                                            gestureBindings.actionFor(
                                                drawerCoord, GestureTrigger.LONG_PRESS,
                                            ),
                                        ) { /* Drawer pages have no grid editor. */ }
                                    },
                                    onBack = {
                                        if (homePageId != currentPageId) {
                                            pageHistory.push(currentPageId, homePageId)
                                        }
                                        currentPageId = homePageId
                                    },
                                    onReverseSwipe = { direction ->
                                        pageHistory.reverseTarget(currentPageId, direction)
                                            .takeIf { target -> pages.any { it.pageId == target } }
                                            ?.let { target ->
                                                pageHistory.push(
                                                    currentPageId,
                                                    target,
                                                    direction.edgeForGesture,
                                                )
                                                currentPageId = target
                                            }
                                    },
                                    onLaunch = { appRepository.launch(it) },
                                    onTogglePin = { scope.launch { prefs.togglePinned(it) } },
                                    onCustomize = { packageName ->
                                        drawerAppEditPackage = packageName
                                    },
                                )
                            } else {
                                HomeScreen(
                                    pages = pages,
                                    edges = edges,
                                    items = items,
                                    currentPageId = currentPageId,
                                    widgetController = widgetController,
                                    customWidgets = customWidgets,
                                    onPageChanged = { targetId, direction ->
                                        pageHistory.push(currentPageId, targetId, direction)
                                        currentPageId = targetId
                                    },
                                    reverseTarget = { pageId, direction ->
                                        pageHistory.reverseTarget(pageId, direction)
                                            .takeIf { target ->
                                                pages.any { it.pageId == target }
                                            }
                                    },
                                    onHomeRequested = {
                                        runGestureAction(doubleTapAction) {
                                            if (homePageId != currentPageId) {
                                                pageHistory.push(currentPageId, homePageId)
                                            }
                                            currentPageId = homePageId
                                        }
                                    },
                                    onAppClick = { appRepository.launch(it) },
                                    onOpenDrawerPage = {
                                        exitCanvasEditor()
                                        appDrawerPageId?.let { target ->
                                            if (target != currentPageId) {
                                                pageHistory.push(currentPageId, target)
                                            }
                                            currentPageId = target
                                        }
                                    },
                                    onOpenSettings = {
                                        exitCanvasEditor()
                                        navigateTo(Screen.SETTINGS)
                                    },
                                    onEditItems = {
                                        exitCanvasEditor()
                                        editor = Editor.ITEMS
                                    },
                                    onOpenWidgetPicker = {
                                        exitCanvasEditor()
                                        overview = false
                                        openWidgetPicker()
                                    },
                                    onLongPress = {
                                        runGestureAction(longPressAction) { enterCanvasEditor() }
                                    },
                                    showCoordinateDetail = showCoordinateDetail,
                                    canvasEditing = canvasEditing,
                                    showEditorHint = showEditorHint,
                                    selectedCanvasItemId = selectedCanvasItemId,
                                    onSelectCanvasItem = ::selectCanvasItem,
                                    onMoveCanvasItem = { item, col, row ->
                                        commitCanvasPlacement(item, col, row)
                                    },
                                    onResizeCanvasItem = { resizeItemId = it.itemId },
                                    onResizeCanvasGeometry = ::resizeCanvasItem,
                                    onWidgetAction = { item ->
                                        if (canvasEditing && item.isAppListWidget()) {
                                            widgetMenuItemId = item.itemId
                                        }
                                    },
                                    onOpenMap = {
                                        exitCanvasEditor()
                                        overview = true
                                    },
                                    apps = launcherApps,
                                    labelForPackage = { packageName ->
                                        launcherApps.firstOrNull { it.packageName == packageName }
                                            ?.label
                                            ?: packageName.substringAfterLast('.')
                                    },
                                    appListAppearances = appListAppearances,
                                    onAppListMenu = { item ->
                                        if (item.isAppListWidget()) widgetMenuItemId = item.itemId
                                    },
                                    onRemoveCanvasItem = ::removeCanvasItem,
                                    onRebindWidget = ::beginRebindWidget,
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

                        Screen.SETTINGS -> SettingsSurface(
                            themeMode = themeMode,
                            versionName = versionName,
                            accessibilityEnabled = accessibilityEnabled,
                            onBack = { goBackScreen() },
                            onThemeChange = { scope.launch { prefs.setTheme(it) } },
                            onEditPages = { editor = Editor.PAGES },
                            onEditItems = { editor = Editor.ITEMS },
                            onEditTypography = { editor = Editor.TYPOGRAPHY },
                            onAddWidget = ::openWidgetPicker,
                            onOpenBackup = { navigateTo(Screen.BACKUP) },
                            onResetAll = {
                                scope.launch {
                                    runCatching { repository.resetToDefault() }
                                        .onSuccess { homeId ->
                                            pageHistory.clear()
                                            currentPageId = homeId
                                            editor = Editor.NONE
                                            overview = false
                                            themePicker = false
                                            customThemeEditor = false
                                            backgroundEditor = false
                                            backgroundDraft = null
                                            navigateTo(Screen.HOME)
                                            canvasEditing = false
                                            selectedCanvasItemId = null
                                            resizeItemId = null
                                            pendingWidgetKind = null
                                            floatingPointerId = null
                                            floatingPoint = null
                                            widgetMenuItemId = null
                                            drawerAppEditPackage = null
                                            prefs.clearDrawerNicknames()
                                            prefs.clearAppListAppearances()
                                            message = "RESET COMPLETE"
                                        }
                                        .onFailure { message = it.message ?: "RESET FAILED" }
                                }
                            },
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
                            onExportLayout = { exportLauncher.launch("voidlauncher-backup.json") },
                            onExportWidgets = {
                                exportWidgetBundleLauncher.launch(CustomWidgetBundleManager.FILE_NAME)
                            },
                            onExportInventory = {
                                exportInventoryLauncher.launch(DeviceInventoryManager.FILE_NAME)
                            },
                            onImportFiles = { importLauncher.launch("application/json") },
                            onBack = { goBackScreen() },
                        )

                        Screen.WIDGET_PICKER -> BuiltInWidgetPickerScreen(
                            onLongPress = ::acceptBuiltInWidget,
                            onBack = { goBackScreen() },
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
                                if (id != currentPageId) pageHistory.push(currentPageId, id)
                                currentPageId = id
                                overview = false
                            },
                            onClose = { overview = false },
                            onThemes = { themePicker = true },
                            onSettings = {
                                overview = false
                                navigateTo(Screen.SETTINGS)
                            },
                            onWidgets = {
                                overview = false
                                navigateTo(Screen.WIDGET_PICKER)
                            },
                            onPageManagement = {
                                overview = false
                                editor = Editor.PAGES
                            },
                            onGestures = {
                                overview = false
                                gestureEditor = true
                            },
                            onAddPage = { x, y, pageType ->
                                scope.launch {
                                    val id = repository.addPage(x, y, pageType)
                                    // Adding a page keeps the map open so several pages can
                                    // be laid out in one pass. HERE only changes when the
                                    // user explicitly selects a page.
                                    if (id == null) {
                                        message = "INVALID PAGE LOCATION"
                                    }
                                }
                            },
                            onMovePage = { pageId, x, y ->
                                scope.launch {
                                    if (!repository.movePage(pageId, x, y)) {
                                        message = "INVALID PAGE LOCATION"
                                    }
                                }
                            },
                            onSetHomePage = { pageId ->
                                scope.launch {
                                    if (!repository.setHomePage(pageId)) {
                                        message = "HOME CANNOT BE SET HERE"
                                    }
                                }
                            },
                            onRenamePage = { pageId, title ->
                                scope.launch {
                                    if (!repository.renamePage(pageId, title)) {
                                        message = "PAGE RENAME FAILED"
                                    }
                                }
                            },
                            onToggleEdge = { pageId, direction, toPageId, enable ->
                                val change = if (enable) {
                                    LinkChange.SetLink(pageId, direction, toPageId)
                                } else {
                                    LinkChange.RemoveLink(pageId, direction, toPageId)
                                }
                                if (!EditorValidation.linkChangeStrandsAdditionalPages(
                                        pages, edges, homePageId, change,
                                    )
                                ) {
                                    scope.launch {
                                        val ok = if (enable) {
                                            repository.setLink(pageId, direction, toPageId)
                                        } else {
                                            repository.removeLink(pageId, direction, toPageId)
                                        }
                                        if (!ok) message = "LINK COULD NOT BE UPDATED"
                                    }
                                } else {
                                    message = "THAT WOULD STRAND A PAGE"
                                }
                            },
                            showCoordinateDetail = showCoordinateDetail,
                        )
                    }

                    val menuItem = items.firstOrNull { it.itemId == widgetMenuItemId }
                    if (menuItem != null && menuItem.isAppListWidget()) {
                        AppListWidgetMenuModal(
                            itemId = menuItem.itemId,
                            packages = BuiltInWidgets.decodePackages(menuItem.appListPackages),
                            apps = launcherApps,
                            labelForPackage = { packageName ->
                                launcherApps.firstOrNull { it.packageName == packageName }
                                    ?.label
                                    ?: packageName.substringAfterLast('.')
                            },
                            appearance = appListAppearances[menuItem.itemId],
                            onDismiss = { widgetMenuItemId = null },
                            onTogglePackage = ::toggleAppInWidgetMenu,
                            onSetNickname = { packageName, nickname ->
                                setGlobalNickname(packageName, nickname)
                            },
                            onAppearanceChange = { appearance ->
                                scope.launch {
                                    prefs.setAppListAppearance(menuItem.itemId, appearance)
                                }
                            },
                        )
                    }

                    val editedDrawerApp = drawerApps.firstOrNull {
                        it.packageName == drawerAppEditPackage
                    }
                    if (editedDrawerApp != null) {
                        AppAppEditModal(
                            packageName = editedDrawerApp.packageName,
                            appLabel = editedDrawerApp.label,
                            globalNickname = customizations.firstOrNull {
                                it.packageName == editedDrawerApp.packageName
                            }?.customNickname,
                            drawerNickname = drawerNicknames[editedDrawerApp.packageName],
                            onDismiss = { drawerAppEditPackage = null },
                            onSave = ::saveDrawerAppEdit,
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
                        onDeletePage = { id ->
                            pageHistory.dropPage(id)
                            if (id == currentPageId) {
                                currentPageId = pages.firstOrNull { it.pageId != id }?.pageId
                                    ?: homePageId
                            }
                            scope.launch { repository.deletePage(id) }
                        },
                        onRename = { id, title ->
                            scope.launch {
                                if (!repository.renamePage(id, title)) {
                                    message = "PAGE RENAME FAILED"
                                }
                            }
                        },
                        onSetLink = { from, direction, to ->
                            val change = LinkChange.SetLink(from, direction, to)
                            if (EditorValidation.linkChangeStrandsAdditionalPages(
                                    pages, edges, homePageId, change,
                                )
                            ) {
                                message = "THAT WOULD STRAND A PAGE"
                            } else {
                                scope.launch {
                                    if (!repository.setLink(from, direction, to)) {
                                        message = "LINK COULD NOT BE UPDATED"
                                    }
                                }
                            }
                        },
                        onRemoveLink = { from, direction, to ->
                            val change = LinkChange.RemoveLink(from, direction, to)
                            if (EditorValidation.linkChangeStrandsAdditionalPages(
                                    pages, edges, homePageId, change,
                                )
                            ) {
                                message = "THAT WOULD STRAND A PAGE"
                            } else {
                                scope.launch {
                                    if (!repository.removeLink(from, direction, to)) {
                                        message = "LINK COULD NOT BE UPDATED"
                                    }
                                }
                            }
                        },
                        onSetHomePage = { id ->
                            scope.launch {
                                if (!repository.setHomePage(id)) {
                                    message = "HOME CANNOT BE SET HERE"
                                }
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

                if (gestureEditor) {
                    GestureSettingsSheet(
                        currentPage = currentPage,
                        bindings = gestureBindings,
                        onDismiss = { gestureEditor = false },
                        onSetBinding = { gestureScope, trigger, action ->
                            scope.launch {
                                prefs.setGestureBinding(
                                    scope = gestureScope,
                                    pageCoord = if (gestureScope == GestureScope.PAGE) {
                                        currentPage?.let { it.coordX to it.coordY }
                                    } else {
                                        null
                                    },
                                    trigger = trigger,
                                    action = action,
                                )
                            }
                        },
                    )
                }

                message?.let { status ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 64.dp, start = 16.dp, end = 16.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        GlassPanel(
                            modifier = Modifier.clickable { message = null },
                        ) {
                            Text(
                                text = status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    }
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
 * storage under their original stable token. Returns the number of skipped rows.
 */
private suspend fun restoreBackgrounds(
    entries: List<BackedUpBackground>,
    database: VoidDatabase,
    repository: BackgroundRepository,
): Int {
    // An empty array is an intentional "clear all backgrounds" instruction.
    var skipped = 0
    val keptTokens = HashSet<String>()
    val restored = entries.mapNotNull { entry ->
        val scope = runCatching { BackgroundScope.valueOf(entry.scope) }.getOrNull()
            ?: run { skipped++; return@mapNotNull null }
        val kind = runCatching { BackgroundKind.valueOf(entry.kind) }.getOrNull()
            ?: run { skipped++; return@mapNotNull null }
        val pageId = if (scope == BackgroundScope.PAGE) {
            val x = entry.pageCoordX ?: run { skipped++; return@mapNotNull null }
            val y = entry.pageCoordY ?: run { skipped++; return@mapNotNull null }
            database.pageDao().getByCoordOnce(x, y)?.pageId
                ?: run { skipped++; return@mapNotNull null }
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
        val ok = runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            if (bytes.size > MAX_BACKUP_IMAGE_BYTES) {
                false
            } else {
                repository.importImage(token, bytes)
                keptTokens.add(token)
                true
            }
        }.getOrDefault(false)
        if (!ok) skipped++
    }
    // Any image not carried by this backup is stale once replace-style restore completes.
    runCatching { repository.deleteUnusedImages(keptTokens) }
    return skipped
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

private fun GridItemEntity.isAppListWidget(): Boolean =
    widgetKind == BuiltInWidgetKind.APP_LIST ||
        BuiltInWidgets.fromReference(referenceId) == BuiltInWidgetKind.APP_LIST

@Composable
private fun HomeScreen(
    pages: List<PageInfo>,
    edges: List<EdgeInfo>,
    items: List<GridItemEntity>,
    currentPageId: Long,
    widgetController: WidgetHostController,
    customWidgets: CustomWidgetBundle = CustomWidgetBundle(),
    onPageChanged: (targetPageId: Long, storedDirection: SwipeDirection) -> Unit,
    reverseTarget: (currentPageId: Long, gestureDirection: SwipeDirection) -> Long? =
        { _, _ -> null },
    onHomeRequested: () -> Unit,
    onAppClick: (String) -> Unit,
    onOpenDrawerPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onEditItems: () -> Unit,
    onOpenWidgetPicker: () -> Unit,
    onLongPress: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    showCoordinateDetail: Boolean = true,
    canvasEditing: Boolean = false,
    showEditorHint: Boolean = false,
    selectedCanvasItemId: Long? = null,
    onSelectCanvasItem: (GridItemEntity) -> Unit = {},
    onMoveCanvasItem: (GridItemEntity, Int, Int) -> Boolean = { _, _, _ -> false },
    onResizeCanvasItem: (GridItemEntity) -> Unit = {},
    onResizeCanvasGeometry: (GridGeometry) -> Boolean = { false },
    onWidgetAction: (GridItemEntity) -> Unit = {},
    apps: List<AppInfo> = emptyList(),
    labelForPackage: (String) -> String = { it.substringAfterLast('.') },
    appListAppearances: Map<Long, AppListAppearance> = emptyMap(),
    onAppListMenu: (GridItemEntity) -> Unit = {},
    onRemoveCanvasItem: (GridItemEntity) -> Unit = {},
    onRebindWidget: (GridItemEntity) -> Unit = {},
    onExitCanvasEditor: () -> Unit = {},
    floatingKind: BuiltInWidgetKind? = null,
    floatingPoint: Offset? = null,
    floatingSpanX: Int = 1,
    floatingSpanY: Int = 1,
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
            reverseTarget = reverseTarget,
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
            floatingSpanX = floatingSpanX,
            floatingSpanY = floatingSpanY,
            widgetContent = { item ->
                val builtIn = item.widgetKind ?: BuiltInWidgets.fromReference(item.referenceId)
                val customId = WidgetReference.customId(item.referenceId)
                when {
                    builtIn == BuiltInWidgetKind.CLOCK -> ClockWidgetContent()
                    builtIn == BuiltInWidgetKind.PAGE_INFO -> PageInfoWidgetContent(
                        page = page,
                        showCoordinates = showCoordinateDetail,
                    )
                    builtIn == BuiltInWidgetKind.APP_LIST -> AppListWidgetContent(
                        packages = BuiltInWidgets.decodePackages(item.appListPackages),
                        labelForPackage = labelForPackage,
                        appearance = appListAppearances[item.itemId],
                        editing = canvasEditing,
                        onLaunch = onAppClick,
                        onLongPress = {
                            lastItemTouch = System.nanoTime()
                            onSelectCanvasItem(item)
                        },
                        onItemTouched = { lastItemTouch = System.nanoTime() },
                    )
                    customId != null -> {
                        val definition = customWidgets.widgets.firstOrNull { it.id == customId }
                        when {
                            definition == null -> MissingCustomWidget(customId)
                            definition.runtime == CUSTOM_WIDGET_RUNTIME_WEBVIEW ->
                                CustomWebViewWidget(definition, Modifier.fillMaxSize())
                            else -> UnknownWidgetReference("${definition.runtime}:${definition.id}")
                        }
                    }
                    else -> {
                        val rawReference = WidgetReference.unwrap(item.referenceId)
                        val widgetId = rawReference.toIntOrNull()
                        val provider = WidgetReference.portableProvider(item.referenceId)
                        when {
                            widgetId != null -> WidgetHostView(
                                controller = widgetController,
                                appWidgetId = widgetId,
                                modifier = Modifier.fillMaxSize(),
                            )
                            provider != null && provider.contains('/') &&
                                ComponentName.unflattenFromString(provider) != null -> Text(
                                "REBIND WIDGET",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { onRebindWidget(item) }
                                    .padding(8.dp),
                            )
                            else -> UnknownWidgetReference(rawReference)
                        }
                    }
                }
            },
            pageBackground = pageBackground,
            modifier = Modifier.fillMaxSize(),
        )

        if (canvasEditing) {
            GlassPanel(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MAP",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { onOpenMap() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "MENU",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable { menuExpanded = !menuExpanded }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    if (menuExpanded) {
                        EditorMenuItem("APPS", onOpenDrawerPage)
                        EditorMenuItem("EDIT", onEditItems)
                        EditorMenuItem("SETTINGS", onOpenSettings)
                        EditorMenuItem("WIDGETS", onOpenWidgetPicker)
                    }
                }
            }

            // The explanatory label is shown only for the first editor session;
            // later sessions keep the action buttons without the description.
            if (showEditorHint || selectedCanvasItem != null) {
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
                        if (showEditorHint) {
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
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        if (selectedCanvasItem != null) {
                            if (selectedCanvasItem.isAppListWidget()) {
                                Text(
                                    text = "MENU",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onAppListMenu(selectedCanvasItem) }
                                        .padding(8.dp),
                                )
                            }
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
    title: String,
    coordX: Int,
    coordY: Int,
    showCoordinateDetail: Boolean = true,
    onOpenMap: () -> Unit,
    onBack: () -> Unit,
    onReverseSwipe: (SwipeDirection) -> Unit,
    onLaunch: (packageName: String) -> Unit,
    onTogglePin: (packageName: String) -> Unit,
    onCustomize: (packageName: String) -> Unit,
) {
    val drawerListState = rememberLazyListState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onOpenMap() })
            }
            .pointerInput(Unit) {
                val thresholdPx = 80.dp.toPx()
                awaitPointerEventScope {
                    var activeId: Long? = null
                    var startX = 0f
                    var startY = 0f
                    var latched = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (activeId == null && event.changes.count { it.pressed } == 1) {
                            val down = event.changes.first { it.pressed }
                            activeId = down.id.value
                            startX = down.position.x
                            startY = down.position.y
                            latched = false
                        }
                        val id = activeId
                        if (id != null) {
                            val active = event.changes.firstOrNull {
                                it.id.value == id && it.pressed
                            }
                            if (active == null) {
                                activeId = null
                                latched = false
                            } else if (!latched) {
                                val dx = active.position.x - startX
                                val dy = active.position.y - startY
                                if (kotlin.math.abs(dx) >= thresholdPx ||
                                    kotlin.math.abs(dy) >= thresholdPx
                                ) {
                                    val direction = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                                        if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                                    } else {
                                        if (dy > 0) SwipeDirection.DOWN else SwipeDirection.UP
                                    }
                                    // Vertical reversals only at the list edges so ordinary
                                    // app-list scrolling never navigates away. Horizontal
                                    // reversals are always allowed.
                                    val allowed = when (direction) {
                                        SwipeDirection.UP -> !drawerListState.canScrollForward
                                        SwipeDirection.DOWN -> !drawerListState.canScrollBackward
                                        else -> true
                                    }
                                    if (allowed) {
                                        latched = true
                                        onReverseSwipe(direction)
                                    }
                                }
                            }
                        }
                    }
                }
            },
    ) {
        AppDrawerOverlay(
            apps = apps,
            pinned = pinned,
            favorites = favorites,
            hidden = hidden,
            listState = drawerListState,
            onBack = onBack,
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
            onCustomize = onCustomize,
        )
        GlassPanel(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (showCoordinateDetail) {
                    Text(
                        text = "$coordX, $coordY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BuiltInWidgetPickerScreen(
    onLongPress: (BuiltInWidgetKind) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "BACK",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "CHOOSE A WIDGET",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "LONG-PRESS TO PICK UP",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(BuiltInWidgetKind.entries, key = { it.name }) { kind ->
                Text(
                    text = BuiltInWidgets.label(kind),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(kind) {
                            detectTapGestures(onLongPress = { onLongPress(kind) })
                        }
                        .padding(vertical = 16.dp),
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
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
            .background(MaterialTheme.colorScheme.surface),
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
                Text(
                    text = "ADD APPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
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
private fun PageInfoWidgetContent(page: PageInfo, showCoordinates: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = page.displayTitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showCoordinates) {
                Text(
                    text = "${page.coordX}, ${page.coordY}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (page.pageType == PageType.HOME) {
                Text(
                    text = "HOME",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListWidgetContent(
    packages: List<String>,
    labelForPackage: (String) -> String,
    appearance: AppListAppearance?,
    editing: Boolean,
    onLaunch: (String) -> Unit,
    onLongPress: () -> Unit,
    onItemTouched: () -> Unit,
) {
    val accent = remember(appearance?.colorHex) {
        val hex = appearance?.colorHex?.trim()?.removePrefix("#")
        if (hex.isNullOrEmpty()) {
            null
        } else {
            runCatching { Color(android.graphics.Color.parseColor("#$hex")) }.getOrNull()
        }
    }
    val rowStyle = if (appearance?.dense == true) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.bodySmall
    }
    val rowPadding = if (appearance?.dense == true) 4.dp else 8.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                accent?.let { Modifier.background(it.copy(alpha = 0.18f)) }
                    ?: Modifier,
            ),
    ) {
        if (packages.isEmpty()) {
            Text(
                text = "OPEN WIDGET MENU TO ADD APPS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                items(packages, key = { it }) { packageName ->
                    Text(
                        text = labelForPackage(packageName),
                        style = rowStyle,
                        color = accent ?: MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                enabled = !editing,
                                onClick = {
                                    onItemTouched()
                                    onLaunch(packageName)
                                },
                                onLongClick = {
                                    onItemTouched()
                                    onLongPress()
                                },
                            )
                            .padding(vertical = rowPadding),
                    )
                }
            }
        }
    }
}
