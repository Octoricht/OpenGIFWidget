package com.octorice.opengifwidget.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.shape.CustomShape
import com.octorice.opengifwidget.shape.CustomShapes
import com.octorice.opengifwidget.shape.WidgetShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The shape library, read off the main thread. Call [ShapeLibrary.reload] after adding or deleting. */
class ShapeLibrary(val shapes: List<CustomShape>, val reload: () -> Unit)

@Composable
fun rememberShapeLibrary(): ShapeLibrary {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val shapes by produceState(emptyList<CustomShape>(), version) {
        value = withContext(Dispatchers.IO) { CustomShapes.all(context) }
    }
    return ShapeLibrary(shapes) { version++ }
}

/**
 * Your shapes as tiles (each drawn as the shape itself), then an "Add new shape" tile.
 * [selected] is highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapeLibraryGrid(
    shapes: List<CustomShape>,
    selected: WidgetShape?,
    enabled: Boolean,
    onPick: (CustomShape) -> Unit,
    onAdd: () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (shape in shapes) {
            ShapeTile(name = null, selected = shape == selected, enabled = enabled, onClick = { onPick(shape) }) { color ->
                ShapeIcon(shape, color)
            }
        }
        ShapeTile(stringResource(R.string.shapes_add), selected = false, enabled = enabled, onClick = onAdd) { color ->
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = color, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * A mask drawn in a dark shade of the theme's color over a checkerboard (the checkered parts get
 * cut away). The checkerboard is always white and light gray, so the color must be dark in both
 * themes: in dark mode the accent color is light (it would blend in), so its deep "container"
 * shade is used instead.
 */
@Composable
fun MaskPreview(mask: Bitmap, modifier: Modifier = Modifier) {
    val image = remember(mask) { mask.asImageBitmap() }
    val colors = MaterialTheme.colorScheme
    val color = if (colors.primary.luminance() > 0.4f) colors.primaryContainer else colors.primary
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        Checkerboard()
        Canvas(Modifier.fillMaxSize()) {
            val aspect = image.width / image.height.toFloat()
            val boxWidth = kotlin.math.min(size.width, size.height * aspect)
            val boxHeight = boxWidth / aspect
            drawImage(
                image,
                dstOffset = IntOffset(((size.width - boxWidth) / 2).toInt(), ((size.height - boxHeight) / 2).toInt()),
                dstSize = IntSize(boxWidth.toInt().coerceAtLeast(1), boxHeight.toInt().coerceAtLeast(1)),
                colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
                filterQuality = FilterQuality.Medium
            )
        }
    }
}

/** Where the "Add new shape" steps are. */
private sealed interface AddStep {
    data object Choose : AddStep                     // Gallery or Files?
    data object Picking : AddStep                    // the system picker is open
    data object Working : AddStep                    // turning the picture into a mask
    data class Preview(val mask: Bitmap) : AddStep   // "Use this shape" / "Cancel"
    data class Failed(val message: String) : AddStep
    data object TutorialDone : AddStep               // tutorial only: "Your cat shape is ready!"
}

/**
 * The "Add new shape" steps, as dialogs: pick a picture (Gallery or Files), it's turned into a
 * mask, you see how it will cut, then "Use this shape" saves it to the library ([onAdded]).
 * Shown while [open]; [onClose] when done or cancelled.
 *
 * Tutorial ([tutorial], or "Try it yourself" in the guide): the very same steps, run on the cat
 * picture built into the app instead of a picked one, with a short hint at each step and a last
 * "Your cat shape is ready!" saying where it went ([tutorialDoneText], which depends on the screen).
 */
