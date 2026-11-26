package com.octorice.opengifwidget.home

import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.activity.compose.BackHandler
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import com.octorice.opengifwidget.widget.GifWidgetProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.data.TapAction
import com.octorice.opengifwidget.data.WidgetTap
import com.octorice.opengifwidget.config.AppIcon
import com.octorice.opengifwidget.ui.rememberInstalledApp
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.data.WidgetBackground
import androidx.compose.ui.graphics.ImageBitmap
import com.octorice.opengifwidget.ui.SlideTransition
import com.octorice.opengifwidget.ui.FrameImage
import com.octorice.opengifwidget.ui.ShapedBox
import com.octorice.opengifwidget.ui.toComposeColor
import com.octorice.opengifwidget.ui.rememberFrameThumb
import com.octorice.opengifwidget.ui.rememberPlayedFrame
import com.octorice.opengifwidget.shape.WidgetShape
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The app's main screen: every GIF widget on the home screen, playing, in its real shape.
 * [refreshKey] changes every time the screen comes back into view, which reloads the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    refreshKey: Int,
    onOpenWidget: (appWidgetId: Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var widgets by remember { mutableStateOf<List<WidgetSummary>?>(null) } // null = still loading
    var health by remember { mutableStateOf<Health?>(null) }
    var showHowToAdd by remember { mutableStateOf(false) }
    var toRemove by remember { mutableStateOf<WidgetSummary?>(null) } // long-pressed card
    var showHealth by remember { mutableStateOf(false) } // the panel behind the header
    var addMenuOpen by remember { mutableStateOf(false) } // "Add widget" split into its two choices

    // Read again whenever the screen comes back (e.g. after changing it in Settings).
    var fpsCap by remember(refreshKey) { mutableIntStateOf(AppSettings.getFpsCap(context)) }

    // Loads the widgets and works out how hard they push this phone.
    suspend fun reload() {
        val (loaded, verdict) = withContext(Dispatchers.IO) {
            val list = HomeData.loadWidgets(context)
            list to UsageHealth.evaluate(context, list)
        }
        widgets = loaded
        health = verdict
    }

    LaunchedEffect(refreshKey) { reload() }

    val pinConfirmMessage = stringResource(R.string.home_pin_confirm)
    val onAddWidget: (slideshow: Boolean) -> Unit = { slideshow ->
        addMenuOpen = false
        if (HomeData.requestPinWidget(context, slideshow)) {
            scope.launch { snackbarHostState.showSnackbar(pinConfirmMessage) }
        } else {
            showHowToAdd = true // This launcher can't place widgets for us.
        }
    }

    // Back closes the two choices first, instead of leaving the app.
    BackHandler(enabled = addMenuOpen) { addMenuOpen = false }

    // Makes the big title shrink smoothly into a normal top bar as you scroll.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val title: @Composable () -> Unit = {
                Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold)
            }
            val actions: @Composable RowScope.() -> Unit = {
                IconButton(onClick = onOpenSettings) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings_title))
                }
            }
            // Short windows (a phone in landscape, split screen): a compact bar, so the widgets stay visible.
            val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
            if (windowHeight < 480.dp) {
                TopAppBar(title = title, actions = actions, scrollBehavior = scrollBehavior)
            } else {
                LargeTopAppBar(title = title, actions = actions, scrollBehavior = scrollBehavior)
            }
        },
        floatingActionButton = {
            AddWidgetButton(
                open = addMenuOpen,
                onOpen = { addMenuOpen = true },
                onPick = onAddWidget
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // Fades between "loading", "no widgets" and "the list", instead of jumping.
        val state = when {
            widgets == null -> ScreenState.Loading
            widgets!!.isEmpty() -> ScreenState.Empty
            else -> ScreenState.List
        }
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "home content",
            modifier = Modifier.padding(innerPadding)
        ) { target ->
            when (target) {
                ScreenState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ScreenState.Empty -> EmptyState(onAddWidget)
                ScreenState.List -> WidgetGrid(
                    widgets.orEmpty(), health, fpsCap, onOpenWidget, onOpenHealth = { showHealth = true },
                    onLongPress = { toRemove = it }
                )
            }
        }
        // While the two choices are showing, a tap anywhere else just closes them.
        if (addMenuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { addMenuOpen = false } }
            )
        }
    }

    val current = health
    if (showHealth && current != null) {
        HealthSheet(
            health = current,
            widgets = widgets.orEmpty(),
            fpsCap = fpsCap,
            onDismiss = { showHealth = false },
            // Same as choosing it in Settings: saved, every widget redrawn, numbers updated.
            onLowerCap = { cap ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppSettings.setFpsCap(context, cap)
                        GifWidgetProvider.updateAll(context)
                    }
                    fpsCap = AppSettings.getFpsCap(context)
                    reload()
                }
            },
            onOpenWidget = { id ->
                showHealth = false
                onOpenWidget(id)
            },
            onOpenStorageSettings = {
                try {
                    context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        )
    }

    toRemove?.let { widget ->
        val removedMessage = stringResource(R.string.remove_done)
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.remove_title)) },
            text = { Text(stringResource(R.string.remove_message)) },
            confirmButton = {
                TextButton(onClick = {
                    toRemove = null
                    scope.launch {
                        withContext(Dispatchers.IO) { HomeData.removeFromList(context, widget.appWidgetId) }
                        reload()
                        snackbarHostState.showSnackbar(removedMessage)
                    }
                }) { Text(stringResource(R.string.remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.remove_cancel)) }
            }
        )
    }

    if (showHowToAdd) {
        AlertDialog(
            onDismissRequest = { showHowToAdd = false },
            confirmButton = { TextButton(onClick = { showHowToAdd = false }) { Text(stringResource(R.string.home_got_it)) } },
            title = { Text(stringResource(R.string.home_how_to_add_title)) },
            text = {
                Text(stringResource(R.string.home_how_to_add_steps))
            }
        )
    }
}

