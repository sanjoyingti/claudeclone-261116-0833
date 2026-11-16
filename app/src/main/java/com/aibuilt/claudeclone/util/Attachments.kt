package com.aibuilt.claudeclone.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.aibuilt.claudeclone.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object Attachments {

    private const val MAX_BYTES = 1_000_000
    private const val MAX_EDGE = 1568

    suspend fun load(context: Context, uri: Uri): Attachment? = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: guessMime(uri)
        val name = queryName(context, uri)
        if (mime == "application/pdf") {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext null
            return@withContext Attachment(name, mime, Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        val shrunk = downscale(context, uri) ?: return@withContext null
        Attachment(name, if (mime.startsWith("image/")) mime else "image/jpeg", shrunk)
    }

    fun decodeThumbnail(base64: String): Bitmap? = runCatching {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }.getOrNull()

    private fun downscale(context: Context, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        var longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / sample > MAX_EDGE) sample *= 2

        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull() ?: return null

        val scaled = if (maxOf(bitmap.width, bitmap.height) > MAX_EDGE) {
            val factor = MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * factor).toInt(), (bitmap.height * factor).toInt(), true)
        } else {
            bitmap
        }

        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > MAX_BYTES && quality >= 30)

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun guessMime(uri: Uri): String {
        val path = uri.lastPathSegment?.lowercase().orEmpty()
        return when {
            path.endsWith(".png") -> "image/png"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".pdf") -> "application/pdf"
            else -> "image/jpeg"
        }
    }

    private fun queryName(context: Context, uri: Uri): String {
        val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(projection[0])
                    if (index >= 0) return cursor.getString(index) ?: "file"
                }
            }
        }
        return uri.lastPathSegment ?: "file"
    }
}
