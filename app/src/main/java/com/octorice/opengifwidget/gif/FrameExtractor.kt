package com.octorice.opengifwidget.gif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.ImportException
import pl.droidsonroids.gif.GifDecoder
import pl.droidsonroids.gif.InputSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** A set of frames saved in one folder: frame_000.webp, frame_001.webp, ... */
data class FrameSet(
    val version: Long,         // folder name; a new one for every set of frames
    val frameCount: Int,       // frames saved
    val sourceFrameCount: Int, // frames in the original file
    val intervalMs: Int,       // average time per saved frame, at normal speed
    val width: Int,
    val height: Int
)

/**
 * Which part of the picture to keep, for Fill mode: a region with the widget's shape
 * ([aspectRatio] = width / height), centered as close as possible to
 * ([centerX], [centerY]), which go from 0 (left/top edge) to 1 (right/bottom edge).
 */
data class Crop(val centerX: Float, val centerY: Float, val aspectRatio: Float)

/**
 * Turns pictures into frames, in two steps:
 * 1. [extractMasterFromGif]: decode the original once into "master" frames: uncropped,
 *    sized by [masterMaxSide] (at most [MASTER_MAX_SIDE_PX]). These are kept, and the original is not.
 * 2. [deriveWidgetFrames]: make the frames the widget actually shows from the master frames:
 *    cropped for Fill, within [FrameLimits] (smaller on low-RAM phones), small WebP files.
 * Changing Fit/Fill or the crop only repeats step 2, so the original is never needed again.
 */
object FrameExtractor {

    // Master frames are bigger than widget frames so that a Fill crop still has detail left.
    private const val MASTER_MAX_SIDE_PX = 768
    private const val MASTER_MIN_SIDE_PX = 256
    private const val MASTER_QUALITY = 85

    /**
     * How big master frames need to be. The memory budget makes widget frames smaller when there
     * are many of them (e.g. 90 frames -> about 300 px), so masters only need about twice that
     * (room for a Fill crop), not the full 768 px. Saves a lot of storage on long animations.
     */
    fun masterMaxSide(frameCount: Int, limits: FrameLimits): Int {
        val widgetSide = sqrt(limits.maxTotalPixels / frameCount.coerceAtLeast(1).toDouble())
        return (2 * widgetSide).roundToInt().coerceIn(MASTER_MIN_SIDE_PX, MASTER_MAX_SIDE_PX)
    }
    const val MAX_FRAMES = 150

    // The launcher holds ALL widget frames in memory at once, so these stay small.
    private const val WIDGET_QUALITY = 80

    // Still pictures: only one bitmap in the launcher, so they can be much sharper.
    private const val STILL_MASTER_MAX_SIDE_PX = 1620 // room for a Fill crop
    private const val STILL_QUALITY = 90
    private const val STILL_INTERVAL_MS = 1000 // unused for display; a still has one frame

    // Browsers treat very short delays (0-10 ms) as 100 ms, so GIFs play at the speed people expect.
    private const val MIN_FRAME_MS = 20
    private const val DEFAULT_FRAME_MS = 100