private enum class ScreenState { Loading, Empty, List }

@Composable
private fun WidgetGrid(
    widgets: List<WidgetSummary>,
    health: Health?,
    fpsCap: Int,
    onOpenWidget: (Int) -> Unit,
    onOpenHealth: () -> Unit,
    onLongPress: (WidgetSummary) -> Unit
) {
    val context = LocalContext.current
    // Big screens (tablets: the shorter side is at least 600dp) get much bigger cards.
    val window = LocalWindowInfo.current.containerSize
    val bigScreen = with(LocalDensity.current) { minOf(window.width, window.height).toDp() } >= 600.dp
    LazyVerticalStaggeredGrid(
        // As many columns as fit: phones 2 (cards at least 170dp wide), tablets 2-3 (at least 360dp).
        columns = StaggeredGridCells.Adaptive(minSize = if (bigScreen) 360.dp else 170.dp),
        // Extra space at the bottom so the last cards aren't hidden behind the Add button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            HealthHeader(
                widgetCount = widgets.size,
                storageBytes = widgets.sumOf { it.storageBytes },
                health = health,
                onClick = onOpenHealth
            )
        }
        items(widgets, key = { it.appWidgetId }) { widget ->
            WidgetCard(
                widget = widget,
                heavy = health?.heavyWidgetIds?.contains(widget.appWidgetId) == true,
                fpsCap = fpsCap,
                onClick = { onOpenWidget(widget.appWidgetId) },
                onLongClick = { onLongPress(widget) },
                modifier = Modifier.animateItem() // Cards slide smoothly when the list changes.
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                stringResource(R.string.home_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }
        // A quiet footer: the version, and a link to new versions on GitHub (opens the browser;
        // the app itself never goes online). People who installed once can find updates here.
        item(span = StaggeredGridItemSpan.FullLine) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val updatesUrl = stringResource(R.string.about_updates_url)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.TextButton(onClick = {
                    try {
                        uriHandler.openUri(updatesUrl)
                    } catch (e: Exception) {
                        // No browser on this phone: nothing to open the link with.
                    }
                }) {
                    Text(
                        stringResource(R.string.home_updates_footer, com.octorice.opengifwidget.BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(
    widget: WidgetSummary,
    heavy: Boolean,
    fpsCap: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val frames = widget.gif?.frames
    val shape = RoundedCornerShape(24.dp)
    ElevatedCard(
        shape = shape,
        // Tap = edit, long-press = remove (with a little vibration, like the home screen).
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        // A slideshow card is a little stack of photos: its next pictures peek out behind the shown one.
        val stack = widget.slideshow && frames != null && widget.slideOrder.isNotEmpty()
        // The widget's frames are already cropped for Fill, so Fill just centers them here.
        // (Slideshows play in their own order, a Shuffle order too, so the card knows which picture is up.)
        val played = rememberPlayedFrame(
            widget.framesDir, frames, maxSidePx = 512, widget.speed, fpsCap,
            reuseBitmaps = !widget.slideshow,
            order = if (stack) widget.slideOrder else null,
            startPosition = widget.slideStart
        )
        val image = played.image
        val position = if (played.position >= 0) played.position else widget.slideStart
        val previewPadding = if (stack) {
            Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp + STACK_STEP * 2, end = 8.dp + STACK_STEP * 2)
        } else {
            Modifier.padding(8.dp)
        }
        Box {
            if (stack) {
                // Back to front: the picture after next (dimmer), then the next one. Missing pictures
                // (a slideshow of 1 or 2) are plain cards.
                val order = widget.slideOrder
                for (depth in 2 downTo 1) {
                    StackLayer(
                        dir = widget.framesDir,
                        index = if (order.size > depth) order[(position + depth) % order.size] else null,
                        depth = depth,
                        shape = widget.shape,
                        modifier = Modifier
                            .matchParentSize()
                            .then(previewPadding)
                            .offset(x = STACK_STEP * depth, y = -STACK_STEP * depth)
                    )
                }
            }
        // The preview has the same shape as the widget on the home screen.
        Box(
            modifier = Modifier
                .then(previewPadding)
                .fillMaxWidth()
                .aspectRatio(widget.aspectRatio)
                .clip(RoundedCornerShape(18.dp))
                // With a shape, only the shape gets the background (drawn by ShapedBox below).
                .then(if (widget.shape == null) Modifier.background(widget.background.toComposeColor()) else Modifier)
        ) {
            // A slideshow changes pictures with its transition, like on the home screen (slow ones
            // show one picture at a time there, so they just switch).
            // (Only slideshows: an animation's frames simply replace each other. Two frames on screen at
            // once, even for a moment, would make see-through edges flicker.)
            val transition = if (widget.slideshow && !widget.slowSlideshow) widget.transition else null
            if (image != null) {
                val frame: @Composable (ImageBitmap, WidgetBackground) -> Unit = { shown, shapeBackground ->
                    if (widget.shape != null) {
                        // Already cut into the shape: just centered, on the shape-colored background.
                        ShapedBox(widget.shape, shapeBackground, Modifier.fillMaxSize(), cutContent = false) {
                            FrameImage(shown, ScaleMode.FIT, { Offset(0.5f, 0.5f) }, Modifier.fillMaxSize())
                        }
                    } else {
                        FrameImage(shown, if (widget.zoomed || widget.slideshow) ScaleMode.FIT else widget.scaleMode, { Offset(0.5f, 0.5f) }, Modifier.fillMaxSize())
                    }
                }
                if (transition == null) {
                    frame(image, widget.background)
                } else {
                    // The shape's background stays still under the changing pictures, like the widget's.
                    if (widget.shape != null) {
                        ShapedBox(widget.shape, widget.background, Modifier.fillMaxSize(), cutContent = false) {}
                    }
                    SlideTransition(image, transition, Modifier.fillMaxSize()) { shown ->
                        frame(shown, widget.background.copy(alpha = 0))
                    }
                }
                // This widget costs a lot of the home screen's memory.
                if (heavy) {
                    HeavyTag(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
            } else {
                Image(
                    painter = painterResource(R.drawable.widget_placeholder),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                )
            }
        }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            Text(
                widget.name.ifEmpty { stringResource(R.string.no_picture_yet) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            if (stack && frames != null) {
                SlideshowStats(
                    count = frames.frameCount,
                    seconds = frames.intervalMs / 1000,
                    shuffle = widget.shuffle,
                    tap = widget.tap,
                    shownIndex = widget.slideOrder[position % widget.slideOrder.size],
                    description = pluralStringResource(
                        R.plurals.home_card_pictures,
                        frames.frameCount,
                        frames.frameCount,
                        Formatter.formatShortFileSize(context, widget.storageBytes)
                    )
                )
            } else Text(
                when {
                    frames == null -> stringResource(R.string.widget_tap_to_choose)
                    frames.frameCount == 1 -> stringResource(
                        R.string.home_card_still,
                        Formatter.formatShortFileSize(context, widget.storageBytes)
                    )
                    else -> pluralStringResource(
                        R.plurals.home_card_frames,
                        frames.frameCount,
                        frames.frameCount,
                        Formatter.formatShortFileSize(context, widget.storageBytes)
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Not "Edit" (the usual): a small icon says what a tap on the home screen does.
            if (!stack && frames != null && widget.tap.action != TapAction.EDIT) {
                Spacer(Modifier.height(6.dp))
                TapStat(widget.tap, withName = true)
            }
        }
    }
}

/** What a tap does, said in words for TalkBack (null for Edit, the usual). */
@Composable
private fun tapDescription(tap: WidgetTap): String? {
    val app = rememberInstalledApp(tap.app.takeIf { tap.action == TapAction.APP })
    return when (tap.action) {
        TapAction.APP -> stringResource(R.string.card_tap_app, app?.label.orEmpty())
        TapAction.NEXT -> stringResource(R.string.card_tap_next)
        TapAction.NOTHING -> stringResource(R.string.card_tap_nothing)
        TapAction.EDIT -> null
    }
}

/**
 * What a tap does, as a small stat like the slideshow's: the app's own icon (and its name on normal
 * cards, where there's room), or the Next / Nothing icon. Nothing is shown for Edit.
 */
@Composable
private fun TapStat(tap: WidgetTap, withName: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val app = rememberInstalledApp(tap.app.takeIf { tap.action == TapAction.APP })
    val description = tapDescription(tap) ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        when (tap.action) {
            TapAction.APP -> {
                AppIcon(app, Modifier.size(16.dp))
                if (withName && app != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(app.label, style = MaterialTheme.typography.labelMedium, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TapAction.NEXT -> Icon(painterResource(R.drawable.ic_next_picture), null, tint = accent, modifier = Modifier.size(16.dp))
            else -> Icon(painterResource(R.drawable.ic_tap_off), null, tint = accent, modifier = Modifier.size(16.dp))
        }
    }
}

/** Small amber "Heavy" pill on cards of widgets that cost a lot of memory. */
@Composable
private fun HeavyTag(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFFFB74D),
        contentColor = Color(0xFF261900),
        modifier = modifier
    ) {
        Text(
            stringResource(R.string.home_heavy_tag),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** How far each picture in a slideshow card's stack sits up and to the right of the one in front. */
private val STACK_STEP = 6.dp

/**
 * One picture in a slideshow card's stack, behind the shown one: dimmer the further back it is
 * ([depth] 1 = right behind). [index] null = no picture for this spot: a plain card in the stack's color.
 */
@Composable
private fun StackLayer(dir: File?, index: Int?, depth: Int, shape: WidgetShape?, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val plain = if (depth == 1) colors.secondaryContainer else lerp(colors.secondaryContainer, colors.surfaceContainerLow, 0.5f)
    val corners = RoundedCornerShape(18.dp)
    if (index == null) {
        if (shape != null) {
            ShapedBox(shape, WidgetBackground(plain.toArgb(), 255), modifier, cutContent = false) {}
        } else {
            Box(modifier.clip(corners).background(plain))
        }
        return
    }
    val thumb = rememberFrameThumb(dir, index, maxSidePx = 256)
    // Shaped pictures are already cut out; plain ones sit on the stack's color (seen in Fit's empty space).
    Box(if (shape != null) modifier else modifier.clip(corners).background(plain)) {
        if (thumb != null) {
            Image(
                thumb,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                // Darker the further back, without darkening the cut-away parts of a shape.
                colorFilter = ColorFilter.tint(Color.Black.copy(alpha = if (depth == 1) 0.45f else 0.7f), BlendMode.SrcAtop),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * A slideshow card's stats: how many pictures, the time per picture (hourglass, like the editor) and
 * Shuffle, then one bar per picture in the slideshow's order. The bar of the picture on the card lights
 * up: in order it fills up like stories, with Shuffle the light jumps around.
 */
@Composable
private fun SlideshowStats(count: Int, seconds: Int, shuffle: Boolean, tap: WidgetTap, shownIndex: Int, description: String) {
    val accent = MaterialTheme.colorScheme.primary
    // TalkBack reads the whole stats block as one sentence, the tap included.
    val tapText = tapDescription(tap)
    Column(Modifier.clearAndSetSemantics { contentDescription = listOfNotNull(description, tapText).joinToString(". ") }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardStat(R.drawable.ic_photo_library, "$count")
            CardStat(R.drawable.ic_duration, shortTime(seconds))
            if (shuffle) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            // What a tap does (no name here: this row is already full).
            if (tap.action != TapAction.EDIT) TapStat(tap, withName = false)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
            repeat(count) { i ->
                val lit = if (shuffle) i == shownIndex else i <= shownIndex
                val color by animateColorAsState(
                    when {
                        i == shownIndex -> accent
                        lit -> accent.copy(alpha = 0.55f)
                        else -> accent.copy(alpha = 0.2f)
                    },
                    label = "slide bar"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun CardStat(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** "3 s", "5 min", "1 h", "1 day": short, for the card. */
@Composable
private fun shortTime(seconds: Int): String = when {
    seconds >= 86400 && seconds % 86400 == 0 -> pluralStringResource(R.plurals.slideshow_days, seconds / 86400, seconds / 86400)
    seconds >= 3600 && seconds % 3600 == 0 -> stringResource(R.string.card_hours_short, seconds / 3600)
    seconds >= 60 && seconds % 60 == 0 -> stringResource(R.string.card_minutes_short, seconds / 60)
    else -> stringResource(R.string.card_seconds_short, seconds)
}

@Composable
private fun EmptyState(onAddWidget: (slideshow: Boolean) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Image(
                painter = painterResource(R.drawable.widget_placeholder),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { onAddWidget(false) }) {
                Icon(painterResource(R.drawable.ic_widget_normal), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_add_normal))
            }
            FilledTonalButton(onClick = { onAddWidget(true) }) {
                Icon(painterResource(R.drawable.ic_widget_slideshow), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_add_slideshow))
            }
        }
    }
}

/**
 * The "Add widget" button. Tapping it splits it into two: "Normal widget" and "Slideshow" (the same
 * two widgets the launcher's widget list offers). Picking one asks the launcher to place it.
 */
@Composable
private fun AddWidgetButton(open: Boolean, onOpen: () -> Unit, onPick: (slideshow: Boolean) -> Unit) {
    AnimatedContent(
        targetState = open,
        // The one button fades out while the two grow out of the same spot (and back when closing).
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.85f))
                .togetherWith(fadeOut(tween(120)))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.BottomEnd,
        label = "add widget"
    ) { isOpen ->
        if (!isOpen) {
            ExtendedFloatingActionButton(
                onClick = onOpen,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_widget)) }
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = { onPick(false) },
                    icon = { Icon(painterResource(R.drawable.ic_widget_normal), contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_normal), maxLines = 1) }
                )
                ExtendedFloatingActionButton(
                    onClick = { onPick(true) },
                    icon = { Icon(painterResource(R.drawable.ic_widget_slideshow), contentDescription = null) },
                    text = { Text(stringResource(R.string.home_add_slideshow), maxLines = 1) }
                )
            }
        }
    }
}
