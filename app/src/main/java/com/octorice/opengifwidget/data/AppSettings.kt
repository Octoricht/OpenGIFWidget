package com.octorice.opengifwidget.data

import android.content.Context
import androidx.core.content.edit

/** App-wide settings (the Settings screen), stored in their own small SharedPreferences file. */
object AppSettings {

    /** The FPS cap choices. Never above 30: faster flipping makes the launcher work too hard. */
    val FPS_CAP_OPTIONS = listOf(10, 15, 24, 30)
    const val DEFAULT_FPS_CAP = 30

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The highest frame rate any widget plays at. */
    fun getFpsCap(context: Context): Int {
        val saved = prefs(context).getInt("fps_cap", DEFAULT_FPS_CAP)
        return if (saved in FPS_CAP_OPTIONS) saved else DEFAULT_FPS_CAP
    }

    fun setFpsCap(context: Context, fps: Int) {
        prefs(context).edit { putInt("fps_cap", fps) }
    }
}
