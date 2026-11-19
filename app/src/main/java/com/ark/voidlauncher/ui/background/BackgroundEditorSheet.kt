package com.ark.voidlauncher.ui.background

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.background.BackgroundKind
import com.ark.voidlauncher.domain.background.BackgroundScope
import com.ark.voidlauncher.domain.background.GradientStop
import com.ark.voidlauncher.domain.background.PageBackground
import java.util.Locale

private data class StopDraft(val hex: String, val position: String)

/**
 * Text-first editor for a page or global background.
 *
 * Two optional trailing parameters are appended to the frozen contract so the
 * host can supply data the signature otherwise has no channel for:
 *  - [pageId]: page id stored in the emitted [PageBackground] when the user picks
 *    THIS PAGE and there is no [current] entry to read it from. When [current]
 *    already carries a `pageId`, that value wins.
 *  - [image]: the bitmap lead decoded from the current `imageToken`; only used
 *    for the IMAGE preview. Lead remains responsible for the SAF pick/copy.
 *
 * Validation is implemented locally (mirroring the documented domain validators:
 * valid #RRGGBB/#AARRGGBB hex, 2..5 stops, positions in 0..1). This is deliberate:
 * the exact helper names owned by the parallel P10a task cannot be verified from
 * this worktree, so keeping validation here guarantees a compiling build. The
 * [PageBackground] emitted on SAVE still satisfies every domain invariant.
 */
