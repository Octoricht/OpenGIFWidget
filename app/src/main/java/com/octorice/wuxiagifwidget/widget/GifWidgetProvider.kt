package com.octorice.wuxiagifwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.widget.RemoteViews
import com.octorice.wuxiagifwidget.R
import com.octorice.wuxiagifwidget.config.GifConfigActivity
import com.octorice.wuxiagifwidget.data.GifStorage
import com.octorice.wuxiagifwidget.data.WidgetStore

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
    }

    companion object {
        /** Builds the look of one widget and sends it to the launcher. */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_gif)

            // For now the widget shows the first frame and some info (animation comes later).
            val gif = WidgetStore.getGif(context, appWidgetId)
            val frames = gif?.frames
            val label = when {
                gif == null -> context.getString(R.string.widget_tap_to_choose)
                frames == null -> "${gif.name}\n${Formatter.formatShortFileSize(context, gif.sizeBytes)}"
                else -> "${gif.name}\n${frames.frameCount} frames · ${frames.intervalMs} ms"
            }
            views.setTextViewText(R.id.widget_label, label)

            if (frames != null) {
                // Temporary: send the first frame as a small bitmap. Bitmaps sent this way must
                // stay small (there's a ~1 MB limit), which is why Milestone 4 switches to Uris.
                val firstFrame = GifStorage.frameFile(context, appWidgetId, frames.version, 0)
                GifStorage.loadFrame(firstFrame, maxSidePx = 256)?.let {
                    views.setImageViewBitmap(R.id.widget_image, it)
                }
            }

            // Tapping the widget opens the GIF picker for this widget.
            views.setOnClickPendingIntent(android.R.id.background, configIntent(context, appWidgetId))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, GifConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
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
