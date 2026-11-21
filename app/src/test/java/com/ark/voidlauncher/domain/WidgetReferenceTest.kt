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
}
