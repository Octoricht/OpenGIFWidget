package com.octorice.opengifwidget.home

import com.octorice.opengifwidget.data.Transition
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.StoredGif
import com.octorice.opengifwidget.data.WidgetBackground
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.shape.Shapes
import com.octorice.opengifwidget.shape.WidgetShape
import com.octorice.opengifwidget.widget.GifWidgetProvider
import com.octorice.opengifwidget.widget.SlideshowWidgetProvider
import com.octorice.opengifwidget.widget.SlowSlideshow
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
    val shape: WidgetShape?, // the shape the widget's frames are cut into (null = none)
    val zoomed: Boolean,     // made with a custom zoom (the frames already have the widget's shape)
    val slideshow: Boolean,  // a slideshow: its frames are its pictures
    val slowSlideshow: Boolean, // 5 min or more per picture: only one picture is shown at a time
    val transition: Transition, // a slideshow's change from one picture to the next
    val framesDir: File?,    // the widget's own frames, played as the card's preview
    val aspectRatio: Float,  // width / height of the widget on the home screen
    val storageBytes: Long
)

object HomeData {

    /** Reads every widget on the home screen. Touches files, so call it off the main thread. */
    fun loadWidgets(context: Context): List<WidgetSummary> {
        val ids = GifWidgetProvider.allWidgetIds(context)
        return ids.sorted().filterNot { WidgetStore.isHidden(context, it) }.map { id ->
            val slideshow = GifWidgetProvider.isSlideshow(context, id)
            // A slideshow's time per picture is applied when drawing, so its card (and the flips-per-second
            // estimate) use it too, instead of the time stored with the frames.
            val gif = WidgetStore.getGif(context, id)?.let { stored ->
                val frames = stored.frames
                if (slideshow && frames != null) {
                    stored.copy(frames = frames.copy(intervalMs = WidgetStore.getSlideSeconds(context, id) * 1000))
                } else {
                    stored
                }
            }
            WidgetSummary(
                appWidgetId = id,
                gif = gif,
                name = WidgetStore.displayName(context, id, gif),
                scaleMode = WidgetStore.getScaleMode(context, id),
                speed = WidgetStore.getSpeed(context, id),
                background = WidgetStore.getBackground(context, id),
                // (Slideshow frames are always boxed, but that's their Fit / Fill, not a custom zoom.)
                zoomed = gif?.frames?.boxed == true && !slideshow,
                slideshow = slideshow,
                slowSlideshow = SlowSlideshow.isSlow(context, id),
                transition = WidgetStore.getTransition(context, id),
                shape = if (gif?.frames?.masked == true) Shapes.byId(context, WidgetStore.getFramesShapeId(context, id)) else null,
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

    /**
     * True if the launcher can place a widget for us (it shows its own "Add" dialog).
     * [slideshow] picks which of the two widgets in the launcher's list gets placed.
     */
    fun requestPinWidget(context: Context, slideshow: Boolean): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = if (slideshow) SlideshowWidgetProvider::class.java else GifWidgetProvider::class.java
        return manager.requestPinAppWidget(ComponentName(context, provider), null, null)
    }
}
