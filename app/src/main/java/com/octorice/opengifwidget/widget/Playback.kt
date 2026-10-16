package com.octorice.opengifwidget.widget

import com.octorice.opengifwidget.data.AppSettings
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** Which saved frames to show ([frames], in order) and how long each one stays on screen. */
data class PlaybackPlan(val frames: List<Int>, val intervalMs: Int)

object Playback {

    /**
     * target = average frame time / speed.
     * If that's within the FPS cap, every frame is shown for `target` ms.
     * If it's faster, we show as many frames as the cap allows, picked evenly across the
     * animation (e.g. 53 of 59, dropping about 1 in 10), and stretch each one so a full loop
     * still takes exactly as long as before. Uses the saved frames as they are: nothing is decoded again.
     */
    fun plan(frameCount: Int, averageDelayMs: Int, speed: Float = 1f, fpsCap: Int = AppSettings.DEFAULT_FPS_CAP): PlaybackPlan {
        val count = max(1, frameCount)
        val target = averageDelayMs / speed.toDouble()
        val minInterval = 1000.0 / fpsCap
        if (target >= minInterval) {
            return PlaybackPlan((0 until count).toList(), max(1, target.roundToInt()))
        }
        val loopMs = target * count
        val kept = max(1, floor(loopMs / minInterval).toInt())
        // Spread the kept frames evenly: frame k of the plan is saved frame k * count / kept.
        val frames = (0 until kept).map { k -> (k.toLong() * count / kept).toInt() }
        return PlaybackPlan(frames, max(1, (loopMs / kept).roundToInt()))
    }
}
