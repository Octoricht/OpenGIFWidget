package com.octorice.opengifwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.Slide
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.data.friendlyErrors
import com.octorice.opengifwidget.gif.Crop
import com.octorice.opengifwidget.gif.FrameExtractor
import com.octorice.opengifwidget.gif.FrameLimits
import com.octorice.opengifwidget.gif.FrameSet
import com.octorice.opengifwidget.gif.SlidePicture
import com.octorice.opengifwidget.gif.Spin
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.shape.WidgetShape
import com.octorice.opengifwidget.shape.ShapeStyle
import kotlin.math.abs

/**
 * Makes a widget's frames from its master frames, sized and shaped for the widget as it is on
 * the home screen right now. Used when saving in the editor, and again after the widget is
 * resized (so frames are sharp when it grows, and use less memory when it shrinks).
 */
object WidgetFrames {

    // One remake at a time, so a resize and a Save never write the same widget at once.
    private val lock = Any()

    /** Makes new widget frames (a new folder). Throws ImportException on failure. Slow: background only. */
    fun make(
        context: Context,
        appWidgetId: Int,
        master: FrameSet,
        scaleMode: ScaleMode,
        cropCenter: Pair<Float, Float>,
        roundCorners: Boolean,
        pickedShape: WidgetShape?,
        zoom: Float?, // custom zoom (1 = Fill), or null for the Fit / Fill preset in [scaleMode]
        spinSeconds: Float?, // "Make the CD spin": seconds per turn, or null (only used if the shape spins)
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        style: ShapeStyle = ShapeStyle.PLAIN // Pixelify / Reverse (only with a shape)
    ): FrameSet = synchronized(lock) {
        // The shape as drawn at this widget's size (a pattern like Dots gets more dots when bigger).
        val shape = WidgetSizes.shapeFor(context, appWidgetId, pickedShape, style)
        val aspect = WidgetSizes.aspectRatio(context, appWidgetId)
        val spinUsed = spinFor(shape, spinSeconds)
        // With a shape, frames have the shape's box shape (e.g. square), not the widget's.
        val target = if (shape != null) {
            WidgetSizes.shapeMaxSidePx(context, appWidgetId, shape.aspect)
        } else {
            WidgetSizes.maxSidePx(context, appWidgetId)
        }
        val cropAspect = shape?.aspect ?: aspect
        // (A custom zoom uses the crop for the box's shape and center too.)
        val crop = if (scaleMode == ScaleMode.FILL || zoom != null) Crop(cropCenter.first, cropCenter.second, cropAspect) else null
        // A shape replaces the rounded corners (it doesn't reach the corners anyway).
        val rounded = shape == null && cutsCorners(scaleMode, roundCorners, zoom)
        val cornerFraction = if (rounded) WidgetSizes.cornerRadiusFraction(context, appWidgetId) else 0f
        // Each set of frames gets a new folder, so old and new frames never mix.
        val version = System.currentTimeMillis()
        val outDir = GifStorage.framesDir(context, appWidgetId, version)
        val frames = friendlyErrors(cleanUp = { outDir.deleteRecursively() }) {
            FrameExtractor.deriveWidgetFrames(
                master, GifStorage.framesDir(context, appWidgetId, master.version), outDir, version, crop,
                limits = FrameLimits.forDevice(context),
                widgetMaxSidePx = target,
                cornerRadiusFraction = cornerFraction,
                shape = shape,
                zoom = zoom,
                spin = spinUsed?.let { Spin(it, AppSettings.getFpsCap(context)) },
                onProgress = onProgress
            )
        }
        // Remember the size and shape these frames were made for, to know later if they still fit.
        WidgetStore.saveFramesFit(context, appWidgetId, target ?: 0, aspect, rounded, shape?.id, zoom, spinUsed)
        frames
    }

    /** How long each slideshow picture stays up, until "Time per picture" exists (next step). */
    const val SLIDE_INTERVAL_MS = 5000

    /**
     * Makes a slideshow's widget frames (a new folder): one frame per picture in [slides], in order,
     * each Fit or Fill-ed into the widget's box (or the shape's box). Throws ImportException on failure.
     * Each picture may have its own Reposition (Slide.zoom). No spin in slideshows, and no widget-wide
     * custom zoom, so those are always saved as "none".
     */
    fun makeSlideshow(
        context: Context,
        appWidgetId: Int,
        slides: List<Slide>,
        scaleMode: ScaleMode,
        roundCorners: Boolean,
        pickedShape: WidgetShape?,
        style: ShapeStyle = ShapeStyle.PLAIN,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): FrameSet = synchronized(lock) {
        val shape = WidgetSizes.shapeFor(context, appWidgetId, pickedShape, style)
        val aspect = WidgetSizes.aspectRatio(context, appWidgetId)
        val target = if (shape != null) {
            WidgetSizes.shapeMaxSidePx(context, appWidgetId, shape.aspect)
        } else {
            WidgetSizes.maxSidePx(context, appWidgetId)
        }
        val rounded = shape == null && cutsCorners(scaleMode, roundCorners, null)
        val cornerFraction = if (rounded) WidgetSizes.cornerRadiusFraction(context, appWidgetId) else 0f
        val version = GifStorage.newVersion()
        val outDir = GifStorage.framesDir(context, appWidgetId, version)
        val frames = friendlyErrors(cleanUp = { outDir.deleteRecursively() }) {
            FrameExtractor.deriveSlideshowFrames(
                pictures = slides.map {
                    SlidePicture(it.master, GifStorage.framesDir(context, appWidgetId, it.id), it.zoom, it.centerX, it.centerY)
                },
                outDir = outDir,
                version = version,
                boxAspect = shape?.aspect ?: aspect,
                fill = scaleMode == ScaleMode.FILL,
                intervalMs = SLIDE_INTERVAL_MS,
                limits = FrameLimits.forDevice(context),
                widgetMaxSidePx = target,
                cornerRadiusFraction = cornerFraction,
                shape = shape,
                onProgress = onProgress
            )
        }
        WidgetStore.saveFramesFit(context, appWidgetId, target ?: 0, aspect, rounded, shape?.id, null, null)
        frames
    }

