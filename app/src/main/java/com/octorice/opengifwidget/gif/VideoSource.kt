package com.octorice.opengifwidget.gif

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.ImportException
import java.io.Closeable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A video's length and its size as it should be shown (already turned upright). */
data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

/**
 * Reads a video with Android's built-in MediaMetadataRetriever (no extra library, no audio).
 * The video is read straight from the picked file, never copied.
 * Not thread-safe: use it from one thread at a time. Always [close] it.
 */
class VideoSource(context: Context, uri: Uri) : Closeable {

    private val retriever = MediaMetadataRetriever()
    val info: VideoInfo
    private val rotation: Int
    private val rawWidth: Int
    private val rawHeight: Int

    /** How many frames the video has, if it says (Android 9+). Needed for fast batch decoding. */
    val totalFrames: Int?

    init {
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            totalFrames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()?.takeIf { it > 0 }
            } else {
                null
            }
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            if (!hasVideo || durationMs <= 0 || rawWidth <= 0 || rawHeight <= 0) {
                throw ImportException(R.string.error_unsupported_video)
            }
            // Phones often store portrait videos sideways plus a "rotate 90" note.
            val sideways = rotation % 180 != 0
            info = VideoInfo(
                durationMs = durationMs,
                width = if (sideways) rawHeight else rawWidth,
                height = if (sideways) rawWidth else rawHeight
            )
        } catch (e: ImportException) {
            retriever.release()
            throw e
        } catch (e: Exception) {
            retriever.release()
            throw ImportException(R.string.error_unsupported_video)
        }
    }

    /**
     * The frame shown at [timeMs], upright and at most [maxSidePx] on its longest side,
     * or null if the video has no picture there.
     */
    fun frameAt(timeMs: Long, maxSidePx: Int): Bitmap? {
        // Never ask for more pixels than the video has: enlarging adds size, not detail.
        val maxSide = min(maxSidePx, max(info.width, info.height))
        val timeUs = timeMs * 1000
        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Asking for a square box means "shrink to fit", whichever way the frame comes out.
            retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, maxSide, maxSide)
        } else {
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } ?: return null
        return uprightAndSized(frame, maxSide)
    }

    /** The frame number shown at [timeMs] (only when [totalFrames] is known). */
    fun indexAt(timeMs: Long): Int {
        val total = totalFrames ?: return 0
        return (timeMs.toDouble() / info.durationMs * total).roundToInt().coerceIn(0, total - 1)
    }

    /**
     * How many full-size frames to decode in one batch: as many as fit in about 48 MB,
     * so a 4K video uses small batches and a small video bigger ones.
     */
    fun batchSize(): Int = (48L * 1024 * 1024 / (rawWidth.toLong() * rawHeight * 4)).toInt().coerceIn(1, 30)

    /**
     * Frames [first] to [first] + [count] - 1, decoded in ONE pass (Android 9+). Much faster than
     * asking for each frame by time, which restarts decoding from the previous keyframe every time.
     * Each frame is turned upright and shrunk to at most [maxSidePx].
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun framesAtIndex(first: Int, count: Int, maxSidePx: Int): List<Bitmap> {
        val maxSide = min(maxSidePx, max(info.width, info.height))
        val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
        return retriever.getFramesAtIndex(first, count, params).map { uprightAndSized(it, maxSide) }
    }

    /**
     * Some Android versions apply the video's rotation to frames, some don't. Instead of guessing,
     * compare the frame's shape to the upright shape and rotate only if it came out sideways.
     * Also shrinks frames that are still too big (Android 8.0 can't decode them shrunk).
     */
    private fun uprightAndSized(frame: Bitmap, maxSidePx: Int): Bitmap {
        val matrix = Matrix()
        val frameIsLandscape = frame.width > frame.height
        val shouldBeLandscape = info.width > info.height
        val needsTurn = rotation % 180 != 0 && frame.width != frame.height && frameIsLandscape != shouldBeLandscape
        if (needsTurn) matrix.postRotate(rotation.toFloat())
        // (A 180° turn can't be detected from the shape; those rare videos are left as Android returns them.)
        val scale = min(1f, maxSidePx / max(frame.width, frame.height).toFloat())
        if (scale < 1f) matrix.postScale(scale, scale)
        if (matrix.isIdentity) return frame
        val result = Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, matrix, true)
        if (result !== frame) frame.recycle()
        return result
    }

    override fun close() {
        try {
            retriever.release()
        } catch (e: Exception) {
            // Already released or broken: nothing left to free.
        }
    }

    companion object {
        /** Frame count for a clip of [lengthMs] at [fps], at least 1, at most [FrameExtractor.MAX_FRAMES]. */
        fun frameCount(lengthMs: Long, fps: Int): Int =
            (lengthMs * fps / 1000.0).roundToInt().coerceIn(1, FrameExtractor.MAX_FRAMES)
    }
}
