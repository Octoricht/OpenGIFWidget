package com.octorice.wuxiagifwidget.gif

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The result of splitting a GIF into PNG frames. */
data class FrameSet(
    val version: Long,        // folder name; changes every time a GIF is picked
    val frameCount: Int,      // frames we saved
    val sourceFrameCount: Int, // frames in the original GIF
    val intervalMs: Int,      // average time per saved frame (the ViewFlipper speed)
    val width: Int,
    val height: Int
)

/**
 * Splits a GIF into PNG files: frame_000.png, frame_001.png, ...
 *
 * Later, the launcher will hold ALL frames in memory at once, so we keep the total small:
 * frames are shrunk to at most [MAX_SIDE_PX], long GIFs skip frames to stay under [MAX_FRAMES],
 * and if it's still too much, frames are shrunk further to fit [MAX_TOTAL_PIXELS].
 */
object FrameExtractor {

    private const val MAX_SIDE_PX = 512
    private const val MAX_FRAMES = 120
    private const val MAX_TOTAL_PIXELS = 8_000_000 // about 32 MB of bitmaps in the launcher

    // Browsers treat very short delays (0-10 ms) as 100 ms, so GIFs play at the speed people expect.
    private const val MIN_FRAME_MS = 20
    private const val DEFAULT_FRAME_MS = 100

    fun frameFileName(index: Int): String = String.format(Locale.ROOT, "frame_%03d.png", index)

    /** Slow: decodes and saves every frame. Call it off the main thread. */
    fun extract(
        gifFile: File,
        outDir: File,
        version: Long,
        onProgress: (done: Int, total: Int) -> Unit
    ): FrameSet {
        val decoder = GifDecoder(InputSource.FileSource(gifFile))
        try {
            val sourceCount = decoder.numberOfFrames
            val width = decoder.width
            val height = decoder.height
            if (sourceCount < 1 || width < 1 || height < 1) throw IOException("That GIF has no frames")

            // Keep every frame, or every 2nd/3rd/... frame for very long GIFs.
            val step = ceil(sourceCount / MAX_FRAMES.toDouble()).toInt()
            val keptIndices = (0 until sourceCount step step).toList()

            // Shrink big GIFs, then shrink more if all frames together are still too big.
            var scale = min(1.0, MAX_SIDE_PX / max(width, height).toDouble())
            val totalPixels = keptIndices.size * (width * scale) * (height * scale)
            if (totalPixels > MAX_TOTAL_PIXELS) scale *= sqrt(MAX_TOTAL_PIXELS / totalPixels)
            val outWidth = max(1, (width * scale).roundToInt())
            val outHeight = max(1, (height * scale).roundToInt())

            outDir.mkdirs()
            // One full-size bitmap that every frame is drawn into, to save memory.
            val buffer = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            var totalMs = 0L
            try {
                keptIndices.forEachIndexed { savedIndex, sourceIndex ->
                    // Always moving forward, so the decoder never has to restart from frame 0.
                    decoder.seekToFrame(sourceIndex, buffer)
                    val frame = if (outWidth == width && outHeight == height) {
                        buffer
                    } else {
                        buffer.scale(outWidth, outHeight, filter = true)
                    }
                    File(outDir, frameFileName(savedIndex)).outputStream().use {
                        frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    if (frame !== buffer) frame.recycle()

                    // A saved frame lasts as long as itself plus the frames we skipped after it.
                    for (i in sourceIndex until min(sourceIndex + step, sourceCount)) {
                        totalMs += frameDuration(decoder, i)
                    }
                    onProgress(savedIndex + 1, keptIndices.size)
                }
            } finally {
                buffer.recycle()
            }

            return FrameSet(
                version = version,
                frameCount = keptIndices.size,
                sourceFrameCount = sourceCount,
                intervalMs = (totalMs / keptIndices.size).toInt(),
                width = outWidth,
                height = outHeight
            )
        } catch (e: OutOfMemoryError) {
            throw IOException("That GIF is too big to process")
        } finally {
            decoder.recycle()
        }
    }

    private fun frameDuration(decoder: GifDecoder, index: Int): Int {
        val ms = decoder.getFrameDuration(index)
        return if (ms < MIN_FRAME_MS) DEFAULT_FRAME_MS else ms
    }
}
