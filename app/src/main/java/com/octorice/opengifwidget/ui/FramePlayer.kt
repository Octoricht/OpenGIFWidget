package com.octorice.opengifwidget.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.gif.FrameFiles
import com.octorice.opengifwidget.gif.FrameSet
import com.octorice.opengifwidget.gif.Zoom
import com.octorice.opengifwidget.widget.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A frame being shown, and the folder it came from. */
data class PlayedFrame(val dir: File?, val image: ImageBitmap?)

/**
 * Plays a saved frame set on screen, the same way the widget does (same frames, same speed).
 * Frames are loaded one at a time in the background, reusing the memory of an old frame,
 * so even long animations use only two frames' worth of memory.
 * Returns the frame to show right now (null while the first one loads).
 * [reuseBitmaps] = false for slideshows: the old picture is still on screen during a transition.
 */
@Composable
fun rememberFramePlayer(
    dir: File?,
    frames: FrameSet?,
    maxSidePx: Int,
    speed: Float = 1f,
    fpsCap: Int = AppSettings.DEFAULT_FPS_CAP,
    reuseBitmaps: Boolean = true
): ImageBitmap? = rememberPlayedFrame(dir, frames, maxSidePx, speed, fpsCap, reuseBitmaps).image

/**
 * [rememberFramePlayer], also saying which folder the frame came from. With [keepWhileLoading], the
 * old frame stays until the new folder's first frame is ready (the slideshow editor, so tapping
 * another picture goes straight from one to the other, with its transition).
 */
@Composable
fun rememberPlayedFrame(
    dir: File?,
    frames: FrameSet?,
    maxSidePx: Int,
    speed: Float = 1f,
    fpsCap: Int = AppSettings.DEFAULT_FPS_CAP,
    reuseBitmaps: Boolean = true,
    keepWhileLoading: Boolean = false
): PlayedFrame {
    var current by remember(if (keepWhileLoading) Unit else dir) { mutableStateOf(PlayedFrame(null, null)) }
    // Read the newest values on every frame, so moving the speed slider changes the
    // preview right away without restarting the animation.
    val latestSpeed by rememberUpdatedState(speed)
    val latestFpsCap by rememberUpdatedState(fpsCap)
    LaunchedEffect(dir, frames, maxSidePx) {
        if (dir == null || frames == null || frames.frameCount <= 0) {
            current = PlayedFrame(null, null)
            return@LaunchedEffect
        }
        var shown: Bitmap? = null
        var spare: Bitmap? = null
        var step = 0
        while (isActive) {
            val plan = Playback.plan(frames.frameCount, frames.intervalMs, latestSpeed, latestFpsCap)
            val start = SystemClock.uptimeMillis()
            val index = plan.frames[step % plan.frames.size]
            val file = File(dir, FrameFiles.frameFileName(index))
            val next = withContext(Dispatchers.IO) { FrameFiles.decode(file, maxSidePx, if (reuseBitmaps) spare else null) }
                ?: return@LaunchedEffect // frames deleted (e.g. replaced): stop quietly
            spare = shown
            shown = next
            current = PlayedFrame(dir, next.asImageBitmap())
            if (frames.frameCount == 1) return@LaunchedEffect // a single picture doesn't need a loop
            step++
            delay(max(0L, plan.intervalMs - (SystemClock.uptimeMillis() - start)))
        }
    }
    return current
}

/** Where the picture goes inside a box of [boxWidth] x [boxHeight] pixels in Fill mode. */
data class FillPlacement(val width: Int, val height: Int, val x: Int, val y: Int)

/**
 * Fill mode: scale the picture up until it covers the whole box, then slide it so the point
 * [center] (0..1 on each axis) is as close to the middle as possible without showing an edge.
 * This matches FrameExtractor.cropRect, so previews show exactly what the widget will show.
 */
fun fillPlacement(boxWidth: Int, boxHeight: Int, imageWidth: Int, imageHeight: Int, center: Offset): FillPlacement {
    val scale = max(boxWidth / imageWidth.toFloat(), boxHeight / imageHeight.toFloat())
    val width = (imageWidth * scale).roundToInt()
    val height = (imageHeight * scale).roundToInt()
    val x = (boxWidth / 2f - center.x * width).roundToInt().coerceIn(boxWidth - width, 0)
    val y = (boxHeight / 2f - center.y * height).roundToInt().coerceIn(boxHeight - height, 0)
    return FillPlacement(width, height, x, y)
}

/**
 * Draws one frame the way the widget will: whole (Fit), or covering the box centered on
 * [center] (Fill). [center] is a function so dragging only redraws, without rebuilding the screen.
 */
@Composable
fun FrameImage(
    image: ImageBitmap,
    scaleMode: ScaleMode,
    center: () -> Offset,
    modifier: Modifier = Modifier,
    zoom: (() -> Float?)? = null // custom zoom (1 = Fill, see Zoom); null = the scaleMode preset
) {
    Canvas(modifier.clipToBounds()) {
        val custom = zoom?.invoke()
        if (custom != null) {
            // The same box the widget frames are made from (Zoom.box), stretched over this view.
            val c = center()
            val box = Zoom.box(image.width.toFloat(), image.height.toFloat(), size.width / size.height, custom, c.x, c.y)
            val k = size.width / box.width()
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset((-box.left * k).roundToInt(), (-box.top * k).roundToInt()),
                dstSize = IntSize((image.width * k).roundToInt(), (image.height * k).roundToInt()),
                filterQuality = FilterQuality.Medium
            )
            return@Canvas
        }
        val boxWidth = size.width.roundToInt()
        val boxHeight = size.height.roundToInt()
        val spot = if (scaleMode == ScaleMode.FIT) {
            val scale = min(boxWidth / image.width.toFloat(), boxHeight / image.height.toFloat())
            val width = (image.width * scale).roundToInt()
            val height = (image.height * scale).roundToInt()
            FillPlacement(width, height, (boxWidth - width) / 2, (boxHeight - height) / 2)
        } else {
            fillPlacement(boxWidth, boxHeight, image.width, image.height, center())
        }
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(spot.x, spot.y),
            dstSize = IntSize(spot.width, spot.height),
            filterQuality = FilterQuality.Medium
        )
    }
}
