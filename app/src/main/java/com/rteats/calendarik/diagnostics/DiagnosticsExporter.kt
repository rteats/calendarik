package com.rteats.calendarik.diagnostics

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Save to Downloads on Android 10+, or use the document picker on older Android. */
object DiagnosticsExporter {
    const val MIME = "application/zip"
    const val DOWNLOAD_SUBFOLDER = "Calendarik/Diagnostics"

    @RequiresApi(29)
    suspend fun saveToDownloads(context: Context, name: String, bytes: ByteArray): Uri =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME)
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_SUBFOLDER + "/")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Не удалось создать ZIP в Загрузках")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Не удалось записать диагностический ZIP")
                check(resolver.update(uri, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null) > 0)
                uri
            } catch (t: Exception) {
                resolver.delete(uri, null, null)
                throw t
            }
        }

    suspend fun savePicked(context: Context, uri: Uri, bytes: ByteArray): Unit =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: error("Не удалось сохранить ZIP в выбранное место")
        }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Calendarik diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить диагностический ZIP"))
    }
}

