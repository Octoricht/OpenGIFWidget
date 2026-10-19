package com.octorice.opengifwidget.home

import android.content.Context
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.data.DeviceProfile
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.gif.FrameLimits
import com.octorice.opengifwidget.gif.FrameSet
import com.octorice.opengifwidget.widget.Playback
import kotlin.math.roundToInt

/** How hard the widgets push this phone, from fine to too much. */
enum class HealthLevel { GOOD, HEAVY, TOO_MUCH }

/** What a warning is about. */
enum class HealthReason { MEMORY, ANIMATION, STORAGE }

/**
 * The verdict shown in the main screen's header.
 * [memoryBytes]: estimated launcher memory for all widgets; [memoryAtLowerCap]: the same with a
 * lower FPS cap ([lowerCap]), to suggest it; [flipsPerSecond]: all widgets together;
 * [heavyWidgetIds]: widgets that cost the most (tagged "Heavy" on their cards).
 */
data class Health(
    val level: HealthLevel,
    val reason: HealthReason?,
    val memoryBytes: Long,
    val memoryAtLowerCap: Long,
    val lowerCap: Int?,
    val flipsPerSecond: Int,
    val freeStorageBytes: Long,
    val heavyWidgetIds: Set<Int>
)

/**
 * Estimates what the widgets cost, compared with limits that scale with the phone:
 * - Memory: the launcher keeps every shown frame in memory, about 4 bytes per pixel.
 *   Heavy above 2% of the phone's RAM, too much above 4% (about 230 / 460 MB on a 12 GB phone,
 *   60 / 120 MB on a 3 GB phone). Calibrated on a real 9-widget setup that runs smoothly (~175 MB).
 * - Animation: frame flips per second of all widgets. Widgets on other home screen pages are
 *   paused, and we can't know which page each one is on, so this only ever suggests (amber),
 *   above 8 widgets' worth at the phone's highest FPS cap.
 * - Storage: only a problem when the phone itself is nearly full.
 * It's an estimate: apps can't read another app's (the launcher's) real memory use.
 */
object UsageHealth {

    private const val LOW_STORAGE_BYTES = 1_000_000_000L      // 1 GB
    private const val VERY_LOW_STORAGE_BYTES = 300_000_000L   // 300 MB

    /** Launcher memory and flips per second of one widget, with this speed and FPS cap. */
    fun widgetCost(frames: FrameSet, speed: Float, fpsCap: Int): Pair<Long, Float> {
        val frameBytes = frames.width.toLong() * frames.height * 4
        if (frames.frameCount <= 1) return frameBytes to 0f // a still picture doesn't flip
        val plan = Playback.plan(frames.frameCount, frames.intervalMs, speed, fpsCap)
        return plan.frames.size * frameBytes to 1000f / plan.intervalMs
    }

    fun evaluate(context: Context, widgets: List<WidgetSummary>): Health {
        val fpsCap = AppSettings.getFpsCap(context)
        val ram = DeviceProfile.totalRamBytes(context).coerceAtLeast(1L)
        val maxFps = AppSettings.maxFpsCap(context)

        val costs = widgets.mapNotNull { widget ->
            widget.gif?.frames?.let { widget.appWidgetId to widgetCost(it, widget.speed, fpsCap) }
        }
        val memory = costs.sumOf { it.second.first }
        val flips = costs.sumOf { it.second.second.toDouble() }.roundToInt()

        // What a lower cap would save (only worth suggesting if there is a lower option).
        val lowerCap = AppSettings.FPS_CAP_OPTIONS.filter { it < fpsCap }.maxOrNull()?.let { minOf(it, 15) }
        val memoryAtLowerCap = if (lowerCap == null) memory else widgets.sumOf { widget ->
            widget.gif?.frames?.let { widgetCost(it, widget.speed, lowerCap).first } ?: 0L
        }

        val memoryLevel = when {
            memory > ram / 25 -> HealthLevel.TOO_MUCH
            memory > ram / 50 -> HealthLevel.HEAVY
            else -> HealthLevel.GOOD
        }
        val animationLevel = if (flips > maxFps * 8) HealthLevel.HEAVY else HealthLevel.GOOD
        val free = GifStorage.freeStorageBytes(context)
        val storageLevel = when {
            widgets.isEmpty() -> HealthLevel.GOOD
            free < VERY_LOW_STORAGE_BYTES -> HealthLevel.TOO_MUCH
            free < LOW_STORAGE_BYTES -> HealthLevel.HEAVY
            else -> HealthLevel.GOOD
        }

        // The worst one decides the color; on a tie, memory matters most, then animation.
        val (level, reason) = listOf(
            memoryLevel to HealthReason.MEMORY,
            animationLevel to HealthReason.ANIMATION,
            storageLevel to HealthReason.STORAGE
        ).maxBy { it.first.ordinal }

        // A widget is "heavy" if it uses most (60%+) of the memory one widget may use on this phone
        // (usually long GIFs and videos). Only tagged when there's a problem, so a healthy setup
        // doesn't show warnings.
        val perWidgetBudget = FrameLimits.forDevice(context).maxTotalPixels * 4L
        val heavyIds = if (level == HealthLevel.GOOD) {
            emptySet()
        } else {
            costs.filter { it.second.first >= perWidgetBudget * 0.6 }.map { it.first }.toSet()
        }

        return Health(
            level = level,
            reason = if (level == HealthLevel.GOOD) null else reason,
            memoryBytes = memory,
            memoryAtLowerCap = memoryAtLowerCap,
            lowerCap = lowerCap,
            flipsPerSecond = flips,
            freeStorageBytes = free,
            heavyWidgetIds = heavyIds
        )
    }
}
