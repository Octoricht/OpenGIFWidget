package com.octorice.wuxiagifwidget.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
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
 * Which part of the GIF to keep, for Fill mode: a region with the widget's shape
 * ([aspectRatio] = width / height), centered as close as possible to
 * ([centerX], [centerY]), which go from 0 (left/top edge) to 1 (right/bottom edge).
 */
data class Crop(val centerX: Float, val centerY: Float, val aspectRatio: Float)

/**
 * Splits a GIF into PNG files: frame_000.png, frame_001.png, ...
 *
 * Later, the launcher will hold ALL frames in memory at once, so we keep the total small:
 * frames are shrunk to at most [MAX_SIDE_PX], long GIFs skip frames to stay under [MAX_FRAMES],
 * and if it's still too much, frames are shrunk further to fit [MAX_TOTAL_PIXELS].
 * Fast GIFs also skip frames so the widget flips at most every [MIN_INTERVAL_MS]: each flip
 * makes the launcher redo part of its layout, so fewer flips keep the home screen smooth.
 */
object FrameExtractor {

    private const val MAX_SIDE_PX = 512
    private const val MAX_FRAMES = 120
    private const val MAX_TOTAL_PIXELS = 8_000_000 // about 32 MB of bitmaps in the launcher
    private const val MIN_INTERVAL_MS = 40 // at most 25 flips per second

    // Browsers treat very short delays (0-10 ms) as 100 ms, so GIFs play at the speed people expect.
    private const val MIN_FRAME_MS = 20
    private const val DEFAULT_FRAME_MS = 100

    fun frameFileName(index: Int): String = String.format(Locale.ROOT, "frame_%03d.png", index)

    /** Slow: decodes and saves every frame. Call it off the main thread. */
    fun extract(
        gifFile: File,
        outDir: File,
        version: Long,
        crop: Crop?, // null = keep the whole frame (Fit mode)
        onProgress: (done: Int, total: Int) -> Unit
    ): FrameSet {
        val decoder = GifDecoder(InputSource.FileSource(gifFile))
        try {
            val sourceCount = decoder.numberOfFrames
            val width = decoder.width
            val height = decoder.height
            if (sourceCount < 1 || width < 1 || height < 1) throw IOException("That GIF has no frames")

            // Keep every frame, or every 2nd/3rd/... frame for very long or very fast GIFs.
            val durations = IntArray(sourceCount) { frameDuration(decoder, it) }
            val averageSourceMs = durations.sum() / sourceCount.toDouble()
            val stepForLength = ceil(sourceCount / MAX_FRAMES.toDouble()).toInt()
            val stepForSpeed = ceil(MIN_INTERVAL_MS / averageSourceMs).toInt()
            val step = max(1, max(stepForLength, stepForSpeed))
            val keptIndices = (0 until sourceCount step step).toList()

            // The part of each frame we keep: everything, or the Fill crop.
            val source = if (crop == null) Rect(0, 0, width, height) else cropRect(width, height, crop)

            // Shrink big GIFs, then shrink more if all frames together are still too big.
            // Measured on the kept part, so a cropped GIF keeps more detail.
            var scale = min(1.0, MAX_SIDE_PX / max(source.width(), source.height()).toDouble())
            val totalPixels = keptIndices.size * (source.width() * scale) * (source.height() * scale)
            if (totalPixels > MAX_TOTAL_PIXELS) scale *= sqrt(MAX_TOTAL_PIXELS / totalPixels)
            val outWidth = max(1, (source.width() * scale).roundToInt())
            val outHeight = max(1, (source.height() * scale).roundToInt())

            outDir.mkdirs()
            // Two bitmaps reused for every frame, to save memory: the decoder draws the full
            // frame into [buffer], then the kept part is drawn (shrunk) into [output].
            val buffer = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val output = createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG) // smooth shrinking
            val target = Rect(0, 0, outWidth, outHeight)
            var totalMs = 0L
            try {
                keptIndices.forEachIndexed { savedIndex, sourceIndex ->
                    // Always moving forward, so the decoder never has to restart from frame 0.
                    decoder.seekToFrame(sourceIndex, buffer)
                    output.eraseColor(Color.TRANSPARENT) // keep transparent GIFs transparent
                    canvas.drawBitmap(buffer, source, target, paint)
                    File(outDir, frameFileName(savedIndex)).outputStream().use {
                        output.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }

                    // A saved frame lasts as long as itself plus the frames we skipped after it.
                    for (i in sourceIndex until min(sourceIndex + step, sourceCount)) {
                        totalMs += durations[i]
                    }
                    onProgress(savedIndex + 1, keptIndices.size)
                }
            } finally {
                buffer.recycle()
                output.recycle()
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

    /**
     * The biggest region with the crop's shape that fits in the frame, slid as close as
     * possible to the chosen center (it can't go past the frame's edges).
     */
    fun cropRect(width: Int, height: Int, crop: Crop): Rect {
        val cropWidth: Int
        val cropHeight: Int
        if (width.toFloat() / height > crop.aspectRatio) {
            cropHeight = height // GIF is wider than the widget: keep full height
            cropWidth = max(1, (height * crop.aspectRatio).roundToInt()).coerceAtMost(width)
        } else {
            cropWidth = width // GIF is taller than the widget: keep full width
            cropHeight = max(1, (width / crop.aspectRatio).roundToInt()).coerceAtMost(height)
        }
        val left = (crop.centerX * width - cropWidth / 2f).roundToInt().coerceIn(0, width - cropWidth)
        val top = (crop.centerY * height - cropHeight / 2f).roundToInt().coerceIn(0, height - cropHeight)
        return Rect(left, top, left + cropWidth, top + cropHeight)
    }

    private fun frameDuration(decoder: GifDecoder, index: Int): Int {
        val ms = decoder.getFrameDuration(index)
        return if (ms < MIN_FRAME_MS) DEFAULT_FRAME_MS else ms
    }
}
