package com.ark.voidlauncher.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Where a nickname edit is stored: shared Room customization or drawer-only DataStore. */
enum class AppEditScope { GLOBAL, DRAWER_ONLY }

/**
 * Single-app nickname editor opened from the app drawer.
 *
 * Both current labels are shown read-only. SAVE emits the chosen scope and the
 * trimmed nickname (null clears it), then dismisses; CLEAR only empties the field
 * so the user can still cancel with DONE.
 */
@Composable
fun AppAppEditModal(
    packageName: String,
    appLabel: String,
    globalNickname: String?,
    drawerNickname: String?,
    onDismiss: () -> Unit,
    onSave: (scope: AppEditScope, nickname: String?) -> Unit,
) {
    val scopes = listOf(AppEditScope.GLOBAL, AppEditScope.DRAWER_ONLY)
    var scope by remember(packageName) {
        mutableStateOf(
            if (drawerNickname != null && globalNickname == null) {
                AppEditScope.DRAWER_ONLY
            } else {
                AppEditScope.GLOBAL
            },
        )
    }
    var globalDraft by remember(packageName, globalNickname) {
        mutableStateOf(globalNickname.orEmpty())
    }
    var drawerDraft by remember(packageName, drawerNickname) {
        mutableStateOf(drawerNickname.orEmpty())
    }
    val draft = if (scope == AppEditScope.GLOBAL) globalDraft else drawerDraft

    EditorSheetScaffold(title = "Edit app", onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = appLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )

            EditorSectionHeader("Current labels")
            EditorHint(text = "GLOBAL: ${globalNickname ?: "NOT SET"}")
            EditorHint(text = "DRAWER: ${drawerNickname ?: "NOT SET"}")

            EditorSectionHeader("Scope")
            EditorChoiceRow(
                label = "Scope",
                options = scopes.map {
                    if (it == AppEditScope.GLOBAL) "GLOBAL" else "DRAWER ONLY"
                },
                selectedIndex = scopes.indexOf(scope),
                onSelect = { scope = scopes[it] },
            )
            EditorHint(
                text = if (scope == AppEditScope.GLOBAL) {
                    "GLOBAL APPLIES WHEREVER THIS APP IS LABELLED"
                } else {
                    "DRAWER ONLY APPLIES IN THE APP DRAWER"
                },
            )

            EditorSectionHeader("Nickname")
            EditorTextField(
                value = draft,
                onValueChange = { value ->
                    if (scope == AppEditScope.GLOBAL) globalDraft = value else drawerDraft = value
                },
                label = "Nickname",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorTextAction(
                    text = "CLEAR",
                    onClick = {
                        if (scope == AppEditScope.GLOBAL) globalDraft = "" else drawerDraft = ""
                    },
                )
                EditorTextAction(
                    text = "SAVE",
                    onClick = {
                        onSave(scope, draft.trim().ifEmpty { null })
                        onDismiss()
                    },
                )
            }
        }
    }
}
