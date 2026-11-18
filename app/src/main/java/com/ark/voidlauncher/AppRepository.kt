package com.ark.voidlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class AppInfo(val packageName: String, val label: String)

/** Reads the list of launchable apps and opens them. */
class AppRepository(private val context: Context) {
    private val packageManager: PackageManager = context.packageManager

    fun installedApps(): List<AppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(packageManager).toString().ifBlank { activityInfo.packageName }
                AppInfo(activityInfo.packageName, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
