package com.octorice.opengifwidget.home

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.StoredGif
import com.octorice.opengifwidget.data.WidgetBackground
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.widget.GifWidgetProvider
import com.octorice.opengifwidget.widget.WidgetSizes
import java.io.File

/** Everything the home screen shows about one widget. */
data class WidgetSummary(
    val appWidgetId: Int,
    val gif: StoredGif?,
    val name: String,        // the widget's name ("" if no picture yet)
    val scaleMode: ScaleMode,
    val speed: Float,
    val background: WidgetBackground,
    val framesDir: File?,    // the widget's own frames, played as the card's preview
    val aspectRatio: Float,  // width / height of the widget on the home screen
    val storageBytes: Long
)

object HomeData {

    /** Reads every widget on the home screen. Touches files, so call it off the main thread. */
    fun loadWidgets(context: Context): List<WidgetSummary> {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, GifWidgetProvider::class.java))
        return ids.sorted().filterNot { WidgetStore.isHidden(context, it) }.map { id ->
            val gif = WidgetStore.getGif(context, id)
            WidgetSummary(
                appWidgetId = id,
                gif = gif,
                name = WidgetStore.displayName(context, id, gif),
                scaleMode = WidgetStore.getScaleMode(context, id),
                speed = WidgetStore.getSpeed(context, id),
                background = WidgetStore.getBackground(context, id),
                framesDir = gif?.frames?.let { GifStorage.framesDir(context, id, it.version) },
                aspectRatio = WidgetSizes.aspectRatio(context, id),
                storageBytes = GifStorage.widgetStorageBytes(context, id)
            )
        }
    }

    /**
     * Removes a widget from the app's list and deletes its files and settings. Apps can't take a
     * widget off the home screen; if this one is actually there, it simply shows
     * "Tap to choose a GIF" again (and reappears in the list once set up again).
     */
    fun removeFromList(context: Context, appWidgetId: Int) {
        WidgetStore.remove(context, appWidgetId)
        GifStorage.deleteWidgetFiles(context, appWidgetId)
        WidgetStore.setHidden(context, appWidgetId, true)
        GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    /** True if the launcher can place a widget for us (it shows its own "Add" dialog). */
    fun requestPinWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        return manager.requestPinAppWidget(ComponentName(context, GifWidgetProvider::class.java), null, null)
    }
}
