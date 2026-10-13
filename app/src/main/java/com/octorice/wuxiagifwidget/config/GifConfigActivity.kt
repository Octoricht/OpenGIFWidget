package com.octorice.wuxiagifwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.octorice.wuxiagifwidget.data.GifStorage
import com.octorice.wuxiagifwidget.data.ScaleMode
import com.octorice.wuxiagifwidget.data.StoredGif
import com.octorice.wuxiagifwidget.data.WidgetStore
import com.octorice.wuxiagifwidget.gif.FrameExtractor
import com.octorice.wuxiagifwidget.ui.theme.WuxiaGIFWidgetTheme
import com.octorice.wuxiagifwidget.widget.GifWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Opens when a widget is added (the launcher starts it because of android:configure),
 * and when you tap an existing widget. Lets you pick the GIF for that widget.
 */
class GifConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Default answer is "cancelled": if you press back, the launcher won't add the widget.
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            WuxiaGIFWidgetTheme {
                ConfigScreen(
                    initialGif = WidgetStore.getGif(this, appWidgetId),
                    initialScaleMode = WidgetStore.getScaleMode(this, appWidgetId),
                    onScaleModeChange = ::changeScaleMode,
                    importGif = ::importGif,
                    loadThumbnails = ::loadThumbnails,
                    onDone = ::finishWithSuccess
                )
            }
        }
    }

    /**
     * Copies the GIF, splits it into PNG frames, remembers it,
     * and refreshes the widget so it shows the first frame.
     */
    private suspend fun importGif(uri: Uri, onProgress: (Int, Int) -> Unit): StoredGif =
        withContext(Dispatchers.IO) {
            val context = applicationContext
            val copied = GifStorage.importGif(context, uri, appWidgetId)

            // Each pick gets a new folder, so old and new frames never mix.
            val version = System.currentTimeMillis()
            val outDir = GifStorage.framesDir(context, appWidgetId, version)
            val frames = try {
                FrameExtractor.extract(GifStorage.gifFile(context, appWidgetId), outDir, version, onProgress)
            } catch (e: IOException) {
                outDir.deleteRecursively() // Don't leave half-finished frames behind.
                throw e
            }

            val gif = copied.copy(frames = frames)
            WidgetStore.saveGif(context, appWidgetId, gif)
            GifStorage.deleteOldFrames(context, appWidgetId, keepVersion = version)
            GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
            gif
        }

    /** Small previews of every saved frame, for the strip on screen. */
    private suspend fun loadThumbnails(gif: StoredGif): List<ImageBitmap> =
        withContext(Dispatchers.IO) {
            val frames = gif.frames ?: return@withContext emptyList()
            (0 until frames.frameCount).mapNotNull { index ->
                val file = GifStorage.frameFile(applicationContext, appWidgetId, frames.version, index)
                GifStorage.loadFrame(file, maxSidePx = 96)?.asImageBitmap()
            }
        }

    /** Saves Fit/Fill for this widget and redraws it right away. */
    private fun changeScaleMode(mode: ScaleMode) {
        WidgetStore.saveScaleMode(this, appWidgetId, mode)
        GifWidgetProvider.updateWidget(this, AppWidgetManager.getInstance(this), appWidgetId)
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(
    initialGif: StoredGif?,
    initialScaleMode: ScaleMode,
    onScaleModeChange: (ScaleMode) -> Unit,
    importGif: suspend (Uri, (Int, Int) -> Unit) -> StoredGif,
    loadThumbnails: suspend (StoredGif) -> List<ImageBitmap>,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gif by remember { mutableStateOf(initialGif) }
    var scaleMode by remember { mutableStateOf(initialScaleMode) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) } // (done, total) frames
    var error by remember { mutableStateOf<String?>(null) }
    var thumbnails by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }

    // Whenever the GIF changes, reload the preview strip.
    LaunchedEffect(gif) {
        thumbnails = gif?.let { loadThumbnails(it) } ?: emptyList()
    }

    fun onPicked(uri: Uri?) {
        if (uri == null) return // You closed the picker without choosing.
        scope.launch {
            busy = true
            error = null
            progress = null
            try {
                gif = importGif(uri) { done, total -> progress = done to total }
            } catch (e: IOException) {
                error = e.message ?: "Couldn't read that GIF"
            } finally {
                busy = false
                progress = null
            }
        }
    }

    // The system photo picker, showing only GIFs.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { onPicked(it) }
    // The system file browser, for GIFs that aren't in the gallery (e.g. Downloads, Drive).
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { onPicked(it) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Choose a GIF", style = MaterialTheme.typography.headlineMedium)
            Text("This GIF will play in your widget.")

            Button(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")
                        )
                    )
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pick from gallery") }

            OutlinedButton(
                onClick = { filePicker.launch(arrayOf("image/gif")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Browse files") }

            // Fit or Fill, saved separately for every widget.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ScaleMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = scaleMode == mode,
                        onClick = {
                            scaleMode = mode
                            onScaleModeChange(mode)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, ScaleMode.entries.size)
                    ) {
                        Text(if (mode == ScaleMode.FIT) "Fit" else "Fill")
                    }
                }
            }
            Text(
                if (scaleMode == ScaleMode.FIT) {
                    "Fit: shows the whole GIF, with empty space around it if needed."
                } else {
                    "Fill: covers the whole widget, cutting off the edges that don't fit."
                },
                style = MaterialTheme.typography.bodySmall
            )

            if (busy) {
                val (done, total) = progress ?: (0 to 0)
                Text(if (total == 0) "Copying…" else "Processing frames… $done / $total")
                if (total == 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { done / total.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            gif?.let { current ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(current.name, style = MaterialTheme.typography.titleMedium)
                        Text(Formatter.formatShortFileSize(context, current.sizeBytes))
                        current.frames?.let { frames ->
                            Text("${frames.frameCount} frames · avg ${frames.intervalMs} ms · ${frames.width}×${frames.height}")
                            if (frames.frameCount < frames.sourceFrameCount) {
                                Text(
                                    "Long GIF: kept ${frames.frameCount} of ${frames.sourceFrameCount} frames",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } ?: Text("Pick this GIF again to prepare its frames.")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(thumbnails) { thumbnail ->
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onDone,
                enabled = gif?.frames != null && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done") }
        }
    }
}
