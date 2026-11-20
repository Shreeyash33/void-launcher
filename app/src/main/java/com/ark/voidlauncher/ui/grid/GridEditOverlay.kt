package com.ark.voidlauncher.ui.grid

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Visual grid guide shown while items are being edited. Dotted boundary lines
 * and a dot at every intersection make cell coordinates legible without adding
 * any pointer handling of its own, so it never steals a tap from the items.
 */
@Composable
internal fun GridGuide(
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    Canvas(modifier = modifier) {
        if (cols <= 0 || rows <= 0) return@Canvas
        val dashes = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
        for (col in 1 until cols) {
            val x = size.width * col / cols.toFloat()
            drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f, pathEffect = dashes)
        }
        for (row in 1 until rows) {
            val y = size.height * row / rows.toFloat()
            drawLine(color, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = dashes)
        }
        for (col in 0..cols) {
            for (row in 0..rows) {
                drawCircle(
                    color = color,
                    radius = 2.5f,
                    center = Offset(
                        size.width * col / cols.toFloat(),
                        size.height * row / rows.toFloat(),
                    ),
                )
            }
        }
    }
}

/** Compact span editor for a selected item. Caller validates the candidate. */
@Composable
fun ItemResizeDialog(
    initialCols: Int,
    initialRows: Int,
    maxCols: Int,
    maxRows: Int,
    onApply: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var colsText by remember { mutableStateOf(initialCols.toString()) }
    var rowsText by remember { mutableStateOf(initialRows.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(text = "RESIZE ITEM", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ResizeField("COLUMNS (1-$maxCols)", colsText) {
                    colsText = it
                    error = null
                }
                ResizeField("ROWS (1-$maxRows)", rowsText) {
                    rowsText = it
                    error = null
                }
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newCols = colsText.trim().toIntOrNull()
                val newRows = rowsText.trim().toIntOrNull()
                if (newCols == null || newRows == null ||
                    newCols !in 1..maxCols || newRows !in 1..maxRows
                ) {
                    error = "ENTER A SPAN INSIDE THE GRID"
                } else {
                    onApply(newCols, newRows)
                }
            }) {
                Text("APPLY", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Composable
private fun ResizeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface.copy(alpha = 0.55f),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = scheme.onSurface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        )
        HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.12f))
    }
}
