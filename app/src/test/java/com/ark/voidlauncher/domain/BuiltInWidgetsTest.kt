package com.ark.voidlauncher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM coverage for built-in widget identity, labels and default geometry. */
class BuiltInWidgetsTest {

    @Test
    fun `every kind has a stable sentinel that round trips`() {
        BuiltInWidgetKind.entries.forEach { kind ->
            val reference = BuiltInWidgets.reference(kind)
            assertEquals("builtin:${kind.name}", reference)
            assertEquals(kind, BuiltInWidgets.fromReference(reference))
        }
        assertEquals(3, BuiltInWidgetKind.entries.size)
        assertNull(BuiltInWidgets.fromReference("builtin:UNKNOWN"))
        assertNull(BuiltInWidgets.fromReference("com.example.Widget"))
        assertNull(BuiltInWidgets.fromReference(null))
    }

    @Test
    fun `labels are stable including page info`() {
        assertEquals("APP LIST", BuiltInWidgets.label(BuiltInWidgetKind.APP_LIST))
        assertEquals("CLOCK", BuiltInWidgets.label(BuiltInWidgetKind.CLOCK))
        assertEquals("PAGE INFO", BuiltInWidgets.label(BuiltInWidgetKind.PAGE_INFO))
        assertEquals("PAGE INFO", BuiltInWidgets.LABEL_PAGE_INFO)
    }

    @Test
    fun `default spans are per kind`() {
        assertEquals(
            BuiltInWidgets.BuiltInSpan(4, 2),
            BuiltInWidgets.defaultSpan(BuiltInWidgetKind.CLOCK),
        )
        assertEquals(
            BuiltInWidgets.BuiltInSpan(4, 3),
            BuiltInWidgets.defaultSpan(BuiltInWidgetKind.APP_LIST),
        )
        assertEquals(
            BuiltInWidgets.BuiltInSpan(2, 2),
            BuiltInWidgets.defaultSpan(BuiltInWidgetKind.PAGE_INFO),
        )
    }

    @Test
    fun `package encoding stays sorted deduped and newline delimited`() {
        val encoded = BuiltInWidgets.encodePackages(listOf(" com.b ", "com.a", "com.b", ""))
        assertEquals("com.a\ncom.b", encoded)
        assertEquals(listOf("com.a", "com.b"), BuiltInWidgets.decodePackages(encoded))
        assertNull(BuiltInWidgets.encodePackages(listOf("  ")))
        assertEquals(emptyList<String>(), BuiltInWidgets.decodePackages(null))
    }
}
