package com.octorice.wuxiagifwidget.home

import android.text.format.Formatter
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.octorice.wuxiagifwidget.R
import com.octorice.wuxiagifwidget.data.ScaleMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.droidsonroids.gif.GifDrawable
import java.io.File

/** Same dark color as the widget's own background, so previews look like the real widget. */
private val WidgetBackground = Color(0xCC202020)

/**
 * The app's main screen: every GIF widget on the home screen, playing, in its real shape.
 * [refreshKey] changes every time the screen comes back into view, which reloads the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    refreshKey: Int,
    onOpenWidget: (appWidgetId: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var widgets by remember { mutableStateOf<List<WidgetSummary>?>(null) } // null = still loading
    var showHowToAdd by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        widgets = withContext(Dispatchers.IO) { HomeData.loadWidgets(context) }
    }

    val onAddWidget: () -> Unit = {
        if (HomeData.requestPinWidget(context)) {
            scope.launch { snackbarHostState.showSnackbar("Confirm in the dialog to place your widget") }
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
            LargeTopAppBar(
                title = { Text("Wuxia GIF", fontWeight = FontWeight.SemiBold) },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddWidget,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text("Add widget") }
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
                ScreenState.List -> WidgetGrid(widgets.orEmpty(), onOpenWidget)
            }
        }
    }

    if (showHowToAdd) {
        AlertDialog(
            onDismissRequest = { showHowToAdd = false },
            confirmButton = { TextButton(onClick = { showHowToAdd = false }) { Text("Got it") } },
            title = { Text("Add it from your home screen") },
            text = {
                Text(
                    "1. Long-press an empty spot on your home screen\n" +
                        "2. Tap Widgets\n" +
                        "3. Find WuxiaGIFWidget and drag it onto the screen"
                )
            }
        )
    }
}

private enum class ScreenState { Loading, Empty, List }

@Composable
private fun WidgetGrid(widgets: List<WidgetSummary>, onOpenWidget: (Int) -> Unit) {
    val context = LocalContext.current
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        // Extra space at the bottom so the last cards aren't hidden behind the Add button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            StatsHeader(
                widgetCount = widgets.size,
                storage = Formatter.formatShortFileSize(context, widgets.sumOf { it.storageBytes })
            )
        }
        items(widgets, key = { it.appWidgetId }) { widget ->
            WidgetCard(
                widget = widget,
                onClick = { onOpenWidget(widget.appWidgetId) },
                modifier = Modifier.animateItem() // Cards slide smoothly when the list changes.
            )
        }
        item(span = StaggeredGridItemSpan.FullLine) {
            Text(
                "Tap a widget to change its GIF or switch between Fit and Fill.",
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
private fun StatsHeader(widgetCount: Int, storage: String) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Stat(value = "$widgetCount", label = if (widgetCount == 1) "widget" else "widgets", Modifier.weight(1f))
            Stat(value = storage, label = "storage used", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun WidgetCard(widget: WidgetSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frames = widget.gif?.frames
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        // The preview has the same shape as the widget on the home screen.
        Box(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .aspectRatio(widget.aspectRatio)
                .clip(RoundedCornerShape(18.dp))
                .background(WidgetBackground)
        ) {
            if (widget.gifFile != null) {
                GifPreview(widget.gifFile, widget.scaleMode, Modifier.fillMaxSize())
                ModeBadge(
                    widget.scaleMode,
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
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
                widget.gif?.name?.substringBeforeLast('.') ?: "No GIF yet",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (frames != null) {
                    "${frames.frameCount} frames · ${Formatter.formatShortFileSize(context, widget.storageBytes)}"
                } else {
                    "Tap to choose a GIF"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
            if (mode == ScaleMode.FIT) "Fit" else "Fill",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Plays the original GIF with android-gif-drawable's GifDrawable. It decodes one frame at a
 * time on its own background thread, so it stays smooth and light even for big GIFs.
 * GifDrawable is a classic Android Drawable, so it's shown in a classic ImageView inside Compose.
 */
@Composable
private fun GifPreview(file: File, scaleMode: ScaleMode, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context -> ImageView(context) },
        update = { view ->
            // Only reload when the GIF actually changed (a re-pick replaces the file).
            val key = "${file.path}:${file.lastModified()}"
            if (view.tag != key) {
                (view.drawable as? GifDrawable)?.recycle()
                view.setImageDrawable(runCatching { GifDrawable(file) }.getOrNull())
                view.tag = key
            }
            view.scaleType = when (scaleMode) {
                ScaleMode.FIT -> ImageView.ScaleType.FIT_CENTER
                ScaleMode.FILL -> ImageView.ScaleType.CENTER_CROP
            }
        },
        onRelease = { view ->
            // Frees the GIF's memory when the card scrolls away or the screen closes.
            (view.drawable as? GifDrawable)?.recycle()
            view.setImageDrawable(null)
        },
        modifier = modifier
    )
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
        Text("No GIF widgets yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Put a widget on your home screen, pick any GIF, and watch it play right there.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onAddWidget) { Text("Add your first widget") }
    }
}