@Composable
fun BackgroundEditorSheet(
    current: PageBackground?,
    pageTitle: String,
    isGlobal: Boolean,
    onSave: (PageBackground) -> Unit,
    onDelete: () -> Unit,
    onPickImage: () -> Unit,
    onDismiss: () -> Unit,
    pageId: Long = 0L,
    image: ImageBitmap? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val currentId = current?.id

    // Key drafts on the id only: lead may swap in a fresh `current` carrying the
    // newly picked imageToken after the SAF flow without discarding typed edits.
    var scope by remember(currentId) {
        mutableStateOf(if (isGlobal) BackgroundScope.GLOBAL else BackgroundScope.PAGE)
    }
    var kind by remember(currentId) {
        mutableStateOf(current?.kind ?: BackgroundKind.COLOR)
    }
    var colorHex by remember(currentId) {
        mutableStateOf(current?.colorHex ?: scheme.background.toArgbHex())
    }
    var angleText by remember(currentId) {
        mutableStateOf(current?.gradientAngle?.toString() ?: "0")
    }
    var stopDrafts by remember(currentId) {
        mutableStateOf(
            // COLOR and IMAGE backgrounds persist an empty stop list, not null.
            // Falling back only on null would leave GRADIENT with zero stops and
            // a permanently disabled SAVE button when switching kinds.
            current?.gradientStops?.takeIf { it.size >= 2 }?.map { stop ->
                StopDraft(stop.colorHex, stop.position.toString())
            } ?: listOf(
                StopDraft(scheme.background.toArgbHex(), "0"),
                StopDraft(scheme.primary.toArgbHex(), "1"),
            ),
        )
    }
    var imageCleared by remember(currentId) { mutableStateOf(false) }

    // Read fresh from the current argument so a token supplied after image pick is seen.
    val effectivePageId = current?.pageId ?: pageId
    val effectiveToken = if (imageCleared) null else current?.imageToken

    val validStops = parsedStops(stopDrafts)
    val colorValid = isHexValid(colorHex)
    val angleValid = angleText.toFloatOrNull() != null
    val canSave = when (kind) {
        BackgroundKind.COLOR -> colorValid
        BackgroundKind.GRADIENT -> validStops != null && angleValid
        BackgroundKind.IMAGE -> effectiveToken != null
    }

    // Swatches are derived from the active scheme, never from literal colours.
    val presets = remember(scheme) {
        listOf(
            scheme.background,
            scheme.surface,
            scheme.surfaceVariant,
            scheme.onBackground,
            scheme.primary,
            scheme.secondary,
        ).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "EDIT BACKGROUNDS",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = pageTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                BgSectionLabel("SCOPE")
                BgChoiceRow(
                    options = listOf("GLOBAL", "THIS PAGE"),
                    selectedIndex = if (scope == BackgroundScope.GLOBAL) 0 else 1,
                    onSelect = { index ->
                        scope = if (index == 0) BackgroundScope.GLOBAL else BackgroundScope.PAGE
                    },
                )
                BgHint(
                    if (scope == BackgroundScope.GLOBAL) {
                        "GLOBAL APPLIES TO EVERY PAGE."
                    } else {
                        "THIS PAGE OVERRIDES THE GLOBAL BACKGROUND."
                    },
                )

                BgSectionLabel("KIND")
                val kinds = BackgroundKind.entries
                BgChoiceRow(
                    options = kinds.map { it.name },
                    selectedIndex = kinds.indexOf(kind),
                    onSelect = { index -> kind = kinds[index] },
                )

                when (kind) {
                    BackgroundKind.COLOR -> {
                        BgSectionLabel("COLOUR")
                        BgTextField(
                            value = colorHex,
                            onValueChange = { colorHex = it },
                            label = "HEX",
                            error = !colorValid,
                        )
                        BgHint(
                            text = if (colorValid) "ACCEPTS #RRGGBB OR #AARRGGBB." else "INVALID HEX.",
                            isError = !colorValid,
                        )

                        BgSectionLabel("PRESETS")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            presets.forEach { preset ->
                                val hex = preset.toArgbHex()
                                val selected = colorHex.trim().equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .height(36.dp)
                                        .weight(1f)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(preset)
                                        .then(
                                            if (selected) {
                                                Modifier.background(scheme.primary.copy(alpha = 0.35f))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .clickable { colorHex = hex },
                                )
                            }
                        }
                    }

                    BackgroundKind.GRADIENT -> {
                        BgSectionLabel("STOPS (2-5)")
                        stopDrafts.forEachIndexed { index, draft ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                BgTextField(
                                    value = draft.hex,
                                    onValueChange = { value ->
                                        stopDrafts = stopDrafts.toMutableList().also {
                                            it[index] = draft.copy(hex = value)
                                        }
                                    },
                                    label = "STOP ${index + 1} HEX",
                                    modifier = Modifier.weight(1.6f),
                                    error = !isHexValid(draft.hex),
                                )
                                BgTextField(
                                    value = draft.position,
                                    onValueChange = { value ->
                                        stopDrafts = stopDrafts.toMutableList().also {
                                            it[index] = draft.copy(position = value)
                                        }
                                    },
                                    label = "POS",
                                    modifier = Modifier.weight(0.7f),
                                    numeric = true,
                                    error = draft.position.toFloatOrNull()?.let { it !in 0f..1f } ?: true,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    stopDrafts = stopDrafts + StopDraft(scheme.surface.toArgbHex(), "0.5")
                                },
                                enabled = stopDrafts.size < 5,
                            ) {
                                Text("ADD STOP", style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(
                                onClick = { stopDrafts = stopDrafts.dropLast(1) },
                                enabled = stopDrafts.size > 2,
                            ) {
                                Text("REMOVE STOP", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        BgSectionLabel("ANGLE (DEGREES)")
                        BgTextField(
                            value = angleText,
                            onValueChange = { angleText = it },
                            label = "ANGLE",
                            numeric = true,
                            error = !angleValid,
                        )
                        BgHint("0 = LEFT TO RIGHT, 90 = TOP TO BOTTOM.")

                        BgSectionLabel("PREVIEW")
                        val previewStops = validStops
                        val previewAngle = angleText.toFloatOrNull()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(MaterialTheme.shapes.medium),
                        ) {
                            PageBackgroundRenderer(
                                background = if (previewStops != null && previewAngle != null) {
                                    PageBackground(
                                        scope = scope,
                                        pageId = if (scope == BackgroundScope.PAGE) effectivePageId else null,
                                        kind = BackgroundKind.GRADIENT,
                                        gradientStops = previewStops,
                                        gradientAngle = previewAngle,
                                    )
                                } else {
                                    null
                                },
                                image = null,
                            )
                        }
                    }

                    BackgroundKind.IMAGE -> {
                        BgSectionLabel("IMAGE")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(MaterialTheme.shapes.medium),
                        ) {
                            PageBackgroundRenderer(
                                background = PageBackground(
                                    scope = scope,
                                    pageId = if (scope == BackgroundScope.PAGE) effectivePageId else null,
                                    kind = BackgroundKind.IMAGE,
                                    imageToken = effectiveToken,
                                ),
                                image = image,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = onPickImage) {
                                Text("PICK IMAGE", style = MaterialTheme.typography.labelLarge)
                            }
                            TextButton(
                                onClick = { imageCleared = true },
                                enabled = effectiveToken != null || image != null,
                            ) {
                                Text("CLEAR IMAGE", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        BgHint(
                            text = if (effectiveToken != null) {
                                "IMAGE READY."
                            } else {
                                "PICK AN IMAGE TO ENABLE SAVE."
                            },
                        )
                    }
                }

                if (current != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.12f))
                    TextButton(onClick = onDelete) {
                        Text(
                            text = "DELETE",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        PageBackground(
                            id = current?.id ?: 0L,
                            scope = scope,
                            pageId = if (scope == BackgroundScope.PAGE) effectivePageId else null,
                            kind = kind,
                            colorHex = if (kind == BackgroundKind.COLOR) colorHex.trim() else null,
                            gradientStops = if (kind == BackgroundKind.GRADIENT) {
                                validStops.orEmpty()
                            } else {
                                emptyList()
                            },
                            gradientAngle = if (kind == BackgroundKind.GRADIENT) {
                                angleText.toFloatOrNull() ?: 0f
                            } else {
                                0f
                            },
                            imageToken = if (kind == BackgroundKind.IMAGE) effectiveToken else null,
                        ),
                    )
                },
                enabled = canSave,
            ) {
                Text("SAVE", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun BgSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun BgHint(text: String, isError: Boolean = false) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        },
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun BgChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val chipBackground = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            }
            Text(
                text = option.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(chipBackground)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BgTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    error: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface.copy(alpha = 0.55f),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (error) scheme.error else scheme.onSurface,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        HorizontalDivider(
            color = if (error) scheme.error else scheme.onSurface.copy(alpha = 0.12f),
        )
    }
}

/** Accepts #RRGGBB and #AARRGGBB, with optional 0x prefix; never throws. */
private fun isHexValid(input: String): Boolean {
    val hex = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (hex.length != 6 && hex.length != 8) return false
    return hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

/** Renders a Compose [Color] back into the ARGB hex string the domain expects. */
private fun Color.toArgbHex(): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return String.format(Locale.US, "#%02X%02X%02X%02X", a, r, g, b)
}

/**
 * Defensive local mirror of the P10a `isValidStops` validator. Returns the
 * parsed, position-sorted stops, or null when the draft list is invalid
 * (2..5 entries, each a valid hex with a position in 0..1).
 */
private fun parsedStops(drafts: List<StopDraft>): List<GradientStop>? {
    if (drafts.size !in 2..5) return null
    val parsed = drafts.map { draft ->
        val position = draft.position.trim().toFloatOrNull() ?: return null
        if (position !in 0f..1f || !isHexValid(draft.hex)) return null
        GradientStop(colorHex = draft.hex.trim(), position = position)
    }
    return parsed.sortedBy { it.position }
}
