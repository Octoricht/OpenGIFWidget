package com.octorice.opengifwidget.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's small vibrations, in one place so they feel the same everywhere. They go through
 * Android's own haptics, so they follow the phone's "Touch feedback" setting (off there = off here)
 * and older phones get the nearest effect they have.
 *
 * Kept light on purpose: a tick when a choice or a slider step changes, a toggle for switches,
 * a firmer "done" when something is saved, a buzz for errors. Plain navigation buttons stay silent.
 */
class Haptics(private val feedback: HapticFeedback) {
    /** A choice was picked (segmented buttons, shape tiles, color swatches). */
    fun pick() = feedback.performHapticFeedback(HapticFeedbackType.ContextClick)

    /** A main button was pressed (Add widget, Gallery, Files...). */
    fun press() = feedback.performHapticFeedback(HapticFeedbackType.VirtualKey)

    /** A switch or an on/off chip changed. */
    fun toggle(on: Boolean) =
        feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** A slider landed on a new stop (few, meaningful stops). */
    fun tick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)

    /** A slider moved one small step (many stops: a fainter tick). */
    fun smallTick() = feedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

    /** Something finished well (Save, Done, Use this clip). */
    fun confirm() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something went wrong (an error message appeared). */
    fun reject() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A long press started something (drag to reorder, remove from list). */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)

    /** Ticks when a slider's shown value changes ([old] -> [new]), so each stop is felt once. */
    fun <T> step(old: T, new: T, small: Boolean = false) {
        if (old != new) if (small) smallTick() else tick()
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}
