package com.ark.voidlauncher.ui.gestures

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.GestureAction
import com.ark.voidlauncher.domain.GestureBindings
import com.ark.voidlauncher.domain.GestureScope
import com.ark.voidlauncher.domain.GestureTrigger
import com.ark.voidlauncher.domain.PageInfo

/** null represents "unbound"; NONE is the same state, so it appears only once. */
private val actionOptions: List<GestureAction?> = buildList<GestureAction?> {
    add(null)
    addAll(GestureAction.entries.filter { it != GestureAction.NONE })
}

private fun readableLabel(enumName: String): String = enumName.replace('_', ' ')

private fun actionLabel(action: GestureAction?): String =
    if (action == null) "NONE (UNBOUND)" else readableLabel(action.name)

/**
 * Modal editor for gesture bindings. Scope is local UI state: bindings are
 * committed immediately through [onSetBinding], so dismissing never discards a
 * choice. The host supplies the current page when PAGE scope is chosen.
 */
@Composable
fun GestureSettingsSheet(
    currentPage: PageInfo?,
    bindings: GestureBindings,
    onDismiss: () -> Unit,
    onSetBinding: (GestureScope, GestureTrigger, GestureAction?) -> Unit,
) {
    var scope by remember { mutableStateOf(GestureScope.GLOBAL) }
    var expandedTrigger by remember { mutableStateOf<GestureTrigger?>(null) }
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurface,
        title = {
            Column {
                Text(
                    text = "GESTURES",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = currentPage?.let { "PAGE ${it.coordX}, ${it.coordY}" } ?: "NO PAGE SELECTED",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(
                    text = "SCOPE",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GestureScopeChip(
                        text = "GLOBAL",
                        selected = scope == GestureScope.GLOBAL,
                        enabled = true,
                        onClick = { scope = GestureScope.GLOBAL },
                    )
                    GestureScopeChip(
                        text = "THIS PAGE",
                        selected = scope == GestureScope.PAGE,
                        enabled = currentPage != null,
                        onClick = { scope = GestureScope.PAGE },
                    )
                }
                if (currentPage == null) {
                    Text(
                        text = "THIS PAGE NEEDS AN OPEN PAGE FIRST.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurface.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = "THE SAME ACTION CAN BE ASSIGNED TO MULTIPLE GESTURES.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 10.dp),
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.12f))

                GestureTrigger.entries.forEach { trigger ->
                    val currentAction = when (scope) {
                        GestureScope.GLOBAL -> {
                            bindings.global.firstOrNull { it.trigger == trigger }?.action
                        }
                        GestureScope.PAGE -> {
                            currentPage?.let { page ->
                                bindings.byPageCoord[page.coordX to page.coordY]
                                    ?.firstOrNull { it.trigger == trigger }
                                    ?.action
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = readableLabel(trigger.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            val expanded = expandedTrigger == trigger
                            Text(
                                text = actionLabel(currentAction),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (currentAction == null) {
                                    scheme.onSurface.copy(alpha = 0.6f)
                                } else {
                                    scheme.primary
                                },
                                modifier = Modifier
                                    .background(
                                        if (expanded) {
                                            scheme.primary.copy(alpha = 0.14f)
                                        } else {
                                            scheme.onSurface.copy(alpha = 0.06f)
                                        },
                                        RoundedCornerShape(4.dp),
                                    )
                                    .clickable {
                                        expandedTrigger = if (expanded) null else trigger
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expandedTrigger = null },
                            ) {
                                actionOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = actionLabel(option),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (option == currentAction) {
                                                    scheme.primary
                                                } else {
                                                    scheme.onSurface
                                                },
                                            )
                                        },
                                        onClick = {
                                            onSetBinding(scope, trigger, option)
                                            expandedTrigger = null
                                        },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.08f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "DONE", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun GestureScopeChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = when {
            !enabled -> scheme.onSurface.copy(alpha = 0.3f)
            selected -> scheme.primary
            else -> scheme.onSurface.copy(alpha = 0.6f)
        },
        modifier = Modifier
            .background(
                if (!enabled) {
                    scheme.onSurface.copy(alpha = 0.03f)
                } else if (selected) {
                    scheme.primary.copy(alpha = 0.14f)
                } else {
                    scheme.onSurface.copy(alpha = 0.06f)
                },
                RoundedCornerShape(4.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
