package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.PageInfo

/**
 * Complete, portable snapshot of launcher state.
 *
 * All relationships between pages are expressed through [PageInfo.pageId] values
 * inside [edges] and through page coordinates inside [items]; the lead's restore
 * step remaps those ids to freshly generated Room ids.
 *
 * The [version] defaults to [BackupManager.FORMAT_VERSION] so that callers
 * constructing a payload by hand produce a current-format document.
 */
data class BackupPayload(
    val version: Int = BackupManager.FORMAT_VERSION,
    val pages: List<PageInfo>,
    val edges: List<EdgeInfo>,
    val items: List<BackedUpItem>,
    val customizations: List<AppCustomizationEntity>,
    val homeCoordX: Int,
    val homeCoordY: Int,
    val theme: String,
)
