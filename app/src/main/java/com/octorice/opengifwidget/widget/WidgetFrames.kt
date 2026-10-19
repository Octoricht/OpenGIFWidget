package com.octorice.opengifwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.data.friendlyErrors
import com.octorice.opengifwidget.gif.Crop
import com.octorice.opengifwidget.gif.FrameExtractor
import com.octorice.opengifwidget.gif.FrameLimits
import com.octorice.opengifwidget.gif.FrameSet
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
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): FrameSet = synchronized(lock) {
        val aspect = WidgetSizes.aspectRatio(context, appWidgetId)
        val target = WidgetSizes.maxSidePx(context, appWidgetId)
        val crop = if (scaleMode == ScaleMode.FILL) Crop(cropCenter.first, cropCenter.second, aspect) else null
        val rounded = cutsCorners(scaleMode, roundCorners)
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
                onProgress = onProgress
            )
        }
        // Remember the size and shape these frames were made for, to know later if they still fit.
        WidgetStore.saveFramesFit(context, appWidgetId, target ?: 0, aspect, rounded)
        frames
    }

    /**
     * Whether frames get rounded corners cut in: only on Android 8-11 (newer versions round widgets
     * themselves), only in Fill (a Fit picture doesn't reach the corners), and only if switched on.
     */
    fun cutsCorners(scaleMode: ScaleMode, roundCorners: Boolean): Boolean =
        WidgetSizes.needsRoundedFrames && scaleMode == ScaleMode.FILL && roundCorners

    /**
     * Remakes the frames if the widget's size or shape changed enough to matter:
     * - noticeably bigger (frames would look soft) or much smaller (memory can be saved),
     * - or a different shape in Fill mode (the crop is redone for the new shape).
     * Returns true if it remade them. Never throws: a failed remake keeps the old frames.
     */
    fun refreshIfNeeded(context: Context, appWidgetId: Int): Boolean {
        val gif = WidgetStore.getGif(context, appWidgetId) ?: return false
        val master = gif.master ?: return false
        val old = gif.frames ?: return false
        val newTarget = WidgetSizes.maxSidePx(context, appWidgetId) ?: return false
        val (oldTarget, oldAspect) = WidgetStore.getFramesFit(context, appWidgetId)
        val scaleMode = WidgetStore.getScaleMode(context, appWidgetId)
        val roundCorners = WidgetStore.getRoundCorners(context, appWidgetId)

        val sizeChanged = oldTarget == 0 || newTarget > oldTarget * 1.15 || newTarget < oldTarget * 0.7
        val newAspect = WidgetSizes.aspectRatio(context, appWidgetId)
        val shapeChanged = scaleMode == ScaleMode.FILL && oldAspect > 0f && abs(newAspect - oldAspect) / oldAspect > 0.1f
        // Frames made before the corners option existed (or after it changed) on Android 8-11.
        val cornersChanged = cutsCorners(scaleMode, roundCorners) != WidgetStore.getFramesRounded(context, appWidgetId)
        if (!sizeChanged && !shapeChanged && !cornersChanged) return false

        return try {
            val frames = make(
                context, appWidgetId, master, scaleMode, WidgetStore.getCropCenter(context, appWidgetId), roundCorners
            )
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
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))
            .count { refreshIfNeeded(context, it) }
}
