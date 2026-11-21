package com.ark.voidlauncher.data.backup

import com.ark.voidlauncher.domain.CustomWidgetBundle
import com.ark.voidlauncher.domain.CustomWidgetDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomWidgetBundleValidatorTest {

    private fun widget(
        id: String = "weather-card",
        name: String = "Weather Card",
        runtime: String = "WEBVIEW",
        colSpan: Int = 4,
        rowSpan: Int = 2,
        minColSpan: Int = 1,
        minRowSpan: Int = 1,
        html: String = "<div>18&deg;</div>",
        css: String = "",
        js: String = "",
    ) = CustomWidgetDefinition(
        id = id,
        name = name,
        runtime = runtime,
        colSpan = colSpan,
        rowSpan = rowSpan,
        minColSpan = minColSpan,
        minRowSpan = minRowSpan,
        html = html,
        css = css,
        js = js,
    )

    private fun assertHasError(result: CustomWidgetValidationResult, text: String) {
        assertTrue(
            "Expected error containing '$text' but got ${result.errors}",
            result.errors.any { it.contains(text) },
        )
    }

    @Test
    fun validBundleHasNoErrors() {
        val result = CustomWidgetBundleValidator.validate(CustomWidgetBundle(widgets = listOf(widget())))
        assertEquals(emptyList<String>(), result.errors)
        assertTrue(result.isValid)
    }

    @Test
    fun unknownRuntimeIsPreservedNotRejected() {
        val result = CustomWidgetBundleValidator.validate(
            CustomWidgetBundle(widgets = listOf(widget(runtime = "FUTURE_RUNTIME"))),
        )
        assertTrue(result.isValid)
    }

    @Test
    fun duplicateAndInvalidIdsAreReported() {
        val result = CustomWidgetBundleValidator.validate(
            CustomWidgetBundle(widgets = listOf(widget("bad id"), widget("weather-card"), widget("weather-card"))),
        )
        assertHasError(result, "INVALID WIDGET ID bad id")
        assertHasError(result, "DUPLICATE WIDGET ID weather-card")
    }

    @Test
    fun badSpansAreReported() {
        val result = CustomWidgetBundleValidator.validate(
            CustomWidgetBundle(
                widgets = listOf(
                    widget("wide", colSpan = 11),
                    widget("tall", rowSpan = 21),
                    widget("min-wide", minColSpan = 5),
                ),
            ),
        )
        assertHasError(result, "INVALID WIDGET COL SPAN wide")
        assertHasError(result, "INVALID WIDGET ROW SPAN tall")
        assertHasError(result, "INVALID WIDGET MIN COL SPAN min-wide")
    }

    @Test
    fun blankHtmlAndExternalResourcesAreReported() {
        val result = CustomWidgetBundleValidator.validate(
            CustomWidgetBundle(
                widgets = listOf(
                    widget("blank", html = "   "),
                    widget("external", html = "<img src='https://example.com/x.png'>"),
                    widget("css-url", css = "background:url(https://example.com/bg.png)"),
                ),
            ),
        )
        assertHasError(result, "BLANK WIDGET HTML blank")
        assertHasError(result, "EXTERNAL WIDGET RESOURCE external")
        assertHasError(result, "EXTERNAL WIDGET RESOURCE css-url")
    }

    @Test
    fun oversizedPayloadIsReported() {
        val result = CustomWidgetBundleValidator.validate(
            CustomWidgetBundle(widgets = listOf(widget("huge", js = "x".repeat(256 * 1024)))),
        )
        assertHasError(result, "WIDGET PAYLOAD TOO LARGE huge")
    }
}
