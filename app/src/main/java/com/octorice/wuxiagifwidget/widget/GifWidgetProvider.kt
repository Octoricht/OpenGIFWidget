package com.octorice.wuxiagifwidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.octorice.wuxiagifwidget.R

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
            val views = RemoteViews(context.packageName, R.layout.widget_gif)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
