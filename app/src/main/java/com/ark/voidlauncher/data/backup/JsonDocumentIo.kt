package com.ark.voidlauncher.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared SAF text I/O for the layout, widget bundle and inventory documents. */
internal object JsonDocumentIo {
    suspend fun write(context: Context, uri: Uri, json: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                if (json.isEmpty()) return@runCatching false
                val output = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
                output.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                true
            }.getOrDefault(false)
        }

    suspend fun read(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes().toString(Charsets.UTF_8)
                }
            }.getOrNull()
        }
}
