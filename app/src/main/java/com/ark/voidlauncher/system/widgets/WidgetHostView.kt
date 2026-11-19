package com.ark.voidlauncher.system.widgets

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders a live app widget inside Compose.
 *
 * The view is created by the controller's [android.appwidget.AppWidgetHost] so
 * it receives updates, and stored options are reapplied whenever the slot is
 * recomposed. When the allocated slot changes size, fresh sizing options are
 * pushed to the provider and reported through [onOptionsChanged] so the owner
 * can persist them.
 */
@Composable
fun WidgetHostView(
    controller: WidgetHostController,
    appWidgetId: Int,
    modifier: Modifier = Modifier,
    onOptionsChanged: (Bundle) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier) {
        val density = this.density
        val widthDp = dpSize(constraints.maxWidth, constraints.hasBoundedWidth, density)
        val heightDp = dpSize(constraints.maxHeight, constraints.hasBoundedHeight, density)

        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                val info = controller.providerInfo(appWidgetId)
                if (info == null) {
                    // Stale or unbound id: render an empty placeholder.
                    View(context)
                } else {
                    val view = controller.createHostView(appWidgetId, info)
                    controller.getOptions(appWidgetId)?.let { options ->
                        controller.updateOptions(appWidgetId, options)
                    }
                    view
                }
            },
            update = {
                // Reapply the persisted options on each recomposition.
                controller.getOptions(appWidgetId)?.let { options ->
                    controller.updateOptions(appWidgetId, options)
                }
            },
        )

        LaunchedEffect(controller, appWidgetId, widthDp, heightDp) {
            if (widthDp <= 0 || heightDp <= 0) return@LaunchedEffect
            val options = Bundle().apply {
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            }
            controller.updateOptions(appWidgetId, options)
            onOptionsChanged(options)
        }
    }
}

private fun dpSize(pixels: Int, bounded: Boolean, density: Float): Int {
    if (!bounded || pixels == Constraints.Infinity || density <= 0f) return 0
    return (pixels / density).toInt().coerceAtLeast(0)
}
