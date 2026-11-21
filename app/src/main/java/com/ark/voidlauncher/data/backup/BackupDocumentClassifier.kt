package com.ark.voidlauncher.data.backup

import org.json.JSONObject

/** Identifies which of the three JSON documents a user selected. */
enum class BackupDocumentKind { LAYOUT, WIDGET_BUNDLE, DEVICE_INVENTORY, UNKNOWN }

object BackupDocumentClassifier {
    fun classify(json: String): BackupDocumentKind = runCatching {
        val root = JSONObject(json)
        val looksLikeLayout = root.has("pages") && root.has("edges") && root.has("items")
        val looksLikeBundle = root.has("widgets")
        val looksLikeInventory = root.has("apps") || root.has("systemWidgets")
        when {
            looksLikeLayout && !looksLikeBundle && !looksLikeInventory -> BackupDocumentKind.LAYOUT
            looksLikeBundle && !looksLikeLayout && !looksLikeInventory -> BackupDocumentKind.WIDGET_BUNDLE
            looksLikeInventory && !looksLikeLayout && !looksLikeBundle -> BackupDocumentKind.DEVICE_INVENTORY
            else -> BackupDocumentKind.UNKNOWN
        }
    }.getOrDefault(BackupDocumentKind.UNKNOWN)
}
