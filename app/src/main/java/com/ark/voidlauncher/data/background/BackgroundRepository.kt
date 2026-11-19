package com.ark.voidlauncher.data.background

import android.content.Context
import androidx.room.withTransaction
import com.ark.voidlauncher.data.db.VoidDatabase
import com.ark.voidlauncher.domain.background.BackgroundScope
import com.ark.voidlauncher.domain.background.PageBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Single write surface for backgrounds. Rows are reactive; image bytes live in
 * app-internal storage keyed by an opaque caller-supplied token so no content://
 * URI is ever persisted or backed up.
 */
class BackgroundRepository(
    private val database: VoidDatabase,
    private val context: Context,
) {
    private val dao get() = database.backgroundDao()

    val backgrounds: Flow<List<PageBackground>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    /**
     * Enforces one global row and at most one row per page. The incoming id is
     * intentionally discarded: the existing row is deleted first, and Room
     * generates a fresh id, so a stale id cannot replace an unrelated row.
     */
    suspend fun save(background: PageBackground): Long = withContext(Dispatchers.IO) {
        when (background.scope) {
            BackgroundScope.GLOBAL -> database.withTransaction {
                dao.clearGlobal()
                dao.upsert(background.copy(id = 0L, pageId = null).toEntity())
            }
            BackgroundScope.PAGE -> {
                val pageId = requireNotNull(background.pageId) {
                    "PAGE backgrounds require a non-null pageId"
                }
                database.withTransaction {
                    dao.deleteForPage(pageId)
                    dao.upsert(background.copy(id = 0L, pageId = pageId).toEntity())
                }
            }
        }
    }

    suspend fun remove(id: Long) {
        withContext(Dispatchers.IO) { dao.deleteById(id) }
    }

    suspend fun clearPage(pageId: Long) {
        withContext(Dispatchers.IO) { dao.deleteForPage(pageId) }
    }

    /**
     * Replaces all rows. Duplicate globals and duplicate per-page entries in the
     * input are collapsed to the first occurrence to preserve the DB invariants.
     */
    suspend fun importAll(items: List<PageBackground>) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.deleteAll()
                var globalInserted = false
                val insertedPages = HashSet<Long>()
                items.forEach { item ->
                    if (item.scope == BackgroundScope.GLOBAL) {
                        if (!globalInserted) {
                            dao.upsert(item.copy(id = 0L, pageId = null).toEntity())
                            globalInserted = true
                        }
                        return@forEach
                    }
                    val pageId = item.pageId ?: return@forEach // defensive: skip malformed row
                    if (!insertedPages.add(pageId)) return@forEach
                    dao.upsert(item.copy(id = 0L, pageId = pageId).toEntity())
                }
            }
        }
    }

    /** Copies SAF-derived bytes into filesDir/backgrounds/<token>.img. */
    suspend fun importImage(token: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val file = imageFile(token)
                ?: throw IllegalArgumentException("Invalid background image token: $token")
            file.parentFile?.mkdirs()
            file.outputStream().use { it.write(bytes) }
        }
    }

    /** Returns null for an invalid token, missing file, or unreadable bytes. */
    suspend fun imageBytes(token: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = imageFile(token) ?: return@withContext null
        if (!file.exists()) return@withContext null
        runCatching { file.inputStream().use { it.readBytes() } }.getOrNull()
    }

    suspend fun deleteImage(token: String) {
        withContext(Dispatchers.IO) {
            val file = imageFile(token) ?: return@withContext
            if (file.exists()) file.delete()
        }
    }

    /**
     * Restricts tokens to a flat filename so a caller can never escape the image
     * directory via path traversal. UUIDs from the SAF flow pass unchanged.
     */
    private fun imageFile(token: String): File? {
        if (!TOKEN_REGEX.matches(token) || token == "." || token == "..") return null
        return File(File(context.filesDir, IMAGE_DIR), "$token.img")
    }

    companion object {
        private const val IMAGE_DIR = "backgrounds"
        private val TOKEN_REGEX = Regex("[A-Za-z0-9._-]+")
    }
}
