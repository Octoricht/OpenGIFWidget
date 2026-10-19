package com.octorice.opengifwidget.data

import android.app.ActivityManager
import android.content.Context

/**
 * How capable the phone is, judged by its memory. Cheaper phones get gentler limits:
 * [maxFpsCap] highest FPS cap, [maxClipMs] longest video clip. Shorter clips have fewer frames,
 * so each frame can be bigger (sharper) within the same memory budget.
 */
enum class DeviceTier(val maxFpsCap: Int, val maxClipMs: Long) {
    LOW(maxFpsCap = 15, maxClipMs = 2_000),  // about 3 GB of RAM or less, or "Android Go" phones
    MID(maxFpsCap = 24, maxClipMs = 3_000),  // about 4-6 GB
    HIGH(maxFpsCap = 30, maxClipMs = 3_000)  // about 8 GB or more
}

object DeviceProfile {

    /** The phone's total RAM in bytes (Android reports a bit less than the number on the box). */
    fun totalRamBytes(context: Context): Long {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return 0L
        return ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }.totalMem
    }

    fun tier(context: Context): DeviceTier {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return DeviceTier.HIGH
        if (activityManager.isLowRamDevice) return DeviceTier.LOW
        val gigabytes = totalRamBytes(context) / (1024.0 * 1024 * 1024)
        return when {
            gigabytes < 3.5 -> DeviceTier.LOW   // "3 GB" phones report about 2.7-2.9 GB
            gigabytes < 7.0 -> DeviceTier.MID   // "4-6 GB" phones
            else -> DeviceTier.HIGH             // "8 GB" phones report about 7.3-7.6 GB
        }
    }
}
