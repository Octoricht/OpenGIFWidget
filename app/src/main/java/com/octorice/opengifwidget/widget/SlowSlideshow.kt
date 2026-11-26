package com.octorice.opengifwidget.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.octorice.opengifwidget.data.SlideTiming
import com.octorice.opengifwidget.data.WidgetStore

/**
 * Slow slideshows (5 minutes or more per picture). The widget shows ONE picture (so only one sits in
 * the launcher's RAM), and a timer moves it to the next one.
 *
 * The timer is an inexact, non-waking alarm (AlarmManager.set with RTC): it never wakes the phone up.
 * If it comes due while the screen is off, it runs as soon as the phone is used again, so the new
 * picture is there when you look. Android may also run it a few minutes late to save battery, which
 * is why the editor says "about every hour". No permission is needed for this kind of alarm.
 * Alarms are lost on reboot; RestoreWidgetsReceiver redraws every widget then, which sets them again.
 */
object SlowSlideshow {

    private const val ACTION_NEXT = "com.octorice.opengifwidget.NEXT_PICTURE"
    // "When tapped: Next picture" (slow slideshows only: in fast ones the launcher's own timer decides
    // which picture is up, and Android drops a "flip once" request before it reaches the launcher,
    // so the app can't move them on). Its own action, so it never
    // replaces or cancels the slow slideshow's timer (same receiver, same request code).
    const val ACTION_TAP_NEXT = "com.octorice.opengifwidget.TAP_NEXT_PICTURE"

    /** The widget's tap when it's set to "Next picture". */
    fun tapNextIntent(context: Context, appWidgetId: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            appWidgetId,
            Intent(context, NextPictureReceiver::class.java)
                .setAction(ACTION_TAP_NEXT)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** True for a slideshow with 5 minutes or more per picture. */
    fun isSlow(context: Context, appWidgetId: Int): Boolean =
        GifWidgetProvider.isSlideshow(context, appWidgetId) &&
            SlideTiming.isSlow(WidgetStore.getSlideSeconds(context, appWidgetId))

    /**
     * The frame to show now. Makes sure the play order fits [count] pictures (after pictures were
     * added or removed, or Shuffle switched) and starts the clock the first time.
     */
    fun currentFrame(context: Context, appWidgetId: Int, count: Int, shuffle: Boolean): Int {
        var state = WidgetStore.getSlidePosition(context, appWidgetId)
        val orderFits = if (shuffle) state.order.sorted() == (0 until count).toList() else state.order.isEmpty()
        if (!orderFits || state.position !in 0 until count || state.changedAt == 0L) {
            state = WidgetStore.SlidePosition(
                position = state.position.takeIf { orderFits && it in 0 until count } ?: 0,
                order = if (orderFits) state.order else newOrder(count, shuffle, avoidFirst = null),
                changedAt = state.changedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
            WidgetStore.saveSlidePosition(context, appWidgetId, state)
        }
        return state.order.getOrNull(state.position) ?: state.position
    }

    /** Back to the first picture, with the clock starting now (after the slideshow was saved). */
    fun restart(context: Context, appWidgetId: Int, count: Int, shuffle: Boolean) {
        WidgetStore.saveSlidePosition(
            context, appWidgetId,
            WidgetStore.SlidePosition(0, newOrder(count, shuffle, avoidFirst = null), System.currentTimeMillis())
        )
    }

    /** Moves to the next picture. After the last one it starts over (in a new order with Shuffle). */
    fun advance(context: Context, appWidgetId: Int) {
        val count = WidgetStore.getGif(context, appWidgetId)?.frames?.frameCount ?: return
        val shuffle = WidgetStore.getShuffle(context, appWidgetId)
        val shown = currentFrame(context, appWidgetId, count, shuffle)
        val state = WidgetStore.getSlidePosition(context, appWidgetId)
        val next = state.position + 1
        WidgetStore.saveSlidePosition(
            context, appWidgetId,
            if (next < count) {
                state.copy(position = next, changedAt = System.currentTimeMillis())
            } else {
                // A new round. With Shuffle a new order, which never starts with the picture just shown.
                WidgetStore.SlidePosition(0, newOrder(count, shuffle, avoidFirst = shown), System.currentTimeMillis())
            }
        )
    }

    /** Sets the timer for the next picture: [seconds] after the current one appeared. */
    fun schedule(context: Context, appWidgetId: Int, seconds: Int) {
        val changedAt = WidgetStore.getSlidePosition(context, appWidgetId).changedAt
        val start = if (changedAt > 0L) changedAt else System.currentTimeMillis()
        // (Already overdue, e.g. after a reboot: Android runs it right away.)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        // A plain set() may run up to 75% of the wait late (45 min for 1 hour). setWindow keeps it within
        // 10% (at least 1 minute): still inexact and battery-friendly, and still no permission needed.
        // (Android 13+ widens windows under 10 minutes, measured on OnePlus: so "up to about 10 minutes late".)
        val windowMs = maxOf(60_000L, seconds * 100L)
        alarms.setWindow(AlarmManager.RTC, start + seconds * 1000L, windowMs, pendingIntent(context, appWidgetId, create = true)!!)
    }

    /** Stops the timer (the widget was removed, or isn't a slow slideshow anymore). */
    fun cancel(context: Context, appWidgetId: Int) {
        val pending = pendingIntent(context, appWidgetId, create = false) ?: return // no timer set
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
        pending.cancel()
    }

    private fun newOrder(count: Int, shuffle: Boolean, avoidFirst: Int?): List<Int> {
        if (!shuffle) return emptyList() // plain order: 0, 1, 2, ...
        val order = (0 until count).shuffled().toMutableList()
        if (count > 1 && order.first() == avoidFirst) order.add(order.removeAt(0)) // no repeat across rounds
        return order
    }

    private fun pendingIntent(context: Context, appWidgetId: Int, create: Boolean): PendingIntent? {
        val intent = Intent(context, NextPictureReceiver::class.java)
            .setAction(ACTION_NEXT)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        // requestCode = widget id, so each slideshow has its own timer.
        return PendingIntent.getBroadcast(context, appWidgetId, intent, flags)
    }
}

/**
 * The slow slideshow timer went off, or a slow slideshow set to "Next picture" was tapped: show the
 * next picture (redrawing sets the next timer, so after a tap the new picture gets its full time).
 */
class NextPictureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        // Gone from the home screen, or no longer a slow slideshow: nothing to do (and no new timer).
        if (appWidgetId !in GifWidgetProvider.allWidgetIds(context)) return
        if (SlowSlideshow.isSlow(context, appWidgetId)) SlowSlideshow.advance(context, appWidgetId)
        GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }
}
