package com.octorice.opengifwidget.gif

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
//noinspection ExifInterface (see the note on StillImageDecoder)
import android.media.ExifInterface
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Uses the built-in android.media.ExifInterface on purpose: it only runs on Android 8 here
 * (Android 9+ uses ImageDecoder, which handles EXIF itself), where it reads orientation reliably,
 * and the AndroidX version would be an extra library.
 *
 * Decodes a still picture (PNG, JPEG, WebP, HEIC, ...) shrunk to at most [maxSidePx] on its
 * longest side, turned the right way up (photos store their rotation in EXIF data), and in a
 * normal 32-bit format with transparency. Returns null if the file can't be read as a picture.
 */
@SuppressLint("ExifInterface")
object StillImageDecoder {

    fun decode(file: File, maxSidePx: Int): Bitmap? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(file, maxSidePx)
        } else {
            decodeLegacy(file, maxSidePx)
        } ?: return null
        return toArgb8888(bitmap)
    }

    /** Android 9+: ImageDecoder shrinks while decoding and applies the EXIF rotation by itself. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(file: File, maxSidePx: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // so we can draw and save it
            val width = info.size.width
            val height = info.size.height
            val scale = min(1.0, maxSidePx / max(width, height).toDouble())
            if (scale < 1.0) {
                decoder.setTargetSize(max(1, (width * scale).roundToInt()), max(1, (height * scale).roundToInt()))
            }
        }

    /** Android 8: decode roughly shrunk, then scale exactly and rotate with the EXIF orientation. */
    private fun decodeLegacy(file: File, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Halve while the picture stays at least as big as needed (cheap and saves memory).
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSidePx) sampleSize *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            ?: return null

        val matrix = Matrix()
        val scale = min(1f, maxSidePx / max(decoded.width, decoded.height).toFloat())
        matrix.postScale(scale, scale)
        applyExifOrientation(matrix, exifOrientation(file))
        if (matrix.isIdentity) return decoded
        val result = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (result !== decoded) decoded.recycle()
        return result
    }

    private fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL // no EXIF data (PNG, WebP, ...) or unreadable: leave as is
    }

    private fun applyExifOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
        }
    }

    /** Some pictures decode as 16-bit or wide-color bitmaps; convert to the usual 32-bit format. */
    private fun toArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return bitmap
        bitmap.recycle()
        return copy
    }
}
