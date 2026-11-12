package com.octorice.opengifwidget.shape

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import androidx.annotation.StringRes
import androidx.core.graphics.createBitmap
import com.octorice.opengifwidget.R
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Edge sizes (Pixel edges and Halftone edges): how many cells across the long side of the shape's box. */
enum class PixelSize(val cells: Int, @StringRes val nameRes: Int) {
    SMALL(32, R.string.pixel_small),
    MEDIUM(20, R.string.pixel_medium),
    CHONKY(12, R.string.pixel_chonky)
}

/**
 * The shape tools: Pixel edges ([pixel] = its size, null = off), Halftone edges ([halftone], same
 * sizes; one edge style at a time) and Reverse (the shape becomes a hole).
 * They only change the cutter, never the picture.
 */
data class ShapeStyle(val pixel: PixelSize? = null, val reverse: Boolean = false, val halftone: PixelSize? = null) {
    val isPlain: Boolean get() = pixel == null && halftone == null && !reverse

    companion object {
        val PLAIN = ShapeStyle()
    }
}

/**
 * A shape with the tools applied: [inner] (already sized for the widget) pixelated and/or reversed.
 * It's a shape itself, so frames, background layer, previews, zoom, resize re-cuts and the RAM
 * count all work unchanged. Order: Pixelify first, then Reverse.
 * - Pixel edges: square cells on a grid aligned to the shape's box, each taking the mask's value at
 *   its center (crisp steps for solid shapes, stepped grays for partly see-through ones), drawn
 *   with no smoothing.
 * - Halftone edges: round dots on a slanted grid, sized by a softened (blurred) mask: merged into a
 *   solid inside the shape, shrinking to specks as they fade out past the edge; grays become dots.
 * - Reverse: the mask flipped (70% visible becomes 30%). The box is then the whole widget
 *   ([widgetAspect]); the hole sits where the shape's box would be (centered, same size).
 * Ids look like "heart#p20#r1.50" or "heart#h20", so the frames remember which tools they were cut with.
 */
