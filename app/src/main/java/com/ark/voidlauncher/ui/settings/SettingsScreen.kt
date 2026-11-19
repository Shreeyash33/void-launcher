package com.ark.voidlauncher.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.ThemeMode
import com.ark.voidlauncher.domain.OrientationMode

private enum class SettingsConfirmation { NONE, ACCESSIBILITY, IMPORT }

/**
 * Text-first settings surface. Everything except the search drawer's query is
 * stateless here: the only local state is which confirmation, if any, is open.
 *
 * The accessibility row never enables anything by itself. Opting in shows an
 * honest disclosure and then calls [onAccessibilityToggle] with `true`; the
 * host is responsible for opening Android's accessibility settings.
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    versionName: String,
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onEditPages: () -> Unit,
    onEditItems: () -> Unit,
    onEditTypography: () -> Unit,
    onAddWidget: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAccessibilityToggle: (Boolean) -> Unit,
    orientationMode: OrientationMode,
    onOrientationChange: (OrientationMode) -> Unit,
    onEditCustomTheme: () -> Unit,
    onEditBackgrounds: () -> Unit,
) {
    val context = LocalContext.current
    var confirmation by remember { mutableStateOf(SettingsConfirmation.NONE) }

    val pending = confirmation
    if (pending != SettingsConfirmation.NONE) {
        SettingsConfirmationDialog(
            kind = pending,
            onDismiss = { confirmation = SettingsConfirmation.NONE },
            onConfirm = {
                when (pending) {
                    SettingsConfirmation.ACCESSIBILITY -> onAccessibilityToggle(true)
                    SettingsConfirmation.IMPORT -> onImport()
                    SettingsConfirmation.NONE -> Unit
                }
                confirmation = SettingsConfirmation.NONE
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 40.dp),
    ) {
        Text(
            text = "BACK",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 8.dp),
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("THEME")
            Spacer(Modifier.height(12.dp))
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

            Spacer(Modifier.height(8.dp))
            SettingsAction("EDIT CUSTOM THEME", onEditCustomTheme)

            Spacer(Modifier.height(28.dp))

            SectionLabel("ORIENTATION")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                OrientationMode.entries.forEach { mode ->
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (mode == orientationMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
                        },
                        modifier = Modifier
                            .clickable { onOrientationChange(mode) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
            Text(
                text = "PORTRAIT IS THE DEFAULT. AUTO FOLLOWS THE DEVICE.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Spacer(Modifier.height(28.dp))

            SectionLabel("APPEARANCE")
            SettingsAction("EDIT BACKGROUNDS", onEditBackgrounds)

            Spacer(Modifier.height(28.dp))

            SectionLabel("MAP")
            SettingsAction("EDIT PAGES", onEditPages)
            SettingsAction("EDIT ITEMS", onEditItems)
            SettingsAction("APP TYPOGRAPHY", onEditTypography)
            SettingsAction("ADD WIDGET", onAddWidget)

            Spacer(Modifier.height(28.dp))

            SectionLabel("BACKUP")
            SettingsAction("EXPORT", onExport)
            SettingsAction("IMPORT") { confirmation = SettingsConfirmation.IMPORT }
            Text(
                text = "EXPORT WRITES A BACKUP FILE. IMPORT REPLACES THE CURRENT LAYOUT.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            Spacer(Modifier.height(28.dp))

            SectionLabel("ACCESSIBILITY")
            Text(
                text = "Off by default. Needed only for system actions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Nothing changes until you switch the service on in system settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
            if (accessibilityEnabled) {
                Text(
                    text = "ACCESSIBILITY: ON",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                SettingsAction("ENABLE ACCESSIBILITY") {
                    confirmation = SettingsConfirmation.ACCESSIBILITY
                }
            }

            Spacer(Modifier.height(28.dp))

            SettingsAction("SET AS HOME") {
                context.startActivity(
                    Intent(Settings.ACTION_HOME_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
            thickness = 0.5.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Void Launcher $versionName",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
    )
}

@Composable
private fun SettingsAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun SettingsConfirmationDialog(
    kind: SettingsConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val isImport = kind == SettingsConfirmation.IMPORT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isImport) "IMPORT BACKUP?" else "ENABLE ACCESSIBILITY?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = if (isImport) {
                    "Importing replaces the current pages, edges, items, and customizations. " +
                        "Export first if you want to keep them."
                } else {
                    "Off by default. Needed only for system actions. This opens Android settings; " +
                        "the service stays off until you switch it on there."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = if (isImport) "IMPORT" else "OPEN SETTINGS",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CANCEL",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    )
}
