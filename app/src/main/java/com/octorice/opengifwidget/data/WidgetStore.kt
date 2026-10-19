package com.octorice.opengifwidget.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.octorice.opengifwidget.gif.FrameSet

/** What we remember about the picture chosen for one widget. */
data class StoredGif(
    val name: String,
    val sizeBytes: Long,          // size of the original file
    val frames: FrameSet? = null, // the frames the widget shows (null until saved)
    val master: FrameSet? = null  // the uncropped master frames everything is made from
)

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
        return setOfNotNull(gif.frames?.version, gif.master?.version)
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

    fun saveFramesFit(context: Context, appWidgetId: Int, sidePx: Int, aspect: Float, rounded: Boolean) {
        prefs(context).edit {
            putInt("fit_px_$appWidgetId", sidePx)
            putFloat("fit_aspect_$appWidgetId", aspect)
            putBoolean("fit_round_$appWidgetId", rounded)
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
            height = getInt("${prefix}_height_$id", 0)
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
    }
}