class StyledShape private constructor(
    val inner: WidgetShape,
    val style: ShapeStyle,
    aspect: Float,
    id: String
) : WidgetShape(id, inner.nameRes, aspect, drawUnit = { addRect(0f, 0f, 1f, 1f, Path.Direction.CW) }) {

    override val hasOutline: Boolean get() = false
    override val base: WidgetShape get() = inner.base

    // A reversed picture can't spin (its corners would show while turning); a pixel CD still can.
    override val spins: Boolean get() = inner.spins && !style.reverse

    // The CD's light is drawn for the smooth disc: off with any tool on.
    override fun renderShine(width: Int, height: Int): Bitmap? = null

    override fun renderMask(width: Int, height: Int): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        if (!style.reverse) return pixelated(w, h)
        // Reverse: everything shows, minus the shape (in its own box, centered).
        val holeWidth: Int
        val holeHeight: Int
        if (w / h.toFloat() > inner.aspect) {
            holeHeight = h
            holeWidth = max(1, (h * inner.aspect).roundToInt()).coerceAtMost(w)
        } else {
            holeWidth = w
            holeHeight = max(1, (w / inner.aspect).roundToInt()).coerceAtMost(h)
        }
        val hole = pixelated(holeWidth, holeHeight)
        val mask = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mask.eraseColor(Color.WHITE)
        // DST_OUT keeps (1 - hole) of what's there: 70% visible in the shape becomes 30%.
        val left = (w - holeWidth) / 2
        val top = (h - holeHeight) / 2
        Canvas(mask).drawBitmap(hole, left.toFloat(), top.toFloat(), Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        hole.recycle()
        return mask
    }

    /** The inner shape's mask at [w] x [h], with its edge style (pixel or halftone) if one is on. */
    private fun pixelated(w: Int, h: Int): Bitmap {
        style.halftone?.let { return halftone(w, h, it.cells) }
        val smooth = inner.renderMask(w, h)
        val size = style.pixel ?: return smooth
        val cell = max(w, h) / size.cells.toFloat()
        val columns = ceil(w / cell).toInt()
        val rows = ceil(h / cell).toInt()
        val mask = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val paint = Paint() // no anti-aliasing: crisp, square cells
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                // The mask's value at the cell's center decides the whole cell.
                val x = ((column + 0.5f) * cell).toInt().coerceIn(0, w - 1)
                val y = ((row + 0.5f) * cell).toInt().coerceIn(0, h - 1)
                val alpha = Color.alpha(smooth.getPixel(x, y))
                if (alpha == 0) continue
                paint.color = Color.argb(alpha, 255, 255, 255)
                val cellRect = Rect(
                    (column * cell).roundToInt(), (row * cell).roundToInt(),
                    min(w, ((column + 1) * cell).roundToInt()), min(h, ((row + 1) * cell).roundToInt())
                )
                canvas.drawRect(cellRect, paint)
            }
        }
        smooth.recycle()
        return mask
    }

    /**
     * Halftone: the mask is softened (blurred over about a cell, so the edge fades out over a few
     * dots), then a dot is drawn on a slanted grid wherever there's something: its area follows the
     * softened value (full = dots big enough to merge into a solid, faint = specks).
     */
    private fun halftone(w: Int, h: Int, cells: Int): Bitmap {
        val cell = max(w, h) / cells.toFloat()
        // The dots fade out past the shape's edge (about 2 cells), so the shape is drawn that much
        // smaller (centered, same proportions): the fade then fits inside the box instead of being cut off.
        val inset = 2 * cell
        val shrink = max(0.2f, min((w - 2 * inset) / w, (h - 2 * inset) / h))
        val innerWidth = max(1, (w * shrink).roundToInt())
        val innerHeight = max(1, (h * shrink).roundToInt())
        val smooth = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        inner.renderMask(innerWidth, innerHeight).let { shapeMask ->
            Canvas(smooth).drawBitmap(shapeMask, (w - innerWidth) / 2f, (h - innerHeight) / 2f, null)
            shapeMask.recycle()
        }
        val step = cell / 2 // the soft map has one value per half cell
        val gw = ceil(w / step).toInt() + 1
        val gh = ceil(h / step).toInt() + 1
        // The mask shrunk to the soft map's size (smoothed while shrinking), then blurred.
        val small = android.graphics.Bitmap.createScaledBitmap(smooth, gw, gh, true)
        val soft = FloatArray(gw * gh) { i -> Color.alpha(small.getPixel(i % gw, i / gw)) / 255f }
        if (small != smooth) small.recycle()
        smooth.recycle()
        repeat(2) { boxBlur(soft, gw, gh, radius = 2) } // ~1 cell, twice: a soft, round fade
        fun valueAt(x: Float, y: Float): Float {
            val gx = (x / step).coerceIn(0f, gw - 1f)
            val gy = (y / step).coerceIn(0f, gh - 1f)
            val x0 = gx.toInt().coerceAtMost(gw - 2).coerceAtLeast(0)
            val y0 = gy.toInt().coerceAtMost(gh - 2).coerceAtLeast(0)
            val fx = gx - x0
            val fy = gy - y0
            val a = soft[y0 * gw + x0] * (1 - fx) + soft[y0 * gw + x0 + 1] * fx
            val b = soft[(y0 + 1) * gw + x0] * (1 - fx) + soft[(y0 + 1) * gw + x0 + 1] * fx
            return a * (1 - fy) + b * fy
        }
        val mask = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mask)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        // A slanted grid: rows half a cell apart, every other row shifted by half a cell.
        // At full strength the dots (0.56 cell) are just big enough to cover everything.
        val rows = ceil(h / step).toInt() + 1
        val columns = ceil(w / cell).toInt() + 1
        for (row in 0..rows) {
            val y = row * step
            val shift = if (row % 2 == 1) cell / 2 else 0f
            for (column in 0..columns) {
                val x = column * cell + shift
                val value = valueAt(x, y)
                val radius = 0.56f * cell * kotlin.math.sqrt(value)
                if (radius >= 0.06f * cell) canvas.drawCircle(x, y, radius, paint)
            }
        }
        return mask
    }

    companion object {
        /** Blurs [values] ([w] x [h]) in place: the average over [radius] on each side, across then down. */
        private fun boxBlur(values: FloatArray, w: Int, h: Int, radius: Int) {
            val line = FloatArray(max(w, h))
            for (y in 0 until h) {
                for (x in 0 until w) {
                    var sum = 0f
                    var count = 0
                    for (k in -radius..radius) {
                        val xx = x + k
                        if (xx in 0 until w) { sum += values[y * w + xx]; count++ }
                    }
                    line[x] = sum / count
                }
                for (x in 0 until w) values[y * w + x] = line[x]
            }
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var sum = 0f
                    var count = 0
                    for (k in -radius..radius) {
                        val yy = y + k
                        if (yy in 0 until h) { sum += values[yy * w + x]; count++ }
                    }
                    line[y] = sum / count
                }
                for (y in 0 until h) values[y * w + x] = line[y]
            }
        }

        // One shared copy per id, so previews can reuse their masks.
        private val made = HashMap<String, StyledShape>()

        /**
         * [shape] (already sized for the widget) with [style] applied, or [shape] itself when no
         * tool is on. [widgetAspect]: the widget's width / height (Reverse covers the whole widget).
         */
        fun of(shape: WidgetShape, style: ShapeStyle, widgetAspect: Float): WidgetShape {
            if (style.isPlain) return shape
            val aspect = if (style.reverse) (widgetAspect * 20).roundToInt().coerceIn(6, 80) / 20f else shape.aspect
            val id = buildString {
                append(shape.id)
                style.pixel?.let { append("#p").append(it.cells) }
                style.halftone?.let { append("#h").append(it.cells) }
                if (style.reverse) append(String.format(Locale.ROOT, "#r%.2f", aspect))
            }
            return synchronized(made) { made.getOrPut(id) { StyledShape(shape, style, aspect, id) } }
        }

        /** Reads a styled id back ("heart#p20#r1.50"): [inner] is the shape before "#". */
        fun fromId(inner: WidgetShape, id: String): WidgetShape {
            val parts = id.split('#').drop(1)
            fun size(prefix: String) = parts.firstOrNull { it.startsWith(prefix) }?.drop(1)?.toIntOrNull()
                ?.let { cells -> PixelSize.entries.firstOrNull { it.cells == cells } }
            val reverseAspect = parts.firstOrNull { it.startsWith("r") }?.drop(1)?.toFloatOrNull()
            return of(inner, ShapeStyle(size("p"), reverseAspect != null, size("h")), reverseAspect ?: 1f)
        }
    }
}