    /** Slow: decodes the whole GIF. Call it off the main thread. */
    fun extractMasterFromGif(
        gifFile: File,
        outDir: File,
        version: Long,
        limits: FrameLimits = FrameLimits.NORMAL,
        onProgress: (done: Int, total: Int) -> Unit
    ): FrameSet {
        val decoder = GifDecoder(InputSource.FileSource(gifFile))
        try {
            val sourceCount = decoder.numberOfFrames
            val width = decoder.width
            val height = decoder.height
            if (sourceCount < 1 || width < 1 || height < 1) throw ImportException(R.string.error_unsupported)

            // Keep every frame, or every 2nd/3rd/... frame for very long GIFs.
            // (Fast GIFs keep all their frames: the FPS cap is applied when the widget is drawn.)
            val durations = IntArray(sourceCount) { frameDuration(decoder, it) }
            val step = max(1, ceil(sourceCount / MAX_FRAMES.toDouble()).toInt())
            val keptIndices = (0 until sourceCount step step).toList()

            val scale = min(1.0, masterMaxSide(keptIndices.size, limits) / max(width, height).toDouble())
            val outWidth = max(1, (width * scale).roundToInt())
            val outHeight = max(1, (height * scale).roundToInt())

            outDir.mkdirs()
            // The decoder draws each full frame into [buffer]; it's then shrunk into [output].
            val buffer = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val output = createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG) // smooth shrinking
            val whole = Rect(0, 0, width, height)
            val target = Rect(0, 0, outWidth, outHeight)
            var totalMs = 0L
            try {
                keptIndices.forEachIndexed { savedIndex, sourceIndex ->
                    // Always moving forward, so the decoder never has to restart from frame 0.
                    decoder.seekToFrame(sourceIndex, buffer)
                    output.eraseColor(Color.TRANSPARENT) // keep transparent GIFs transparent
                    canvas.drawBitmap(buffer, whole, target, paint)
                    FrameFiles.writeWebp(output, File(outDir, FrameFiles.frameFileName(savedIndex)), MASTER_QUALITY)

                    // A saved frame lasts as long as itself plus the frames we skipped after it.
                    for (i in sourceIndex until min(sourceIndex + step, sourceCount)) totalMs += durations[i]
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
        } finally {
            decoder.recycle()
        }
    }

    /**
     * Takes frames from a video clip ([startMs], [lengthMs] long) at [fps] frames per second
     * (at most [MAX_FRAMES]) and saves them as master frames. Cancelling the coroutine
     * (the Cancel button) stops it before the next frame.
     *
     * Android 9+: frames are decoded in batches, which is very fast. Otherwise (or if the video
     * doesn't allow it) each frame is fetched by its time, which is slow because every frame means
     * decoding again from the previous keyframe, so several workers share that job.
     */
    suspend fun extractMasterFromVideo(
        openVideo: () -> VideoSource,
        outDir: File,
        version: Long,
        startMs: Long,
        lengthMs: Long,
        fps: Int,
        limits: FrameLimits = FrameLimits.NORMAL,
        onProgress: (done: Int, total: Int) -> Unit
    ): FrameSet = coroutineScope {
        val count = VideoSource.frameCount(lengthMs, fps)
        val maxSide = masterMaxSide(count, limits)
        val stepMs = lengthMs.toDouble() / count
        val times = List(count) { startMs + (it * stepMs).roundToLong() }
        outDir.mkdirs()
        val size = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) videoInBatches(openVideo, times, outDir, maxSide, onProgress) else null)
            ?: videoFrameByFrame(openVideo, times, outDir, maxSide, onProgress)
        FrameSet(
            version = version,
            frameCount = count,
            sourceFrameCount = count,
            intervalMs = max(1, stepMs.roundToInt()),
            width = size.first,
            height = size.second
        )
    }

    /**
     * Fast path (Android 9+): decodes runs of consecutive frames in one go and keeps the ones we need.
     * Returns the frame size, or null if this video can't be read this way (then use the slow path).
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private suspend fun videoInBatches(
        openVideo: () -> VideoSource,
        times: List<Long>,
        outDir: File,
        maxSide: Int,
        onProgress: (Int, Int) -> Unit
    ): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        openVideo().use { video ->
            val total = video.totalFrames ?: return@withContext null
            val wanted = times.map { video.indexAt(it) } // frame number for each saved frame
            var size = 0 to 0
            var next = 0 // first saved frame not written yet
            val written = AtomicInteger(0)
            try {
                while (next < wanted.size) {
                    ensureActive()
                    val first = wanted[next]
                    val count = min(video.batchSize(), total - first)
                    val batch = video.framesAtIndex(first, count, maxSide)
                    if (batch.isEmpty()) return@withContext null
                    try {
                        // Every saved frame whose frame number falls inside this batch...
                        val jobs = mutableListOf<Pair<Int, Bitmap>>()
                        while (next < wanted.size && wanted[next] - first < batch.size) {
                            jobs += next to batch[wanted[next] - first]
                            next++
                        }
                        size = jobs.first().second.width to jobs.first().second.height
                        // ...is saved in parallel: encoding WebP is the slow part, and frames don't depend on each other.
                        coroutineScope {
                            jobs.map { (index, frame) ->
                                async(Dispatchers.Default) {
                                    FrameFiles.writeWebp(frame, File(outDir, FrameFiles.frameFileName(index)), MASTER_QUALITY)
                                    onProgress(written.incrementAndGet(), wanted.size)
                                }
                            }.awaitAll()
                        }
                    } finally {
                        batch.forEach { it.recycle() }
                    }
                }
                size
            } catch (e: IllegalStateException) {
                null // This video can't be decoded in batches: use the slow path.
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    /** Slow path: fetches each frame by its time; several workers share the frames. */
    private suspend fun videoFrameByFrame(
        openVideo: () -> VideoSource,
        times: List<Long>,
        outDir: File,
        maxSide: Int,
        onProgress: (Int, Int) -> Unit
    ): Pair<Int, Int> = coroutineScope {
        val count = times.size
        val done = AtomicInteger(0)
        val workers = VIDEO_WORKERS.coerceAtMost(count)
        // Worker w handles frames w, w + workers, w + 2 * workers, ...
        (0 until workers).map { worker ->
            async(Dispatchers.IO) {
                openVideo().use { video ->
                    var size = 0 to 0
                    for (index in worker until count step workers) {
                        ensureActive() // stop here if cancelled
                        // Near the very end a video may have no picture: use the last one there is.
                        val frame = video.frameAt(times[index], maxSide)
                            ?: video.frameAt(maxOf(0L, video.info.durationMs - 100), maxSide)
                            ?: throw ImportException(R.string.error_unsupported_video)
                        try {
                            size = frame.width to frame.height
                            FrameFiles.writeWebp(frame, File(outDir, FrameFiles.frameFileName(index)), MASTER_QUALITY)
                        } finally {
                            frame.recycle()
                        }
                        onProgress(done.incrementAndGet(), count)
                    }
                    size
                }
            }
        }.awaitAll().first()
    }

    /** How many video readers work at once: half the CPU cores, 2 to 4. */
    private val VIDEO_WORKERS = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    /** Decodes a still picture into a single master frame. Call it off the main thread. */
    fun extractMasterFromImage(imageFile: File, outDir: File, version: Long): FrameSet {
        val bitmap = StillImageDecoder.decode(imageFile, STILL_MASTER_MAX_SIDE_PX)
            ?: throw ImportException(R.string.error_unsupported)
        try {
            outDir.mkdirs()
            FrameFiles.writeWebp(bitmap, File(outDir, FrameFiles.frameFileName(0)), STILL_QUALITY)
            return FrameSet(version, 1, 1, STILL_INTERVAL_MS, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Makes the widget's frames from the master frames in [masterDir]:
     * the whole picture (Fit, [crop] = null) or the Fill crop, small enough for the launcher.
     */
    fun deriveWidgetFrames(
        master: FrameSet,
        masterDir: File,
        outDir: File,
        version: Long,
        crop: Crop?,
        limits: FrameLimits = FrameLimits.NORMAL,
        widgetMaxSidePx: Int? = null, // the widget's longest side on screen, if known
        cornerRadiusFraction: Float = 0f, // > 0: cut rounded corners (radius = this x frame width)
        onProgress: (done: Int, total: Int) -> Unit
    ): FrameSet {
        val source = if (crop == null) {
            Rect(0, 0, master.width, master.height)
        } else {
            cropRect(master.width, master.height, crop)
        }

        // Shrink to the size limit, then more if all frames together are still too big.
        val still = master.frameCount == 1
        // Never bigger than the widget shows them (if we know its size), nor than the phone's limit.
        val limitSide = if (still) limits.stillMaxSidePx else limits.animationMaxSidePx
        val maxSide = if (widgetMaxSidePx != null && widgetMaxSidePx > 0) minOf(limitSide, widgetMaxSidePx) else limitSide
        val quality = if (still) STILL_QUALITY else WIDGET_QUALITY
        var scale = min(1.0, maxSide / max(source.width(), source.height()).toDouble())
        val totalPixels = master.frameCount * (source.width() * scale) * (source.height() * scale)
        // (The launcher holds all frames in memory at once: about 4 bytes per pixel.)
        if (totalPixels > limits.maxTotalPixels) scale *= sqrt(limits.maxTotalPixels / totalPixels)
        val outWidth = max(1, (source.width() * scale).roundToInt())
        val outHeight = max(1, (source.height() * scale).roundToInt())

        outDir.mkdirs()
        val output = createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val target = Rect(0, 0, outWidth, outHeight)
        // Rounded corners (for Android 8-11, which don't round widgets): the four corner areas,
        // erased after each frame is drawn. Anti-aliased, so the edges stay smooth.
        val corners = if (cornerRadiusFraction > 0f) {
            val radius = cornerRadiusFraction * outWidth
            val full = RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat())
            Path().apply {
                addRect(full, Path.Direction.CW)
                op(Path().apply { addRoundRect(full, radius, radius, Path.Direction.CW) }, Path.Op.DIFFERENCE)
            }
        } else {
            null
        }
        val erasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
        var masterFrame: Bitmap? = null
        try {
            for (index in 0 until master.frameCount) {
                val file = File(masterDir, FrameFiles.frameFileName(index))
                masterFrame = FrameFiles.decode(file, reuse = masterFrame)
                    ?: throw ImportException(R.string.error_frames_missing)
                output.eraseColor(Color.TRANSPARENT)
                canvas.drawBitmap(masterFrame, source, target, paint)
                if (corners != null) canvas.drawPath(corners, erasePaint)
                FrameFiles.writeWebp(output, File(outDir, FrameFiles.frameFileName(index)), quality)
                onProgress(index + 1, master.frameCount)
            }
        } finally {
            output.recycle()
            masterFrame?.recycle()
        }

        return master.copy(version = version, width = outWidth, height = outHeight)
    }

    /**
     * The biggest region with the crop's shape that fits in the frame, slid as close as
     * possible to the chosen center (it can't go past the frame's edges).
     */
    fun cropRect(width: Int, height: Int, crop: Crop): Rect {
        val cropWidth: Int
        val cropHeight: Int
        if (width.toFloat() / height > crop.aspectRatio) {
            cropHeight = height // picture is wider than the widget: keep full height
            cropWidth = max(1, (height * crop.aspectRatio).roundToInt()).coerceAtMost(width)
        } else {
            cropWidth = width // picture is taller than the widget: keep full width
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
