package com.ark.voidlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.data.migration.DataStoreToRoomMigration
import com.ark.voidlauncher.ui.theme.VoidTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val prefs = remember { LauncherPrefs(context) }
            val repository = remember { AppRepository(context) }
            val apps = remember { repository.installedApps() }
            val pinned by prefs.pinnedApps.collectAsState(initial = emptySet())
            val themeMode by prefs.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                DataStoreToRoomMigration(
                    context = context,
                    database = VoidDatabase.getInstance(context),
                    dataStore = prefs.dataStore,
                ).migrate()
            }

            VoidTheme(themeMode) {
                LauncherScreen(
                    apps = apps,
                    pinned = pinned,
                    themeMode = themeMode,
                    onLaunch = { repository.launch(it) },
                    onTogglePin = { scope.launch { prefs.togglePinned(it) } },
                    onThemeChange = { scope.launch { prefs.setTheme(it) } },
                )
            }
        }
    }
}

private enum class Screen { HOME, DRAWER, SETTINGS }

@Composable
private fun LauncherScreen(
    apps: List<AppInfo>,
    pinned: Set<String>,
    themeMode: ThemeMode,
    onLaunch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
) {
    var screen by remember { mutableStateOf(Screen.HOME) }

    BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }

    when (screen) {
        Screen.HOME -> HomeScreen(
            apps = apps,
            pinned = pinned,
            onLaunch = onLaunch,
            onOpenDrawer = { screen = Screen.DRAWER },
            onOpenSettings = { screen = Screen.SETTINGS },
        )

        Screen.DRAWER -> DrawerScreen(
            apps = apps,
            pinned = pinned,
            onBack = { screen = Screen.HOME },
            onLaunch = onLaunch,
            onTogglePin = onTogglePin,
        )

        Screen.SETTINGS -> SettingsScreen(
            themeMode = themeMode,
            onBack = { screen = Screen.HOME },
            onThemeChange = onThemeChange,
        )
    }
}

@Composable
private fun HomeScreen(
    apps: List<AppInfo>,
    pinned: Set<String>,
    onLaunch: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pinnedApps = remember(apps, pinned) {
        apps.filter { it.packageName in pinned }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        Clock()

        Spacer(modifier = Modifier.height(40.dp))

        if (pinnedApps.isEmpty()) {
            Text("Nothing pinned yet.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Open APPS below to choose a few essentials.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            pinnedApps.forEach { app ->
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunch(app.packageName) }
                        .padding(vertical = 10.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = "APPS",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onOpenDrawer)
                    .padding(vertical = 8.dp),
            )
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }

    val date = Date(now)
    Text(
        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date),
        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.ExtraLight),
    )
    Text(
        text = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(date),
        style = MaterialTheme.typography.bodyMedium,
    )
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
        if (search.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.contains(search, ignoreCase = true) ||
                    it.packageName.contains(search, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text(
            text = "BACK",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(8.dp),
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(visibleApps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLaunch(app.packageName) }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (app.packageName in pinned) "REMOVE" else "PIN",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clickable { onTogglePin(app.packageName) }
                            .padding(8.dp),
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
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        Text(
            text = "BACK",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
        )

        Spacer(modifier = Modifier.height(40.dp))

        Text("THEME", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ThemeMode.entries.forEach { mode ->
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mode == themeMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                    },
                    modifier = Modifier
                        .clickable { onThemeChange(mode) }
                        .padding(vertical = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Text(
            text = "SET AS HOME",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .clickable {
                    context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                .padding(vertical = 10.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Void Launcher 0.1.0",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}
