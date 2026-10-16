package com.octorice.opengifwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ImportedFile
import com.octorice.opengifwidget.data.ImportException
import com.octorice.opengifwidget.data.WidgetBackground
import com.octorice.opengifwidget.data.friendlyErrors
import com.octorice.opengifwidget.ui.BackgroundPicker
import com.octorice.opengifwidget.ui.friendlyMessage
import com.octorice.opengifwidget.ui.toComposeColor
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.StoredGif
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.gif.Crop
import com.octorice.opengifwidget.gif.FrameExtractor
import com.octorice.opengifwidget.gif.FrameSet
import com.octorice.opengifwidget.gif.VideoSource
import com.octorice.opengifwidget.widget.Playback
import com.octorice.opengifwidget.ui.FrameImage
import com.octorice.opengifwidget.ui.fillPlacement
import com.octorice.opengifwidget.ui.rememberFramePlayer
import com.octorice.opengifwidget.ui.theme.OpenGIFWidgetTheme
import com.octorice.opengifwidget.widget.GifWidgetProvider
import com.octorice.opengifwidget.widget.WidgetSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.NumberFormat
import kotlin.math.roundToInt

/**
 * Opens when a widget is added (the launcher starts it because of android:configure),
 * when you tap an existing widget, and from the app's home screen.
 *
 * It works like an editor:
 * 1. Pick a file: it's decoded once into master frames (uncropped), which the preview plays.
 * 2. Choose Fit or Fill (and, for Fill, drag to choose what stays in view).
 * 3. Save: the widget's own frames are made from the master frames and the widget updates.
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
        // Frames prepared last time but never saved are no longer wanted.
        if (savedInstanceState == null) discardUnsavedFrames()

        val (cropX, cropY) = WidgetStore.getCropCenter(this, appWidgetId)
        val gif = WidgetStore.getGif(this, appWidgetId)
        val saved = SavedSettings(
            gif = gif,
            settings = EditorSettings(
                name = WidgetStore.displayName(this, appWidgetId, gif),
                scaleMode = WidgetStore.getScaleMode(this, appWidgetId),
                cropCenter = Offset(cropX, cropY),
                background = WidgetStore.getBackground(this, appWidgetId),
                speed = WidgetStore.getSpeed(this, appWidgetId)
            )
        )

        enableEdgeToEdge()
        setContent {
            OpenGIFWidgetTheme {
                EditorScreen(
                    saved = saved,
                    widgetAspectRatio = WidgetSizes.aspectRatio(this, appWidgetId),
                    fpsCap = AppSettings.getFpsCap(this),
                    framesDir = { version -> GifStorage.framesDir(this, appWidgetId, version) },
                    prepare = ::prepare,
                    openVideo = ::openVideo,
                    videoThumbnail = ::videoThumbnail,
                    prepareVideo = ::prepareVideo,
                    save = ::save,
                    onClose = ::finish,
                    onSaved = ::finishWithSuccess
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) discardUnsavedFrames()
    }

    /** Deletes frame folders the widget doesn't use (e.g. from a file picked but not saved). */
    private fun discardUnsavedFrames(keepVersion: Long? = null) {
        val keep = WidgetStore.usedFrameVersions(this, appWidgetId) + setOfNotNull(keepVersion)
        GifStorage.deleteFramesExcept(this, appWidgetId, keep)
        GifStorage.importCopyFile(this, appWidgetId).delete()
    }

    /**
     * Copies a picked file and decodes it once into master frames, for the preview and for Save.
     * The copy is deleted right after: the master frames replace it.
     */
    /** Checks and opens a picked video for the trim dialog. Close it when done. */
    private suspend fun openVideo(uri: Uri): OpenedVideo = withContext(Dispatchers.IO) {
        friendlyErrors {
            val file = GifStorage.checkVideo(applicationContext, uri)
            OpenedVideo(VideoSource(applicationContext, uri), uri, file)
        }
    }

    /** The video's frame at [timeMs], small, for the trim dialog's thumbnail (null if none). */
    private suspend fun videoThumbnail(video: OpenedVideo, timeMs: Long): Bitmap? =
        withContext(Dispatchers.IO) {
            video.lock.withLock {
                try {
                    video.source.frameAt(timeMs, 720)
                } catch (e: Exception) {
                    null // A thumbnail is just nice to have.
                }
            }
        }

    /**
     * Takes frames from the chosen clip into master frames, at the FPS cap. Can be cancelled:
     * then (or on any error) the frames made so far are deleted.
     */
    private suspend fun prepareVideo(
        video: OpenedVideo,
        startMs: Long,
        lengthMs: Long,
        onProgress: (Int, Int) -> Unit
    ): StoredGif = withContext(Dispatchers.IO) {
        val context = applicationContext
        val version = System.currentTimeMillis()
        val outDir = GifStorage.framesDir(context, appWidgetId, version)
        // The trim dialog's reader isn't needed anymore; the workers open their own.
        video.lock.withLock { video.source.close() }
        friendlyErrors(cleanUp = { outDir.deleteRecursively() }) {
            val master = FrameExtractor.extractMasterFromVideo(
                openVideo = { VideoSource(context, video.uri) },
                outDir = outDir,
                version = version,
                startMs = startMs,
                lengthMs = lengthMs,
                fps = AppSettings.getFpsCap(context),
                onProgress = onProgress
            )
            discardUnsavedFrames(keepVersion = version)
            StoredGif(video.file.name, video.file.sizeBytes, master = master)
        }
    }

    private suspend fun prepare(uri: Uri, onProgress: (Int, Int) -> Unit): StoredGif =
        withContext(Dispatchers.IO) {
            val context = applicationContext
            val copy = GifStorage.importCopyFile(context, appWidgetId)
            val version = System.currentTimeMillis()
            val outDir = GifStorage.framesDir(context, appWidgetId, version)
            try {
                // Any failure (damaged file, full storage, ...) becomes a friendly message,
                // and half-finished frames are deleted.
                friendlyErrors(cleanUp = { outDir.deleteRecursively() }) {
                    val imported = GifStorage.importFile(context, uri, appWidgetId)
                    val master = if (imported.isGif) {
                        FrameExtractor.extractMasterFromGif(copy, outDir, version, onProgress)
                    } else {
                        FrameExtractor.extractMasterFromImage(copy, outDir, version)
                    }
                    // Only one unsaved pick is kept: drop frames from an earlier pick.
                    discardUnsavedFrames(keepVersion = version)
                    StoredGif(imported.name, imported.sizeBytes, master = master)
                }
            } finally {
                copy.delete() // The master frames replace the original.
            }
        }

    /**
     * Makes the widget's frames from the master frames (cropped for Fill), remembers
     * everything, and refreshes the widget. [gif] is the newly picked file or the current one.
     */
    private suspend fun save(
        gif: StoredGif,
        settings: EditorSettings,
        remakeFrames: Boolean,
        aspectRatio: Float,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val context = applicationContext
        val master = gif.master ?: throw ImportException(R.string.error_frames_missing)
        var frames = gif.frames
        if (remakeFrames || frames == null) {
            val crop = if (settings.scaleMode == ScaleMode.FILL) {
                Crop(settings.cropCenter.x, settings.cropCenter.y, aspectRatio)
            } else {
                null
            }
            // Each save gets a new folder, so old and new frames never mix.
            val version = System.currentTimeMillis()
            val outDir = GifStorage.framesDir(context, appWidgetId, version)
            frames = friendlyErrors(cleanUp = { outDir.deleteRecursively() }) {
                FrameExtractor.deriveWidgetFrames(
                    master, GifStorage.framesDir(context, appWidgetId, master.version), outDir, version, crop, onProgress
                )
            }
        }

        WidgetStore.saveGif(context, appWidgetId, gif.copy(frames = frames, master = master))
        WidgetStore.saveScaleMode(context, appWidgetId, settings.scaleMode)
        WidgetStore.saveCropCenter(context, appWidgetId, settings.cropCenter.x, settings.cropCenter.y)
        WidgetStore.saveBackground(context, appWidgetId, settings.background)
        WidgetStore.saveSpeed(context, appWidgetId, settings.speed)
        // An empty name falls back to the file's name.
        WidgetStore.saveName(context, appWidgetId, settings.name.trim().ifEmpty { WidgetStore.defaultName(gif.name) })
        WidgetStore.setHidden(context, appWidgetId, false) // set up again: show it in the app's list
        GifStorage.deleteFramesExcept(context, appWidgetId, setOf(master.version, frames.version))
        GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK, resultIntent())
        finish()
    }

    private fun resultIntent() =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

