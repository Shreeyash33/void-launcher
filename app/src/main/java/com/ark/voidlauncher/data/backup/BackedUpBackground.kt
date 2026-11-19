package com.ark.voidlauncher.data.backup

/**
 * Portable form of a P10 background.
 *
 * Page-specific backgrounds are addressed by page coordinates rather than the
 * volatile Room [pageId], matching how [BackedUpItem] survives a restore. Image
 * bytes travel as an opaque base64 string (encoded by the Android side) because
 * `content://` URIs and app-internal tokens are not portable across installs.
 */
data class BackedUpBackground(
    val scope: String,                  // BackgroundScope.name
    val pageCoordX: Int? = null,
    val pageCoordY: Int? = null,
    val kind: String,                   // BackgroundKind.name
    val colorHex: String? = null,
    val gradientSpec: String? = null,
    val gradientAngle: Float = 0f,
    val imageToken: String? = null,
    val imageBase64: String? = null,
)
