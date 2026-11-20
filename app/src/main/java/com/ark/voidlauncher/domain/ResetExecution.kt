package com.ark.voidlauncher.domain

/** Identifiers captured before a reset so host-bound resources can be released afterwards. */
data class ResetCleanup(
    val appWidgetIds: List<Int>,
    val backgroundImageTokens: List<String>,
)

/**
 * Orders the phases of RESET ALL.
 *
 * Cleanup data is read first so it is not lost when Room deletes the rows, but widget/
 * image deletion and preference writes are strictly post-commit: a failed Room
 * transaction must leave both the host and DataStore untouched. Preference writes
 * happen only after cleanup succeeds, and any post-commit failure is surfaced with an
 * explicit message instead of being swallowed.
 */
class ResetExecution(
    private val readCleanup: suspend () -> ResetCleanup,
    private val commitRoom: suspend () -> Long,
    private val releaseAppWidget: (Int) -> Unit,
    private val deleteBackgroundImage: suspend (String) -> Unit,
    private val writeHomeCoord: suspend () -> Unit,
    private val clearPreferences: suspend () -> Unit,
) {
    suspend fun execute(): Long {
        val cleanup = readCleanup()

        // Transaction failures propagate unchanged; nothing below this line runs.
        val homeId = commitRoom()

        runCatching {
            cleanup.appWidgetIds.forEach(releaseAppWidget)
            cleanup.backgroundImageTokens.forEach { token -> deleteBackgroundImage(token) }
        }.onFailure { cause ->
            throw IllegalStateException("DATA RESET; CLEANUP INCOMPLETE", cause)
        }

        runCatching {
            writeHomeCoord()
            clearPreferences()
        }.onFailure { cause ->
            throw IllegalStateException("DATA RESET; PREFERENCES NOT CLEARED", cause)
        }

        return homeId
    }
}
