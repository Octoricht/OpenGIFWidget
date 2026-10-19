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
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.provider.FrameProvider

/**
 * The home screen widget. Android calls these methods; we answer by sending
 * RemoteViews (a description of the layout) to the launcher, which draws it.
 */
class GifWidgetProvider : AppWidgetProvider() {

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
        val allIds = appWidgetManager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))
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
            WidgetStore.remove(context, appWidgetId)
            GifStorage.deleteWidgetFiles(context, appWidgetId)
        }
    }

    /** The last widget was removed: nothing should be left, so clear everything. */
    override fun onDisabled(context: Context) {
        WidgetStore.removeAllExcept(context, emptySet())
        GifStorage.deleteAllExcept(context, emptySet())
    }

    companion object {
        /** Redraws every widget, e.g. after the FPS cap changed in Settings. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            for (id in manager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))) {
                updateWidget(context, manager, id)
            }
        }

        /** Builds the look of one widget and sends it to the launcher. */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_gif)
            val gif = WidgetStore.getGif(context, appWidgetId)
            val frames = gif?.frames

            if (frames == null) {
                // No frames yet: show the placeholder and a hint.
                val label = if (gif == null) {
                    context.getString(R.string.widget_tap_to_choose)
                } else {
                    context.getString(R.string.widget_tap_to_repick)
                }
                views.setTextViewText(R.id.widget_label, label)
            } else {
                views.setViewVisibility(R.id.widget_placeholder, View.GONE)
                views.setViewVisibility(R.id.widget_label, View.GONE)

                // Fit and Fill are two tiny layouts that differ only in the ImageView's scaleType.
                val frameLayout = when (WidgetStore.getScaleMode(context, appWidgetId)) {
                    ScaleMode.FIT -> R.layout.widget_frame_fit
                    ScaleMode.FILL -> R.layout.widget_frame_fill
                }
                // The launcher often re-applies an update on top of the widget it already shows
                // instead of rebuilding it, so clear the old frames first. Without this, every
                // update (Fit/Fill switch, new GIF, reinstall) piled another full set of frames on top.
                views.removeAllViews(R.id.widget_flipper)
                views.removeAllViews(R.id.widget_still)

                if (frames.frameCount == 1) {
                    // A still picture: one plain ImageView, no flipper (nothing to flip).
                    val frameViews = RemoteViews(context.packageName, frameLayout)
                    frameViews.setImageViewUri(
                        R.id.frame_image,
                        FrameProvider.frameUri(context, appWidgetId, frames.version, 0)
                    )
                    views.addView(R.id.widget_still, frameViews)
                    views.setViewVisibility(R.id.widget_still, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_flipper, View.GONE)
                } else {
                    // Fast animations show fewer frames, spread evenly, so the launcher never flips
                    // faster than the FPS cap (see Playback). Frames left out aren't loaded at all.
                    val plan = Playback.plan(
                        frames.frameCount,
                        frames.intervalMs,
                        speed = WidgetStore.getSpeed(context, appWidgetId),
                        fpsCap = AppSettings.getFpsCap(context)
                    )
                    // Add one ImageView per shown frame to the ViewFlipper. We only send the frame's
                    // address (Uri); the launcher loads the WebP itself through FrameProvider.
                    for (index in plan.frames) {
                        val frameViews = RemoteViews(context.packageName, frameLayout)
                        frameViews.setImageViewUri(
                            R.id.frame_image,
                            FrameProvider.frameUri(context, appWidgetId, frames.version, index)
                        )
                        views.addView(R.id.widget_flipper, frameViews)
                    }
                    views.setInt(R.id.widget_flipper, "setFlipInterval", plan.intervalMs)
                    views.setViewVisibility(R.id.widget_flipper, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_still, View.GONE)
                }
            }

            // Background color and opacity: the rounded shape is tinted to the color, then faded.
            val background = WidgetStore.getBackground(context, appWidgetId)
            views.setInt(R.id.widget_bg, "setColorFilter", background.color)
            views.setInt(R.id.widget_bg, "setImageAlpha", background.alpha)

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
