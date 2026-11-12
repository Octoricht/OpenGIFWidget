package com.octorice.opengifwidget.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.content.Context
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.res.pluralStringResource
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
import com.octorice.opengifwidget.data.DeviceProfile
import com.octorice.opengifwidget.data.GifStorage
import com.octorice.opengifwidget.data.ImportedFile
import com.octorice.opengifwidget.data.ImportException
import com.octorice.opengifwidget.data.WidgetBackground
import com.octorice.opengifwidget.data.friendlyErrors
import com.octorice.opengifwidget.ui.BackgroundPicker
import com.octorice.opengifwidget.ui.ShapePicker
import com.octorice.opengifwidget.gif.Zoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.max
import kotlin.math.exp
import kotlin.math.ln
import com.octorice.opengifwidget.ui.ShapedBox
import com.octorice.opengifwidget.shape.WidgetShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.asComposePath
import com.octorice.opengifwidget.ui.friendlyMessage
import com.octorice.opengifwidget.ui.toComposeColor
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.StoredGif
import com.octorice.opengifwidget.data.WidgetStore
import com.octorice.opengifwidget.gif.FrameExtractor
import com.octorice.opengifwidget.gif.FrameLimits
import com.octorice.opengifwidget.gif.FrameSet
import com.octorice.opengifwidget.gif.VideoSource
import com.octorice.opengifwidget.widget.Playback
import com.octorice.opengifwidget.ui.FrameImage
import com.octorice.opengifwidget.ui.fillPlacement
import com.octorice.opengifwidget.ui.rememberFramePlayer
import com.octorice.opengifwidget.ui.theme.OpenGIFWidgetTheme
import com.octorice.opengifwidget.widget.GifWidgetProvider
import com.octorice.opengifwidget.widget.WidgetFrames
import com.octorice.opengifwidget.widget.WidgetSizes
import com.octorice.opengifwidget.home.UsageHealth
import com.octorice.opengifwidget.gif.Crop
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.Switch
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
 * 2. Choose Fit or Fill (and, for Fill, "Reposition" to choose what stays in view).
 * 3. Save: the widget's own frames are made from the master frames and the widget updates.
 */
class GifConfigActivity : ComponentActivity() {

