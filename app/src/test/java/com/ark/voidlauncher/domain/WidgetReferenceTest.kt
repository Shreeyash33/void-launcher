package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetReferenceTest {

    companion object {
        private const val PROVIDER = "com.foo/.Widget"
    }

    @Test
    fun placeholderIsDetectedAndUnwrapped() {
        val reference = WidgetReference.PLACEHOLDER_PREFIX + PROVIDER

        assertTrue(WidgetReference.isPlaceholder(reference))
        assertEquals(PROVIDER, WidgetReference.unwrap(reference))
    }

    @Test
    fun nonPlaceholderIsUnchanged() {
        assertFalse(WidgetReference.isPlaceholder(PROVIDER))
        assertEquals(PROVIDER, WidgetReference.unwrap(PROVIDER))
    }

    @Test
    fun numericReferenceIsALiveId() {
        assertTrue(WidgetReference.isLiveId("7"))
        assertFalse(WidgetReference.isLiveId(PROVIDER))
    }

    @Test
    fun legacyNumericPlaceholderHasNoPortableProvider() {
        assertNull(WidgetReference.portableProvider(WidgetReference.PLACEHOLDER_PREFIX + "12"))
    }

    @Test
    fun builtInSentinelHasNoPortableProvider() {
        val sentinel = BuiltInWidgets.reference(BuiltInWidgetKind.APP_LIST)

        assertNull(WidgetReference.portableProvider(sentinel))
        assertNull(
            WidgetReference.portableProvider(WidgetReference.PLACEHOLDER_PREFIX + sentinel),
        )
    }

    @Test
    fun providerStringIsReturnedUnchanged() {
        assertEquals(
            PROVIDER,
            WidgetReference.portableProvider(WidgetReference.PLACEHOLDER_PREFIX + PROVIDER),
        )
        assertEquals(PROVIDER, WidgetReference.portableProvider(PROVIDER))
    }

    @Test
    fun customReferenceIdIsExtractedAndCanonicalized() {
        assertEquals("weather-card", WidgetReference.customId("custom:weather-card"))
        assertEquals("weather-card", WidgetReference.customId("placeholder:custom:weather-card"))
        assertEquals("custom:weather-card", WidgetReference.customReference("weather-card"))
        assertTrue(WidgetReference.isCustom("custom:weather-card"))
    }

    @Test
    fun invalidCustomReferenceIdsAreRejected() {
        assertNull(WidgetReference.customId("custom:bad id"))
        assertNull(WidgetReference.customId("custom:"))
        assertNull(WidgetReference.customId("custom:${"x".repeat(65)}"))
        assertFalse(WidgetReference.isCustom(PROVIDER))
    }

    @Test
    fun customReferenceHasNoSystemProvider() {
        assertNull(WidgetReference.portableProvider("custom:weather-card"))
        assertNull(WidgetReference.portableProvider("placeholder:custom:weather-card"))
    }
}
