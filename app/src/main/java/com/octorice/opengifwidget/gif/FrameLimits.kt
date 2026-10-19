package com.octorice.opengifwidget.gif

import android.app.ActivityManager
import android.content.Context
import com.octorice.opengifwidget.data.DeviceProfile
import com.octorice.opengifwidget.data.DeviceTier

/**
 * How big a widget's frames may be. The launcher keeps every frame of every widget in memory,
 * so cheap phones with little RAM get smaller limits to keep the launcher from crashing.
 */
data class FrameLimits(
    val animationMaxSidePx: Int, // longest side of animation frames
    val stillMaxSidePx: Int,     // longest side of still pictures
    val maxTotalPixels: Int      // all frames of one widget together
) {
    companion object {
        // maxTotalPixels x 4 bytes = the launcher memory one widget may use: ~32 / 24 / 12 MB.
        val NORMAL = FrameLimits(animationMaxSidePx = 512, stillMaxSidePx = 1080, maxTotalPixels = 8_000_000)
        val MID = FrameLimits(animationMaxSidePx = 512, stillMaxSidePx = 1080, maxTotalPixels = 6_000_000)
        val LOW_RAM = FrameLimits(animationMaxSidePx = 384, stillMaxSidePx = 720, maxTotalPixels = 3_000_000)

        /**
         * Limits by phone tier (see DeviceProfile). Phones that give each app at most 128 MB of
         * memory get the low-end limits whatever their tier.
         */
        fun forDevice(context: Context): FrameLimits {
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return NORMAL
            if (activityManager.memoryClass <= 128) return LOW_RAM
            return when (DeviceProfile.tier(context)) {
                DeviceTier.LOW -> LOW_RAM
                DeviceTier.MID -> MID
                DeviceTier.HIGH -> NORMAL
            }
        }
    }
}