    companion object {
        /**
         * Set when the widget is already on the home screen (opened by tapping it, or from the app's list).
         * Not set when the launcher opens this while you're adding a widget.
         */
        const val EXTRA_ON_HOME_SCREEN = "com.octorice.opengifwidget.ON_HOME_SCREEN"
    }

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var savedSuccessfully = false // true once Save / "Add to home screen" succeeded
    // Already placed (e.g. Pixel places pinned widgets without opening this): the button says "Save".
    private var onHomeScreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        onHomeScreen = intent?.getBooleanExtra(EXTRA_ON_HOME_SCREEN, false) == true

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
                roundCorners = WidgetStore.getRoundCorners(this, appWidgetId),
                shape = WidgetStore.getShape(this, appWidgetId),
                zoom = WidgetStore.getZoom(this, appWidgetId),
                background = WidgetStore.getBackground(this, appWidgetId),
                speed = WidgetStore.getSpeed(this, appWidgetId)
            )
        )

        enableEdgeToEdge()
        setContent {
            OpenGIFWidgetTheme {
                EditorScreen(
                    saved = saved,
                    onHomeScreen = onHomeScreen,
                    widgetAspectRatio = WidgetSizes.aspectRatio(this, appWidgetId),
                    fpsCap = AppSettings.getFpsCap(this),
                    outputSidePx = ::outputSidePx,
                    shapeForWidget = { shape -> WidgetSizes.shapeFor(this, appWidgetId, shape) },
                    framesDir = { version -> GifStorage.framesDir(this, appWidgetId, version) },
                    prepare = ::prepare,
                    openVideo = ::openVideo,
                    videoThumbnail = ::videoThumbnail,
                    prepareVideo = ::prepareVideo,
                    save = ::save,
                    onClose = {
                        // Delete an unsaved pick right away, so the app's list never counts it
                        // (onDestroy also does this, but only after the list has already reloaded).
                        discardUnsavedFrames()
                        finish()
                    },
                    onSaved = ::finishWithSuccess
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            discardUnsavedFrames()
            // Setup cancelled before the widget ever got a picture: the launcher should now delete
            // this widget, but some (e.g. OnePlus) keep it as an invisible leftover. Hide it from
            // the app's list; saving a picture for it later shows it again.
            // (A widget that's already on the home screen is real, so it stays in the list.)
            if (!savedSuccessfully && !onHomeScreen && WidgetStore.getGif(this, appWidgetId)?.frames == null) {
                WidgetStore.setHidden(this, appWidgetId, true)
            }
        }
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
                limits = FrameLimits.forDevice(context),
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
                        FrameExtractor.extractMasterFromGif(copy, outDir, version, FrameLimits.forDevice(context), onProgress)
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
            // Never fill the device's storage to the brim with new frames.
            if (GifStorage.freeStorageBytes(context) < UsageHealth.VERY_LOW_STORAGE_BYTES) {
                throw ImportException(R.string.error_no_space)
            }
            // Remembered so a rejected save (below) leaves everything as it was.
            val oldFit = WidgetStore.getFramesFit(context, appWidgetId)
            val oldRounded = WidgetStore.getFramesRounded(context, appWidgetId)
            val oldShapeId = WidgetStore.getFramesShapeId(context, appWidgetId)
            val oldZoom = WidgetStore.getFramesZoom(context, appWidgetId)
            // Sized and shaped for the widget as it is on the home screen right now.
            val made = WidgetFrames.make(
                context, appWidgetId, master, settings.scaleMode,
                settings.cropCenter.x to settings.cropCenter.y, settings.roundCorners, settings.shape, settings.zoom, onProgress
            )
            try {
                checkMemoryLimit(context, made, settings.speed)
            } catch (e: ImportException) {
                GifStorage.framesDir(context, appWidgetId, made.version).deleteRecursively()
                WidgetStore.saveFramesFit(context, appWidgetId, oldFit.first, oldFit.second, oldRounded, oldShapeId, oldZoom)
                throw e
            }
            frames = made
        } else {
            checkMemoryLimit(context, frames, settings.speed) // e.g. a slower speed shows more frames
        }

        WidgetStore.saveGif(context, appWidgetId, gif.copy(frames = frames, master = master))
        WidgetStore.saveScaleMode(context, appWidgetId, settings.scaleMode)
        WidgetStore.saveCropCenter(context, appWidgetId, settings.cropCenter.x, settings.cropCenter.y)
        WidgetStore.saveRoundCorners(context, appWidgetId, settings.roundCorners)
        WidgetStore.saveShape(context, appWidgetId, settings.shape)
        WidgetStore.saveZoom(context, appWidgetId, settings.zoom)
        WidgetStore.saveBackground(context, appWidgetId, settings.background)
        WidgetStore.saveSpeed(context, appWidgetId, settings.speed)
        // An empty name falls back to the file's name.
        WidgetStore.saveName(context, appWidgetId, settings.name.trim().ifEmpty { WidgetStore.defaultName(gif.name) })
        WidgetStore.setHidden(context, appWidgetId, false) // set up again: show it in the app's list
        GifStorage.deleteFramesExcept(context, appWidgetId, setOf(master.version, frames.version))
        GifWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    /**
     * Refuses a save that would push the home screen past the memory limit ([UsageHealth.memoryLimitBytes]).
     * A change that makes this widget lighter (or no heavier) is always allowed, even when already over:
     * widgets you have are never blocked from getting lighter.
     */
    private fun checkMemoryLimit(context: Context, frames: FrameSet, speed: Float) {
        val fpsCap = AppSettings.getFpsCap(context)
        val newCost = UsageHealth.widgetCost(frames, speed, fpsCap).first
        val oldCost = WidgetStore.getGif(context, appWidgetId)?.frames
            ?.let { UsageHealth.widgetCost(it, WidgetStore.getSpeed(context, appWidgetId), fpsCap).first } ?: 0L
        if (newCost <= oldCost) return
        val limit = UsageHealth.memoryLimitBytes(context)
        val total = UsageHealth.memoryOfOthers(context, appWidgetId) + newCost
        if (total > limit) {
            throw ImportException(
                R.string.error_home_screen_full,
                Formatter.formatShortFileSize(context, total),
                Formatter.formatShortFileSize(context, limit)
            )
        }
    }

    /**
     * About how big (longest side, px) this widget's frames will really be, for the zoom-in limit:
     * the widget's (or shape box's) size on screen, at most the phone's frame size limit, and
     * smaller again when there are many frames (the RAM budget shares its pixels between them,
     * the same way deriveWidgetFrames does).
     */
    private fun outputSidePx(shape: WidgetShape?, frameCount: Int, boxAspect: Float): Int {
        val limits = FrameLimits.forDevice(this)
        val limit = if (frameCount == 1) limits.stillMaxSidePx else limits.animationMaxSidePx
        val widget = if (shape != null) {
            WidgetSizes.shapeMaxSidePx(this, appWidgetId, shape.aspect)
        } else {
            WidgetSizes.maxSidePx(this, appWidgetId)
        }
        val layers = frameCount + (if (shape != null) 1 else 0) // a shape's background counts as a frame
        val shortToLong = kotlin.math.min(boxAspect, 1f / boxAspect)
        val budget = kotlin.math.sqrt(limits.maxTotalPixels / layers.coerceAtLeast(1).toDouble() / shortToLong).toInt()
        return minOf(if (widget != null && widget > 0) minOf(limit, widget) else limit, budget)
    }

    private fun finishWithSuccess() {
        savedSuccessfully = true
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
    val roundCorners: Boolean, // Android 8-11 only: cut rounded corners into Fill pictures
    val shape: WidgetShape?,   // cookie-cutter shape, null = none
    val zoom: Float?,          // custom zoom (1 = Fill), null = the Fit / Fill preset in scaleMode
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
    onHomeScreen: Boolean,
    widgetAspectRatio: Float,
    fpsCap: Int,
    outputSidePx: (shape: WidgetShape?, frameCount: Int, boxAspect: Float) -> Int,
    shapeForWidget: (WidgetShape?) -> WidgetShape?,
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
    var roundCorners by remember { mutableStateOf(saved.settings.roundCorners) }
    var shape by remember { mutableStateOf(saved.settings.shape) }
    // The first time you pick a shape here, Reposition opens by itself (only once per visit).
    var shapeRepositionShown by remember { mutableStateOf(false) }
    // Opens Reposition on the next frame, once the preview knows the new shape's box.
    var openRepositionSoon by remember { mutableStateOf(false) }
    // Fill: the picture is locked until you tap "Reposition" (no accidental moves). Cancel goes back here.
    var repositioning by remember { mutableStateOf(false) }
    var cropBeforeReposition by remember { mutableStateOf(cropCenter) }
    // Your own zoom (1 = Fill), or null while a Fit / Fill preset is used. Cancel goes back to these.
    var customZoom by remember { mutableStateOf(saved.settings.zoom) }
    var zoomBeforeReposition by remember { mutableStateOf(customZoom) }
    var modeBeforeReposition by remember { mutableStateOf(scaleMode) }
    var background by remember { mutableStateOf(saved.settings.background) }
    var name by remember { mutableStateOf(saved.settings.name) }
    var speed by remember { mutableFloatStateOf(saved.settings.speed) }
    // Once you type your own name, picking another file doesn't overwrite it.
    var nameEdited by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<Busy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    // Errors are shown at the bottom of the page: scroll there so they're never missed.
    LaunchedEffect(error) {
        if (error != null) scrollState.animateScrollTo(scrollState.maxValue)
    }
    var trimming by remember { mutableStateOf<OpenedVideo?>(null) } // video waiting for its clip
    var videoJob by remember { mutableStateOf<Job?>(null) } // running frame extraction (cancellable)

    // The preview plays the master frames: of the new pick, or of what the widget uses now.
    val shownGif = picked ?: saved.gif
    val master = shownGif?.master
    val image = rememberFramePlayer(master?.let { framesDir(it.version) }, master, maxSidePx = 1080, speed, fpsCap)

    // Zoom: the box is the shape's box, or the whole widget without a shape. Fit and Fill are two
    // points on the same scale (Fill = 1), so the preview always uses the zoom math (Zoom.box).
    // The picked shape as drawn at this widget's size (Dots: as many dots as fit). Previews,
    // Reposition and the zoom limits use this one; the picked shape is what gets saved.
    val drawnShape = shapeForWidget(shape)
    val boxAspect = drawnShape?.aspect ?: widgetAspectRatio
    val fitZoom = master?.let { Zoom.fitZoom(it.width.toFloat(), it.height.toFloat(), boxAspect) } ?: 1f
    val minZoom = fitZoom * Zoom.MIN_OF_FIT
    val maxZoom = master?.let {
        Zoom.maxZoom(it.width.toFloat(), it.height.toFloat(), boxAspect, outputSidePx(drawnShape, it.frameCount, boxAspect))
    } ?: 1f
    val zoom = customZoom ?: if (scaleMode == ScaleMode.FIT) fitZoom else 1f
    // A Fit preset is always centered (as before zoom existed).
    val shownCenter = if (customZoom == null && scaleMode == ScaleMode.FIT) Offset(0.5f, 0.5f) else cropCenter
    val centered = kotlin.math.abs(shownCenter.x - 0.5f) < 0.002f && kotlin.math.abs(shownCenter.y - 0.5f) < 0.002f
    // Which preset button is lit: none when the zoom is anywhere else.
    val preset = when {
        customZoom == null -> scaleMode
        customZoom == 1f -> ScaleMode.FILL
        customZoom == fitZoom && centered -> ScaleMode.FIT
        else -> null
    }

    // Frames only need remaking for a new picture, Fit/Fill or the crop; not for the background.
    // These are functions (not values worked out while drawing), so the Save button always gets the
    // live answer: it once kept an old "nothing changed" and closed without saving a new zoom.
    fun remakeFrames() = picked != null ||
        saved.gif?.frames == null ||
        customZoom != saved.settings.zoom ||
        (customZoom == null && scaleMode != saved.settings.scaleMode) ||
        ((scaleMode == ScaleMode.FILL || customZoom != null) && cropCenter != saved.settings.cropCenter) ||
        shape != saved.settings.shape ||
        (shape == null && WidgetFrames.cutsCorners(scaleMode, roundCorners, customZoom) !=
            (saved.settings.shape == null &&
                WidgetFrames.cutsCorners(saved.settings.scaleMode, saved.settings.roundCorners, saved.settings.zoom)))
    // Speed, name and background are just settings: saving them doesn't remake frames.
    fun hasChanges() = remakeFrames() ||
        background != saved.settings.background ||
        name != saved.settings.name ||
        speed != saved.settings.speed

    // Don't leave halfway through preparing or saving. While taking frames from a video,
    // Back cancels instead (like the Cancel button).
    BackHandler(enabled = busy != null) { videoJob?.cancel() }

    // Buttons can hold on to an older copy of these two functions, so they never use values worked out
    // while drawing the screen (those can be old): they read the live state, plus the newest fitZoom.
    val latestFitZoom by rememberUpdatedState(fitZoom)

    fun startReposition() {
        val fit = latestFitZoom
        val usesFitPreset = customZoom == null && scaleMode == ScaleMode.FIT
        cropBeforeReposition = cropCenter
        zoomBeforeReposition = customZoom
        modeBeforeReposition = scaleMode
        // While repositioning the zoom is always a number; Done turns it back into a preset if it is one.
        if (usesFitPreset) cropCenter = Offset(0.5f, 0.5f) // a Fit preset is always centered
        customZoom = customZoom ?: if (usesFitPreset) fit else 1f
        repositioning = true
    }

    fun endReposition(keep: Boolean) {
        if (keep) {
            val fit = latestFitZoom
            val isCentered = kotlin.math.abs(cropCenter.x - 0.5f) < 0.002f && kotlin.math.abs(cropCenter.y - 0.5f) < 0.002f
            when {
                customZoom == 1f -> { scaleMode = ScaleMode.FILL; customZoom = null }
                customZoom == fit && isCentered -> { scaleMode = ScaleMode.FIT; customZoom = null }
                else -> Unit // your own zoom: kept as a number
            }
        } else {
            cropCenter = cropBeforeReposition
            customZoom = zoomBeforeReposition
            scaleMode = modeBeforeReposition
        }
        repositioning = false
    }
    LaunchedEffect(openRepositionSoon) {
        if (openRepositionSoon) {
            openRepositionSoon = false
            startReposition()
        }
    }
    // Back while repositioning = Cancel.
    BackHandler(enabled = repositioning && busy == null) { endReposition(keep = false) }

    // A new pick is shown with a fresh, centered crop and (unless you typed one) its own name.
    fun usePicked(newGif: StoredGif) {
        picked = newGif
        repositioning = false
        cropCenter = Offset(0.5f, 0.5f)
        customZoom = null // a new picture starts from the Fit / Fill preset
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
        val gif = picked ?: saved.gif ?: return
        if (!hasChanges()) {
            onSaved()
            return
        }
        scope.launch {
            busy = Busy.Saving(0, 0)
            error = null
            try {
                val settings = EditorSettings(name, scaleMode, cropCenter, roundCorners, shape, customZoom, background, speed)
                save(gif, settings, remakeFrames(), widgetAspectRatio) { done, total ->
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
            uri = video.uri,
            info = video.source.info,
            fpsCap = fpsCap,
            maxClipMs = remember { DeviceProfile.tier(context).maxClipMs },
            loadFrame = { timeMs -> videoThumbnail(video, timeMs) },
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
                title = { Text(stringResource(if (saved.gif == null) R.string.editor_title_new else R.string.editor_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = busy == null) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.editor_close))
                    }
                },
                // Shorter than the usual 64dp: more room for the settings under the pinned preview.
                expandedHeight = 48.dp
            )
        },
        bottomBar = {
            SaveBar(
                busy = busy,
                // Finish repositioning first (Done or Cancel), so a half-done move is never saved.
                enabled = master != null && busy == null && !repositioning,
                // "Add to home screen" only while adding a widget; once it's placed, it's "Save".
                label = stringResource(if (saved.gif == null && !onHomeScreen) R.string.editor_add else R.string.editor_save),
                onSave = { onSave() },
                onCancel = { videoJob?.cancel() }
            )
        }
    ) { innerPadding ->
        // The preview stays pinned at the top (always visible); the settings scroll underneath it.
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            // At most 40% of the screen's height (and 380dp), so the settings keep room, even in landscape.
            val previewMaxHeight = min(380.dp, maxHeight * 0.4f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // On tablets and in landscape, keep a readable width in the middle.
                    .wrapContentWidth()
                    .widthIn(max = 640.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 4.dp)) {
                    Preview(
                        maxPreviewHeight = previewMaxHeight,
                        image = image,
                        background = background,
                        scaleMode = scaleMode,
                        shape = drawnShape,
                        aspectRatio = widgetAspectRatio,
                        cropCenter = { shownCenter },
                        zoom = { zoom },
                        fitZoom = fitZoom,
                        minZoom = minZoom,
                        maxZoom = maxZoom,
                        onZoomChange = { newZoom, newCenter ->
                            customZoom = newZoom
                            cropCenter = newCenter
                        },
                        repositioning = repositioning,
                        canReposition = image != null && busy == null,
                        onStartReposition = { startReposition() },
                        onEndReposition = { keep -> endReposition(keep) },
                        // Shows the corners the widget will really have (square on Android 8-11 when switched off).
                        squareCorners = shape == null && WidgetSizes.needsRoundedFrames &&
                            (scaleMode == ScaleMode.FILL || customZoom != null) && !roundCorners
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = when {
                            shownGif == null -> stringResource(R.string.editor_nothing_picked)
                            master == null -> shownGif.name
                            master.frameCount == 1 -> stringResource(
                                R.string.editor_info_still,
                                shownGif.name, master.width, master.height,
                                Formatter.formatShortFileSize(context, shownGif.sizeBytes)
                            )
                            else -> pluralStringResource(
                                R.plurals.editor_info_frames,
                                master.sourceFrameCount,
                                shownGif.name, master.sourceFrameCount,
                                Formatter.formatShortFileSize(context, shownGif.sizeBytes)
                            )
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
                    SectionTitle(stringResource(if (shownGif == null) R.string.editor_pick else R.string.editor_change))
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
                            Text(stringResource(R.string.editor_gallery))
                        }
                        OutlinedButton(
                            onClick = { filePicker.launch(arrayOf("image/*", "video/*")) },
                            enabled = busy == null,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.editor_files))
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionTitle(stringResource(R.string.editor_display))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ScaleMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                // Presets: they snap the zoom. Neither is lit when the zoom is anywhere else.
                                selected = preset == mode,
                                onClick = {
                                    if (repositioning) {
                                        // Keep repositioning, just jump to the preset's zoom (Fit is centered).
                                        customZoom = if (mode == ScaleMode.FIT) fitZoom else 1f
                                        if (mode == ScaleMode.FIT) cropCenter = Offset(0.5f, 0.5f)
                                    } else {
                                        scaleMode = mode
                                        customZoom = null
                                    }
                                },
                                enabled = busy == null,
                                shape = SegmentedButtonDefaults.itemShape(index, ScaleMode.entries.size)
                            ) {
                                Text(stringResource(if (mode == ScaleMode.FIT) R.string.mode_fit else R.string.mode_fill))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(
                                when (preset) {
                                    ScaleMode.FIT -> R.string.editor_fit_description
                                    ScaleMode.FILL -> R.string.editor_fill_description
                                    null -> R.string.editor_zoom_description
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        AnimatedVisibility(visible = !repositioning && preset != ScaleMode.FIT && !centered) {
                            TextButton(onClick = { cropCenter = Offset(0.5f, 0.5f) }, enabled = busy == null) {
                                Icon(painterResource(R.drawable.ic_recenter), null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.editor_recenter))
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    SectionTitle(stringResource(R.string.editor_shape))
                    ShapePicker(shape, enabled = busy == null) { picked ->
                        // (A custom zoom keeps its number; Fit / Fill presets follow the new shape's box.)
                        val first = shape == null && picked != null && !shapeRepositionShown
                        shape = picked
                        // Picking a shape changes what fits, so show where the shape sits right away (Fill only).
                        if (first && image != null && !repositioning) {
                            shapeRepositionShown = true
                            openRepositionSoon = true
                        }
                    }

                    // Android 8-11 don't round widget corners, so a Fill picture's corners are square there.
                    // Newer versions never see this switch. A shape replaces it.
                    AnimatedVisibility(
                        visible = WidgetSizes.needsRoundedFrames && (scaleMode == ScaleMode.FILL || customZoom != null) && shape == null
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.editor_round_corners), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    stringResource(R.string.editor_round_corners_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = roundCorners, onCheckedChange = { roundCorners = it }, enabled = busy == null)
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
 * The widget-shaped preview. The picture is locked, so a stray swipe can't move it:
 * the "Reposition" button switches to [ZoomView], where you zoom and move it.
 */
@Composable
private fun Preview(
    maxPreviewHeight: androidx.compose.ui.unit.Dp,
    image: ImageBitmap?,
    background: WidgetBackground,
    scaleMode: ScaleMode,
    shape: WidgetShape?,
    aspectRatio: Float,
    cropCenter: () -> Offset,
    zoom: () -> Float,
    fitZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float, Offset) -> Unit,
    squareCorners: Boolean,
    repositioning: Boolean,
    canReposition: Boolean,
    onStartReposition: () -> Unit,
    onEndReposition: (keep: Boolean) -> Unit
) {
    val cornerRadius by animateDpAsState(if (squareCorners) 0.dp else 24.dp, label = "corners")

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // As wide as the screen allows, but never taller than [maxPreviewHeight] (for tall widgets).
        val width = min(maxWidth, maxPreviewHeight * aspectRatio)
        val height = width / aspectRatio
        val fullWidth = maxWidth
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = repositioning && image != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "reposition"
            ) { cropping ->
                if (cropping && image != null) {
                    // The full width (room around the window to see the rest of the picture), at least 260dp tall.
                    // With a shape, the window has the shape's box shape (e.g. square), with the shape drawn in it.
                    ZoomView(
                        image, shape?.aspect ?: aspectRatio, shape, background, cropCenter, zoom,
                        fitZoom, minZoom, maxZoom, onZoomChange,
                        Modifier.size(fullWidth, max(height, maxPreviewHeight))
                    )
                } else {
                    val widgetShape = RoundedCornerShape(cornerRadius)
                    Box(
                        modifier = Modifier
                            .width(width)
                            .aspectRatio(aspectRatio)
                            .clip(widgetShape)
                            .then(
                                // With a shape, the background takes the shape (drawn by ShapedBox);
                                // a thin outline shows where the widget's edges are.
                                if (shape == null) {
                                    Modifier.background(background.toComposeColor())
                                } else {
                                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, widgetShape)
                                }
                            )
                    ) {
                        val picture: @Composable () -> Unit = {
                            // Fades in when the first frame is ready; frames then simply replace each other.
                            AnimatedContent(
                                targetState = image != null,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "preview"
                            ) { hasImage ->
                                if (hasImage && image != null) {
                                    FrameImage(image, scaleMode, cropCenter, Modifier.fillMaxSize(), zoom = zoom)
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Image(painterResource(R.drawable.widget_placeholder), null, Modifier.size(56.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(stringResource(R.string.editor_preview_empty), color = Color.White.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                        if (shape == null) {
                            picture()
                        } else {
                            ShapedBox(shape, background, Modifier.fillMaxSize()) { picture() }
                        }

                        // The button that unlocks zooming and moving the picture.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = canReposition,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(10.dp)
                        ) {
                            Surface(
                                onClick = onStartReposition,
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.6f),
                                contentColor = Color.White
                            ) {
                                Text(
                                    stringResource(R.string.editor_reposition),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // While repositioning: a zoom slider (one-hand use), then keep the new spot or go back.
            AnimatedVisibility(visible = repositioning && image != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.editor_reposition_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (image != null) {
                    ZoomSlider(zoom, fitZoom, minZoom, maxZoom, cropCenter, image, shape?.aspect ?: aspectRatio, onZoomChange)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    OutlinedButton(onClick = { onEndReposition(false) }) {
                        Text(stringResource(R.string.editor_reposition_cancel))
                    }
                    Button(onClick = { onEndReposition(true) }) {
                        Text(stringResource(R.string.editor_reposition_done))
                    }
                }
                }
            }
        }
    }
}

/** The widget-shaped window in the middle of the zoom view (24dp margin all round). */
private fun zoomWindow(viewWidth: Float, viewHeight: Float, aspect: Float, margin: Float): Rect {
    val width = kotlin.math.min(viewWidth - 2 * margin, (viewHeight - 2 * margin) * aspect)
    val height = width / aspect
    val left = (viewWidth - width) / 2
    val top = (viewHeight - height) / 2
    return Rect(left, top, left + width, top + height)
}

/** Clamps a zoom, snaps it onto Fit / Fill, and gives a small tick when it lands on one. */
private class ZoomSnapper(val haptics: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    fun apply(raw: Float, old: Float, fit: Float, min: Float, max: Float): Float {
        val snapped = Zoom.snap(raw.coerceIn(min, max), fit).coerceIn(min, max)
        if (snapped != old && (snapped == 1f || snapped == fit)) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        return snapped
    }
}

/** The new zoom's center: [center] slid so the box stays where it's allowed to be (see Zoom.box). */
private fun clampedCenter(image: ImageBitmap, aspect: Float, zoom: Float, center: Offset): Offset {
    val box = Zoom.box(image.width.toFloat(), image.height.toFloat(), aspect, zoom, center.x, center.y)
    val (x, y) = Zoom.centerOf(box, image.width.toFloat(), image.height.toFloat())
    return Offset(x, y)
}

/**
 * Reposition: a widget-shaped window stays still, and the picture moves under it. Pinch to zoom,
 * drag to move. Outside the window (or the shape) is dimmed: that's what the widget won't show.
 * Empty space inside the window shows the background, like the widget.
 */
@Composable
private fun ZoomView(
    image: ImageBitmap,
    aspectRatio: Float,
    shape: WidgetShape?,
    background: WidgetBackground,
    cropCenter: () -> Offset,
    zoom: () -> Float,
    fitZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    onZoomChange: (Float, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val snapper = ZoomSnapper(LocalHapticFeedback.current)
    // The gesture below keeps running while the GIF plays (it only restarts if the picture's size or
    // the zoom range changes), so it reads the newest values through these.
    val latestCenter by rememberUpdatedState(cropCenter)
    val latestZoom by rememberUpdatedState(zoom)
    val latestOnChange by rememberUpdatedState(onZoomChange)
    // The pinch's own zoom, before snapping, so snapping onto Fit / Fill doesn't hold your fingers there.
    var rawZoom by remember { mutableFloatStateOf(zoom()) }
    val bgColor = background.toComposeColor()

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(image.width, image.height, aspectRatio, fitZoom, minZoom, maxZoom) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    val oldZoom = latestZoom()
                    // The zoom was changed elsewhere (slider, Fit / Fill buttons): continue from there.
                    if (Zoom.snap(rawZoom.coerceIn(minZoom, maxZoom), fitZoom).coerceIn(minZoom, maxZoom) != oldZoom) rawZoom = oldZoom
                    rawZoom = (rawZoom * gestureZoom).coerceIn(minZoom, maxZoom)
                    val newZoom = snapper.apply(rawZoom, oldZoom, fitZoom, minZoom, maxZoom)
                    // The picture follows your finger: moving right shows more of the left side.
                    val window = zoomWindow(size.width.toFloat(), size.height.toFloat(), aspectRatio, 24.dp.toPx())
                    val c = latestCenter()
                    val box = Zoom.box(image.width.toFloat(), image.height.toFloat(), aspectRatio, newZoom, c.x, c.y)
                    val k = window.width / box.width() // screen pixels per picture pixel
                    val moved = Offset(c.x - pan.x / k / image.width, c.y - pan.y / k / image.height)
                    latestOnChange(newZoom, clampedCenter(image, aspectRatio, newZoom, moved))
                }
            }
    ) {
        val window = zoomWindow(size.width, size.height, aspectRatio, 24.dp.toPx())
        val c = cropCenter()
        val box = Zoom.box(image.width.toFloat(), image.height.toFloat(), aspectRatio, zoom(), c.x, c.y)
        val k = window.width / box.width()

        val corner = CornerRadius(12.dp.toPx())
        // What stays visible: the window, or with a shape, the shape inside the window.
        val kept = if (shape != null) {
            shape.path(window.width, window.height).asComposePath().apply { translate(window.topLeft) }
        } else {
            Path().apply { addRoundRect(RoundRect(window, corner)) }
        }
        // The background first (seen where the picture doesn't reach), then the picture.
        drawPath(kept, bgColor)
        drawImage(
            image = image,
            dstOffset = IntOffset((window.left - box.left * k).roundToInt(), (window.top - box.top * k).roundToInt()),
            dstSize = IntSize((image.width * k).roundToInt(), (image.height * k).roundToInt()),
            filterQuality = FilterQuality.Medium
        )
        // Dim everything outside the window / shape.
        val outside = Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            op(this, kept, PathOperation.Difference)
        }
        drawPath(outside, Color.Black.copy(alpha = 0.6f))
        if (shape != null) {
            drawRect(Color.White.copy(alpha = 0.5f), window.topLeft, window.size, style = Stroke(1.dp.toPx()))
            drawPath(kept, Color.White, style = Stroke(2.dp.toPx()))
        } else {
            drawRoundRect(Color.White, window.topLeft, window.size, corner, style = Stroke(2.dp.toPx()))
        }
        // Rule-of-thirds grid inside the window, like a photo crop tool.
        val gridColor = Color.White.copy(alpha = 0.5f)
        for (i in 1..2) {
            val x = window.left + window.width * i / 3
            val y = window.top + window.height * i / 3
            drawLine(gridColor, Offset(x, window.top), Offset(x, window.bottom), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(window.left, y), Offset(window.right, y), strokeWidth = 1.dp.toPx())
        }
    }
}

/**
 * The zoom slider, for one hand. Logarithmic (each step zooms by the same factor), snapping onto
 * Fit and Fill with a small tick. Says "Fit" / "Fill" when it's on one of them.
 */
@Composable
private fun ZoomSlider(
    zoom: () -> Float,
    fitZoom: Float,
    minZoom: Float,
    maxZoom: Float,
    cropCenter: () -> Offset,
    image: ImageBitmap,
    aspectRatio: Float,
    onZoomChange: (Float, Offset) -> Unit
) {
    val snapper = ZoomSnapper(LocalHapticFeedback.current)
    val range = ln(maxZoom / minZoom).coerceAtLeast(0.0001f)
    val current = zoom()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            stringResource(
                when (current) {
                    1f -> R.string.mode_fill
                    fitZoom -> R.string.mode_fit
                    else -> R.string.editor_zoom_label
                }
            ),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp)
        )
        Slider(
            value = (ln(current / minZoom) / range).coerceIn(0f, 1f),
            onValueChange = { t ->
                val newZoom = snapper.apply(minZoom * exp(t * range), current, fitZoom, minZoom, maxZoom)
                onZoomChange(newZoom, clampedCenter(image, aspectRatio, newZoom, cropCenter()))
            },
            modifier = Modifier.weight(1f)
        )
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
                // On tablets and in landscape, the same readable width as the rest of the screen.
                .wrapContentWidth()
                .widthIn(max = 640.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) { state ->
            when (state) {
                null -> Button(
                    onClick = onSave,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) { Text(label, style = MaterialTheme.typography.titleSmall) }

                Busy.OpeningVideo -> Progress(stringResource(R.string.progress_opening_video), 0, 0)
                is Busy.Preparing -> if (state.video) {
                    Progress(stringResource(R.string.progress_reading_video), state.done, state.total, onCancel)
                } else {
                    Progress(stringResource(R.string.progress_reading_file), state.done, state.total)
                }
                is Busy.Saving -> Progress(stringResource(R.string.progress_saving), state.done, state.total)
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
            .heightIn(min = 52.dp)
    ) {
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.weight(1f)) {
            Text(
                if (total == 0) text else stringResource(R.string.progress_count, text, done, total),
                style = MaterialTheme.typography.bodyMedium
            )
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
