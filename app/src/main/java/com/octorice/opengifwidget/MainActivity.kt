package com.octorice.opengifwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.octorice.opengifwidget.config.GifConfigActivity
import com.octorice.opengifwidget.home.HomeScreen
import com.octorice.opengifwidget.settings.SettingsActivity
import com.octorice.opengifwidget.ui.theme.OpenGIFWidgetTheme
import com.octorice.opengifwidget.widget.WidgetFrames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    // Goes up by one every time the screen comes back into view, which makes it reload
    // (e.g. after changing a GIF, or adding a widget from the launcher's dialog).
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenGIFWidgetTheme {
                HomeScreen(
                    refreshKey = refreshKey,
                    onOpenWidget = ::openWidgetSettings,
                    onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Widgets made by older versions (or whose size changed while the app was closed) get
        // frames fitted to their current size, then the list refreshes with the new numbers.
        lifecycleScope.launch {
            val changed = withContext(Dispatchers.IO) { WidgetFrames.refreshAll(applicationContext) }
            if (changed > 0) refreshKey++
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKey++
    }

    /** Opens the same "Choose a GIF" screen you get when tapping the widget itself. */
    private fun openWidgetSettings(appWidgetId: Int) {
        startActivity(
            Intent(this, GifConfigActivity::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(GifConfigActivity.EXTRA_ON_HOME_SCREEN, true)
        )
    }
}
