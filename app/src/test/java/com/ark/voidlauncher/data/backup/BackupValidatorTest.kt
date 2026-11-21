package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.data.db.AppCustomizationEntity
import com.ark.voidlauncher.domain.CustomPalette
import com.ark.voidlauncher.domain.EdgeInfo
import com.ark.voidlauncher.domain.GridItemType
import com.ark.voidlauncher.domain.PageInfo
import com.ark.voidlauncher.domain.PageType
import com.ark.voidlauncher.domain.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the pre-import validator. */
class BackupValidatorTest {

    private fun validPayload(): BackupPayload = BackupPayload(
        pages = listOf(
            PageInfo(1, 0, 0, "Home", pageType = PageType.HOME),
            PageInfo(2, 1, 0, "Second", gridCols = 8, gridRows = 12),
        ),
        edges = listOf(
            EdgeInfo(1, SwipeDirection.RIGHT, 2),
            EdgeInfo(2, SwipeDirection.LEFT, 1),
        ),
        items = listOf(
            BackedUpItem(0, 0, GridItemType.APP_SHORTCUT, 1, 2, 1, 1, "com.example.app", false),
            BackedUpItem(1, 0, GridItemType.WIDGET, 3, 4, 2, 3, "provider/.Widget", true),
        ),
        customizations = listOf(AppCustomizationEntity("com.example.app")),
        homeCoordX = 0,
        homeCoordY = 0,
        theme = "DARK",
        orientation = "LANDSCAPE",
        customPalette = CustomPalette.darkDefault(),
        backgrounds = listOf(
            BackedUpBackground(scope = "GLOBAL", kind = "COLOR", colorHex = "#FF101010"),
            BackedUpBackground(
                scope = "PAGE",
                pageCoordX = 1,
                pageCoordY = 0,
                kind = "IMAGE",
                imageToken = "bg.main-1",
                imageBase64 = "AA==",
            ),
        ),
    )

    private fun assertHasError(result: BackupValidationResult, text: String) {
        assertTrue(
            "Expected error containing '$text' but got ${result.errors}",
            result.errors.any { it.contains(text) },
        )
    }

    @Test
    fun validCanonicalPayloadHasNoErrors() {
        val result = BackupValidator.validate(validPayload())

        assertTrue(result.isValid)
        assertEquals(emptyList<String>(), result.errors)
    }

