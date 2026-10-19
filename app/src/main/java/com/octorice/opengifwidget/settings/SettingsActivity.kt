package com.octorice.opengifwidget.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.BuildConfig
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.ui.theme.OpenGIFWidgetTheme
import com.octorice.opengifwidget.widget.GifWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** App settings (the FPS cap) and information about the app. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpenGIFWidgetTheme {
                SettingsScreen(onBack = ::finish)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var fpsCap by remember { mutableIntStateOf(AppSettings.getFpsCap(context)) }
    val maxFpsCap = remember { AppSettings.maxFpsCap(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.settings_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // On tablets and in landscape, keep a readable width in the middle.
                .wrapContentWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionTitle(stringResource(R.string.settings_animation))
            SettingsCard {
                Text(stringResource(R.string.settings_fps_cap), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_fps_cap_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AppSettings.FPS_CAP_OPTIONS.forEachIndexed { index, fps ->
                        SegmentedButton(
                            selected = fpsCap == fps,
                            // Caps above what this phone handles well are shown but turned off.
                            enabled = fps <= maxFpsCap,
                            onClick = {
                                fpsCap = fps
                                // Save, then redraw every widget so the new cap applies right away.
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppSettings.setFpsCap(context, fps)
                                        GifWidgetProvider.updateAll(context)
                                    }
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, AppSettings.FPS_CAP_OPTIONS.size)
                        ) { Text("$fps") }
                    }
                }
                if (maxFpsCap < AppSettings.FPS_CAP_OPTIONS.max()) {
                    Text(
                        stringResource(R.string.settings_fps_cap_device_limit, maxFpsCap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionTitle(stringResource(R.string.settings_reliability))
            SettingsCard {
                Text(stringResource(R.string.settings_battery_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_battery_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { openBatterySettings(context) }) {
                    Text(stringResource(R.string.settings_battery_button))
                }
            }

            SectionTitle(stringResource(R.string.settings_about))
            SettingsCard(horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon()
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                // Donation: opens the Ko-fi page in the browser (the app itself never goes online).
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.about_donate_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                val donateUrl = stringResource(R.string.about_donate_url)
                Button(onClick = {
                    try {
                        uriHandler.openUri(donateUrl)
                    } catch (e: Exception) {
                        // No browser on this phone: nothing to open the link with.
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_favorite), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_donate_button))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_thanks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )
        }
    }
}

/**
 * Opens the phone's list of apps and their battery optimization (no permission needed).
 * Phones without that screen get the app's own info page, which has the battery options too.
 */
private fun openBatterySettings(context: Context) {
    val batteryList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    for (intent in listOf(batteryList, appInfo)) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // This phone doesn't have that screen: try the next one.
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SettingsCard(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(20.dp)
        ) { content() }
    }
}

/**
 * The app icon, drawn from its two layers (Compose can't draw an adaptive icon directly).
 * Scaled up a little so it looks like the launcher's version (which shows the middle 72 of 108 dp).
 */
@Composable
private fun AppIcon() {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
    ) {
        for (layer in listOf(R.mipmap.ic_launcher_background, R.mipmap.ic_launcher_foreground)) {
            Image(
                painterResource(layer),
                contentDescription = null,
                // Bigger than the box on purpose: the circle crops it, like a launcher does.
                modifier = Modifier
                    .requiredSize(108.dp)
                    .align(Alignment.Center)
            )
        }
    }
}
