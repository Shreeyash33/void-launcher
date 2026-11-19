package com.ark.voidlauncher.ui.customtheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.CustomPalette

// Text-first, low-glare sheet: every colour is derived from a theme token.
private const val MutedAlpha = 0.55f
private const val HairlineAlpha = 0.12f

/** Order matches the CustomPalette constructor and drives both the fields and the preview strip. */
private val FIELD_LABELS = listOf(
    "BACKGROUND",
    "SURFACE",
    "SURFACE VARIANT",
    "ON BACKGROUND",
    "ON SURFACE",
    "PRIMARY",
    "SECONDARY",
    "OUTLINE",
)

/**
 * Editor for the eight ARGB-hex slots of a [CustomPalette].
 *
 * Validation is live: SAVE stays disabled until every field passes
 * [CustomPalette.isValidHex]. Presets populate all fields at once and the
 * preview strip reflects the current (valid) colours. No persistence happens
 * here — the caller receives the complete edited palette via [onSave].
 */
@Composable
fun CustomThemeEditorSheet(
    palette: CustomPalette,
    isDarkBase: Boolean,
    onSave: (CustomPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    val values = remember(palette) {
        mutableStateListOf(
            palette.background,
            palette.surface,
            palette.surfaceVariant,
            palette.onBackground,
            palette.onSurface,
            palette.primary,
            palette.secondary,
            palette.outline,
        )
    }

    fun applyPreset(preset: CustomPalette) {
        values[0] = preset.background
        values[1] = preset.surface
        values[2] = preset.surfaceVariant
        values[3] = preset.onBackground
        values[4] = preset.onSurface
        values[5] = preset.primary
        values[6] = preset.secondary
        values[7] = preset.outline
    }

    val allValid = values.all { CustomPalette.isValidHex(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "EDIT CUSTOM THEME",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "BASE: ${if (isDarkBase) "DARK" else "LIGHT"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { applyPreset(CustomPalette.darkDefault()) }) {
                            Text(
                                text = "DARK PRESET",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        TextButton(onClick = { applyPreset(CustomPalette.lightDefault()) }) {
                            Text(
                                text = "LIGHT PRESET",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = HairlineAlpha),
                )

                Text(
                    text = "PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    values.forEach { hex ->
                        val swatch = parseHexColor(hex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .background(
                                    color = swatch ?: MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                )
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = HairlineAlpha),
                                    shape = MaterialTheme.shapes.small,
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = HairlineAlpha),
                )

                Text(
                    text = "COLORS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                FIELD_LABELS.forEachIndexed { index, label ->
                    HexField(
                        label = label,
                        value = values[index],
                        isValid = CustomPalette.isValidHex(values[index]),
                        onValueChange = { values[index] = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        CustomPalette(
                            background = values[0],
                            surface = values[1],
                            surfaceVariant = values[2],
                            onBackground = values[3],
                            onSurface = values[4],
                            primary = values[5],
                            secondary = values[6],
                            outline = values[7],
                        ),
                    )
                },
                enabled = allValid,
            ) {
                Text(text = "SAVE", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "CANCEL", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun HexField(
    label: String,
    value: String,
    isValid: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MutedAlpha),
                modifier = Modifier.weight(1f),
            )
            if (!isValid) {
                Text(
                    text = "INVALID",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
        HorizontalDivider(
            color = if (isValid) {
                MaterialTheme.colorScheme.outline.copy(alpha = HairlineAlpha)
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

/** Parse a validated hex string into a Compose colour for the preview swatches. */
private fun parseHexColor(value: String): Color? {
    if (!CustomPalette.isValidHex(value)) return null
    val normalized = if (value.startsWith("#")) value else "#$value"
    return try {
        Color(android.graphics.Color.parseColor(normalized))
    } catch (e: IllegalArgumentException) {
        null
    }
}
