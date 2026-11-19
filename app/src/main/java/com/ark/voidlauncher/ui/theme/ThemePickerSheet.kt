package com.ark.voidlauncher.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.ThemeMode

/**
 * Text-only theme chooser used by the overview's THEMES entry point.
 * Rows come from [ThemeMode.entries], so CUSTOM appears automatically once the
 * enum gains it. Selecting CUSTOM applies the saved palette; editing it is a
 * separate explicit action so a tap on the row never opens the editor by surprise.
 */
@Composable
fun ThemePickerSheet(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onEditCustom: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "THEME", style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == current
                    Text(
                        text = if (selected) "${mode.name}  (CURRENT)" else mode.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(mode)
                                onDismiss()
                            }
                            .padding(vertical = 14.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CLOSE", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onEditCustom) {
                Text(
                    text = "EDIT CUSTOM THEME",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}
