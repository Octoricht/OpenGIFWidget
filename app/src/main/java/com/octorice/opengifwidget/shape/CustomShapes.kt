package com.octorice.opengifwidget.shape

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ImportException
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.data.friendlyErrors
import com.octorice.opengifwidget.gif.StillImageDecoder
import com.octorice.opengifwidget.widget.GifWidgetProvider
import com.octorice.opengifwidget.widget.WidgetFrames
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A shape made from a picture: its mask is a stored PNG (white where the picture shows,
 * see-through where it's cut, partly see-through in between), stretched to the box when used.
 * It has no outline, so Reposition dims "outside" using the mask itself.
 */
class CustomShape internal constructor(id: String, val file: File, aspect: Float) :
    WidgetShape(id, R.string.shape_custom, aspect, drawUnit = { addRect(0f, 0f, 1f, 1f, Path.Direction.CW) }) {

    override val hasOutline: Boolean get() = false

    override fun renderMask(width: Int, height: Int): Bitmap {
        val mask = createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val source = BitmapFactory.decodeFile(file.path)
        if (source == null) {
            // The picture was deleted meanwhile: cut nothing (the widget is re-cut without a shape later).
            mask.eraseColor(Color.WHITE)
            return mask
        }
        Canvas(mask).drawBitmap(source, null, Rect(0, 0, mask.width, mask.height), Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return mask
    }
}

/**
 * The shape library: custom shapes everyone's widgets can use, kept in files/shapes/<number>.png
 * (lossless). A widget refers to one as "custom:<number>" (no copy per widget), so a widget pack
 * can later bring its PNG files along. A missing file means the shape is gone: it counts as no shape.
 */
object CustomShapes {

    const val PREFIX = "custom:"

    /** Masks are about this big on their long side. */
    private const val MASK_SIDE_PX = 512

    /** Pictures are read at most this big, so a small shape in a big empty picture keeps its detail. */
    private const val READ_SIDE_PX = 1600

    /** Darkness / opacity (0-255) up to this counts as "fully cut" when trimming empty borders. */
    private const val EMPTY = 8

    // One object per shape, so screens can reuse its previews.
    private val loaded = HashMap<String, CustomShape>()

    private fun dir(context: Context) = File(context.filesDir, "shapes")

    /** The shape "custom:<number>", or null if it doesn't exist (anymore). Only plain numbers, so no other file is reachable. */
    fun get(context: Context, id: String): CustomShape? = synchronized(loaded) {
        val number = id.removePrefix(PREFIX)
        if (number.isEmpty() || !number.all { it.isDigit() }) return null
        val file = File(dir(context), "$number.png")
        if (!file.isFile) {
            loaded.remove(id)
            return null
        }
        loaded.getOrPut(id) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val aspect = if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth / bounds.outHeight.toFloat() else 1f
            CustomShape(id, file, aspect)
        }
    }

    /** Every saved shape, newest first. Reads files: call it off the main thread. */
    fun all(context: Context): List<CustomShape> =
        dir(context).listFiles { file -> file.name.matches(Regex("\\d+\\.png")) }
            .orEmpty()
            .sortedByDescending { it.name.removeSuffix(".png").toLongOrNull() ?: 0L }
            .mapNotNull { get(context, PREFIX + it.name.removeSuffix(".png")) }

    /**
     * Turns a picked picture into a mask (not saved yet: show it first, then [save] it).
     * Same 50 MB limit and friendly errors as the editor; EXIF rotation is applied. The copy of
     * the picture is deleted right after. Slow: call it off the main thread.
     */
    fun makeMask(context: Context, uri: Uri): Bitmap {
        val copy = File(context.cacheDir, "shape-import")
        try {
            return friendlyErrors {
                GifStorage.importFile(context, uri, copy)
                val picture = StillImageDecoder.decode(copy, READ_SIDE_PX) ?: throw ImportException(R.string.error_unsupported)
                try {
                    maskFrom(picture)
                } finally {
                    picture.recycle()
                }
            }
        } finally {
            copy.delete() // the original is never kept
        }
    }

    /**
     * The mask of a picture: its see-through parts if it has any, otherwise its darkness (black =
     * the picture shows, white = cut, gray = partly). Empty (fully cut) borders are trimmed so the
     * shape fills the widget, and it's shrunk to about 512 px, keeping its proportions.
     */
    private fun maskFrom(picture: Bitmap): Bitmap {
        val width = picture.width
        val height = picture.height
        val pixels = IntArray(width * height)
        picture.getPixels(pixels, 0, width, 0, 0, width, height)
        // Any pixel that isn't fully solid means the picture has transparency: use it.
        val useAlpha = pixels.any { (it ushr 24) < 255 }
        for (i in pixels.indices) {
            val color = pixels[i]
            pixels[i] = if (useAlpha) {
                color ushr 24
            } else {
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                // Darkness: black is the shape, white is cut away (like shape stencils on Pinterest).
                255 - (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
            }
        }
        // Trim the empty borders.
        var left = width; var top = height; var right = -1; var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (pixels[y * width + x] > EMPTY) {
                    left = min(left, x); right = max(right, x)
                    top = min(top, y); bottom = max(bottom, y)
                }
            }
        }
        if (right < 0) throw ImportException(R.string.error_shape_empty)
        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        val trimmed = IntArray(cropWidth * cropHeight) { i ->
            val alpha = pixels[(top + i / cropWidth) * width + left + i % cropWidth]
            (alpha shl 24) or 0xFFFFFF // white, with the mask's opacity
        }
        val mask = createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        mask.setPixels(trimmed, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        val scale = min(1f, MASK_SIDE_PX / max(cropWidth, cropHeight).toFloat())
        if (scale >= 1f) return mask
        val small = mask.scale(max(1, (cropWidth * scale).roundToInt()), max(1, (cropHeight * scale).roundToInt()))
        mask.recycle()
        return small
    }

    /** Saves a mask from [makeMask] to the library (lossless PNG) and returns the new shape. */
    fun save(context: Context, mask: Bitmap): CustomShape = friendlyErrors {
        val folder = dir(context).apply { mkdirs() }
        var number = System.currentTimeMillis()
        while (File(folder, "$number.png").exists()) number++
        val file = File(folder, "$number.png")
        val ok = file.outputStream().use { mask.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!ok) {
            file.delete()
            throw ImportException(R.string.error_generic)
        }
        get(context, PREFIX + number) ?: throw ImportException(R.string.error_generic)
    }

    /** The widgets (on the home screen) that use this shape. */
    fun usedBy(context: Context, shape: CustomShape): List<Int> =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))
            .filter { WidgetStore.getShapeId(context, it) == shape.id }

    /**
     * Deletes the shape. Every widget that used it goes back to no shape: re-cut from its master
     * frames (never blocked by the RAM limit: no shape is never heavier), its old cut frames
     * deleted, and redrawn. Slow: call it off the main thread.
     */
    fun delete(context: Context, shape: CustomShape) {
        val users = usedBy(context, shape)
        synchronized(loaded) {
            shape.file.delete()
            loaded.remove(shape.id)
        }
        for (appWidgetId in users) {
            WidgetStore.saveShape(context, appWidgetId, null)
            WidgetFrames.refreshIfNeeded(context, appWidgetId)
            GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
        }
    }
}
