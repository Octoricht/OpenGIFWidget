package com.octorice.opengifwidget.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.octorice.opengifwidget.gif.FrameSet
import com.octorice.opengifwidget.shape.Shapes
import com.octorice.opengifwidget.shape.WidgetShape

/** What we remember about the picture chosen for one widget. */
data class StoredGif(
    val name: String,
    val sizeBytes: Long,          // size of the original file
    val frames: FrameSet? = null, // the frames the widget shows (null until saved)
    val master: FrameSet? = null  // the uncropped master frames everything is made from
)

/**
 * One picture of a slideshow: its own master frame (one still, uncropped) and where it came from.
 * [master]'s version (its folder name) is unique, so it also identifies the picture.
 * [zoom] = this picture's own Reposition (1 = Fill, see Zoom) with its center ([centerX], [centerY],
 * 0..1), or null to follow the slideshow's Fit / Fill, centered.
 */
data class Slide(
    val master: FrameSet,
    val name: String,
    val sizeBytes: Long,
    val zoom: Float? = null,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f
) {
    val id: Long get() = master.version

    /** Repositioned by hand (Fit / Fill for all resets it). */
    val custom: Boolean get() = zoom != null

    /** Back to following the slideshow's Fit / Fill. */
    fun reset(): Slide = copy(zoom = null, centerX = 0.5f, centerY = 0.5f)
}

/**
 * How a slideshow changes from one picture to the next. Each is a flipper animation in the widget's
 * layout (res/anim), played by the launcher itself: no extra frames, no extra RAM.
 */
enum class Transition {
    NONE,  // the next picture just appears
    FADE,  // the next picture fades in over the old one, then the old one fades away (never darker)
    SLIDE  // the next picture slides in from the right, pushing the old one out
}

/**
 * "Time per picture" choices, in seconds. Up to a minute the launcher flips the pictures itself
 * (fast mode). From 5 minutes on ([SLOW_FROM]) the widget shows one picture and a timer redraws it
 * with the next one (slow mode, see SlowSlideshow): the launcher's flipper restarts its countdown
 * every time the home screen comes back, so it would never get through an hour.
 */
object SlideTiming {
    val CHOICES = listOf(
        1, 2, 3, 5, 10, 15, 30, 60,                         // fast
        5 * 60, 15 * 60, 30 * 60,                           // slow: minutes
        3600, 3 * 3600, 6 * 3600, 12 * 3600, 24 * 3600      // slow: hours, 1 day
    )
    const val DEFAULT = 5
    const val SLOW_FROM = 5 * 60

    fun isSlow(seconds: Int): Boolean = seconds >= SLOW_FROM

    /** The choice closest to [seconds] (older or odd values never break anything). */
    fun nearest(seconds: Int): Int = CHOICES.minBy { kotlin.math.abs(it - seconds) }
}

/** How a picture is shown when its shape doesn't match the widget's shape. */
enum class ScaleMode {
    FIT,  // show the whole picture, with empty space around it
    FILL  // cover the whole widget, cutting off the edges
}

/**
 * The color behind the picture (visible around Fit pictures and through transparent ones).
 * [color] is an opaque RGB color, [alpha] its opacity from 0 (invisible) to 255 (solid).
 */
data class WidgetBackground(val color: Int, val alpha: Int) {
    companion object {
        val DEFAULT = WidgetBackground(color = 0xFF202020.toInt(), alpha = 0xCC)
    }
}

/**
 * Remembers which picture belongs to which widget, using SharedPreferences
 * (a small key-value file in the app's private storage).
 * Every widget has its own id, so keys look like "gif_name_42".
 */
object WidgetStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun getGif(context: Context, appWidgetId: Int): StoredGif? {
        val prefs = prefs(context)
        val name = prefs.getString("gif_name_$appWidgetId", null) ?: return null
        return StoredGif(
            name = name,
            sizeBytes = prefs.getLong("gif_size_$appWidgetId", 0L),
            frames = prefs.readFrameSet("frames", appWidgetId),
            master = prefs.readFrameSet("master", appWidgetId)
        )
    }

    fun saveGif(context: Context, appWidgetId: Int, gif: StoredGif) {
        prefs(context).edit {
            putString("gif_name_$appWidgetId", gif.name)
            putLong("gif_size_$appWidgetId", gif.sizeBytes)
            writeFrameSet("frames", appWidgetId, gif.frames)
            writeFrameSet("master", appWidgetId, gif.master)
        }
    }

    /** Every frame folder this widget uses, so all others can be deleted. */
    fun usedFrameVersions(context: Context, appWidgetId: Int): Set<Long> {
        val gif = getGif(context, appWidgetId) ?: return emptySet()
        return setOfNotNull(gif.frames?.version, gif.master?.version) + getSlides(context, appWidgetId).map { it.id }
    }

    /**
     * A slideshow's pictures, in the order they play (empty for normal widgets). Each picture has its
     * own master folder; they're stored as a small JSON list in "slides_<id>".
     */
    fun getSlides(context: Context, appWidgetId: Int): List<Slide> {
        val json = prefs(context).getString("slides_$appWidgetId", null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(json)
            List(array.length()) { i ->
                val item = array.getJSONObject(i)
                Slide(
                    master = FrameSet(
                        version = item.getLong("v"),
                        frameCount = 1,
                        sourceFrameCount = 1,
                        intervalMs = 1000,
                        width = item.getInt("w"),
                        height = item.getInt("h")
                    ),
                    name = item.optString("name"),
                    sizeBytes = item.optLong("size"),
                    zoom = if (item.has("z")) item.getDouble("z").toFloat() else null,
                    centerX = item.optDouble("cx", 0.5).toFloat(),
                    centerY = item.optDouble("cy", 0.5).toFloat()
                )
            }
        } catch (e: org.json.JSONException) {
            emptyList() // never crash on a damaged list
        }
    }

    fun saveSlides(context: Context, appWidgetId: Int, slides: List<Slide>) {
        val array = org.json.JSONArray()
        for (slide in slides) {
            val item = org.json.JSONObject()
                .put("v", slide.master.version)
                .put("w", slide.master.width)
                .put("h", slide.master.height)
                .put("name", slide.name)
                .put("size", slide.sizeBytes)
            // Only a repositioned picture stores its zoom and center.
            slide.zoom?.let { item.put("z", it.toDouble()).put("cx", slide.centerX.toDouble()).put("cy", slide.centerY.toDouble()) }
            array.put(item)
        }
        prefs(context).edit { putString("slides_$appWidgetId", array.toString()) }
    }

    /** Slideshow: how many seconds each picture stays up (one of [SlideTiming.CHOICES]). 5 s by default. */
    fun getSlideSeconds(context: Context, appWidgetId: Int): Int =
        SlideTiming.nearest(prefs(context).getInt("slide_seconds_$appWidgetId", SlideTiming.DEFAULT))

    fun saveSlideSeconds(context: Context, appWidgetId: Int, seconds: Int) {
        prefs(context).edit { putInt("slide_seconds_$appWidgetId", SlideTiming.nearest(seconds)) }
    }

    /**
     * Slow slideshows: where they are. [position] = place in [order] (the frame numbers in play
     * order; empty = 0, 1, 2, ... without Shuffle), [changedAt] = when the current picture appeared
     * (wall-clock ms, 0 = never).
     */
    data class SlidePosition(val position: Int, val order: List<Int>, val changedAt: Long)

    fun getSlidePosition(context: Context, appWidgetId: Int): SlidePosition {
        val prefs = prefs(context)
        val order = prefs.getString("slide_order_$appWidgetId", null)
            ?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        return SlidePosition(
            prefs.getInt("slide_pos_$appWidgetId", 0),
            order,
            prefs.getLong("slide_changed_$appWidgetId", 0L)
        )
    }

    fun saveSlidePosition(context: Context, appWidgetId: Int, position: SlidePosition) {
        prefs(context).edit {
            putInt("slide_pos_$appWidgetId", position.position)
            if (position.order.isEmpty()) remove("slide_order_$appWidgetId")
            else putString("slide_order_$appWidgetId", position.order.joinToString(","))
            putLong("slide_changed_$appWidgetId", position.changedAt)
        }
    }

    /** Slideshow: how one picture changes into the next. Fade by default. */
    fun getTransition(context: Context, appWidgetId: Int): Transition {
        val name = prefs(context).getString("transition_$appWidgetId", null)
        return Transition.entries.firstOrNull { it.name == name } ?: Transition.FADE
    }

    fun saveTransition(context: Context, appWidgetId: Int, transition: Transition) {
        prefs(context).edit { putString("transition_$appWidgetId", transition.name) }
    }

    /** Slideshow: play the pictures in a new random order every time the widget is drawn. Off by default. */
    fun getShuffle(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("shuffle_$appWidgetId", false)

    fun saveShuffle(context: Context, appWidgetId: Int, shuffle: Boolean) {
        prefs(context).edit { if (shuffle) putBoolean("shuffle_$appWidgetId", true) else remove("shuffle_$appWidgetId") }
    }

    /** Forgets everything about one widget (every key ends with "_<widgetId>"). */
    fun remove(context: Context, appWidgetId: Int) {
        removeWhere(context) { it == appWidgetId }
    }

    /** Forgets every widget that isn't in [activeIds]. */
    fun removeAllExcept(context: Context, activeIds: Set<Int>) {
        removeWhere(context) { it !in activeIds }
    }

    private fun removeWhere(context: Context, shouldRemove: (appWidgetId: Int) -> Boolean) {
        val prefs = prefs(context)
        val keys = prefs.all.keys.filter { key ->
            val id = key.substringAfterLast('_').toIntOrNull()
            id != null && shouldRemove(id)
        }
        if (keys.isNotEmpty()) prefs.edit { keys.forEach { remove(it) } }
    }

    /**
     * The widget size (longest side in px, 0 = unknown) and shape the current frames were made for,
     * so a resize can tell whether they need remaking.
     */
    fun getFramesFit(context: Context, appWidgetId: Int): Pair<Int, Float> {
        val prefs = prefs(context)
        return prefs.getInt("fit_px_$appWidgetId", 0) to prefs.getFloat("fit_aspect_$appWidgetId", 0f)
    }

    fun saveFramesFit(
        context: Context, appWidgetId: Int, sidePx: Int, aspect: Float, rounded: Boolean, shapeId: String?, zoom: Float?,
        spin: Float?
    ) {
        prefs(context).edit {
            if (spin == null) remove("fit_spin_$appWidgetId") else putFloat("fit_spin_$appWidgetId", spin)
            if (zoom == null) remove("fit_zoom_$appWidgetId") else putFloat("fit_zoom_$appWidgetId", zoom)
            putInt("fit_px_$appWidgetId", sidePx)
            putFloat("fit_aspect_$appWidgetId", aspect)
            putBoolean("fit_round_$appWidgetId", rounded)
            if (shapeId == null) remove("fit_shape_$appWidgetId") else putString("fit_shape_$appWidgetId", shapeId)
        }
    }

    /** The spin (seconds per turn) the current frames were made with, or null if they don't spin. */
    fun getFramesSpin(context: Context, appWidgetId: Int): Float? =
        prefs(context).let { if (it.contains("fit_spin_$appWidgetId")) it.getFloat("fit_spin_$appWidgetId", 4f) else null }

    /**
     * "Make the CD spin": seconds per turn, or null when off. Only used with a shape that can spin
     * (see WidgetShape.spins); other shapes ignore it.
     */
    fun getSpin(context: Context, appWidgetId: Int): Float? =
        prefs(context).let { if (it.contains("spin_$appWidgetId")) it.getFloat("spin_$appWidgetId", 4f) else null }

    fun saveSpin(context: Context, appWidgetId: Int, secondsPerTurn: Float?) {
        prefs(context).edit {
            if (secondsPerTurn == null) remove("spin_$appWidgetId") else putFloat("spin_$appWidgetId", secondsPerTurn)
        }
    }

    /** The custom zoom the current frames were made with (null = a Fit / Fill preset). */
    fun getFramesZoom(context: Context, appWidgetId: Int): Float? =
        prefs(context).let { if (it.contains("fit_zoom_$appWidgetId")) it.getFloat("fit_zoom_$appWidgetId", 1f) else null }

    /**
     * The widget's own zoom (1 = Fill, see Zoom), or null when it uses the Fit / Fill preset in
     * [getScaleMode] (every v1.0 widget: they have no zoom saved, so they look exactly as before).
     */
    fun getZoom(context: Context, appWidgetId: Int): Float? =
        prefs(context).let { if (it.contains("zoom_$appWidgetId")) it.getFloat("zoom_$appWidgetId", 1f) else null }

    fun saveZoom(context: Context, appWidgetId: Int, zoom: Float?) {
        prefs(context).edit { if (zoom == null) remove("zoom_$appWidgetId") else putFloat("zoom_$appWidgetId", zoom) }
    }

    /** The shape the current frames were cut into (null = none), to know if they need remaking. */
    fun getFramesShapeId(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("fit_shape_$appWidgetId", null)

    /** The widget's shape ("cookie cutter"), or null for none (the default, and every v1.0 widget). */
    fun getShape(context: Context, appWidgetId: Int): WidgetShape? =
        Shapes.byId(context, prefs(context).getString("shape_$appWidgetId", null))

    /**
     * The shape tools (Pixelify size, Reverse). Kept even with no shape, so they're still set when
     * a shape is picked again; they only apply to a shape.
     */
    fun getShapeStyle(context: Context, appWidgetId: Int): com.octorice.opengifwidget.shape.ShapeStyle {
        val prefs = prefs(context)
        val pixel = prefs.getString("shape_pixel_$appWidgetId", null)
            ?.let { name -> com.octorice.opengifwidget.shape.PixelSize.entries.firstOrNull { it.name == name } }
        val halftone = prefs.getString("shape_halftone_$appWidgetId", null)
            ?.let { name -> com.octorice.opengifwidget.shape.PixelSize.entries.firstOrNull { it.name == name } }
        return com.octorice.opengifwidget.shape.ShapeStyle(
            pixel, prefs.getBoolean("shape_reverse_$appWidgetId", false), halftone
        )
    }

    fun saveShapeStyle(context: Context, appWidgetId: Int, style: com.octorice.opengifwidget.shape.ShapeStyle) {
        prefs(context).edit {
            if (style.pixel == null) remove("shape_pixel_$appWidgetId") else putString("shape_pixel_$appWidgetId", style.pixel.name)
            if (!style.reverse) remove("shape_reverse_$appWidgetId") else putBoolean("shape_reverse_$appWidgetId", true)
            if (style.halftone == null) remove("shape_halftone_$appWidgetId") else putString("shape_halftone_$appWidgetId", style.halftone.name)
        }
    }

    /** The saved shape's id as stored ("heart", "custom:1712345", ...), even if that shape is gone. */
    fun getShapeId(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("shape_$appWidgetId", null)

    fun saveShape(context: Context, appWidgetId: Int, shape: WidgetShape?) {
        prefs(context).edit {
            if (shape == null) remove("shape_$appWidgetId") else putString("shape_$appWidgetId", shape.id)
        }
    }

    /** Whether the current frames have rounded corners cut in (Android 8-11 only). */
    fun getFramesRounded(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("fit_round_$appWidgetId", false)

    /**
     * The "Rounded corners" switch (only shown on Android 8-11, which don't round widgets themselves).
     * On by default.
     */
    fun getRoundCorners(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("round_$appWidgetId", true)

    fun saveRoundCorners(context: Context, appWidgetId: Int, round: Boolean) {
        prefs(context).edit { putBoolean("round_$appWidgetId", round) }
    }

    /** Playback speed of the widget's animation, from 0.25x to 3x. Normal speed (1x) by default. */
    fun getSpeed(context: Context, appWidgetId: Int): Float =
        prefs(context).getFloat("speed_$appWidgetId", 1f).coerceIn(MIN_SPEED, MAX_SPEED)

    fun saveSpeed(context: Context, appWidgetId: Int, speed: Float) {
        prefs(context).edit { putFloat("speed_$appWidgetId", speed.coerceIn(MIN_SPEED, MAX_SPEED)) }
    }

    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f

    /** The name you gave the widget, or null if you never changed it (then [defaultName] is used). */
    fun getName(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("title_$appWidgetId", null)

    fun saveName(context: Context, appWidgetId: Int, name: String) {
        prefs(context).edit { putString("title_$appWidgetId", name) }
    }

    /** The widget's name: the one you chose, or else the picked file's name. */
    fun displayName(context: Context, appWidgetId: Int, gif: StoredGif?): String =
        getName(context, appWidgetId) ?: gif?.let { defaultName(it.name) }.orEmpty()

    /** A file's name without its extension: "cat dance.gif" -> "cat dance". */
    fun defaultName(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }

    /**
     * "Removed from the list" in the app. Used for leftovers: widgets the launcher still keeps
     * but never put on the home screen (apps can't delete those, only the launcher can).
     */
    fun isHidden(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean("hidden_$appWidgetId", false)

    fun setHidden(context: Context, appWidgetId: Int, hidden: Boolean) {
        prefs(context).edit {
            if (hidden) putBoolean("hidden_$appWidgetId", true) else remove("hidden_$appWidgetId")
        }
    }

    fun getScaleMode(context: Context, appWidgetId: Int): ScaleMode {
        val name = prefs(context).getString("scale_mode_$appWidgetId", null)
        return ScaleMode.entries.firstOrNull { it.name == name } ?: ScaleMode.FILL // new widgets fill
    }

    fun saveScaleMode(context: Context, appWidgetId: Int, mode: ScaleMode) {
        prefs(context).edit { putString("scale_mode_$appWidgetId", mode.name) }
    }

    /** Where Fill mode is centered, from 0 to 1 on each axis. The middle (0.5, 0.5) by default. */
    fun getCropCenter(context: Context, appWidgetId: Int): Pair<Float, Float> {
        val prefs = prefs(context)
        return prefs.getFloat("crop_x_$appWidgetId", 0.5f) to prefs.getFloat("crop_y_$appWidgetId", 0.5f)
    }

    fun saveCropCenter(context: Context, appWidgetId: Int, x: Float, y: Float) {
        prefs(context).edit {
            putFloat("crop_x_$appWidgetId", x)
            putFloat("crop_y_$appWidgetId", y)
        }
    }

    /** The widget's background color and opacity. Dark at 80% by default (the original look). */
    fun getBackground(context: Context, appWidgetId: Int): WidgetBackground {
        val prefs = prefs(context)
        return WidgetBackground(
            color = prefs.getInt("bg_color_$appWidgetId", WidgetBackground.DEFAULT.color),
            alpha = prefs.getInt("bg_alpha_$appWidgetId", WidgetBackground.DEFAULT.alpha)
        )
    }

    fun saveBackground(context: Context, appWidgetId: Int, background: WidgetBackground) {
        prefs(context).edit {
            putInt("bg_color_$appWidgetId", background.color)
            putInt("bg_alpha_$appWidgetId", background.alpha)
        }
    }

    // A frame set is stored as keys like "frames_version_42" / "master_version_42".
    private fun SharedPreferences.readFrameSet(prefix: String, id: Int): FrameSet? {
        if (!contains("${prefix}_version_$id")) return null
        return FrameSet(
            version = getLong("${prefix}_version_$id", 0L),
            frameCount = getInt("${prefix}_count_$id", 0),
            sourceFrameCount = getInt("${prefix}_source_count_$id", 0),
            intervalMs = getInt("${prefix}_interval_$id", 100),
            width = getInt("${prefix}_width_$id", 0),
            height = getInt("${prefix}_height_$id", 0),
            masked = getBoolean("${prefix}_masked_$id", false),
            boxed = getBoolean("${prefix}_boxed_$id", false)
        )
    }

    private fun SharedPreferences.Editor.writeFrameSet(prefix: String, id: Int, frames: FrameSet?) {
        if (frames == null) {
            remove("${prefix}_version_$id")
            return
        }
        putLong("${prefix}_version_$id", frames.version)
        putInt("${prefix}_count_$id", frames.frameCount)
        putInt("${prefix}_source_count_$id", frames.sourceFrameCount)
        putInt("${prefix}_interval_$id", frames.intervalMs)
        putInt("${prefix}_width_$id", frames.width)
        putInt("${prefix}_height_$id", frames.height)
        putBoolean("${prefix}_masked_$id", frames.masked)
        putBoolean("${prefix}_boxed_$id", frames.boxed)
    }
}
