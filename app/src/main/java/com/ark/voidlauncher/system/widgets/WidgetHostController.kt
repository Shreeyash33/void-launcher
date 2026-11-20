package com.ark.voidlauncher.system.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns the launcher's [AppWidgetHost] and wraps the framework app-widget APIs.
 *
 * This class never launches an activity: the caller is responsible for firing
 * [createPickIntent] / [configureIntentFor] and for calling [bindAllowed] after
 * the picker returns.
 *
 * Integration note (lead): the host needs
 * `android.permission.BIND_APPWIDGET` in the manifest to be able to bind
 * widgets without the system picker, and [WidgetDeleteReceiver] must be
 * registered for `android.appwidget.action.APPWIDGET_DELETED`.
 */
class WidgetHostController(context: Context) {

    private val appContext = context.applicationContext
    private val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    @Volatile
    private var listening = false

    /** Starts receiving widget updates. Safe to call repeatedly. */
    @Synchronized
    fun startListening() {
        if (listening) return
        host.startListening()
        listening = true
    }

    /** Stops receiving widget updates. Safe to call repeatedly. */
    @Synchronized
    fun stopListening() {
        if (!listening) return
        host.stopListening()
        listening = false
    }

    /** Allocates a fresh app-widget id from the framework. */
    fun allocateAppWidgetId(): Int = host.allocateAppWidgetId()

    /** Every appWidgetId currently owned by this launcher host. */
    suspend fun allocatedWidgetIds(): List<Int> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // AppWidgetHost.getAppWidgetIds() is API 26+; API 24/25 have no public
            // host-side enumeration, so reset cannot release untracked ids there.
            emptyList()
        } else {
            try {
                host.appWidgetIds.toList()
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                // A stale id can theoretically make the host query fail; reset
                // should still continue with the ids that can be read.
                emptyList()
            }
        }
    }

    /**
     * Attempts a direct bind. Returns `false` when the system requires the
     * picker flow; the caller should then launch [createPickIntent].
     */
    fun bindAllowed(appWidgetId: Int, provider: ComponentName): Boolean {
        return appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, provider)
    }

    /** Intent for the system widget picker, carrying the pre-allocated id. */
    fun createPickIntent(appWidgetId: Int): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Configure activity for this widget, or `null` when none is declared. */
    fun configureIntentFor(appWidgetId: Int): Intent? {
        val configure = providerInfo(appWidgetId)?.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(configure)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Releases an id; safe for ids that are no longer valid. */
    fun deleteWidget(appWidgetId: Int) {
        host.deleteAppWidgetId(appWidgetId)
    }

    /** Pushes a new options bundle to the widget provider. */
    fun updateOptions(appWidgetId: Int, options: Bundle) {
        try {
            appWidgetManager.updateAppWidgetOptions(appWidgetId, options)
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Stale id or provider removed while the app was in the background.
        }
    }

    /**
     * Returns the stored options, guaranteeing the four sizing keys so a
     * provider can size itself. `null` for an unknown/stale id.
     */
    fun getOptions(appWidgetId: Int): Bundle? {
        if (providerInfo(appWidgetId) == null) return null
        val stored = try {
            appWidgetManager.getAppWidgetOptions(appWidgetId)
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            null
        }
        return withDefaultSizes(stored)
    }

    /** Non-null only for a bound, still-live widget id. */
    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        return try {
            appWidgetManager.getAppWidgetInfo(appWidgetId)
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            null
        }
    }

    /** Providers available to the current user profile. */
    @Suppress("DEPRECATION")
    fun installedProviders(): List<AppWidgetProviderInfo> {
        return try {
            appWidgetManager.getInstalledProvidersForProfile(Process.myUserHandle())
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            appWidgetManager.installedProviders ?: emptyList()
        }
    }

    /** Views must come from the same host that is listening for updates. */
    internal fun createHostView(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
    ): AppWidgetHostView = host.createView(appContext, appWidgetId, info)

    private fun withDefaultSizes(source: Bundle?): Bundle {
        val result = if (source == null) Bundle() else Bundle(source)
        putIfMissing(result, AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_MIN_WIDTH_DP)
        putIfMissing(result, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, DEFAULT_MAX_WIDTH_DP)
        putIfMissing(result, AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_MIN_HEIGHT_DP)
        putIfMissing(result, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, DEFAULT_MAX_HEIGHT_DP)
        return result
    }

    private fun putIfMissing(bundle: Bundle, key: String, value: Int) {
        if (!bundle.containsKey(key)) bundle.putInt(key, value)
    }

    private companion object {
        const val HOST_ID = 101

        // Sizing options are expressed in density-independent pixels.
        const val DEFAULT_MIN_WIDTH_DP = 40
        const val DEFAULT_MAX_WIDTH_DP = 400
        const val DEFAULT_MIN_HEIGHT_DP = 40
        const val DEFAULT_MAX_HEIGHT_DP = 400
    }
}