/** Everything you can change on this screen besides the picture itself. */
private data class EditorSettings(
    val name: String,
    val scaleMode: ScaleMode,
    val cropCenter: Offset,
    val background: WidgetBackground,
    val speed: Float
)

/** What the widget uses right now, to know whether anything changed. */
private data class SavedSettings(val gif: StoredGif?, val settings: EditorSettings)

/** Long enough for a real name, short enough to show on a card. */
private const val MAX_NAME_LENGTH = 40

/** What the screen is busy with, if anything (done/total frames, 0/0 = just started). */
private sealed interface Busy {
    data object OpeningVideo : Busy
    data class Preparing(val done: Int, val total: Int, val video: Boolean = false) : Busy
    data class Saving(val done: Int, val total: Int) : Busy
}

/** A picked video, open while you choose a clip. [lock]: the reader handles one request at a time. */
private class OpenedVideo(val source: VideoSource, val uri: Uri, val file: ImportedFile) {
    val lock = Mutex()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    saved: SavedSettings,
    widgetAspectRatio: Float,
    fpsCap: Int,
    framesDir: (version: Long) -> File,
    prepare: suspend (Uri, (Int, Int) -> Unit) -> StoredGif,
    openVideo: suspend (Uri) -> OpenedVideo,
    videoThumbnail: suspend (OpenedVideo, Long) -> Bitmap?,
    prepareVideo: suspend (OpenedVideo, Long, Long, (Int, Int) -> Unit) -> StoredGif,
    save: suspend (StoredGif, EditorSettings, Boolean, Float, (Int, Int) -> Unit) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<StoredGif?>(null) } // newly picked, not saved yet
    var scaleMode by remember { mutableStateOf(saved.settings.scaleMode) }
    var cropCenter by remember { mutableStateOf(saved.settings.cropCenter) }
    var background by remember { mutableStateOf(saved.settings.background) }
    var name by remember { mutableStateOf(saved.settings.name) }
    var speed by remember { mutableFloatStateOf(saved.settings.speed) }
    // Once you type your own name, picking another file doesn't overwrite it.
    var nameEdited by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<Busy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var trimming by remember { mutableStateOf<OpenedVideo?>(null) } // video waiting for its clip
    var videoJob by remember { mutableStateOf<Job?>(null) } // running frame extraction (cancellable)

    // The preview plays the master frames: of the new pick, or of what the widget uses now.
    val shownGif = picked ?: saved.gif
    val master = shownGif?.master
    val image = rememberFramePlayer(master?.let { framesDir(it.version) }, master, maxSidePx = 1080, speed, fpsCap)

    // Frames only need remaking for a new picture, Fit/Fill or the crop; not for the background.
    val remakeFrames = picked != null ||
        saved.gif?.frames == null ||
        scaleMode != saved.settings.scaleMode ||
        (scaleMode == ScaleMode.FILL && cropCenter != saved.settings.cropCenter)
    // Speed, name and background are just settings: saving them doesn't remake frames.
    val hasChanges = remakeFrames ||
        background != saved.settings.background ||
        name != saved.settings.name ||
        speed != saved.settings.speed

    // Don't leave halfway through preparing or saving. While taking frames from a video,
    // Back cancels instead (like the Cancel button).
    BackHandler(enabled = busy != null) { videoJob?.cancel() }

    // A new pick is shown with a fresh, centered crop and (unless you typed one) its own name.
    fun usePicked(newGif: StoredGif) {
        picked = newGif
        cropCenter = Offset(0.5f, 0.5f)
        if (!nameEdited) name = WidgetStore.defaultName(newGif.name)
    }

    fun onVideoPicked(uri: Uri) {
        scope.launch {
            busy = Busy.OpeningVideo
            error = null
            try {
                trimming = openVideo(uri)
            } catch (e: IOException) {
                error = e.friendlyMessage(context)
            } finally {
                busy = null
            }
        }
    }

    fun onClipChosen(video: OpenedVideo, startMs: Long, lengthMs: Long) {
        trimming = null
        videoJob = scope.launch {
            busy = Busy.Preparing(0, 0, video = true)
            error = null
            try {
                usePicked(prepareVideo(video, startMs, lengthMs) { done, total ->
                    busy = Busy.Preparing(done, total, video = true)
                })
            } catch (e: IOException) {
                error = e.friendlyMessage(context)
            } finally {
                // Cancelled or not, the video isn't needed anymore.
                video.source.close()
                busy = null
                videoJob = null
            }
        }
    }

    fun onPicked(uri: Uri?) {
        if (uri == null) return // You closed the picker without choosing.
        if (GifStorage.isVideo(context, uri)) {
            onVideoPicked(uri)
            return
        }
        scope.launch {
            busy = Busy.Preparing(0, 0)
            error = null
            try {
                usePicked(prepare(uri) { done, total -> busy = Busy.Preparing(done, total) })
            } catch (e: IOException) {
                error = e.friendlyMessage(context)
            } finally {
                busy = null
            }
        }
    }

    fun onSave() {
        val gif = shownGif ?: return
        if (!hasChanges) {
            onSaved()
            return
        }
        scope.launch {
            busy = Busy.Saving(0, 0)
            error = null
            try {
                val settings = EditorSettings(name, scaleMode, cropCenter, background, speed)
                save(gif, settings, remakeFrames, widgetAspectRatio) { done, total ->
                    busy = Busy.Saving(done, total)
                }
                onSaved()
            } catch (e: IOException) {
                error = e.friendlyMessage(context)
                busy = null
            }
        }
    }

    // The system photo picker, showing pictures, GIFs and videos.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { onPicked(it) }
    // The system file browser, for files that aren't in the gallery (e.g. Downloads, Drive).
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { onPicked(it) }

    trimming?.let { video ->
        TrimDialog(
            info = video.source.info,
            fpsCap = fpsCap,
            loadThumbnail = { timeMs -> videoThumbnail(video, timeMs) },
            onCancel = {
                trimming = null
                // Wait for a thumbnail that may still be loading before closing the video.
                scope.launch(Dispatchers.IO) { video.lock.withLock { video.source.close() } }
            },
            onConfirm = { startMs, lengthMs -> onClipChosen(video, startMs, lengthMs) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (saved.gif == null) "New GIF widget" else "Edit widget") },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = busy == null) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Close")
                    }
                }
            )
        },
        bottomBar = {
            SaveBar(
                busy = busy,
                enabled = master != null && busy == null,
                label = if (saved.gif == null) "Add to home screen" else "Save",
                onSave = ::onSave,
                onCancel = { videoJob?.cancel() }
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
                image = image,
                background = background,
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
                    master == null -> shownGif.name
                    else -> "${shownGif.name} · " +
                        (if (master.frameCount == 1) "${master.width}×${master.height}" else "${master.sourceFrameCount} frames") +
                        " · " + Formatter.formatShortFileSize(context, shownGif.sizeBytes)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (shownGif != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(MAX_NAME_LENGTH)
                        nameEdited = true
                    },
                    label = { Text(stringResource(R.string.editor_name)) },
                    placeholder = { Text(WidgetStore.defaultName(shownGif.name)) },
                    singleLine = true,
                    enabled = busy == null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionTitle(if (shownGif == null) "Pick a GIF" else "Change GIF")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo // pictures, GIFs, videos
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
                    onClick = { filePicker.launch(arrayOf("image/*", "video/*")) },
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

            // Speed only matters for animations, not still pictures.
            if (master != null && master.frameCount > 1) {
                Spacer(Modifier.height(24.dp))
                SectionTitle(stringResource(R.string.editor_speed))
                SpeedPicker(speed, master, fpsCap, enabled = busy == null, onChange = { speed = it })
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.editor_background))
            BackgroundPicker(background, enabled = busy == null, onChange = { background = it })

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
 * The widget-shaped preview. In Fill mode you can drag it to move the picture around;
 * a crop grid fades in while your finger is down.
 */
@Composable
private fun Preview(
    image: ImageBitmap?,
    background: WidgetBackground,
    scaleMode: ScaleMode,
    aspectRatio: Float,
    cropCenter: () -> Offset,
    onCropCenterChange: (Offset) -> Unit,
    dragEnabled: Boolean
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val gridAlpha by animateFloatAsState(if (dragging) 1f else 0f, label = "grid")
    val canDrag = image != null && scaleMode == ScaleMode.FILL && dragEnabled
    // Only the picture's shape matters for dragging; the frames all have the same one.
    val imageSize = image?.let { IntSize(it.width, it.height) }

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // As wide as the screen allows, but never taller than 380dp (for tall widgets).
        val width = min(maxWidth, 380.dp * aspectRatio)
        Box(
            modifier = Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(24.dp))
                .background(background.toComposeColor())
                .onSizeChanged { boxSize = it }
                .pointerInput(canDrag, imageSize, boxSize) {
                    if (!canDrag || imageSize == null) return@pointerInput
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, amount ->
                        change.consume()
                        // Moving your finger right moves the picture right, so the center moves left.
                        val spot = fillPlacement(
                            boxSize.width, boxSize.height, imageSize.width, imageSize.height, cropCenter()
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
            // Fades in when the first frame is ready; frames then simply replace each other.
            AnimatedContent(
                targetState = image != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "preview"
            ) { hasImage ->
                if (hasImage && image != null) {
                    FrameImage(image, scaleMode, cropCenter, Modifier.fillMaxSize())
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

/**
 * Speed slider (0.25x to 3x, in steps of 0.05) with a Reset button, and how fast the widget
 * will really flip with this speed and the FPS cap.
 */
@Composable
private fun SpeedPicker(speed: Float, frames: FrameSet, fpsCap: Int, enabled: Boolean, onChange: (Float) -> Unit) {
    val plan = remember(speed, frames, fpsCap) { Playback.plan(frames.frameCount, frames.intervalMs, speed, fpsCap) }
    val fps = (1000f / plan.intervalMs).roundToInt()
    // "1.25" or "1,25" depending on the phone's language.
    val speedText = remember(speed) {
        NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(speed)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(
                stringResource(R.string.editor_speed_value, speedText),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(visible = speed != 1f) {
                TextButton(onClick = { onChange(1f) }, enabled = enabled) {
                    Text(stringResource(R.string.editor_speed_reset))
                }
            }
        }
        Slider(
            value = speed,
            onValueChange = { onChange(((it * 20).roundToInt() / 20f).coerceIn(WidgetStore.MIN_SPEED, WidgetStore.MAX_SPEED)) },
            valueRange = WidgetStore.MIN_SPEED..WidgetStore.MAX_SPEED,
            enabled = enabled
        )
        Text(
            stringResource(R.string.editor_speed_fps, fps, fpsCap),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The bottom bar: the Save button, or progress while preparing/saving. */
@Composable
private fun SaveBar(busy: Busy?, enabled: Boolean, label: String, onSave: () -> Unit, onCancel: () -> Unit) {
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

                Busy.OpeningVideo -> Progress(stringResource(R.string.progress_opening_video), 0, 0)
                is Busy.Preparing -> if (state.video) {
                    Progress(stringResource(R.string.progress_reading_video), state.done, state.total, onCancel)
                } else {
                    Progress("Reading the GIF…", state.done, state.total)
                }
                is Busy.Saving -> Progress("Preparing the widget…", state.done, state.total)
            }
        }
    }
}

@Composable
private fun Progress(text: String, done: Int, total: Int, onCancel: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            Text(if (total == 0) text else "$text $done / $total", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (total == 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (onCancel != null) {
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onCancel) { Text(stringResource(R.string.progress_cancel)) }
        }
    }
}
