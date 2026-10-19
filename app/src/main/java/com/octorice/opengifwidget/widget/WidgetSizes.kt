package com.octorice.opengifwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.octorice.opengifwidget.R
import kotlin.math.max
import kotlin.math.roundToInt

object WidgetSizes {

    /**
     * The widget's size on the home screen right now, in dp (width to height), or null if unknown yet.
     * The launcher gives two sizes: in portrait a widget is "min width" wide and "max height" tall,
     * in landscape "max width" wide and "min height" tall. Phone home screens stay in portrait
     * (even if this app is rotated), so only tablets and other big screens use the landscape size.
     */
    private fun currentSizeDp(context: Context, appWidgetId: Int): Pair<Int, Int>? {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val configuration = context.resources.configuration
        val landscape = configuration.smallestScreenWidthDp >= 600 &&
            configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val width = options.getInt(
            if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
        )
        val height = options.getInt(
            if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
        )
        return if (width > 0 && height > 0) width to height else null
    }

    /**
     * The widget's shape on the home screen (width / height). Clamped so very thin widgets
     * still make usable previews. 1 (square) if unknown yet.
     */
    fun aspectRatio(context: Context, appWidgetId: Int): Float {
        val (width, height) = currentSizeDp(context, appWidgetId) ?: return 1f
        return (width.toFloat() / height).coerceIn(0.5f, 2f)
    }

    /**
     * Android 12+ rounds every widget's corners by itself. Older versions don't, so a Fill picture
     * covers the corners and they look square: there, the corners can be cut into the frames.
     */
    val needsRoundedFrames: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    /**
     * The widget's corner radius as a fraction of its width, to cut the same corners into
     * frames made for it. 0 if the launcher hasn't told us the size yet.
     */
    fun cornerRadiusFraction(context: Context, appWidgetId: Int): Float {
        val widthDp = currentSizeDp(context, appWidgetId)?.first ?: return 0f
        val resources = context.resources
        val radiusDp = resources.getDimension(R.dimen.widget_corner_radius) / resources.displayMetrics.density
        return (radiusDp / widthDp).coerceAtMost(0.5f)
    }

    /**
     * The widget's longest side on screen, in pixels: the biggest it gets in portrait or
     * landscape. Frames bigger than this would only waste memory. Null if the launcher
     * hasn't told us the size yet.
     */
    fun maxSidePx(context: Context, appWidgetId: Int): Int? {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val maxWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val maxHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (maxWidthDp <= 0 && maxHeightDp <= 0) return null
        return (max(maxWidthDp, maxHeightDp) * context.resources.displayMetrics.density).roundToInt()
    }
}