    /** The spin that really applies: only with a shape that can spin (the CD), else none. */
    fun spinFor(shape: WidgetShape?, spinSeconds: Float?): Float? = if (shape?.spins == true) spinSeconds else null

    /**
     * Whether frames get rounded corners cut in: only on Android 8-11 (newer versions round widgets
     * themselves), only in Fill or a custom zoom (a Fit picture doesn't reach the corners), and only if switched on.
     */
    fun cutsCorners(scaleMode: ScaleMode, roundCorners: Boolean, zoom: Float?): Boolean =
        WidgetSizes.needsRoundedFrames && (scaleMode == ScaleMode.FILL || zoom != null) && roundCorners

    /**
     * Remakes the frames if the widget's size or shape changed enough to matter:
     * - noticeably bigger (frames would look soft) or much smaller (memory can be saved),
     * - or a different shape in Fill mode (the crop is redone for the new shape),
     * - or the cutter shape (heart, circle, ...) isn't the one the frames were cut with.
     * Returns true if it remade them. Never throws: a failed remake keeps the old frames.
     */
    fun refreshIfNeeded(context: Context, appWidgetId: Int): Boolean {
        val gif = WidgetStore.getGif(context, appWidgetId) ?: return false
        val slides = WidgetStore.getSlides(context, appWidgetId)
        val slideshow = slides.isNotEmpty()
        val master = gif.master
        if (master == null && !slideshow) return false
        val old = gif.frames ?: return false
        // At the widget's current size: a resized Dots widget gets a new grid, so its id changes
        // ("dots@3x3" -> "dots@6x3") and the frames are re-cut below.
        val style = WidgetStore.getShapeStyle(context, appWidgetId)
        val cutter = WidgetSizes.shapeFor(context, appWidgetId, WidgetStore.getShape(context, appWidgetId), style)
        val newTarget = (if (cutter != null) {
            WidgetSizes.shapeMaxSidePx(context, appWidgetId, cutter.aspect)
        } else {
            WidgetSizes.maxSidePx(context, appWidgetId)
        }) ?: return false
        val (oldTarget, oldAspect) = WidgetStore.getFramesFit(context, appWidgetId)
        val scaleMode = WidgetStore.getScaleMode(context, appWidgetId)
        val roundCorners = WidgetStore.getRoundCorners(context, appWidgetId)
        val cutterChanged = cutter?.id != WidgetStore.getFramesShapeId(context, appWidgetId)
        val zoom = WidgetStore.getZoom(context, appWidgetId)
        val zoomChanged = zoom != WidgetStore.getFramesZoom(context, appWidgetId)
        val spinSeconds = WidgetStore.getSpin(context, appWidgetId)
        val spinChanged = spinFor(cutter, spinSeconds) != WidgetStore.getFramesSpin(context, appWidgetId)

        val sizeChanged = oldTarget == 0 || newTarget > oldTarget * 1.15 || newTarget < oldTarget * 0.7
        val newAspect = WidgetSizes.aspectRatio(context, appWidgetId)
        // (With a cutter shape the crop keeps the cutter's shape, whatever the widget's shape.)
        // (Slideshow frames always have the widget's shape, even in Fit: every picture shares one box.)
        val shapeChanged = cutter == null && (scaleMode == ScaleMode.FILL || zoom != null || slideshow) &&
            oldAspect > 0f && abs(newAspect - oldAspect) / oldAspect > 0.1f
        // Frames made before the corners option existed (or after it changed) on Android 8-11.
        val cornersChanged = (cutter == null && cutsCorners(scaleMode, roundCorners, zoom)) != WidgetStore.getFramesRounded(context, appWidgetId)
        if (!sizeChanged && !shapeChanged && !cornersChanged && !cutterChanged && !zoomChanged && !spinChanged) return false

        return try {
            val frames = if (slideshow) {
                makeSlideshow(context, appWidgetId, slides, scaleMode, roundCorners, cutter, style)
            } else {
                make(
                    context, appWidgetId, master!!, scaleMode, WidgetStore.getCropCenter(context, appWidgetId), roundCorners, cutter, zoom, spinSeconds, style = style
                )
            }
            WidgetStore.saveGif(context, appWidgetId, gif.copy(frames = frames))
            GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            // Only the old widget frames go (an unsaved pick in an open editor stays untouched).
            if (old.version != frames.version) GifStorage.framesDir(context, appWidgetId, old.version).deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks every widget (e.g. when the app opens, to optimize widgets made by older versions).
     * Returns how many were remade.
     */
    fun refreshAll(context: Context): Int =
        GifWidgetProvider.allWidgetIds(context)
            .count { refreshIfNeeded(context, it) }
}
