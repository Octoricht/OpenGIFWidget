package com.octorice.opengifwidget.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit

/** What tapping a widget on the home screen does. */
enum class TapAction {
    EDIT,    // opens the editor (every widget before this setting existed)
    APP,     // opens another app, like its icon on the home screen
    NEXT,    // slow slideshows only (5 min or more per picture): shows the next picture right away
    NOTHING  // taps do nothing (the widget is edited from the app's list)
}

/** A widget's tap setting. [app] is only set (and used) with [TapAction.APP]. */
data class WidgetTap(val action: TapAction, val app: ComponentName? = null) {
    companion object {
        val EDIT = WidgetTap(TapAction.EDIT)
    }
}

/**
 * Stores the tap setting ("tap_<id>", absent = Edit, so older widgets are unchanged; "tap_app_<id>" =
 * the app as "package/activity") and finds the apps you can open.
 *
 * Seeing other apps: Android 11+ only shows an app the apps it declares it needs. The manifest's
 * <queries> asks for "apps with a home screen icon" only: no permission, nothing to allow.
 */
object WidgetTaps {

    private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /** The saved setting, exactly as stored (see [usable] for what the widget really does). */
    fun get(context: Context, appWidgetId: Int): WidgetTap {
        val prefs = prefs(context)
        val action = TapAction.entries.firstOrNull { it.name == prefs.getString("tap_$appWidgetId", null) } ?: TapAction.EDIT
        val app = prefs.getString("tap_app_$appWidgetId", null)?.let { ComponentName.unflattenFromString(it) }
        return when {
            action == TapAction.APP && app == null -> WidgetTap.EDIT // damaged: never crash, just edit
            action == TapAction.APP -> WidgetTap(action, app)
            else -> WidgetTap(action)
        }
    }

    fun save(context: Context, appWidgetId: Int, tap: WidgetTap) {
        prefs(context).edit {
            if (tap.action == TapAction.EDIT) remove("tap_$appWidgetId") else putString("tap_$appWidgetId", tap.action.name)
            val app = tap.app
            if (tap.action == TapAction.APP && app != null) putString("tap_app_$appWidgetId", app.flattenToString())
            else remove("tap_app_$appWidgetId")
        }
    }

    /**
     * What the widget really does: Edit when its app was uninstalled, or "Next picture" on a widget
     * that isn't a slow slideshow (the editor never saves that; it's only a safety net).
     * Fast slideshows can't do "Next picture": the launcher's own timer decides which picture is up.
     */
    fun usable(context: Context, appWidgetId: Int, slideshow: Boolean): WidgetTap {
        val tap = get(context, appWidgetId)
        return when (tap.action) {
            TapAction.APP -> if (tap.app != null && launchIntent(context, tap.app) != null) tap else WidgetTap.EDIT
            TapAction.NEXT -> if (slideshow && SlideTiming.isSlow(WidgetStore.getSlideSeconds(context, appWidgetId))) tap else WidgetTap.EDIT
            else -> tap
        }
    }

    /**
     * Opens [app] like its home screen icon does (back to where you left it if it's already open).
     * If the app was updated and its start screen got a new name, its new start screen is used.
     * Null when the app isn't installed anymore.
     */
    fun launchIntent(context: Context, app: ComponentName): Intent? {
        val pm = context.packageManager
        val exact = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val found = if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(exact, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(exact, 0)
        }
        if (found != null) return exact
        return pm.getLaunchIntentForPackage(app.packageName)
    }
}
