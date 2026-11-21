package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.domain.CustomWidgetBundle
import com.ark.voidlauncher.domain.CustomWidgetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CustomWidgetBundleManagerTest {

    private fun widget(
        id: String = "weather-card",
        runtime: String = "WEBVIEW",
    ) = CustomWidgetDefinition(
        id = id,
        name = "Weather Card",
        runtime = runtime,
        colSpan = 4,
        rowSpan = 2,
        minColSpan = 2,
        minRowSpan = 1,
        allowResize = true,
        allowOverlap = false,
        html = "<div class='card'>18&deg;</div>",
        css = ".card{padding:8px}",
        js = "document.querySelector('.card').title='ok'",
    )

    @Test
    fun roundTripPreservesDefinitionsAndOrder() {
        val bundle = CustomWidgetBundle(
            widgets = listOf(widget("weather-card"), widget("notes", runtime = "FUTURE_RUNTIME")),
        )

        val json = CustomWidgetBundleManager.toJson(bundle)
        val decoded = CustomWidgetBundleManager.fromJson(json)

        assertNotNull(decoded)
        assertEquals(bundle, decoded)
        assertEquals(listOf("weather-card", "notes"), decoded!!.widgets.map { it.id })
        assertEquals("FUTURE_RUNTIME", decoded.widgets[1].runtime)
    }

    @Test
    fun emptyBundleRoundTrips() {
        val bundle = CustomWidgetBundle()

        assertEquals(bundle, CustomWidgetBundleManager.fromJson(CustomWidgetBundleManager.toJson(bundle)))
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val json = """{"version": 2, "widgets": []}"""
        assertNull(CustomWidgetBundleManager.fromJson(json))
    }

    @Test
    fun malformedRowFailsTheWholeBundle() {
        val json = """
            {
              "version": 1,
              "widgets": [
                {"id":"ok","name":"Ok","colSpan":1,"rowSpan":1,"html":"<b>x</b>"},
                {"id":"broken","name":"Broken","colSpan":1,"rowSpan":1}
              ]
            }
        """.trimIndent()

        assertNull(CustomWidgetBundleManager.fromJson(json))
    }

    @Test
    fun missingWidgetsArrayIsRejected() {
        assertNull(CustomWidgetBundleManager.fromJson("""{"version": 1}"""))
    }
}
