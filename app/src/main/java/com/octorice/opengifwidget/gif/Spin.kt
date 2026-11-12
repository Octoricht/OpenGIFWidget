package com.octorice.opengifwidget.gif

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Spinning (the CD shape): the picture turns under the shape, baked into the widget frames
 * (a widget can't rotate anything itself). [secondsPerTurn] = how long one full turn takes at
 * normal speed; [fpsCap] = the app's FPS cap, so no more frames are made than can be shown.
 */
data class Spin(val secondsPerTurn: Float, val fpsCap: Int) {

    /** One widget frame: which master frame it shows and how far it's turned (degrees, clockwise). */
    data class Step(val masterIndex: Int, val degrees: Float)

    data class Plan(val steps: List<Step>, val intervalMs: Int)

    /**
     * The frames to make from [master]. The loop always ends on whole turns, so there's no jump
     * when it starts over:
     * - a still picture: one turn, at up to [SPIN_FPS] frames per second;
     * - an animation: as many of its loops as make about one turn (or as many whole turns as fit
     *   in one long loop); each frame is turned by how far the disc got at that moment.
     * Never more than [FrameExtractor.MAX_FRAMES] frames (then fewer per second).
     */
    fun plan(master: FrameSet): Plan {
        val turnMs = secondsPerTurn * 1000f
        val fps = max(1, minOf(fpsCap, SPIN_FPS))
        if (master.frameCount <= 1) {
            val count = (turnMs / 1000f * fps).roundToInt().coerceIn(MIN_FRAMES, FrameExtractor.MAX_FRAMES)
            return Plan(List(count) { Step(0, 360f * it / count) }, max(1, (turnMs / count).roundToInt()))
        }
        val loopMs = master.frameCount * master.intervalMs.toFloat()
        // Repeat short animations until about one turn fits; long ones hold several whole turns.
        val loops = if (loopMs < turnMs) max(1, (turnMs / loopMs).roundToInt()) else 1
        val totalMs = loops * loopMs
        val turns = max(1, (totalMs / turnMs).roundToInt())
        // At least the animation's own frame rate (capped), so it doesn't lose frames to the spin.
        val ownFps = 1000f / master.intervalMs
        val rate = minOf(fpsCap.toFloat(), max(fps.toFloat(), ownFps))
        val count = (totalMs / 1000f * rate).roundToInt().coerceIn(MIN_FRAMES, FrameExtractor.MAX_FRAMES)
        val steps = List(count) { j ->
            val time = totalMs * j / count
            val index = floor((time % loopMs) / master.intervalMs).toInt().coerceIn(0, master.frameCount - 1)
            Step(index, 360f * turns * j / count)
        }
        return Plan(steps, max(1, (totalMs / count).roundToInt()))
    }

    companion object {
        /** Spins look smooth enough at this rate and cost far less RAM than 30. */
        private const val SPIN_FPS = 15
        private const val MIN_FRAMES = 8

        /** The speed choices (seconds per turn), slow to fast. */
        val SECONDS_PER_TURN = listOf(12f, 8f, 6f, 4f, 3f, 2f, 1.5f, 1f)
        const val DEFAULT_SECONDS_PER_TURN = 4f
    }
}
