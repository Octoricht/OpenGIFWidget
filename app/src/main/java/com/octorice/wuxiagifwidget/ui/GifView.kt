package com.octorice.wuxiagifwidget.ui

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.viewinterop.AndroidView
import com.octorice.wuxiagifwidget.data.ScaleMode
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Opens a GIF for playing on screen, and frees it when it's no longer shown.
 * GifDrawable (from android-gif-drawable) decodes one frame at a time on its own background
 * thread, so it stays smooth and light even for big GIFs.
 */
@Composable
fun rememberGifDrawable(file: File?): GifDrawable? {
    // Reopen when the file changes (a re-pick replaces it).
    val key = file?.let { "${it.path}:${it.lastModified()}:${it.length()}" }
    val drawable = remember(key) { file?.let { runCatching { GifDrawable(it) }.getOrNull() } }
    DisposableEffect(drawable) {
        onDispose { drawable?.recycle() }
    }
    return drawable
}

/** Where the GIF goes inside a box of [boxWidth] x [boxHeight] pixels in Fill mode. */
data class FillPlacement(val width: Int, val height: Int, val x: Int, val y: Int)

/**
 * Fill mode: scale the GIF up until it covers the whole box, then slide it so the point
 * [center] (0..1 on each axis) is as close to the middle as possible without showing an edge.
 * This matches FrameExtractor.cropRect, so the preview shows exactly what the widget will show.
 */
fun fillPlacement(boxWidth: Int, boxHeight: Int, gifWidth: Int, gifHeight: Int, center: Offset): FillPlacement {
    val scale = max(boxWidth / gifWidth.toFloat(), boxHeight / gifHeight.toFloat())
    val width = (gifWidth * scale).roundToInt()
    val height = (gifHeight * scale).roundToInt()
    val x = (boxWidth / 2f - center.x * width).roundToInt().coerceIn(boxWidth - width, 0)
    val y = (boxHeight / 2f - center.y * height).roundToInt().coerceIn(boxHeight - height, 0)
    return FillPlacement(width, height, x, y)
}

/**
 * Shows a playing GIF the way the widget will: whole (Fit), or covering the box and
 * centered on [center] (Fill). [center] is a function so dragging only moves the GIF,
 * without rebuilding the screen on every finger movement.
 */
@Composable
fun GifView(
    drawable: GifDrawable,
    scaleMode: ScaleMode,
    center: () -> Offset,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            // GifDrawable is a classic Android Drawable, so it's shown in a classic ImageView.
            AndroidView(
                factory = { context -> ImageView(context) },
                update = { view ->
                    if (view.drawable !== drawable) view.setImageDrawable(drawable)
                    view.scaleType = when (scaleMode) {
                        ScaleMode.FIT -> ImageView.ScaleType.FIT_CENTER
                        ScaleMode.FILL -> ImageView.ScaleType.FIT_XY // we size and place it ourselves
                    }
                },
                onRelease = { view -> view.setImageDrawable(null) }
            )
        },
        modifier = modifier.clipToBounds()
    ) { measurables, constraints ->
        val boxWidth = constraints.maxWidth
        val boxHeight = constraints.maxHeight
        val gifWidth = drawable.intrinsicWidth.coerceAtLeast(1)
        val gifHeight = drawable.intrinsicHeight.coerceAtLeast(1)
        if (scaleMode == ScaleMode.FIT) {
            val placeable = measurables.first().measure(Constraints.fixed(boxWidth, boxHeight))
            layout(boxWidth, boxHeight) { placeable.place(0, 0) }
        } else {
            val spot = fillPlacement(boxWidth, boxHeight, gifWidth, gifHeight, center())
            val placeable = measurables.first().measure(Constraints.fixed(spot.width, spot.height))
            layout(boxWidth, boxHeight) { placeable.place(spot.x, spot.y) }
        }
    }
}
