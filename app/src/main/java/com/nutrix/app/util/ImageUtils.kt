package com.nutrix.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photo preparation for the vision call.
 *
 * Two things matter here. Resolution: beyond about 1568 px on the long edge the API gains
 * nothing and the user pays for the extra tokens, so photos are downscaled first. Orientation:
 * a phone camera writes the rotation into EXIF rather than the pixels, and a sideways plate is
 * measurably harder to read portion size from.
 */
object ImageUtils {

    private const val MAX_EDGE_PX = 1568
    private const val JPEG_QUALITY = 85

    const val MEDIA_TYPE_JPEG = "image/jpeg"

    suspend fun readForAnalysis(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(context, uri) ?: return@withContext null
        val rotated = applyExifRotation(context, uri, bitmap)
        ByteArrayOutputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    suspend fun readForAnalysis(file: File): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null
        val rotated = runCatching {
            rotate(bitmap, ExifInterface(file.absolutePath).rotationDegrees.toFloat())
        }.getOrDefault(bitmap)
        ByteArrayOutputStream().use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_EDGE_PX) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val degrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
        }.getOrNull() ?: 0
        return rotate(bitmap, degrees.toFloat())
    }

    private fun rotate(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