@Composable
fun AddShapeDialogs(
    open: Boolean,
    onClose: () -> Unit,
    onAdded: (CustomShape) -> Unit,
    tutorialDoneText: String,
    tutorial: Boolean = false
) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<AddStep>(AddStep.Choose) }
    var showHelp by remember { mutableStateOf(false) }
    var inTutorial by remember { mutableStateOf(false) }

    fun close() {
        (step as? AddStep.Preview)?.mask?.recycle()
        step = AddStep.Choose
        inTutorial = false
        onClose()
    }

    // Turns a picture into a mask in the background, then shows the preview (or a friendly error).
    fun make(work: () -> Bitmap) {
        step = AddStep.Working
        scope.launch {
            val started = System.currentTimeMillis()
            step = try {
                val mask = withContext(Dispatchers.IO) { work() }
                // In the tutorial, let "Making your shape" be seen for a moment: it's one of the steps.
                if (inTutorial) delay((1200 - (System.currentTimeMillis() - started)).coerceAtLeast(0))
                AddStep.Preview(mask)
            } catch (e: IOException) {
                AddStep.Failed(e.friendlyMessage(context))
            }
        }
    }

    fun startTutorial() {
        inTutorial = true
        make { CustomShapes.makeMaskFromPicture(context, R.drawable.tutorial_cat) }
    }

    LaunchedEffect(open) {
        if (open) {
            step = AddStep.Choose
            if (tutorial) startTutorial()
        }
    }

    fun onPicked(uri: android.net.Uri?) {
        if (uri == null) {
            close() // closed the picker without choosing
            return
        }
        make { CustomShapes.makeMask(context, uri) }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { onPicked(it) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { onPicked(it) }

    if (!open) return
    // The guide replaces the "Add new shape" dialog while it's open; closing it comes back here,
    // and "Try it yourself" runs the tutorial right here.
    if (showHelp) {
        ShapeHelpSheet(
            onClose = { showHelp = false },
            onTryIt = {
                showHelp = false
                startTutorial()
            }
        )
        return
    }
    when (val current = step) {
        AddStep.Choose -> AlertDialog(
            onDismissRequest = ::close,
            title = { Text(stringResource(R.string.shapes_add)) },
            text = {
                Column {
                    Text(stringResource(R.string.shapes_add_from))
                    ShapeHelpLink(onClick = { showHelp = true })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    step = AddStep.Picking
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text(stringResource(R.string.editor_gallery)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    step = AddStep.Picking
                    filePicker.launch(arrayOf("image/*"))
                }) { Text(stringResource(R.string.editor_files)) }
            }
        )
        AddStep.Picking -> Unit
        AddStep.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            confirmButton = {},
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.shapes_making))
                    }
                    if (inTutorial) TutorialHint(stringResource(R.string.tutorial_making_hint))
                }
            }
        )
        is AddStep.Preview -> AlertDialog(
            onDismissRequest = ::close,
            title = { Text(stringResource(R.string.shapes_preview_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.shapes_preview_text))
                    Spacer(Modifier.height(12.dp))
                    MaskPreview(current.mask, Modifier.size(220.dp))
                    if (inTutorial) TutorialHint(stringResource(R.string.tutorial_preview_hint))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    haptics.confirm()
                    scope.launch {
                        try {
                            val shape = withContext(Dispatchers.IO) { CustomShapes.save(context, current.mask) }
                            current.mask.recycle()
                            onAdded(shape)
                            if (inTutorial) {
                                step = AddStep.TutorialDone // one last word on where the shape went
                            } else {
                                step = AddStep.Choose
                                onClose()
                            }
                        } catch (e: IOException) {
                            step = AddStep.Failed(e.friendlyMessage(context))
                        }
                    }
                }) { Text(stringResource(R.string.shapes_use)) }
            },
            dismissButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.shapes_cancel)) } }
        )
        is AddStep.Failed -> AlertDialog(
            onDismissRequest = ::close,
            text = { Text(current.message) },
            confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.shapes_close)) } }
        )
        AddStep.TutorialDone -> AlertDialog(
            onDismissRequest = ::close,
            title = { Text(stringResource(R.string.tutorial_done_title)) },
            text = { Text(tutorialDoneText) },
            confirmButton = { TextButton(onClick = ::close) { Text(stringResource(R.string.shape_help_got_it)) } }
        )
    }
}

/** A tutorial hint: a small highlighted note under a step, saying what's happening and what to do. */
@Composable
private fun TutorialHint(text: String) {
    Spacer(Modifier.height(12.dp))
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}
