package com.octorice.opengifwidget.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.shape.Shapes
import com.octorice.opengifwidget.shape.WidgetShape

/** The little "How does this work?" link that opens [ShapeHelpSheet]. */
@Composable
fun ShapeHelpLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(stringResource(R.string.shape_help_link))
    }
}

/**
 * A visual guide to making your own shapes (user-reviewed layout):
 * 1. three steps (pick a picture -> it becomes a cutter -> your GIF shows inside);
 * 2. "Where do I find shapes?", highlighted near the top (Pinterest first);
 * 3. the two kinds of pictures, each in its own box: pictures without transparency first (darkness
 *    decides: black = shape, gray = partly, both examples in one box), then transparent pictures.
 * No tips section (user's choice: keep it short).
 * All drawings are made in code (no image files), so they follow light / dark mode and the theme
 * colors. The pretend GIF is a slowly moving gradient, so it reads as an animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShapeHelpSheet(onClose: () -> Unit, onTryIt: (() -> Unit)? = null) {
    // The pretend GIF's colors slide along slowly, forever.
    val phase by rememberInfiniteTransition(label = "gif").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "gif"
    )
    // The cutter in the same dark shade as the real "Your new shape" preview (see MaskPreview).
    val colors = MaterialTheme.colorScheme
    val cutterColor = if (colors.primary.luminance() > 0.4f) colors.primaryContainer else colors.primary

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(stringResource(R.string.shape_help_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.shape_help_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )

            // 1. Pick a picture  2. It becomes a cutter  3. Your GIF shows inside
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                HelpStep(1, stringResource(R.string.shape_help_step_pick)) { stickerTile(Shapes.HEART) }
                HelpArrow()
                HelpStep(2, stringResource(R.string.shape_help_step_cutter)) { cutterTile(Shapes.HEART, cutterColor) }
                HelpArrow()
                HelpStep(3, stringResource(R.string.shape_help_step_widget)) { widgetTile(Shapes.HEART, phase) }
            }

            // Where to find shapes: right at the top, highlighted (most people won't draw their own).
            Spacer(Modifier.height(20.dp))
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.shape_help_find_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.shape_help_find_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer
                    )
                }
            }

            // Two kinds of pictures, each in its own box.
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.shape_help_examples_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.shape_help_examples_intro),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Pictures without transparency first (the most common kind: drawings, stencils, photos).
            HelpKind(stringResource(R.string.shape_help_dark_title), stringResource(R.string.shape_help_dark_text)) {
                HelpExample(
                    text = stringResource(R.string.shape_help_black_text),
                    input = { stencilTile(Shapes.STAR) },
                    result = { widgetTile(Shapes.STAR, phase) }
                )
                Spacer(Modifier.height(10.dp))
                HelpExample(
                    text = stringResource(R.string.shape_help_gray_text),
                    input = { grayTile() },
                    result = { fadedWidgetTile(phase) }
                )
            }
            Spacer(Modifier.height(12.dp))
            HelpKind(stringResource(R.string.shape_help_transparent_title), stringResource(R.string.shape_help_transparent_text)) {
                HelpExample(
                    text = stringResource(R.string.shape_help_transparent_example),
                    input = { stickerTile(Shapes.HEART) },
                    result = { widgetTile(Shapes.HEART, phase) }
                )
            }

            // Try it yourself: the real black and white picture, turned into a shape with the real steps.
            if (onTryIt != null) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.shape_help_try_title), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.shape_help_try_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(R.drawable.tutorial_cat),
                        contentDescription = stringResource(R.string.shape_help_try_picture),
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.width(16.dp))
                    androidx.compose.material3.Button(onClick = onTryIt, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.shape_help_try_button), textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.shape_help_got_it))
            }
        }
    }
}

/** One numbered step: a small drawing and a short caption under it. */
@Composable
private fun HelpStep(number: Int, caption: String, draw: DrawScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        HelpTile { draw() }
        Spacer(Modifier.height(6.dp))
        Text("$number. $caption", style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

/** One kind of picture, in its own box: a title, the rule in one line, then its examples. */
@Composable
private fun HelpKind(title: String, text: String, examples: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            examples()
        }
    }
}

