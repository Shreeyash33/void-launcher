package com.ark.voidlauncher.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.AppInfo
import com.ark.voidlauncher.domain.AppListAppearance

/**
 * Body of the APP_LIST widget's MENU sheet.
 *
 * Membership and appearance changes are emitted immediately through callbacks;
 * the host owns the actual data. The external add-widget flow is not part of this
 * modal. Only valid hex values reach [onAppearanceChange].
 */
@Composable
fun AppListWidgetMenuModal(
    itemId: Long,
    packages: List<String>,
    apps: List<AppInfo>,
    labelForPackage: (String) -> String,
    appearance: AppListAppearance?,
    onDismiss: () -> Unit,
    onTogglePackage: (String) -> Unit,
    onSetNickname: (String, String?) -> Unit,
    onAppearanceChange: (AppListAppearance) -> Unit,
) {
    var colorDraft by remember(itemId) { mutableStateOf(appearance?.colorHex.orEmpty()) }
    var hexError by remember(itemId) { mutableStateOf<String?>(null) }
    val nicknameDrafts = remember(itemId) { mutableStateMapOf<String, String>() }

    val included = remember(packages) { packages.toSet() }
    val candidates = remember(packages, apps) {
        val known = apps.map { it.packageName }.toSet()
        (apps.map { it.packageName } + packages.filterNot { it in known }).distinct()
    }

    EditorSheetScaffold(title = "App list menu", onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                EditorHint("TAP ADD OR REMOVE TO CHANGE MEMBERSHIP. NICKNAMES APPLY GLOBALLY WHERE THIS APP IS LABELLED.")
            }

            item { EditorSectionHeader("Apps") }
            if (candidates.isEmpty()) {
                item { EditorHint("NO APPS AVAILABLE") }
            }
            lazyItems(candidates, key = { it }) { packageName ->
                val label = labelForPackage(packageName).ifBlank {
                    apps.firstOrNull { it.packageName == packageName }?.label ?: packageName
                }
                val isIncluded = packageName in included
                val draft = nicknameDrafts[packageName] ?: ""

                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorTextAction(
                        text = if (isIncluded) "REMOVE" else "ADD",
                        onClick = { onTogglePackage(packageName) },
                    )
                    EditorTextAction(
                        text = "CLEAR NAME",
                        onClick = {
                            nicknameDrafts[packageName] = ""
                            onSetNickname(packageName, null)
                        },
                    )
                }
                EditorTextField(
                    value = draft,
                    onValueChange = { nicknameDrafts[packageName] = it },
                    label = "Nickname",
                )
                EditorTextAction(
                    text = "SET NAME",
                    onClick = {
                        onSetNickname(packageName, draft.trim().ifEmpty { null })
                    },
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    thickness = 0.5.dp,
                )
            }

            item { EditorSectionHeader("Appearance") }
            item {
                EditorTextField(
                    value = colorDraft,
                    onValueChange = { text ->
                        colorDraft = text
                        val trimmed = text.trim()
                        if (trimmed.isEmpty()) {
                            hexError = null
                            onAppearanceChange(
                                AppListAppearance(colorHex = null, dense = appearance?.dense ?: false),
                            )
                        } else {
                            val normalized = normalizeHex(trimmed)
                            hexError = if (normalized == null) INVALID_HEX_MESSAGE else null
                            if (normalized != null) {
                                onAppearanceChange(
                                    AppListAppearance(
                                        colorHex = normalized,
                                        dense = appearance?.dense ?: false,
                                    ),
                                )
                            }
                        }
                    },
                    label = "Colour hex",
                )
                hexError?.let { EditorHint(text = it, isError = true) }
                EditorHint("BLANK = THEME DEFAULT. USE #RRGGBB OR #AARRGGBB.")

                EditorTextAction(
                    text = if (appearance?.dense == true) "DENSE: ON" else "DENSE: OFF",
                    onClick = {
                        onAppearanceChange(
                            AppListAppearance(
                                colorHex = resolvedColor(colorDraft, appearance?.colorHex),
                                dense = !(appearance?.dense ?: false),
                            ),
                        )
                    },
                )
            }
        }
    }
}

private const val INVALID_HEX_MESSAGE = "INVALID HEX - USE #RRGGBB OR #AARRGGBB"

/** Returns a normalised #RRGGBB/#AARRGGBB string, or null for any malformed input. */
private fun normalizeHex(input: String): String? {
    val hex = input.trim()
        .removePrefix("#")
        .removePrefix("0x")
        .removePrefix("0X")
    if (hex.length != 6 && hex.length != 8) return null
    if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return "#$hex"
}

/** Keeps the last valid colour when the field is mid-edit; never forwards bad hex. */
private fun resolvedColor(draft: String, fallback: String?): String? {
    val trimmed = draft.trim()
    if (trimmed.isEmpty()) return null
    return normalizeHex(trimmed) ?: fallback?.let(::normalizeHex)
}
