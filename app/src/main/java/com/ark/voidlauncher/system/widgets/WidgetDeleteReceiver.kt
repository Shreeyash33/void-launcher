package com.ark.voidlauncher.system.widgets

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Forwards `ACTION_APPWIDGET_DELETED` broadcasts into [WidgetEvents].
 *
 * Integration note (lead): register this receiver in the manifest with the
 * `android.appwidget.action.APPWIDGET_DELETED` action.
 */
class WidgetDeleteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppWidgetManager.ACTION_APPWIDGET_DELETED) return
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        // tryEmit never blocks on the main thread (the flow uses DROP_OLDEST).
        WidgetEvents.deletedIds.tryEmit(appWidgetId)
    }
}
