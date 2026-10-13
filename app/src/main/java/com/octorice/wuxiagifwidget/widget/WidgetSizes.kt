package com.octorice.wuxiagifwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context

object WidgetSizes {

    /**
     * The widget's shape on the home screen (width / height). The launcher tells each widget its
     * size in dp; in portrait, a widget is "min width" wide and "max height" tall.
     * Clamped so very thin widgets still make usable previews. 1 (square) if unknown yet.
     */
    fun aspectRatio(context: Context, appWidgetId: Int): Float {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (width <= 0 || height <= 0) return 1f
        return (width.toFloat() / height).coerceIn(0.5f, 2f)
    }
}