    @Test
    fun duplicatePageIdAndCoordAreReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(pages = base.pages + base.pages[1].copy(coordX = 0, coordY = 0)),
        )

        assertEquals(
            listOf("DUPLICATE PAGE ID 2", "DUPLICATE PAGE COORD 0,0"),
            result.errors,
        )
    }

    @Test
    fun outOfRangeCoordIsReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(pages = listOf(base.pages[0].copy(coordX = 11), base.pages[1])),
        )

        assertHasError(result, "PAGE COORD OUT OF RANGE 1 11,0")
    }

    @Test
    fun missingHomeIsReported() {
        val result = BackupValidator.validate(validPayload().copy(homeCoordX = 9, homeCoordY = 9))

        assertHasError(result, "MISSING HOME PAGE 9,9")
    }

    @Test
    fun drawerHomeIsReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(
                pages = listOf(
                    base.pages[0].copy(pageType = PageType.APP_DRAWER),
                    base.pages[1],
                ),
            ),
        )

        assertHasError(result, "HOME PAGE IS APP DRAWER 1")
    }

    @Test
    fun danglingAndSelfEdgesAreReported() {
        val result = BackupValidator.validate(
            validPayload().copy(
                edges = listOf(
                    EdgeInfo(1, SwipeDirection.RIGHT, 99),
                    EdgeInfo(1, SwipeDirection.LEFT, 1),
                ),
            ),
        )

        assertHasError(result, "DANGLING EDGE 1 RIGHT 99")
        assertHasError(result, "SELF EDGE 1")
    }

    @Test
    fun conflictingEdgeSlotIsReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(
                pages = base.pages + PageInfo(3, 2, 0, "Third"),
                edges = listOf(
                    EdgeInfo(1, SwipeDirection.RIGHT, 2),
                    EdgeInfo(2, SwipeDirection.LEFT, 1),
                    EdgeInfo(1, SwipeDirection.RIGHT, 3),
                    EdgeInfo(3, SwipeDirection.LEFT, 1),
                ),
            ),
        )

        assertEquals(listOf("CONFLICTING EDGE SLOT 1 RIGHT"), result.errors)
    }

    @Test
    fun halfLinkExpandsIntoAValidPair() {
        val result = BackupValidator.validate(
            validPayload().copy(edges = listOf(EdgeInfo(1, SwipeDirection.RIGHT, 2))),
        )

        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun graphStrandingIsReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(pages = base.pages + PageInfo(3, 2, 0, "Third")),
        )

        assertHasError(result, "PAGE WITHOUT CONNECTION 3")
        assertHasError(result, "PAGE WITHOUT PATH TO HOME 3")
    }

    @Test
    fun invalidItemSpanIsReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(
                items = listOf(
                    base.items[0].copy(col = 9, colSpan = 2),
                    base.items[1].copy(rowSpan = 0),
                ),
            ),
        )

        assertHasError(result, "ITEM EXCEEDS GRID COLS 0,0")
        assertHasError(result, "INVALID ITEM ROW SPAN 0")
    }

    @Test
    fun invalidBackgroundsAreReported() {
        val result = BackupValidator.validate(
            validPayload().copy(
                backgrounds = listOf(
                    BackedUpBackground(scope = "WORLD", kind = "COLOR"),
                    BackedUpBackground(scope = "PAGE", kind = "NOPE"),
                    BackedUpBackground(
                        scope = "GLOBAL", pageCoordX = 0, pageCoordY = 0, kind = "COLOR",
                    ),
                    BackedUpBackground(
                        scope = "PAGE", pageCoordX = 9, pageCoordY = 9, kind = "COLOR",
                    ),
                    BackedUpBackground(
                        scope = "PAGE", pageCoordX = 0, pageCoordY = 0, kind = "IMAGE",
                        imageToken = "..", imageBase64 = " ",
                    ),
                ),
            ),
        )

        assertHasError(result, "INVALID BACKGROUND SCOPE WORLD")
        assertHasError(result, "INVALID BACKGROUND KIND NOPE")
        assertHasError(result, "PAGE BACKGROUND MISSING COORDS")
        assertHasError(result, "GLOBAL BACKGROUND HAS PAGE COORDS")
        assertHasError(result, "PAGE BACKGROUND COORD NOT FOUND 9,9")
        assertHasError(result, "INVALID IMAGE TOKEN ..")
        assertHasError(result, "BLANK IMAGE BASE64")
    }

    @Test
    fun invalidThemeAndOrientationAreReported() {
        val result = BackupValidator.validate(
            validPayload().copy(theme = "MIDNIGHT", orientation = "UPSIDE_DOWN"),
        )

        assertHasError(result, "INVALID THEME MIDNIGHT")
        assertHasError(result, "INVALID ORIENTATION UPSIDE_DOWN")
    }

    @Test
    fun customAndOpaqueWidgetReferencesAreAccepted() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(
                items = listOf(
                    base.items[0].copy(referenceId = "custom:weather-card"),
                    base.items[1].copy(referenceId = "future:opaque-thing"),
                ),
            ),
        )

        assertTrue(result.errors.toString(), result.isValid)
    }

    @Test
    fun malformedCustomReferenceIsReported() {
        val base = validPayload()
        val result = BackupValidator.validate(
            base.copy(items = listOf(base.items[1].copy(referenceId = "custom:bad id"))),
        )

        assertHasError(result, "INVALID CUSTOM WIDGET REFERENCE")
    }
}
