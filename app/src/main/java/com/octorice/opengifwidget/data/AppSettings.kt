package com.octorice.opengifwidget.data

import android.content.Context
import androidx.core.content.edit

/** App-wide settings (the Settings screen), stored in their own small SharedPreferences file. */
object AppSettings {

    /** The FPS cap choices. Never above 30: faster flipping makes the launcher work too hard. */
    val FPS_CAP_OPTIONS = listOf(10, 15, 24, 30)

    /** Fallback when there's no phone to ask (e.g. default parameters). */
    const val DEFAULT_FPS_CAP = 30

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The highest FPS cap this phone should use (lower on phones with little RAM). */
    fun maxFpsCap(context: Context): Int = DeviceProfile.tier(context).maxFpsCap

    /**
     * The highest frame rate any widget plays at. Defaults to the phone's maximum, and never goes
     * above it (even if a higher value was saved, e.g. from a backup of another phone).
     */
    fun getFpsCap(context: Context): Int {
        val max = maxFpsCap(context)
        val saved = prefs(context).getInt("fps_cap", max)
        return (if (saved in FPS_CAP_OPTIONS) saved else max).coerceAtMost(max)
    }

    fun setFpsCap(context: Context, fps: Int) {
        prefs(context).edit { putInt("fps_cap", fps.coerceAtMost(maxFpsCap(context))) }
    }
}