/** One example: the picture you pick -> how your widget looks, with a short line next to it. */
@Composable
private fun HelpExample(text: String, input: DrawScope.() -> Unit, result: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HelpTile(size = 60) { input() }
        HelpArrow()
        HelpTile(size = 60) { result() }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/** A rounded tile for a drawing (decorative: the text next to it says the same thing). */
@Composable
private fun HelpTile(size: Int = 72, draw: DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {}
    ) {
        Canvas(Modifier.fillMaxSize()) { draw() }
    }
}

@Composable
private fun HelpArrow() {
    Text(
        "→",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .padding(top = 18.dp)
            .clearAndSetSemantics {}
    )
}

// --- The drawings ----------------------------------------------------------------------------

/** The pretend GIF: a bright gradient whose colors slide along with [phase] (0..1, looping). */
private fun DrawScope.pictureBrush(phase: Float): Brush {
    val shift = size.width * 2 * phase
    return Brush.linearGradient(
        listOf(Color(0xFFFF5FA2), Color(0xFFFFB347), Color(0xFF7C4DFF), Color(0xFFFF5FA2)),
        start = Offset(shift - size.width, 0f),
        end = Offset(shift + size.width, size.height),
        tileMode = TileMode.Repeated
    )
}

/** [shape] filling most of the tile, centered. */
private fun DrawScope.shapePath(shape: WidgetShape, fraction: Float = 0.72f): Path {
    val side = size.minDimension * fraction
    return shape.path(side, side).asComposePath().apply {
        translate(Offset((size.width - side) / 2, (size.height - side) / 2))
    }
}

/** A transparent checkerboard (the parts that get cut away look like this). */
private fun DrawScope.checker() {
    val cell = size.minDimension / 8
    drawRect(Color.White)
    for (y in 0 until 8) for (x in 0 until 8) {
        if ((x + y) % 2 == 0) drawRect(Color(0xFFCFCFCF), Offset(x * cell, y * cell), Size(cell, cell))
    }
}

/** A sticker: a colored shape on a transparent background (a PNG with transparency). */
private fun DrawScope.stickerTile(shape: WidgetShape) {
    checker()
    val path = shapePath(shape)
    drawPath(path, Color(0xFFFFC83D))
    drawPath(path, Color(0xFF8A5A00), style = Stroke(width = size.minDimension * 0.03f))
}

/** A black stencil on white. */
private fun DrawScope.stencilTile(shape: WidgetShape) {
    drawRect(Color.White)
    drawPath(shapePath(shape), Color.Black)
}

/** The cutter the app makes: the shape in a dark theme color over the checkerboard. */
private fun DrawScope.cutterTile(shape: WidgetShape, color: Color) {
    checker()
    drawPath(shapePath(shape), color)
}

/** The widget: the moving pretend GIF, showing only inside the shape. */
private fun DrawScope.widgetTile(shape: WidgetShape, phase: Float) {
    drawPath(shapePath(shape, 0.84f), pictureBrush(phase))
}

/** Gray: a soft blob, black in the middle fading to white (the same fade as its result). */
private fun DrawScope.grayTile() {
    drawRect(Color.White)
    val radius = size.minDimension * 0.45f
    drawCircle(
        Brush.radialGradient(0f to Color.Black, 0.35f to Color.Black, 1f to Color.White, center = center, radius = radius),
        radius = radius
    )
}

/**
 * The widget for the gray example: the picture, fading smoothly to nothing where the picture was
 * gray. On its own layer, the picture is kept only as much as a soft round mask allows (solid in
 * the middle, gone at the edge), the same way the app treats gray: no dark ring around it.
 */
private fun DrawScope.fadedWidgetTile(phase: Float) {
    val radius = size.minDimension * 0.45f
    drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
    drawRect(pictureBrush(phase))
    drawRect(
        Brush.radialGradient(
            0f to Color.White, 0.35f to Color.White, 0.7f to Color.White.copy(alpha = 0.45f), 1f to Color.Transparent,
            center = center, radius = radius
        ),
        blendMode = BlendMode.DstIn
    )
    drawContext.canvas.restore()
}
