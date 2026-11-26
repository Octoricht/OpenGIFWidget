package com.octorice.opengifwidget.config

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.Slide
import com.octorice.opengifwidget.data.SlideTiming
import com.octorice.opengifwidget.data.Transition
import com.octorice.opengifwidget.gif.FrameFiles
import com.octorice.opengifwidget.ui.SliderLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Pictures per row in the slideshow's grid. */
private const val GRID_COLUMNS = 4

/** The most pictures in one slideshow (they all sit in the launcher's RAM at once). */
internal const val MAX_SLIDES = 20

/**
 * The slideshow part of the editor: name, the pictures (a grid you can reorder by long-pressing and
 * dragging; tap = show it in the preview; the shown one has a remove button), "Add pictures"
 * (Gallery / Files, several at once). How it plays is [SlideshowPlayback], further down (after Shape).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlideshowSection(
    slides: List<Slide>,
    selectedId: Long?,
    name: String,
    enabled: Boolean,
    thumbnailFile: (Slide) -> File,
    onName: (String) -> Unit,
    onDoneTyping: () -> Unit,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (slides.isEmpty()) {
                stringResource(R.string.slideshow_empty)
            } else {
                stringResource(R.string.slideshow_count, slides.size, MAX_SLIDES)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (slides.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.editor_name)) },
                placeholder = { Text(stringResource(R.string.slideshow_default_name)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDoneTyping() }),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))
            SectionTitle(stringResource(R.string.slideshow_pictures))
            SlideGrid(slides, selectedId, enabled, thumbnailFile, onSelect, onRemove, onMove)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.slideshow_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle(stringResource(if (slides.isEmpty()) R.string.slideshow_pick else R.string.slideshow_add))
        val canAdd = enabled && slides.size < MAX_SLIDES
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = onGallery,
                enabled = canAdd,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(painterResource(R.drawable.ic_photo_library), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_gallery))
            }
            OutlinedButton(
                onClick = onFiles,
                enabled = canAdd,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.weight(1f)
            ) {
                Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.editor_files))
            }
        }
        if (slides.size >= MAX_SLIDES) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.slideshow_full, MAX_SLIDES),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * "Playback" for a slideshow (only once there's something to flip between): time per picture, the
 * transition (not in slow mode) and Shuffle. It sits after Shape, the same place as Speed in the normal editor.
 */
