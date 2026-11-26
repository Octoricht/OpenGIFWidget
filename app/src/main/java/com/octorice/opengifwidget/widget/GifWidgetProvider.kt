package com.octorice.opengifwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.config.GifConfigActivity
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.Transition
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.provider.FrameProvider

/**
 * The home screen widget. Android calls these methods; we answer by sending
 * RemoteViews (a description of the layout) to the launcher, which draws it.
 *
 * "open" so [SlideshowWidgetProvider] can reuse all of it: the launcher lists the two as separate
 * widgets, but both kinds are drawn, resized and cleaned up by this same code.
 */
open class GifWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
        // onUpdate runs on reboot, app updates and when widgets are added,
        // which makes it a good moment to sweep away files of widgets that no longer exist.
        // Both kinds count: a slideshow's update must never sweep away a normal widget's files.
        val allIds = allWidgetIds(context)
        // Safety: if Android reports no widgets while we're updating some, something is off; skip.
        if (allIds.isNotEmpty()) {
            val activeIds = (allIds + appWidgetIds).toSet()
            WidgetStore.removeAllExcept(context, activeIds)
            GifStorage.deleteAllExcept(context, activeIds)
        }
    }

    /**
     * The widget was resized (or placed, or the phone rotated). If its frames no longer fit its
     * size or shape, remake them in the background from the master frames (about a second).
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        val pending = goAsync() // keep the app alive until the remake is done
        Thread {
            try {
                WidgetFrames.refreshIfNeeded(context.applicationContext, appWidgetId)
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** A widget was removed from the home screen: delete its GIF, frames and settings. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            SlowSlideshow.cancel(context, appWidgetId)
            WidgetStore.remove(context, appWidgetId)
            GifStorage.deleteWidgetFiles(context, appWidgetId)
        }
    }

    /**
     * The last widget of THIS kind was removed. Android calls this once per kind, so only clear
     * everything when no widget of the other kind is left either.
     */
    override fun onDisabled(context: Context) {
        if (allWidgetIds(context).isNotEmpty()) return
        WidgetStore.removeAllExcept(context, emptySet())
        GifStorage.deleteAllExcept(context, emptySet())
    }

    companion object {
        /** The widget's flippers: plain (animations, slideshows without a transition), then one per transition. */
        private val FLIPPERS = intArrayOf(R.id.widget_flipper, R.id.widget_flipper_fade, R.id.widget_flipper_slide)

        /**
         * Shows [flipper] and hides the others (all of them when null). Always set explicitly,
         * because launchers re-apply updates on top of what they showed before.
         */
        private fun showOnlyFlipper(views: RemoteViews, flipper: Int?) {
            for (id in FLIPPERS) views.setViewVisibility(id, if (id == flipper) View.VISIBLE else View.GONE)
        }

        /** Every widget of ours on the home screen: normal widgets and slideshows. */
        fun allWidgetIds(context: Context): IntArray {
            val manager = AppWidgetManager.getInstance(context)
            return manager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java)) +
                manager.getAppWidgetIds(ComponentName(context, SlideshowWidgetProvider::class.java))
        }

        /** True if this widget was added as a slideshow (the launcher remembers which kind it is). */
        fun isSlideshow(context: Context, appWidgetId: Int): Boolean =
            AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className ==
                SlideshowWidgetProvider::class.java.name

        /** Redraws every widget, e.g. after the FPS cap changed in Settings. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in allWidgetIds(context)) {
                updateWidget(context, manager, id)
            }
        }

        /** Builds the look of one widget and sends it to the launcher. */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_gif)
            val gif = WidgetStore.getGif(context, appWidgetId)
            val frames = gif?.frames

            if (frames == null) {
                // No frames yet: show the placeholder and a hint (and no slideshow timer).
                SlowSlideshow.cancel(context, appWidgetId)
                val label = if (gif == null) {
                    context.getString(
                        if (isSlideshow(context, appWidgetId)) R.string.widget_tap_to_choose_slideshow
                        else R.string.widget_tap_to_choose
                    )
                } else {
                    context.getString(R.string.widget_tap_to_repick)
                }
                views.setTextViewText(R.id.widget_label, label)
            } else {
                views.setViewVisibility(R.id.widget_placeholder, View.GONE)
                views.setViewVisibility(R.id.widget_label, View.GONE)

                // Fit and Fill are two tiny layouts that differ only in the ImageView's scaleType.
                // Shaped and custom-zoom frames already have their box's shape: they're only
                // centered (Fit), never cropped again.
                val slideshow = isSlideshow(context, appWidgetId)
                val fill = WidgetStore.getScaleMode(context, appWidgetId) == ScaleMode.FILL
                val frameLayout = when {
                    frames.masked -> R.layout.widget_frame_fit
                    // A Fill slideshow without a shape: its frames already have the widget's shape, but
                    // the launcher's real size can differ a little from the size it reports. Cropping the
                    // last few pixels (Fill) keeps it edge to edge instead of leaving thin empty strips.
                    slideshow && fill -> R.layout.widget_frame_fill
                    frames.boxed -> R.layout.widget_frame_fit
                    !fill -> R.layout.widget_frame_fit
                    WidgetStore.getScaleMode(context, appWidgetId) == ScaleMode.FIT -> R.layout.widget_frame_fit
                    else -> R.layout.widget_frame_fill
                }
                // The launcher often re-applies an update on top of the widget it already shows
                // instead of rebuilding it, so clear the old frames first. Without this, every
                // update (Fit/Fill switch, new GIF, reinstall) piled another full set of frames on top.
                for (flipper in FLIPPERS) views.removeAllViews(flipper)
                views.removeAllViews(R.id.widget_still)

                // A slow slideshow (5 min or more per picture) shows one picture at a time; a timer
                // (SlowSlideshow) redraws it with the next one.
                val slow = slideshow && frames.frameCount > 1 && SlowSlideshow.isSlow(context, appWidgetId)
                if (frames.frameCount == 1 || slow) {
                    // A still picture: one plain ImageView, no flipper (nothing to flip).
                    val frame = if (slow) {
                        SlowSlideshow.currentFrame(context, appWidgetId, frames.frameCount, WidgetStore.getShuffle(context, appWidgetId))
                    } else {
                        0
                    }
                    val frameViews = RemoteViews(context.packageName, frameLayout)
                    frameViews.setImageViewUri(
                        R.id.frame_image,
                        FrameProvider.frameUri(context, appWidgetId, frames.version, frame)
                    )
                    views.addView(R.id.widget_still, frameViews)
                    views.setViewVisibility(R.id.widget_still, View.VISIBLE)
                    showOnlyFlipper(views, null)
                } else {
                    val order: List<Int>
                    val intervalMs: Int
                    val flipper: Int
                    if (slideshow) {
                        // A slideshow's frames are its pictures: all shown, each for "Time per picture",
                        // in a new random order every time the widget is drawn if Shuffle is on.
                        val all = (0 until frames.frameCount).toList()
                        order = if (WidgetStore.getShuffle(context, appWidgetId)) all.shuffled() else all
                        intervalMs = WidgetStore.getSlideSeconds(context, appWidgetId) * 1000
                        // Each transition is its own flipper in the layout (animations can't be set from code).
                        flipper = when (WidgetStore.getTransition(context, appWidgetId)) {
                            Transition.NONE -> R.id.widget_flipper
                            Transition.FADE -> R.id.widget_flipper_fade
                            Transition.SLIDE -> R.id.widget_flipper_slide
                        }
                    } else {
                        // Fast animations show fewer frames, spread evenly, so the launcher never flips
                        // faster than the FPS cap (see Playback). Frames left out aren't loaded at all.
                        val plan = Playback.plan(
                            frames.frameCount,
                            frames.intervalMs,
                            speed = WidgetStore.getSpeed(context, appWidgetId),
                            fpsCap = AppSettings.getFpsCap(context)
                        )
                        order = plan.frames
                        intervalMs = plan.intervalMs
                        flipper = R.id.widget_flipper
                    }
                    // Add one ImageView per shown frame to the ViewFlipper. We only send the frame's
                    // address (Uri); the launcher loads the WebP itself through FrameProvider.
                    for (index in order) {
                        val frameViews = RemoteViews(context.packageName, frameLayout)
                        frameViews.setImageViewUri(
                            R.id.frame_image,
                            FrameProvider.frameUri(context, appWidgetId, frames.version, index)
                        )
                        views.addView(flipper, frameViews)
                    }
                    views.setInt(flipper, "setFlipInterval", intervalMs)
                    showOnlyFlipper(views, flipper)
                    views.setViewVisibility(R.id.widget_still, View.GONE)
                }
                // The slow slideshow's timer: (re)set while it's slow, stopped otherwise.
                if (slow) SlowSlideshow.schedule(context, appWidgetId, WidgetStore.getSlideSeconds(context, appWidgetId))
                else SlowSlideshow.cancel(context, appWidgetId)
            }

            // Background color and opacity: the rounded shape is tinted to the color, then faded.
            val background = WidgetStore.getBackground(context, appWidgetId)
            if (frames != null && frames.masked) {
                // A shape: the background takes the shape too. Its mask (white where the shape is)
                // is tinted the same way, so changing the color never remakes frames. The launcher
                // loads the small mask file itself; no bitmap travels through RemoteViews.
                views.setViewVisibility(R.id.widget_bg, View.GONE)
                views.setImageViewUri(R.id.widget_shape_bg, FrameProvider.maskUri(context, appWidgetId, frames.version))
                views.setInt(R.id.widget_shape_bg, "setColorFilter", background.color)
                views.setInt(R.id.widget_shape_bg, "setImageAlpha", background.alpha)
                views.setViewVisibility(R.id.widget_shape_bg, View.VISIBLE)
            } else {
                // No shape: exactly the v1.0 widget. The visibilities are the layout's defaults; they're
                // set anyway because launchers re-apply updates on top of the old widget (after Heart -> None).
                views.setViewVisibility(R.id.widget_shape_bg, View.GONE)
                views.setViewVisibility(R.id.widget_bg, View.VISIBLE)
                views.setInt(R.id.widget_bg, "setColorFilter", background.color)
                views.setInt(R.id.widget_bg, "setImageAlpha", background.alpha)
            }

            // Tapping the widget opens the GIF picker for this widget.
            views.setOnClickPendingIntent(android.R.id.background, configIntent(context, appWidgetId))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, GifConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(GifConfigActivity.EXTRA_ON_HOME_SCREEN, true) // tapped on the home screen
            }
            // requestCode = widget id, so each widget gets its own PendingIntent.
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
