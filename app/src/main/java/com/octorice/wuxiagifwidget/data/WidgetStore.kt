package com.octorice.wuxiagifwidget.data

import android.content.Context
import androidx.core.content.edit
import com.octorice.wuxiagifwidget.gif.FrameSet

/** What we remember about the GIF chosen for one widget. */
data class StoredGif(
    val name: String,
    val sizeBytes: Long,
    val frames: FrameSet? = null // null until the frames have been extracted
)

/** How a GIF is shown when its shape doesn't match the widget's shape. */
enum class ScaleMode {
    FIT,  // show the whole GIF, with empty space around it
    FILL  // cover the whole widget, cutting off the edges
}

/**
 * Remembers which GIF belongs to which widget, using SharedPreferences
 * (a small key-value file in the app's private storage).
 * Every widget has its own id, so keys look like "gif_name_42".
 */
object WidgetStore {

    private fun prefs(context: Context) =
        context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    fun getGif(context: Context, appWidgetId: Int): StoredGif? {
        val prefs = prefs(context)
        val name = prefs.getString("gif_name_$appWidgetId", null) ?: return null
        val frames = if (prefs.contains("frames_version_$appWidgetId")) {
            FrameSet(
                version = prefs.getLong("frames_version_$appWidgetId", 0L),
                frameCount = prefs.getInt("frames_count_$appWidgetId", 0),
                sourceFrameCount = prefs.getInt("frames_source_count_$appWidgetId", 0),
                intervalMs = prefs.getInt("frames_interval_$appWidgetId", 100),
                width = prefs.getInt("frames_width_$appWidgetId", 0),
                height = prefs.getInt("frames_height_$appWidgetId", 0)
            )
        } else {
            null
        }
        return StoredGif(name, prefs.getLong("gif_size_$appWidgetId", 0L), frames)
    }

    fun getScaleMode(context: Context, appWidgetId: Int): ScaleMode {
        val name = prefs(context).getString("scale_mode_$appWidgetId", null)
        return ScaleMode.entries.firstOrNull { it.name == name } ?: ScaleMode.FIT
    }

    fun saveScaleMode(context: Context, appWidgetId: Int, mode: ScaleMode) {
        prefs(context).edit { putString("scale_mode_$appWidgetId", mode.name) }
    }

    fun saveGif(context: Context, appWidgetId: Int, gif: StoredGif) {
        prefs(context).edit {
            putString("gif_name_$appWidgetId", gif.name)
            putLong("gif_size_$appWidgetId", gif.sizeBytes)
            val frames = gif.frames
            if (frames == null) {
                remove("frames_version_$appWidgetId")
            } else {
                putLong("frames_version_$appWidgetId", frames.version)
                putInt("frames_count_$appWidgetId", frames.frameCount)
                putInt("frames_source_count_$appWidgetId", frames.sourceFrameCount)
                putInt("frames_interval_$appWidgetId", frames.intervalMs)
                putInt("frames_width_$appWidgetId", frames.width)
                putInt("frames_height_$appWidgetId", frames.height)
            }
        }
    }
}
