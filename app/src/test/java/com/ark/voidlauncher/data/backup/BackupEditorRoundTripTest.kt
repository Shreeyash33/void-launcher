package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.domain.BuiltInWidgetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end portable path for a file authored by the external editor: raw JSON
 * -> codec -> validator -> restore normalizer. No Android APIs are involved.
 */
class BackupEditorRoundTripTest {

    private val editorAuthoredJson = """
        {
          "version": 1,
          "homeCoordX": 0,
          "homeCoordY": 0,
          "theme": "LIGHT",
          "orientation": "PORTRAIT",
          "showCoordinateDetail": true,
          "customPalette": null,
          "backgrounds": [],
          "pages": [
            {"pageId": 1, "coordX": 0, "coordY": 0, "title": "Home", "gridCols": 10, "gridRows": 20, "isSecret": false, "pageType": "HOME"},
            {"pageId": 2, "coordX": 1, "coordY": 0, "title": "Widgets", "gridCols": 10, "gridRows": 20, "isSecret": false, "pageType": "WIDGET"}
          ],
          "edges": [
            {"fromPageId": 1, "swipeDirection": "RIGHT", "toPageId": 2},
            {"fromPageId": 2, "swipeDirection": "LEFT", "toPageId": 1}
          ],
          "items": [
            {"pageCoordX": 0, "pageCoordY": 0, "itemType": "WIDGET", "col": 1, "row": 2, "colSpan": 4, "rowSpan": 2, "referenceId": "builtin:APP_LIST", "allowOverlap": true, "appListPackages": " com.b \n com.a \n com.a "},
            {"pageCoordX": 1, "pageCoordY": 0, "itemType": "WIDGET", "col": 3, "row": 1, "colSpan": 2, "rowSpan": 2, "referenceId": "builtin:CLOCK", "allowOverlap": false},
            {"pageCoordX": 0, "pageCoordY": 0, "itemType": "WIDGET", "col": 1, "row": 4, "colSpan": 2, "rowSpan": 2, "referenceId": "builtin:PAGE_INFO", "allowOverlap": true}
          ],
          "customizations": []
        }
    """.trimIndent()

    @Test
    fun editorFileSurvivesCodecValidationAndNormalization() {
        val payload = BackupManager.fromJson(editorAuthoredJson)
        assertTrue("codec should accept the editor file", payload != null)
        payload!!

        val validation = BackupValidator.validate(payload)
        assertTrue("validator should accept: ${validation.errors}", validation.isValid)
        assertTrue(payload.backgrounds.isEmpty())
        assertNull(payload.customPalette)

        val normalized = payload.items.map(BackupItemNormalizer::normalize)

        val appList = normalized.first { it.referenceId.endsWith("APP_LIST") }
        assertEquals(BuiltInWidgetKind.APP_LIST, appList.widgetKind)
        assertEquals("builtin:APP_LIST", appList.referenceId)
        assertEquals("com.a\ncom.b", appList.appListPackages)

        val clock = normalized.first { it.referenceId.endsWith("CLOCK") }
        assertEquals(BuiltInWidgetKind.CLOCK, clock.widgetKind)
        assertEquals("builtin:CLOCK", clock.referenceId)
        assertNull(clock.appListPackages)

        val pageInfo = normalized.first { it.referenceId.endsWith("PAGE_INFO") }
        assertEquals(BuiltInWidgetKind.PAGE_INFO, pageInfo.widgetKind)
        assertEquals("builtin:PAGE_INFO", pageInfo.referenceId)
        assertEquals(2, pageInfo.colSpan)
        assertEquals(2, pageInfo.rowSpan)
        assertNull(pageInfo.appListPackages)
    }

    @Test
    fun reEncodedEditorFileRemainsIdentical() {
        val first = BackupManager.fromJson(editorAuthoredJson)!!
        val second = BackupManager.fromJson(BackupManager.toJson(first))
        assertEquals(first, second)
    }
}
