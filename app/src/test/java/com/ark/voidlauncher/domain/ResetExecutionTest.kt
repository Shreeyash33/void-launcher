package com.ark.voidlauncher.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetExecutionTest {

    @Test
    fun `happy path reads cleanup before commit and writes HOME before clearing prefs`() =
        runBlocking {
            val events = mutableListOf<String>()
            val execution = ResetExecution(
                readCleanup = {
                    events += "read"
                    ResetCleanup(listOf(7, 8), listOf("bg-1", "bg-2"))
                },
                commitRoom = {
                    events += "commit"
                    42L
                },
                releaseAppWidget = { events += "widget:$it" },
                deleteBackgroundImage = { events += "image:$it" },
                writeHomeCoord = { events += "home" },
                clearPreferences = { events += "prefs" },
            )

            assertEquals(42L, execution.execute())
            assertEquals(
                listOf(
                    "read",
                    "commit",
                    "widget:7",
                    "widget:8",
                    "image:bg-1",
                    "image:bg-2",
                    "home",
                    "prefs",
                ),
                events,
            )
        }

    @Test
    fun `transaction failure skips all post commit work`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = RuntimeException("room failed")
        val execution = ResetExecution(
            readCleanup = {
                events += "read"
                ResetCleanup(listOf(3), listOf("bg-3"))
            },
            commitRoom = {
                events += "commit"
                throw failure
            },
            releaseAppWidget = { events += "widget:$it" },
            deleteBackgroundImage = { events += "image:$it" },
            writeHomeCoord = { events += "home" },
            clearPreferences = { events += "prefs" },
        )

        assertSame(failure, runCatching { execution.execute() }.exceptionOrNull())
        assertEquals(listOf("read", "commit"), events)
    }

    @Test
    fun `post commit cleanup failure throws explicit message`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = RuntimeException("host failed")
        val execution = ResetExecution(
            readCleanup = {
                events += "read"
                ResetCleanup(listOf(9), listOf("bg-9"))
            },
            commitRoom = {
                events += "commit"
                91L
            },
            releaseAppWidget = { events += "widget:$it" },
            deleteBackgroundImage = {
                events += "image:$it"
                throw failure
            },
            writeHomeCoord = { events += "home" },
            clearPreferences = { events += "prefs" },
        )

        val thrown = runCatching { execution.execute() }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals("DATA RESET; CLEANUP INCOMPLETE", thrown?.message)
        assertSame(failure, thrown?.cause)
        assertEquals(listOf("read", "commit", "widget:9", "image:bg-9"), events)
    }

    @Test
    fun `post commit prefs failure throws explicit message`() = runBlocking {
        val events = mutableListOf<String>()
        val failure = RuntimeException("datastore failed")
        val execution = ResetExecution(
            readCleanup = {
                events += "read"
                ResetCleanup(listOf(5), listOf("bg-5"))
            },
            commitRoom = {
                events += "commit"
                55L
            },
            releaseAppWidget = { events += "widget:$it" },
            deleteBackgroundImage = { events += "image:$it" },
            writeHomeCoord = { events += "home" },
            clearPreferences = {
                events += "prefs"
                throw failure
            },
        )

        val thrown = runCatching { execution.execute() }.exceptionOrNull()
        assertTrue(thrown is IllegalStateException)
        assertEquals("DATA RESET; PREFERENCES NOT CLEARED", thrown?.message)
        assertSame(failure, thrown?.cause)
        assertEquals(
            listOf("read", "commit", "widget:5", "image:bg-5", "home", "prefs"),
            events,
        )
    }
}
