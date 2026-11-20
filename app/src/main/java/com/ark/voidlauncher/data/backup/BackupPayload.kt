package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.domain.CustomPalette
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
 *
 * P8–P10 additions ([orientation], [customPalette], [backgrounds]) are trailing
 * defaulted fields so older callers and older backup files remain valid. The JSON
 * codec keeps FORMAT_VERSION = 1 and treats them as optional keys.
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
    val orientation: String = "PORTRAIT",
    val customPalette: CustomPalette? = null,
    val backgrounds: List<BackedUpBackground> = emptyList(),
    val showCoordinateDetail: Boolean = true,
)
