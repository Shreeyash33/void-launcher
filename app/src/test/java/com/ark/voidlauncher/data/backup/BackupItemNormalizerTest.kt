package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.domain.BuiltInWidgetKind
import com.ark.voidlauncher.domain.BuiltInWidgets
import com.ark.voidlauncher.domain.GridItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests for restore-time normalization of editor-authored backups. */
class BackupItemNormalizerTest {

    private fun item(
        itemType: GridItemType = GridItemType.WIDGET,
        referenceId: String = "provider/.Widget",
        widgetKind: BuiltInWidgetKind? = null,
        appListPackages: String? = null,
    ): BackedUpItem = BackedUpItem(
        pageCoordX = 1,
        pageCoordY = -1,
        itemType = itemType,
        col = 2,
        row = 3,
        colSpan = 4,
        rowSpan = 5,
        referenceId = referenceId,
        allowOverlap = true,
        widgetKind = widgetKind,
        appListPackages = appListPackages,
    )

    @Test
    fun clockSentinelDerivesKindAndCanonicalReference() {
        val raw = item(referenceId = "builtin:CLOCK")

        assertEquals(
            item(
                referenceId = BuiltInWidgets.reference(BuiltInWidgetKind.CLOCK),
                widgetKind = BuiltInWidgetKind.CLOCK,
            ),
            BackupItemNormalizer.normalize(raw),
        )
    }

    @Test
    fun appListSentinelDerivesKindAndCanonicalReference() {
        val raw = item(referenceId = "builtin:APP_LIST")

        assertEquals(
            item(
                referenceId = BuiltInWidgets.reference(BuiltInWidgetKind.APP_LIST),
                widgetKind = BuiltInWidgetKind.APP_LIST,
            ),
            BackupItemNormalizer.normalize(raw),
        )
    }

    @Test
    fun explicitWidgetKindAndReferenceArePreserved() {
        val raw = item(
            referenceId = "builtin:CLOCK",
            widgetKind = BuiltInWidgetKind.APP_LIST,
            appListPackages = "com.b\ncom.a",
        )

        val normalized = BackupItemNormalizer.normalize(raw)

        assertEquals(BuiltInWidgetKind.APP_LIST, normalized.widgetKind)
        assertEquals("builtin:CLOCK", normalized.referenceId)
        assertEquals("com.a\ncom.b", normalized.appListPackages)
        assertEquals(raw.copy(appListPackages = "com.a\ncom.b"), normalized)
    }

    @Test
    fun appListPackagesAreTrimmedDedupedAndSorted() {
        val raw = item(
            referenceId = BuiltInWidgets.reference(BuiltInWidgetKind.APP_LIST),
            widgetKind = BuiltInWidgetKind.APP_LIST,
            appListPackages = "  com.z  \ncom.a\ncom.z\n\tcom.b\t\n   \ncom.a  ",
        )

        assertEquals("com.a\ncom.b\ncom.z", BackupItemNormalizer.normalize(raw).appListPackages)
    }

    @Test
    fun nullAppListPackagesStaysNull() {
        val raw = item(referenceId = BuiltInWidgets.reference(BuiltInWidgetKind.APP_LIST))

        assertNull(BackupItemNormalizer.normalize(raw).appListPackages)
    }

    @Test
    fun appShortcutPackageReferenceIsLeftUnchanged() {
        val raw = item(itemType = GridItemType.APP_SHORTCUT, referenceId = "com.example.app")

        assertEquals(raw, BackupItemNormalizer.normalize(raw))
    }
}
