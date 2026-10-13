package com.octorice.wuxiagifwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.octorice.wuxiagifwidget.config.GifConfigActivity
import com.octorice.wuxiagifwidget.home.HomeScreen
import com.octorice.wuxiagifwidget.ui.theme.WuxiaGIFWidgetTheme

class MainActivity : ComponentActivity() {

    // Goes up by one every time the screen comes back into view, which makes it reload
    // (e.g. after changing a GIF, or adding a widget from the launcher's dialog).
    private var refreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WuxiaGIFWidgetTheme {
                HomeScreen(refreshKey = refreshKey, onOpenWidget = ::openWidgetSettings)
            }
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
        )
    }
}
