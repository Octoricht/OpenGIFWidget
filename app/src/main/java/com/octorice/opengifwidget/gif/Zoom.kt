package com.octorice.opengifwidget.gif

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Zoom math, shared by the editor (preview, Reposition) and the widget frames, so they always match.
 *
 * The "box" is what the widget shows: the shape's box, or the whole widget without a shape.
 * Zoom 1 = Fill (the picture just covers the box). [fitZoom] = Fit (the whole picture just fits).
 * Smaller = more empty space around the picture, bigger = closer in.
 */
object Zoom {

    /** How far you can zoom out: the picture shrinks to half its Fit size. */
    const val MIN_OF_FIT = 0.5f

    /** How far you can zoom in at most: twice Fill (less if the picture doesn't have the detail). */
    const val MAX_ZOOM = 2f

    /** You can always zoom in at least this far (a tiny GIF then gets a little soft, as you'd expect). */
    const val MIN_MAX_ZOOM = 1.5f

    /** Zoom values this close to Fit or Fill (4%) snap onto them. */
    private val SNAP = ln(1.04f)

    /** The zoom at which the whole picture just fits in a box of [boxAspect] (width / height). At most 1. */
    fun fitZoom(pictureWidth: Float, pictureHeight: Float, boxAspect: Float): Float {
        val pictureAspect = pictureWidth / pictureHeight
        return min(boxAspect / pictureAspect, pictureAspect / boxAspect)
    }

    /**
     * Which part of the picture the box shows, in the picture's own pixels. It can reach past the
     * picture's edges when zoomed out (that part is empty). [centerX], [centerY] (0..1) is the picture
     * point wanted in the middle of the box; the box then slides as close to it as it can:
     * - where the picture is bigger than the box, the box stays inside the picture (no empty edge),
     * - where the picture is smaller, the picture stays inside the box (it can move around in it).
     */
    fun box(pictureWidth: Float, pictureHeight: Float, boxAspect: Float, zoom: Float, centerX: Float, centerY: Float): RectF {
        // At zoom 1 (Fill): the biggest box of this shape that fits in the picture.
        val fillWidth: Float
        val fillHeight: Float
        if (pictureWidth / pictureHeight > boxAspect) {
            fillHeight = pictureHeight
            fillWidth = pictureHeight * boxAspect
        } else {
            fillWidth = pictureWidth
            fillHeight = pictureWidth / boxAspect
        }
        val width = fillWidth / zoom
        val height = fillHeight / zoom
        val left = place(centerX * pictureWidth - width / 2, pictureWidth, width)
        val top = place(centerY * pictureHeight - height / 2, pictureHeight, height)
        return RectF(left, top, left + width, top + height)
    }

    private fun place(start: Float, picture: Float, length: Float): Float =
        start.coerceIn(min(0f, picture - length), max(0f, picture - length))

    /** The picture point (0..1) in the middle of [box]: the center after sliding, to store. */
    fun centerOf(box: RectF, pictureWidth: Float, pictureHeight: Float): Pair<Float, Float> =
        box.centerX() / pictureWidth to box.centerY() / pictureHeight

    /**
     * The most you can zoom in: 2x Fill, but only while the master frames still have at least one
     * pixel for every widget-frame pixel ([outputSidePx] = the frame's real longest side).
     * Always at least [MIN_MAX_ZOOM], so zooming in is never blocked.
     */
    fun maxZoom(pictureWidth: Float, pictureHeight: Float, boxAspect: Float, outputSidePx: Int): Float {
        val fill = box(pictureWidth, pictureHeight, boxAspect, 1f, 0.5f, 0.5f)
        val detail = max(fill.width(), fill.height()) / outputSidePx.coerceAtLeast(1)
        return detail.coerceIn(MIN_MAX_ZOOM, MAX_ZOOM)
    }

    /** [zoom], or Fit / Fill if it's very close to one of them. */
    fun snap(zoom: Float, fit: Float): Float = when {
        abs(ln(zoom / 1f)) < SNAP -> 1f
        abs(ln(zoom / fit)) < SNAP -> fit
        else -> zoom
    }
}
