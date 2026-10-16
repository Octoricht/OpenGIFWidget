package com.octorice.opengifwidget.gif

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.ImportException
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.max

/** Reading and writing single frame files (lossy WebP with transparency). */
object FrameFiles {

    fun frameFileName(index: Int): String = String.format(Locale.ROOT, "frame_%03d.webp", index)

    /** Lossy WebP keeps transparency and is several times smaller than PNG. */
    @Suppress("DEPRECATION")
    private val LOSSY_WEBP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP // before Android 11 this is lossy when quality < 100
    }

    fun writeWebp(bitmap: Bitmap, file: File, quality: Int) {
        val ok = file.outputStream().use { bitmap.compress(LOSSY_WEBP, quality, it) }
        if (!ok) {
            // compress() just says "no"; the usual reason is a full disk. (A rough check is
            // enough here: it only picks which message to show.)
            @Suppress("UsableSpace")
            val nearlyFull = (file.parentFile?.usableSpace ?: 0L) < 5L * 1024 * 1024
            throw if (nearlyFull) ImportException(R.string.error_no_space) else IOException("Couldn't save a frame")
        }
    }

    /**
     * Loads a frame, shrunk by halving until it's no bigger than needed for [maxSidePx].
     * [reuse] is an old bitmap of the same size whose memory can be recycled (or null).
     */
    fun decode(file: File, maxSidePx: Int = Int.MAX_VALUE, reuse: Bitmap? = null): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSidePx) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inMutable = true
            inBitmap = reuse
        }
        return try {
            BitmapFactory.decodeFile(file.path, options)
        } catch (e: IllegalArgumentException) {
            // The old bitmap couldn't be reused (different size): decode into a fresh one.
            BitmapFactory.decodeFile(file.path, options.apply { inBitmap = null })
        }
    }
}