@Composable
internal fun SlideshowPlayback(
    slideCount: Int,
    seconds: Int,
    transition: Transition,
    shuffle: Boolean,
    enabled: Boolean,
    onSeconds: (Int) -> Unit,
    onTransition: (Transition) -> Unit,
    onShuffle: (Boolean) -> Unit
) {
    if (slideCount < 2) return
    Column {
        Spacer(Modifier.height(24.dp))
        SectionTitle(stringResource(R.string.slideshow_playback))
        TimePerPicture(seconds, enabled, onSeconds)
        // Slow mode: the picture changes while you're not looking, so there's no transition.
        if (SlideTiming.isSlow(seconds)) {
            Text(
                stringResource(R.string.slideshow_slow_hint, timeLabel(seconds)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(visible = !SlideTiming.isSlow(seconds)) {
            TransitionPicker(transition, enabled, onTransition)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.slideshow_shuffle), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.slideshow_shuffle_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = shuffle, onCheckedChange = onShuffle, enabled = enabled)
        }
    }
}

/**
 * "Time per picture": a slider that steps through [SlideTiming.CHOICES] (1 s ... 1 min), with the
 * chosen time written next to its title.
 */
@Composable
private fun TimePerPicture(seconds: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val choices = SlideTiming.CHOICES
    val index = choices.indexOf(SlideTiming.nearest(seconds))
    SliderLabel(stringResource(R.string.slideshow_time), timeLabel(seconds))
    Slider(
        value = index.toFloat(),
        onValueChange = { onChange(choices[it.roundToInt().coerceIn(0, choices.lastIndex)]) },
        valueRange = 0f..choices.lastIndex.toFloat(),
        steps = choices.size - 2, // one stop per choice
        enabled = enabled
    )
}

/** None / Fade / Slide (fast slideshows only). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitionPicker(transition: Transition, enabled: Boolean, onTransition: (Transition) -> Unit) {
    Column {
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.slideshow_transition),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Transition.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = transition == option,
                    onClick = { onTransition(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, Transition.entries.size)
                ) {
                    Text(
                        stringResource(
                            when (option) {
                                Transition.NONE -> R.string.transition_none
                                Transition.FADE -> R.string.transition_fade
                                Transition.SLIDE -> R.string.transition_slide
                            }
                        )
                    )
                }
            }
        }
    }
}

/** "1 second", "5 seconds", "15 minutes", "3 hours", "1 day". */
@Composable
private fun timeLabel(seconds: Int): String = when {
    seconds >= 86400 && seconds % 86400 == 0 -> pluralStringResource(R.plurals.slideshow_days, seconds / 86400, seconds / 86400)
    seconds >= 3600 && seconds % 3600 == 0 -> pluralStringResource(R.plurals.slideshow_hours, seconds / 3600, seconds / 3600)
    seconds >= 60 && seconds % 60 == 0 -> pluralStringResource(R.plurals.slideshow_minutes, seconds / 60, seconds / 60)
    else -> pluralStringResource(R.plurals.slideshow_seconds, seconds, seconds)
}

/**
 * The pictures in a grid, in play order. Long-press a picture and drag it to move it: the others
 * slide out of the way as it passes them. Every tile sits at its slot's position (animated), except
 * the one being dragged, which follows your finger.
 */
@Composable
private fun SlideGrid(
    slides: List<Slide>,
    selectedId: Long?,
    enabled: Boolean,
    thumbnailFile: (Slide) -> File,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onMove: (from: Int, to: Int) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val cell = (maxWidth - gap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val density = LocalDensity.current
        val cellPx = with(density) { cell.toPx() }
        val pitchPx = with(density) { (cell + gap).toPx() } // from one slot to the next
        val rows = (slides.size + GRID_COLUMNS - 1) / GRID_COLUMNS
        fun slotOf(index: Int) = Offset((index % GRID_COLUMNS) * pitchPx, (index / GRID_COLUMNS) * pitchPx)

        // The gesture keeps running while the list changes under it, so it reads these live.
        val currentSlides by rememberUpdatedState(slides)
        val currentOnMove by rememberUpdatedState(onMove)
        // Read live too: changing the gesture's key would restart it, and a restart reports "drag
        // cancelled" to every tile (even ones nobody touched), which once piled tiles in a corner.
        val currentEnabled by rememberUpdatedState(enabled)
        var draggingId by remember { mutableStateOf<Long?>(null) }
        var dragPos by remember { mutableStateOf(Offset.Zero) } // the dragged tile's top-left, px
        val haptics = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()

        Box(
            Modifier
                .fillMaxWidth()
                .height(cell * rows + gap * (rows - 1).coerceAtLeast(0))
        ) {
            slides.forEachIndexed { index, slide ->
                // key: each tile keeps its own state (and its running drag) when the order changes.
                key(slide.id) {
                    val slot = slotOf(index)
                    val dragging = draggingId == slide.id
                    val position = remember { Animatable(slot, Offset.VectorConverter) }
                    LaunchedEffect(slot, dragging) { if (!dragging) position.animateTo(slot) }
                    val lift by animateFloatAsState(if (dragging) 1.08f else 1f, label = "lift")
                    val shown = if (dragging) dragPos else position.value

                    SlideTile(
                        number = index + 1,
                        thumbnail = rememberThumbnail(thumbnailFile(slide)),
                        selected = slide.id == selectedId,
                        repositioned = slide.custom,
                        enabled = enabled,
                        onRemove = { onRemove(slide.id) },
                        modifier = Modifier
                            .offset { IntOffset(shown.x.roundToInt(), shown.y.roundToInt()) }
                            .size(cell)
                            .zIndex(if (dragging) 1f else 0f)
                            .scale(lift)
                            .shadow(if (dragging) 8.dp else 0.dp, RoundedCornerShape(12.dp))
                            .pointerInput(slide.id) {
                                // Ends the drag of this tile, if it's the one being dragged: it glides from
                                // where you let go into its slot. Any other tile ignores it.
                                fun finishDrag() {
                                    if (draggingId != slide.id) return
                                    scope.launch {
                                        position.snapTo(dragPos)
                                        draggingId = null
                                    }
                                }
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        if (!currentEnabled) return@detectDragGesturesAfterLongPress
                                        val from = currentSlides.indexOfFirst { it.id == slide.id }
                                        dragPos = slotOf(from)
                                        draggingId = slide.id
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                    onDrag = { change, amount ->
                                        if (draggingId != slide.id) return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        val list = currentSlides
                                        val lastRow = (list.size - 1) / GRID_COLUMNS
                                        dragPos += amount
                                        // The slot under the tile's middle is where it goes.
                                        val col = ((dragPos.x + cellPx / 2) / pitchPx).toInt().coerceIn(0, GRID_COLUMNS - 1)
                                        val row = ((dragPos.y + cellPx / 2) / pitchPx).toInt().coerceIn(0, lastRow)
                                        val to = (row * GRID_COLUMNS + col).coerceIn(0, list.lastIndex)
                                        val from = list.indexOfFirst { it.id == slide.id }
                                        if (from >= 0 && to != from) {
                                            currentOnMove(from, to)
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onDragEnd = { finishDrag() },
                                    onDragCancel = { finishDrag() }
                                )
                            }
                            .clickable(enabled = enabled) { onSelect(slide.id) }
                    )
                }
            }
        }
    }
}

/** One picture in the grid: its thumbnail, its place in the order, and (when shown) a remove button. */
@Composable
private fun SlideTile(
    number: Int,
    thumbnail: ImageBitmap?,
    selected: Boolean,
    repositioned: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = stringResource(
        if (repositioned) R.string.slideshow_picture_number_repositioned else R.string.slideshow_picture_number,
        number
    )
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier
            )
            .semantics { contentDescription = description }
    ) {
        if (thumbnail != null) {
            Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        // Its place in the order (1 = first).
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        if (repositioned) {
            // A small mark: this picture has its own Reposition (Fit / Fill for all would reset it).
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .size(22.dp)
            ) {
                Icon(painterResource(R.drawable.ic_recenter), contentDescription = null, modifier = Modifier.padding(4.dp))
            }
        }
        if (selected) {
            // Remove: only on the picture you tapped, so it's never hit by accident while scrolling.
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, onClick = onRemove)
            ) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.slideshow_remove),
                    modifier = Modifier.padding(5.dp)
                )
            }
        }
    }
}

/**
 * "Reset 3 repositioned pictures?": asked before Fit / Fill for all undoes pictures you repositioned by hand.
 * Nothing is saved until Save, so even Reset can still be undone by closing the editor.
 */
@Composable
internal fun ResetCropsDialog(count: Int, fit: Boolean, onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.slideshow_reset_title, count, count)) },
        text = {
            Text(
                pluralStringResource(
                    R.plurals.slideshow_reset_text,
                    count,
                    stringResource(if (fit) R.string.mode_fit else R.string.mode_fill)
                )
            )
        },
        confirmButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.slideshow_reset)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.remove_cancel)) } }
    )
}

/** A small copy of a picture's master frame for the grid (null while loading, or if unreadable). */
@Composable
private fun rememberThumbnail(file: File): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { FrameFiles.decode(file, maxSidePx = 256)?.asImageBitmap() }
    }
    return state.value
}
