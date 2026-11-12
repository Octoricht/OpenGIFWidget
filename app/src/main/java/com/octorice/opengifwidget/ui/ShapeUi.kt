package com.octorice.opengifwidget.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.WidgetBackground
import com.octorice.opengifwidget.shape.ShapeMask
import com.octorice.opengifwidget.shape.Shapes
import com.octorice.opengifwidget.shape.WidgetShape

/** The shape's mask at this size, made once (and again only if the shape or size changes). */
@Composable
private fun rememberMask(shape: WidgetShape, size: IntSize): ImageBitmap =
    remember(shape, size) { ShapeMask.render(shape, size.width, size.height).asImageBitmap() }

/**
 * Draws like a widget with a shape: the shape's box (the biggest box with the shape's aspect that
 * fits here, centered), the background taking the shape, and [content] cut by the same mask that
 * cuts the real widget frames. [cutContent] = false for frames that are already cut (the app's cards).
 */
@Composable
fun ShapedBox(
    shape: WidgetShape,
    background: WidgetBackground,
    modifier: Modifier = Modifier,
    cutContent: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val boxWidth = min(maxWidth, maxHeight * shape.aspect)
        val boxHeight = boxWidth / shape.aspect
        val sizePx = with(LocalDensity.current) { IntSize(boxWidth.roundToPx(), boxHeight.roundToPx()) }
        val mask = rememberMask(shape, sizePx)
        val tint = Color(background.color)
        val alpha = background.alpha / 255f
        Box(
            Modifier
                .size(boxWidth, boxHeight)
                // The background: the mask tinted with the color, faded to the opacity (like the widget).
                .drawBehind {
                    if (alpha > 0f) drawImage(mask, alpha = alpha, colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn))
                }
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (cutContent) {
                            Modifier
                                // Drawn on its own layer, so the mask only cuts the picture, not what's behind.
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawImage(mask, blendMode = BlendMode.DstIn) // keep only what the mask keeps
                                }
                        } else {
                            Modifier
                        }
                    ),
                content = content
            )
        }
    }
}

/** "None" and every shape as small tiles, scrolling sideways (room for more shapes later). */
@Composable
fun ShapePicker(shape: WidgetShape?, enabled: Boolean, onChange: (WidgetShape?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            ShapeTile(stringResource(R.string.shape_none), selected = shape == null, enabled = enabled, onClick = { onChange(null) }) { color ->
                // A crossed-out circle: "no shape".
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    val radius = size.minDimension / 2 - stroke
                    drawCircle(color, radius, style = Stroke(stroke))
                    val d = radius * 0.7071f
                    drawLine(color, Offset(center.x - d, center.y + d), Offset(center.x + d, center.y - d), stroke)
                }
            }
        }
        items(Shapes.all, key = { it.id }) { item ->
            ShapeTile(stringResource(item.nameRes), selected = shape == item, enabled = enabled, onClick = { onChange(item) }) { color ->
                // The icon is the shape itself, drawn from the same outline the widget uses.
                Canvas(Modifier.fillMaxSize()) {
                    val boxWidth = kotlin.math.min(size.width, size.height * item.aspect)
                    val boxHeight = boxWidth / item.aspect
                    val path = item.path(boxWidth, boxHeight).asComposePath()
                    path.translate(Offset((size.width - boxWidth) / 2, (size.height - boxHeight) / 2))
                    drawPath(path, color)
                }
            }
        }
    }
}

@Composable
private fun ShapeTile(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val tileShape = RoundedCornerShape(14.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(tileShape)
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerHigh)
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) colors.primary else colors.outlineVariant,
                    tileShape
                )
                .padding(12.dp)
        ) {
            icon(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2 // longer names ("Chonky star") wrap instead of being cut off
        )
    }
}
