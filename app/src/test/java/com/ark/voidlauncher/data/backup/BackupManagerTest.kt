package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.domain.AppFontWeight
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.FontFamily
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.SwipeDirection
import com.ark.voidlauncher.domain.TextAlign
import com.ark.voidlauncher.domain.TextTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests: no Android classes are referenced here or by the JSON codec. */
class BackupManagerTest {

    private fun samplePayload(): BackupPayload = BackupPayload(
        pages = listOf(
            PageInfo(pageId = 1, coordX = 0, coordY = 0, title = "Home"),
            PageInfo(
                pageId = 2,
                coordX = 1,
                coordY = 0,
                title = "Second",
                gridCols = 8,
                gridRows = 12,
                isSecret = true,
            ),
        ),
        edges = listOf(
            EdgeInfo(fromPageId = 1, swipeDirection = SwipeDirection.RIGHT, toPageId = 2),
            EdgeInfo(fromPageId = 2, swipeDirection = SwipeDirection.LEFT, toPageId = 1),
        ),
        items = listOf(
            BackedUpItem(0, 0, GridItemType.APP_SHORTCUT, 1, 2, 1, 1, "com.example.app", false),
            BackedUpItem(
                1,
                0,
                GridItemType.WIDGET,
                3,
                4,
                2,
                3,
                "placeholder:com.example/.SmallWidget",
                true,
            ),
        ),
        customizations = listOf(
            AppCustomizationEntity(
                packageName = "com.example.app",
                isFavorite = true,
                isHidden = false,
                customNickname = "Example",
                fontFamily = FontFamily.MONOSPACE,
                fontSizeSp = 14.5f,
                fontWeight = AppFontWeight.MEDIUM,
                letterSpacingSp = 0.5f,
                textColorHex = "#EEEEEE",
                textAlign = TextAlign.CENTER,
                textTransform = TextTransform.UPPERCASE,
            ),
            AppCustomizationEntity(packageName = "com.example.other"),
        ),
        homeCoordX = 0,
        homeCoordY = 0,
        theme = "DARK",
    )

    /**
     * Builds a raw document so malformed rows can be injected without relying
     * on the codec to produce them.
     */
    private fun document(
        version: Any? = BackupManager.FORMAT_VERSION,
        pages: String = "[]",
        edges: String = "[]",
        items: String = "[]",
        customizations: String = "[]",
        homeCoordX: Int = 0,
        homeCoordY: Int = 0,
        theme: String = "SYSTEM",
    ): String {
        val entries = mutableListOf<String>()
        if (version != null) entries += "\"version\": $version"
        entries += "\"homeCoordX\": $homeCoordX"
        entries += "\"homeCoordY\": $homeCoordY"
        entries += "\"theme\": \"$theme\""
        entries += "\"pages\": $pages"
        entries += "\"edges\": $edges"
        entries += "\"items\": $items"
        entries += "\"customizations\": $customizations"
        return entries.joinToString(separator = ",\n", prefix = "{\n", postfix = "\n}")
    }

    @Test
    fun roundTripPreservesEveryField() {
        val payload = samplePayload()

        val json = BackupManager.toJson(payload)
        assertTrue(json.isNotBlank())

        assertEquals(payload, BackupManager.fromJson(json))
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val json = document(version = BackupManager.FORMAT_VERSION + 1)
        assertNull(BackupManager.fromJson(json))
    }

    @Test
    fun missingVersionIsRejected() {
        assertNull(BackupManager.fromJson(document(version = null)))
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(BackupManager.fromJson(""))
        assertNull(BackupManager.fromJson("{not valid json"))
        assertNull(BackupManager.fromJson("[]"))
        assertNull(BackupManager.fromJson("null"))
    }

    @Test
    fun badRowsAreSkippedWithoutFailingTheFile() {
        val json = document(
            pages = """
                [
                  {"pageId": 1, "coordX": 0, "coordY": 0, "title": "Good"},
                  {"coordX": 1, "coordY": 0, "title": "Missing page id"},
                  42,
                  {"pageId": "x", "coordX": 0, "coordY": 0, "title": "Bad id type"}
                ]
            """.trimIndent(),
            edges = """
                [
                  {"fromPageId": 1, "swipeDirection": "RIGHT", "toPageId": 2},
                  {"fromPageId": 1, "swipeDirection": "SIDEWAYS", "toPageId": 2},
                  {"fromPageId": 1, "toPageId": 2}
                ]
            """.trimIndent(),
            items = """
                [
                  {"pageCoordX": 0, "pageCoordY": 0, "itemType": "APP_SHORTCUT", "col": 1, "row": 2, "colSpan": 1, "rowSpan": 1, "referenceId": "com.example.app", "allowOverlap": false},
                  {"pageCoordX": 0, "pageCoordY": 0, "itemType": "NOT_A_TYPE", "col": 1, "row": 2, "colSpan": 1, "rowSpan": 1, "referenceId": "com.example.bad"}
                ]
            """.trimIndent(),
            customizations = """
                [
                  {"packageName": "com.example.ok", "fontFamily": "MONOSPACE"},
                  {"packageName": "", "fontFamily": "MONOSPACE"},
                  {"isFavorite": true}
                ]
            """.trimIndent(),
        )

        val decoded = BackupManager.fromJson(json)
        assertNotNull(decoded)
        decoded!!

        assertEquals(1, decoded.pages.size)
        assertEquals("Good", decoded.pages[0].title)
        assertEquals(1, decoded.edges.size)
        assertEquals(SwipeDirection.RIGHT, decoded.edges[0].swipeDirection)
        assertEquals(1, decoded.items.size)
        assertEquals("com.example.app", decoded.items[0].referenceId)
        assertEquals(1, decoded.customizations.size)
        assertEquals("com.example.ok", decoded.customizations[0].packageName)
        assertEquals(FontFamily.MONOSPACE, decoded.customizations[0].fontFamily)
    }

    @Test
    fun missingCollectionsAndScalarsFallBackToEmptyValues() {
        val decoded = BackupManager.fromJson("""{"version": 1}""")
        assertNotNull(decoded)
        decoded!!

        assertTrue(decoded.pages.isEmpty())
        assertTrue(decoded.edges.isEmpty())
        assertTrue(decoded.items.isEmpty())
        assertTrue(decoded.customizations.isEmpty())
        assertEquals(0, decoded.homeCoordX)
        assertEquals(0, decoded.homeCoordY)
        assertEquals("", decoded.theme)
    }
}
