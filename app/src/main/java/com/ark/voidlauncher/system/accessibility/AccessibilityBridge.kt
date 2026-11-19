package com.ark.voidlauncher.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Public bridge to the opt-in [VoidAccessibilityService].
 *
 * Nothing is enabled automatically: [isEnabled] only reports the state the user
 * chose in system settings, and [perform] is a no-op while the service is
 * disconnected. Only public `performGlobalAction` APIs are used; no UI content
 * is read and no polling takes place.
 *
 * If this app is published on Google Play, the store listing must declare that
 * an accessibility service is used for system navigation actions and the
 * required accessibility declaration must be submitted, even though this
 * bridge never observes screen content.
 */
object AccessibilityBridge {

    /**
     * Reports whether the user has enabled our service in system accessibility
     * settings. Defensive: missing or malformed settings values yield false.
     */
    fun isEnabled(context: Context): Boolean = runCatching {
        val resolver = context.contentResolver
        val masterEnabled = Settings.Secure.getInt(
            resolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        )
        if (masterEnabled == 0) {
            false
        } else {
            val expected = ComponentName(
                context,
                VoidAccessibilityService::class.java,
            ).flattenToString()
            val enabledServices = Settings.Secure.getString(
                resolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            enabledServices
                .split(':')
                .any { it.trim() == expected }
        }
    }.getOrDefault(false)

    /**
     * Dispatches a global action, or returns false when the service is not
     * connected or the action is unsupported on this Android version.
     */
    fun perform(action: GlobalAction): Boolean {
        val service = VoidAccessibilityService.instance ?: return false
        return runCatching {
            when (action) {
                GlobalAction.HOME ->
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)

                GlobalAction.BACK ->
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

                GlobalAction.RECENTS ->
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)

                GlobalAction.NOTIFICATIONS ->
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)

                GlobalAction.LOCK_SCREEN -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        service.performGlobalAction(
                            AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN,
                        )
                    } else {
                        false
                    }
                }
            }
        }.getOrDefault(false)
    }

    /** Opens the system accessibility settings screen where the user can opt in. */
    fun openSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
