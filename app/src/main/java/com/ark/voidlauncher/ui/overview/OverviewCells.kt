package com.ark.voidlauncher.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ark.voidlauncher.domain.SwipeDirection
import kotlin.math.roundToInt

/**
 * One page as seen from above: coordinate, optional HOME/HERE tags, item count and
 * the four swipe glyphs (bright when an outgoing edge exists, dim when it is a
 * wall). [center] is the cell centre in map-space pixels; the enclosing map layer
 * applies the shared pan/zoom transform, so this cell only positions itself.
 */
@Composable
internal fun PageCell(
    center: Offset,
    cellSizePx: Float,
    coordLabel: String,
    isHome: Boolean,
    isCurrent: Boolean,
    itemCount: Int,
    outgoing: Set<SwipeDirection>,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val containerColor = if (isCurrent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val markerColor = if (isCurrent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }
    val borderColor = if (isCurrent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (center.x - cellSizePx / 2f).roundToInt(),
                    (center.y - cellSizePx / 2f).roundToInt(),
                )
            }
            .size(OverviewCellSize)
            .clip(shape)
            .background(containerColor)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = coordLabel,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Row {
                if (isHome) {
                    Text(
                        text = "HOME",
                        style = MaterialTheme.typography.labelSmall,
                        color = markerColor,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                if (isCurrent) {
                    Text(
                        text = "HERE",
                        style = MaterialTheme.typography.labelSmall,
                        color = markerColor,
                    )
                }
            }
            Text(
                text = "$itemCount items",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwipeDirection.entries.forEach { direction ->
                    val available = direction in outgoing
                    Text(
                        text = direction.overviewGlyph(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (available) {
                            markerColor
                        } else {
                            contentColor.copy(alpha = 0.25f)
                        },
                    )
                }
            }
        }
    }
}

/** One of the four bottom navigation entry points. */
@Composable
internal fun RowScope.NavButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}
