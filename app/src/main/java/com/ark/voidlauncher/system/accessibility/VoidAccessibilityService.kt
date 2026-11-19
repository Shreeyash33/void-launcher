package com.ark.voidlauncher.system.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in accessibility service used only to dispatch system global actions.
 *
 * The service is inert until the user explicitly enables it from the system
 * accessibility settings. It never reads or observes user UI content, and it
 * does not start or poll anything on its own.
 *
 * If this app is published on Google Play, the store listing must declare that
 * an accessibility service is used for system navigation actions and the
 * required accessibility declaration must be submitted. This service performs
 * no content observation, but the declaration is still required by Play policy.
 */
class VoidAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: this service does not read UI content.
    }

    override fun onInterrupt() {
        // No-op: nothing is in flight to interrupt.
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        /** The connected instance, or null when the service is not running. */
        @Volatile
        var instance: VoidAccessibilityService? = null
    }
}
