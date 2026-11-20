package com.ark.voidlauncher.ui.customtheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small self-contained colour editor used by the custom palette screen.
 *
 * Three ways to enter the same ARGB value: a hex field, four numeric RGBA fields,
 * and a hue wheel with saturation/value sliders. No new dependencies: the wheel is
 * drawn as a series of arcs on a Canvas and converts pointer angles to hue.
 */
@Composable
fun ColorPickerModal(
    initialHex: String,
    onDismiss: () -> Unit,
    onColorChange: (String) -> Unit,
) {
    BackHandler(onBack = onDismiss)

    val initial = parseArgb(initialHex) ?: android.graphics.Color.BLACK
    var argb by remember { mutableStateOf(initial) }
    var hexText by remember { mutableStateOf(initial.toArgbHex()) }
    val channels = remember {
        mutableStateListOf(
            android.graphics.Color.alpha(initial).toString(),
            android.graphics.Color.red(initial).toString(),
            android.graphics.Color.green(initial).toString(),
            android.graphics.Color.blue(initial).toString(),
        )
    }

    fun applyArgb(value: Int, updateHex: Boolean, updateChannels: Boolean) {
        argb = value
        if (updateHex) hexText = value.toArgbHex()
        if (updateChannels) {
            channels[0] = android.graphics.Color.alpha(value).toString()
            channels[1] = android.graphics.Color.red(value).toString()
            channels[2] = android.graphics.Color.green(value).toString()
            channels[3] = android.graphics.Color.blue(value).toString()
        }
    }

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var brightness by remember { mutableStateOf(hsv[2]) }

    fun applyHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        brightness = v
        applyArgb(
            android.graphics.Color.HSVToColor(
                android.graphics.Color.alpha(argb),
                floatArrayOf(h, s, v),
            ),
            updateHex = true,
            updateChannels = true,
        )
    }

    val currentColor = Color(argb)
    val markerColor = MaterialTheme.colorScheme.onSurface
    val wheelSize = 180.dp
    val density = LocalDensity.current
    val wheelPx = with(density) { wheelSize.toPx() }
    val ringStrokePx = with(density) { 14.dp.toPx() }
    val centerPx = wheelPx / 2f
    val ringRadius = centerPx - ringStrokePx

    fun hueFromPosition(x: Float, y: Float) {
        val degrees = Math.toDegrees(
            atan2((y - centerPx).toDouble(), (x - centerPx).toDouble()),
        ).toFloat() + 90f
        applyHsv(((degrees % 360f) + 360f) % 360f, saturation, brightness)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "PICK COLOR",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    CrossIcon(color = MaterialTheme.colorScheme.onSurface)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(currentColor)
                            .clickable { applyArgb(argb, updateHex = true, updateChannels = true) },
                    )
                    Spacer(Modifier.size(12.dp))
                    BasicTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            val parsed = parseArgb(text)
                            if (parsed != null) {
                                applyArgb(parsed, updateHex = false, updateChannels = true)
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "HEX",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 4.dp),
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(wheelSize)
                        .align(Alignment.CenterHorizontally)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { hueFromPosition(it.x, it.y) },
                                onDrag = { change, _ -> hueFromPosition(change.position.x, change.position.y) },
                            )
                        },
                ) {
                    Canvas(modifier = Modifier.size(wheelSize)) {
                        val topLeft = Offset(centerPx - ringRadius, centerPx - ringRadius)
                        val diameter = Size(ringRadius * 2f, ringRadius * 2f)
                        for (degree in 0 until 360 step 2) {
                            drawArc(
                                color = Color.hsv(degree.toFloat(), 1f, 1f),
                                startAngle = degree - 90f - 1.2f,
                                sweepAngle = 2.4f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = diameter,
                                style = Stroke(width = ringStrokePx),
                            )
                        }
                        drawCircle(
                            color = currentColor,
                            radius = ringRadius - 10.dp.toPx(),
                            center = Offset(centerPx, centerPx),
                        )
                        val radians = Math.toRadians((hue - 90f).toDouble())
                        val marker = Offset(
                            centerPx + cos(radians).toFloat() * ringRadius,
                            centerPx + sin(radians).toFloat() * ringRadius,
                        )
                        drawCircle(
                            color = markerColor,
                            radius = 5.dp.toPx(),
                            center = marker,
                        )
                        drawCircle(
                            color = currentColor,
                            radius = 2.5.dp.toPx(),
                            center = marker,
                        )
                    }
                }

                SliderLabeled("SATURATION", saturation) { applyHsv(hue, it, brightness) }
                SliderLabeled("BRIGHTNESS", brightness) { applyHsv(hue, saturation, it) }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val labels = listOf("A", "R", "G", "B")
                    labels.forEachIndexed { index, label ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                            BasicTextField(
                                value = channels[index],
                                onValueChange = { text ->
                                    channels[index] = text
                                    val parsed = channels.map { it.toIntOrNull() }
                                    if (parsed.all { it != null && it in 0..255 }) {
                                        applyArgb(
                                            android.graphics.Color.argb(
                                                parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!,
                                            ),
                                            updateHex = true,
                                            updateChannels = false,
                                        )
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorChange(argb.toArgbHex()) }) {
                Text(text = "APPLY", style = MaterialTheme.typography.labelLarge)
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
private fun SliderLabeled(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(top = 8.dp),
    )
    Slider(value = value, onValueChange = onChange)
}

@Composable
private fun CrossIcon(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = 1.5.dp.toPx()
        val inset = 2.dp.toPx()
        drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), stroke, StrokeCap.Round)
    }
}

private fun parseArgb(value: String): Int? {
    val digits = value.trim().removePrefix("#")
    if (digits.length != 6 && digits.length != 8) return null
    if (digits.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return runCatching { android.graphics.Color.parseColor("#$digits") }.getOrNull()
}

private fun Int.toArgbHex(): String = "#%02X%02X%02X%02X".format(
    android.graphics.Color.alpha(this),
    android.graphics.Color.red(this),
    android.graphics.Color.green(this),
    android.graphics.Color.blue(this),
)
