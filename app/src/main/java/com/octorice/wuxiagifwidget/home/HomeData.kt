package com.octorice.wuxiagifwidget.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.octorice.wuxiagifwidget.data.GifStorage
import com.octorice.wuxiagifwidget.data.ScaleMode
import com.octorice.wuxiagifwidget.data.StoredGif
import com.octorice.wuxiagifwidget.data.WidgetStore
import com.octorice.wuxiagifwidget.widget.GifWidgetProvider
import com.octorice.wuxiagifwidget.widget.WidgetSizes
import java.io.File

/** Everything the home screen shows about one widget. */
data class WidgetSummary(
    val appWidgetId: Int,
    val gif: StoredGif?,
    val scaleMode: ScaleMode,
    val cropCenter: Pair<Float, Float>, // where Fill mode is centered (0..1 on each axis)
    val gifFile: File?,      // the original GIF, used for the animated preview
    val aspectRatio: Float,  // width / height of the widget on the home screen
    val storageBytes: Long
)

object HomeData {

    /** Reads every widget on the home screen. Touches files, so call it off the main thread. */
    fun loadWidgets(context: Context): List<WidgetSummary> {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))
        return ids.sorted().map { id ->
            val gif = WidgetStore.getGif(context, id)
            WidgetSummary(
                appWidgetId = id,
                gif = gif,
                scaleMode = WidgetStore.getScaleMode(context, id),
                cropCenter = WidgetStore.getCropCenter(context, id),
                gifFile = GifStorage.gifFile(context, id).takeIf { gif?.frames != null && it.isFile },
                aspectRatio = WidgetSizes.aspectRatio(context, id),
                storageBytes = GifStorage.widgetStorageBytes(context, id)
            )
        }
    }

    /** True if the launcher can place a widget for us (it shows its own "Add" dialog). */
    fun requestPinWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, GifWidgetProvider::class.java), null, null)
    }
}
