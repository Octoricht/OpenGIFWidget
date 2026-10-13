package com.octorice.wuxiagifwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.octorice.wuxiagifwidget.R
import com.octorice.wuxiagifwidget.data.GifStorage
import com.octorice.wuxiagifwidget.data.ScaleMode
import com.octorice.wuxiagifwidget.data.StoredGif
import com.octorice.wuxiagifwidget.data.WidgetStore
import com.octorice.wuxiagifwidget.gif.Crop
import com.octorice.wuxiagifwidget.gif.FrameExtractor
import com.octorice.wuxiagifwidget.ui.GifView
import com.octorice.wuxiagifwidget.ui.fillPlacement
import com.octorice.wuxiagifwidget.ui.rememberGifDrawable
import com.octorice.wuxiagifwidget.ui.theme.WuxiaGIFWidgetTheme
import com.octorice.wuxiagifwidget.widget.GifWidgetProvider
import com.octorice.wuxiagifwidget.widget.WidgetSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.io.IOException

/**
 * Opens when a widget is added (the launcher starts it because of android:configure),
 * when you tap an existing widget, and from the app's home screen.
 *
 * It works like an editor: pick a GIF, choose Fit or Fill (and, for Fill, drag to choose
 * what stays in view), then Save. Saving cuts the GIF into frames and updates the widget.
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
        // A GIF picked last time but never saved is no longer wanted.
        if (savedInstanceState == null) GifStorage.deletePendingGif(this, appWidgetId)

        val (cropX, cropY) = WidgetStore.getCropCenter(this, appWidgetId)
        val saved = SavedSettings(
            gif = WidgetStore.getGif(this, appWidgetId),
            scaleMode = WidgetStore.getScaleMode(this, appWidgetId),
            cropCenter = Offset(cropX, cropY)
        )

        enableEdgeToEdge()
        setContent {
            WuxiaGIFWidgetTheme {
                EditorScreen(
                    saved = saved,
                    widgetAspectRatio = WidgetSizes.aspectRatio(this, appWidgetId),
                    savedGifFile = { GifStorage.gifFile(this, appWidgetId).takeIf { it.isFile } },
                    pendingGifFile = { GifStorage.pendingGifFile(this, appWidgetId) },
                    importGif = ::importGif,
                    save = ::save,
                    onClose = ::finish,
                    onSaved = ::finishWithSuccess
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) GifStorage.deletePendingGif(this, appWidgetId)
    }

    /** Copies a picked GIF into the pending file, for previewing before Save. */
    private suspend fun importGif(uri: Uri): StoredGif = withContext(Dispatchers.IO) {
        GifStorage.importGif(applicationContext, uri, appWidgetId)
    }

    /**
     * Cuts the GIF into frames (cropped for Fill), remembers everything, and refreshes
     * the widget. [pendingGif] is a newly picked GIF, or null to keep the current one.
     */
    private suspend fun save(
        pendingGif: StoredGif?,
        scaleMode: ScaleMode,
        cropCenter: Offset,
        aspectRatio: Float,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val context = applicationContext
        val source = if (pendingGif != null) {
            GifStorage.pendingGifFile(context, appWidgetId)
        } else {
            GifStorage.gifFile(context, appWidgetId)
        }
        val crop = if (scaleMode == ScaleMode.FILL) Crop(cropCenter.x, cropCenter.y, aspectRatio) else null

        // Each save gets a new folder, so old and new frames never mix.
        val version = System.currentTimeMillis()
        val outDir = GifStorage.framesDir(context, appWidgetId, version)
        val frames = try {
            FrameExtractor.extract(source, outDir, version, crop, onProgress)
        } catch (e: IOException) {
            outDir.deleteRecursively() // Don't leave half-finished frames behind.
            throw e
        }

        // Only now that the frames worked does the new GIF replace the old one.
        if (pendingGif != null) GifStorage.commitPendingGif(context, appWidgetId)
        val info = pendingGif ?: WidgetStore.getGif(context, appWidgetId) ?: StoredGif("GIF", source.length())
        WidgetStore.saveGif(context, appWidgetId, info.copy(frames = frames))
        WidgetStore.saveScaleMode(context, appWidgetId, scaleMode)
        WidgetStore.saveCropCenter(context, appWidgetId, cropCenter.x, cropCenter.y)
        GifStorage.deleteOldFrames(context, appWidgetId, keepVersion = version)
        GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

/** What the widget uses right now, to know whether anything changed. */
private data class SavedSettings(val gif: StoredGif?, val scaleMode: ScaleMode, val cropCenter: Offset)

/** What the screen is busy with, if anything. */
private sealed interface Busy {
    data object Copying : Busy
    data class Saving(val done: Int, val total: Int) : Busy
}

/** Same dark color as the widget's own background, so the preview looks like the real widget. */
private val WidgetBackground = Color(0xCC202020)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    saved: SavedSettings,
    widgetAspectRatio: Float,
    savedGifFile: () -> File?,
    pendingGifFile: () -> File,
    importGif: suspend (Uri) -> StoredGif,
    save: suspend (StoredGif?, ScaleMode, Offset, Float, (Int, Int) -> Unit) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingGif by remember { mutableStateOf<StoredGif?>(null) } // newly picked, not saved yet
    var scaleMode by remember { mutableStateOf(saved.scaleMode) }
    var cropCenter by remember { mutableStateOf(saved.cropCenter) }
    var busy by remember { mutableStateOf<Busy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // The GIF being previewed: the newly picked one, or the one the widget uses now.
    val previewFile = remember(pendingGif) { if (pendingGif != null) pendingGifFile() else savedGifFile() }
    val drawable = rememberGifDrawable(previewFile)
    val shownGif = pendingGif ?: saved.gif

    val hasChanges = pendingGif != null ||
        saved.gif?.frames == null ||
        scaleMode != saved.scaleMode ||
        (scaleMode == ScaleMode.FILL && cropCenter != saved.cropCenter)

    // Don't leave halfway through saving.
    BackHandler(enabled = busy is Busy.Saving) {}

    fun onPicked(uri: Uri?) {
        if (uri == null) return // You closed the picker without choosing.
        scope.launch {
            busy = Busy.Copying
            error = null
            try {
                pendingGif = importGif(uri)
                cropCenter = Offset(0.5f, 0.5f) // New GIF: start centered.
            } catch (e: IOException) {
                error = e.message ?: "Couldn't read that GIF"
            } finally {
                busy = null
            }
        }
    }

    fun onSave() {
        if (!hasChanges) {
            onSaved()
            return
        }
        scope.launch {
            busy = Busy.Saving(0, 0)
            error = null
            try {
                save(pendingGif, scaleMode, cropCenter, widgetAspectRatio) { done, total ->
                    busy = Busy.Saving(done, total)
                }
                onSaved()
            } catch (e: IOException) {
                error = e.message ?: "Couldn't prepare that GIF"
                busy = null
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (saved.gif == null) "New GIF widget" else "Edit widget") },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = busy !is Busy.Saving) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                    }
                }
            )
        },
        bottomBar = {
            SaveBar(
                busy = busy,
                enabled = drawable != null && busy == null,
                label = if (saved.gif == null) "Add to home screen" else "Save",
                onSave = ::onSave
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Preview(
                drawable = drawable,
                scaleMode = scaleMode,
                aspectRatio = widgetAspectRatio,
                cropCenter = { cropCenter },
                onCropCenterChange = { cropCenter = it },
                dragEnabled = busy == null
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = when {
                    shownGif == null -> "No GIF yet"
                    drawable == null -> shownGif.name
                    else -> "${shownGif.name.substringBeforeLast('.')} · " +
                        "${drawable.intrinsicWidth}×${drawable.intrinsicHeight} · " +
                        "${drawable.numberOfFrames} frames · " +
                        Formatter.formatShortFileSize(context, shownGif.sizeBytes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle(if (shownGif == null) "Pick a GIF" else "Change GIF")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif")
                            )
                        )
                    },
                    enabled = busy == null,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(painterResource(R.drawable.ic_photo_library), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("image/gif")) },
                    enabled = busy == null,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Files")
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("Display")
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ScaleMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = scaleMode == mode,
                        onClick = { scaleMode = mode },
                        enabled = busy == null,
                        shape = SegmentedButtonDefaults.itemShape(index, ScaleMode.entries.size)
                    ) {
                        Text(if (mode == ScaleMode.FIT) "Fit" else "Fill")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (scaleMode == ScaleMode.FIT) {
                        "Shows the whole GIF, with empty space around it if needed."
                    } else {
                        "Covers the whole widget. Drag the preview to choose what stays in view."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                AnimatedVisibility(visible = scaleMode == ScaleMode.FILL && cropCenter != Offset(0.5f, 0.5f)) {
                    TextButton(onClick = { cropCenter = Offset(0.5f, 0.5f) }, enabled = busy == null) {
                        Icon(painterResource(R.drawable.ic_recenter), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Re-center")
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    )
}

/**
 * The widget-shaped preview. In Fill mode you can drag it to move the GIF around;
 * a crop grid fades in while your finger is down.
 */
@Composable
private fun Preview(
    drawable: GifDrawable?,
    scaleMode: ScaleMode,
    aspectRatio: Float,
    cropCenter: () -> Offset,
    onCropCenterChange: (Offset) -> Unit,
    dragEnabled: Boolean
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val gridAlpha by animateFloatAsState(if (dragging) 1f else 0f, label = "grid")
    val canDrag = drawable != null && scaleMode == ScaleMode.FILL && dragEnabled

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // As wide as the screen allows, but never taller than 380dp (for tall widgets).
        val width = min(maxWidth, 380.dp * aspectRatio)
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(24.dp))
                .background(WidgetBackground)
                .onSizeChanged { boxSize = it }
                .pointerInput(canDrag, drawable, boxSize) {
                    if (!canDrag || drawable == null) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, amount ->
                        change.consume()
                        // Moving your finger right moves the GIF right, so the center moves left.
                        val spot = fillPlacement(
                            boxSize.width, boxSize.height,
                            drawable.intrinsicWidth, drawable.intrinsicHeight, cropCenter()
                        )
                        val halfX = boxSize.width / 2f / spot.width
                        val halfY = boxSize.height / 2f / spot.height
                        val current = cropCenter()
                        onCropCenterChange(
                            Offset(
                                (current.x - amount.x / spot.width).coerceIn(halfX, 1f - halfX),
                                (current.y - amount.y / spot.height).coerceIn(halfY, 1f - halfY)
                            )
                        )
                    }
                }
        ) {
            AnimatedContent(
                targetState = drawable,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "preview"
            ) { shown ->
                if (shown != null) {
                    GifView(shown, scaleMode, cropCenter, Modifier.fillMaxSize())
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Image(painterResource(R.drawable.widget_placeholder), null, Modifier.size(56.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Your GIF will play here", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // Rule-of-thirds grid, like a photo crop tool, shown while dragging.
            if (gridAlpha > 0f) {
                Canvas(Modifier.fillMaxSize()) {
                    val color = Color.White.copy(alpha = 0.6f * gridAlpha)
                    for (i in 1..2) {
                        val x = size.width * i / 3
                        val y = size.height * i / 3
                        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                }
            }

            // Hint shown in Fill mode until you start dragging.
            AnimatedVisibility(
                visible = canDrag && !dragging,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f),
                    contentColor = Color.White
                ) {
                    Text(
                        "Drag to reposition",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** The bottom bar: the Save button, or progress while copying/saving. */
@Composable
private fun SaveBar(busy: Busy?, enabled: Boolean, label: String, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        // Only animate when switching between button and progress, not on every progress tick.
        AnimatedContent(
            targetState = busy,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            contentKey = { it?.javaClass },
            label = "save bar",
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) { state ->
            when (state) {
                null -> Button(
                    onClick = onSave,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) { Text(label, style = MaterialTheme.typography.titleSmall) }

                Busy.Copying -> Progress("Copying GIF…", null)

                is Busy.Saving -> Progress(
                    if (state.total == 0) "Preparing frames…" else "Preparing frames… ${state.done} / ${state.total}",
                    if (state.total == 0) null else state.done / state.total.toFloat()
                )
            }
        }
    }
}

@Composable
private fun Progress(text: String, fraction: Float?) {
    Column(
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        if (fraction == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
    }
}
