package com.octorice.opengifwidget.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.octorice.opengifwidget.data.WidgetTaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** An app you can open from a widget: its start screen, its name and its icon. */
data class InstalledApp(val component: ComponentName, val label: String, val icon: ImageBitmap)

/**
 * The apps with a home screen icon (the only ones this app can see, see the manifest's <queries>),
 * read on a background thread. Icons are small bitmaps: big enough for a list row, cheap in RAM.
 */
object InstalledApps {

    private const val ICON_PX = 108

    // A few icons for the cards and the editor's row, so they aren't read again on every screen.
    private val cache = HashMap<ComponentName, InstalledApp?>()

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    /** Every app with a home screen icon, A to Z (the way the phone's language sorts). */
    suspend fun all(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val found = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(launcherIntent(), PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent(), 0)
        }
        val collator = Collator.getInstance()
        found.mapNotNull { info -> read(pm, info) }
            .distinctBy { it.component }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    /** One app's name and icon, or null if it isn't installed anymore. */
    suspend fun get(context: Context, component: ComponentName): InstalledApp? = withContext(Dispatchers.IO) {
        synchronized(cache) { if (cache.containsKey(component)) return@withContext cache[component] }
        val pm = context.packageManager
        // The same start screen the widget opens (it follows an app that renamed it in an update).
        val launch = WidgetTaps.launchIntent(context, component)
        val info = launch?.let {
            if (Build.VERSION.SDK_INT >= 33) pm.resolveActivity(it, PackageManager.ResolveInfoFlags.of(0))
            else @Suppress("DEPRECATION") pm.resolveActivity(it, 0)
        }
        val app = info?.let { read(pm, it) }?.copy(component = component)
        synchronized(cache) { cache[component] = app }
        app
    }

    private fun read(pm: PackageManager, info: ResolveInfo): InstalledApp? = try {
        val activity = info.activityInfo ?: return null
        InstalledApp(
            component = ComponentName(activity.packageName, activity.name),
            label = info.loadLabel(pm).toString().trim().ifEmpty { activity.packageName },
            icon = info.loadIcon(pm).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
        )
    } catch (e: Exception) {
        null // an app that can't be read is left out, never a crash
    }
}

/** [component]'s name and icon, loaded in the background (null while loading, or if it's gone). */
@Composable
fun rememberInstalledApp(component: ComponentName?): InstalledApp? {
    val context = LocalContext.current
    return produceState<InstalledApp?>(null, component) {
        value = component?.let { InstalledApps.get(context.applicationContext, it) }
    }.value
}
