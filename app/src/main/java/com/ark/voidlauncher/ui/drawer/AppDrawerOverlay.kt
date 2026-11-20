package com.ark.voidlauncher.ui.drawer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.AppInfo

/**
 * Text-only app drawer. The search field takes focus as soon as the overlay is
 * composed, so typing works immediately. Hidden apps never appear; favorites
 * sort ahead of everything else, which stays alphabetical by label.
 *
 * Stateless: the only local state is the search query. All mutations are
 * reported through the callbacks supplied by the host.
 */
@Composable
fun AppDrawerOverlay(
    apps: List<AppInfo>,
    pinned: Set<String>,
    favorites: Set<String>,
    hidden: Set<String>,
    onBack: () -> Unit,
    onLaunch: (packageName: String) -> Unit,
    onTogglePin: (packageName: String) -> Unit,
    onCustomize: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val visibleApps = remember(apps, favorites, hidden, query) {
        val term = query.trim()
        apps.asSequence()
            .distinctBy { it.packageName }
            .filterNot { it.packageName in hidden }
            .filter {
                term.isEmpty() ||
                    it.label.contains(term, ignoreCase = true) ||
                    it.packageName.contains(term, ignoreCase = true)
            }
            .sortedWith(
                compareBy<AppInfo> { it.packageName !in favorites }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.label }
                    .thenBy { it.packageName },
            )
            .toList()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 28.dp),
    ) {
        Text(
            text = "BACK",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(8.dp),
        )

        Spacer(Modifier.height(16.dp))

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = {
                Text("SEARCH APPS", style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                disabledContainerColor = MaterialTheme.colorScheme.background,
                focusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                unfocusedIndicatorColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "APPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        )

        Spacer(Modifier.height(4.dp))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            if (visibleApps.isEmpty()) {
                item {
                    Text(
                        text = if (query.isBlank()) "NO APPS AVAILABLE" else "NO MATCHES",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    )
                }
            } else {
                items(visibleApps, key = { it.packageName }) { app ->
                    DrawerRow(
                        app = app,
                        isPinned = app.packageName in pinned,
                        onLaunch = { onLaunch(app.packageName) },
                        onTogglePin = { onTogglePin(app.packageName) },
                        onCustomize = { onCustomize(app.packageName) },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(
    app: AppInfo,
    isPinned: Boolean,
    onLaunch: () -> Unit,
    onTogglePin: () -> Unit,
    onCustomize: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "EDIT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier
                .clickable(onClick = onCustomize)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Text(
            text = if (isPinned) "REMOVE" else "PIN",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onTogglePin)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}
