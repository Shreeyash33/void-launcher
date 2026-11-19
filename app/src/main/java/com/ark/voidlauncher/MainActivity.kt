package com.ark.voidlauncher

import android.app.Activity.RESULT_OK
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.data.db.GridItemEntity
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.data.migration.DataStoreToRoomMigration
import com.ark.voidlauncher.data.repository.LauncherRepository
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.placement.PlacementResolver
import com.ark.voidlauncher.domain.placement.PlacementResult
import com.ark.voidlauncher.system.widgets.WidgetEvents
import com.ark.voidlauncher.system.widgets.WidgetHostController
import com.ark.voidlauncher.system.widgets.WidgetHostView
import com.ark.voidlauncher.ui.editor.AppTypographyEditorSheet
import com.ark.voidlauncher.ui.editor.EdgeChange
import com.ark.voidlauncher.ui.editor.EditorValidation
import com.ark.voidlauncher.ui.editor.ItemEditorSheet
import com.ark.voidlauncher.ui.editor.PageEditorSheet
import com.ark.voidlauncher.ui.spatial.SpatialPageCanvas
import com.ark.voidlauncher.ui.theme.VoidTheme
import kotlinx.coroutines.launch

private enum class Screen { HOME, DRAWER, SETTINGS, WIDGET_PICKER }
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

            val apps = remember { appRepository.installedApps() }
            val pinned by prefs.pinnedApps.collectAsState(initial = emptySet())
            val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val pages by repository.pages.collectAsState(initial = emptyList())
            val edges by repository.edges.collectAsState(initial = emptyList())
            val items by repository.items.collectAsState(initial = emptyList())
            val customizations by repository.customizations.collectAsState(initial = emptyList())
            val homePageId by repository.homePageId.collectAsState(initial = VoidDatabase.ORIGIN_PAGE_ID)

            var currentPageId by remember { mutableLongStateOf(VoidDatabase.ORIGIN_PAGE_ID) }
            var screen by remember { mutableStateOf(Screen.HOME) }
            var editor by remember { mutableStateOf(Editor.NONE) }
            var message by remember { mutableStateOf<String?>(null) }

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

            BackHandler(enabled = screen != Screen.HOME || editor != Editor.NONE) {
                if (editor != Editor.NONE) editor = Editor.NONE else screen = Screen.HOME
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

            VoidTheme(themeMode) {
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
                        )

                        Screen.DRAWER -> DrawerScreen(
                            apps = apps,
                            pinned = pinned,
                            onBack = { screen = Screen.HOME },
                            onLaunch = { appRepository.launch(it) },
                            onTogglePin = { scope.launch { prefs.togglePinned(it) } },
                        )

                        Screen.SETTINGS -> SettingsScreen(
                            themeMode = themeMode,
                            onBack = { screen = Screen.HOME },
                            onThemeChange = { scope.launch { prefs.setTheme(it) } },
                            onEditPages = { editor = Editor.PAGES },
                            onEditItems = { editor = Editor.ITEMS },
                            onEditTypography = { editor = Editor.TYPOGRAPHY },
                            onAddWidget = ::openWidgetPicker,
                        )

                        Screen.WIDGET_PICKER -> WidgetPickerScreen(
                            widgetController = widgetController,
                            onPick = ::beginAddWidget,
                            onBack = { screen = Screen.SETTINGS },
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
            }
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
            widgetContent = { item ->
                val widgetId = item.referenceId.toIntOrNull()
                if (widgetId != null) {
                    WidgetHostView(
                        controller = widgetController,
                        appWidgetId = widgetId,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("WIDGET UNAVAILABLE", style = MaterialTheme.typography.labelSmall)
                }
            },
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
                text = "SWIPE TO MOVE · DOUBLE-TAP FOR HOME\nSETTINGS → ADD WIDGET",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun DrawerScreen(
    apps: List<AppInfo>,
    pinned: Set<String>,
    onBack: () -> Unit,
    onLaunch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visibleApps = remember(apps, query) {
        val search = query.trim()
        if (search.isEmpty()) apps else apps.filter {
            it.label.contains(search, ignoreCase = true) ||
                it.packageName.contains(search, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp)) {
        Text("BACK", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        Spacer(Modifier.height(16.dp))
        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(visibleApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onLaunch(app.packageName) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    Text(
                        text = if (app.packageName in pinned) "REMOVE" else "PIN",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.clickable { onTogglePin(app.packageName) }.padding(8.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onEditPages: () -> Unit,
    onEditItems: () -> Unit,
    onEditTypography: () -> Unit,
    onAddWidget: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp)) {
        Text("BACK", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp))
        Spacer(Modifier.height(24.dp))

        Text("THEME", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ThemeMode.entries.forEach { mode ->
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mode == themeMode) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                    modifier = Modifier.clickable { onThemeChange(mode) }.padding(vertical = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Text("MAP", style = MaterialTheme.typography.labelMedium)
        SettingsAction("EDIT PAGES", onEditPages)
        SettingsAction("EDIT ITEMS", onEditItems)
        SettingsAction("APP TYPOGRAPHY", onEditTypography)
        SettingsAction("ADD WIDGET", onAddWidget)

        Spacer(Modifier.height(32.dp))
        Text(
            text = "SET AS HOME",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable {
                context.startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.padding(vertical = 10.dp),
        )

        Spacer(Modifier.weight(1f))
        Text("Void Launcher 0.2.0", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
    }
}

@Composable
private fun SettingsAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

@Composable
private fun WidgetPickerScreen(
    widgetController: WidgetHostController,
    onPick: (ComponentName) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val providers = remember { widgetController.installedProviders() }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text("BACK", style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        Spacer(Modifier.height(20.dp))
        Text("CHOOSE A WIDGET", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
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
