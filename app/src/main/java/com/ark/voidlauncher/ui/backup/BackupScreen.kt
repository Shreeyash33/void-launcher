package com.ark.voidlauncher.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// All colours come from MaterialTheme tokens; these are only opacity modifiers.
private const val HairlineAlpha = 0.12f
private const val MutedAlpha = 0.55f

/**
 * Text-first backup/restore screen. Export and import are driven entirely by
 * the caller (which supplies the SAF launchers); this composable reports the
 * latest [status] string, confirms a destructive import, and exposes all three
 * actions.
 */
@Composable
fun BackupScreen(
    status: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmImport by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "BACKUP & RESTORE",
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = scheme.onBackground.copy(alpha = HairlineAlpha))

        BackupRow(
            label = "EXPORT",
            caption = "Save launcher state to a JSON file.",
            onClick = onExport,
        )
        BackupRow(
            label = "IMPORT",
            caption = "Restore launcher state from a JSON file.",
            onClick = { confirmImport = true },
        )
        BackupRow(
            label = "BACK",
            caption = "Return to settings.",
            onClick = onBack,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface.copy(alpha = MutedAlpha),
        )
        Text(
            text = status ?: "NO OPERATION YET",
            style = MaterialTheme.typography.bodyMedium,
            color = if (status == null) {
                scheme.onSurface.copy(alpha = MutedAlpha)
            } else {
                scheme.onSurface
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "WIDGETS: imported widgets are placeholders. Only the provider is stored, " +
                "never the live widget ID. Rebind each widget after a restore.",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface.copy(alpha = MutedAlpha),
        )
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            containerColor = scheme.surface,
            titleContentColor = scheme.onSurface,
            textContentColor = scheme.onSurface,
            title = {
                Text(
                    text = "IMPORT BACKUP?",
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            text = {
                Text(
                    text = "Importing replaces the current pages, edges, items and customizations. " +
                        "Export first if you want to keep them.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmImport = false
                        onImport()
                    },
                ) {
                    Text(text = "IMPORT", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) {
                    Text(text = "CANCEL", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

@Composable
private fun BackupRow(
    label: String,
    caption: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface.copy(alpha = MutedAlpha),
        )
    }
    HorizontalDivider(color = scheme.onBackground.copy(alpha = HairlineAlpha))
}
