package com.octorice.opengifwidget.home

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
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.AppSettings
import com.octorice.opengifwidget.data.ScaleMode
import com.octorice.opengifwidget.ui.FrameImage
import com.octorice.opengifwidget.ui.toComposeColor
import com.octorice.opengifwidget.ui.rememberFramePlayer
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
    val onAddWidget: () -> Unit = {
        if (HomeData.requestPinWidget(context)) {
            scope.launch { snackbarHostState.showSnackbar(pinConfirmMessage) }
        } else {
            showHowToAdd = true // This launcher can't place widgets for us.
        }
    }

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
            ExtendedFloatingActionButton(
                onClick = onAddWidget,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_widget)) }
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
        // The preview has the same shape as the widget on the home screen.
        Box(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .aspectRatio(widget.aspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(widget.background.toComposeColor())
        ) {
            // The widget's frames are already cropped for Fill, so Fill just centers them here.
            val image = rememberFramePlayer(widget.framesDir, frames, maxSidePx = 512, widget.speed, fpsCap)
            if (image != null) {
                FrameImage(image, widget.scaleMode, { Offset(0.5f, 0.5f) }, Modifier.fillMaxSize())
                ModeBadge(
                    widget.scaleMode,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
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
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
            Text(
                widget.name.ifEmpty { stringResource(R.string.no_picture_yet) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
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

/** Small "Fit" / "Fill" pill on top of the preview. */
@Composable
private fun ModeBadge(mode: ScaleMode, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        modifier = modifier
    ) {
        Text(
            stringResource(if (mode == ScaleMode.FIT) R.string.mode_fit else R.string.mode_fill),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyState(onAddWidget: () -> Unit) {
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
        FilledTonalButton(onClick = onAddWidget) { Text(stringResource(R.string.home_empty_button)) }
    }
}
