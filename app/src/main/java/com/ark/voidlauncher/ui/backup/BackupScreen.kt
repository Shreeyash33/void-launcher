package com.ark.voidlauncher.ui.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// All colours come from MaterialTheme tokens; these are only opacity modifiers.
private const val HairlineAlpha = 0.12f
private const val MutedAlpha = 0.55f

/**
 * Text-first backup/restore screen. Export and import are driven entirely by
 * the caller (the lead supplies SAF launchers); this composable only reports
 * the latest [status] string and exposes the three actions.
 */
@Composable
fun BackupScreen(
    status: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = "BACKUP & RESTORE",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = HairlineAlpha),
        )

        BackupRow(label = "EXPORT", caption = "Save launcher state to a JSON file.", onClick = onExport)
        BackupRow(label = "IMPORT", caption = "Restore launcher state from a JSON file.", onClick = onImport)
        BackupRow(label = "BACK", caption = "Return to settings.", onClick = onBack)

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "STATUS",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
        )
        Text(
            text = status ?: "NO OPERATION YET",
            style = MaterialTheme.typography.bodyMedium,
            color = if (status == null) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "WIDGETS: imported widgets are placeholders. Only the provider is stored, never the live widget ID. Rebind each widget after a restore.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
        )
    }
}

@Composable
private fun BackupRow(
    label: String,
    caption: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = HairlineAlpha),
    )
}
