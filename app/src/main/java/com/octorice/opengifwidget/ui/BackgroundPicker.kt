package com.octorice.opengifwidget.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.data.ImportException
import com.octorice.opengifwidget.data.WidgetBackground
import com.octorice.opengifwidget.data.isNoSpaceLeft
import kotlin.math.roundToInt

/** The background as a Compose color (with its opacity), for previews. */
fun WidgetBackground.toComposeColor(): Color = Color(color).copy(alpha = alpha / 255f)

/** A message for the user explaining what went wrong, in plain words. */
fun Throwable.friendlyMessage(context: Context): String = when (this) {
    is ImportException -> context.getString(messageRes, *formatArgs)
    else -> context.getString(if (isNoSpaceLeft()) R.string.error_no_space else R.string.error_generic)
}

private data class Swatch(val color: Int, @StringRes val name: Int)

private val Swatches = listOf(
    Swatch(0xFF202020.toInt(), R.string.color_dark),
    Swatch(0xFF000000.toInt(), R.string.color_black),
    Swatch(0xFFFFFFFF.toInt(), R.string.color_white),
    Swatch(0xFF5F6368.toInt(), R.string.color_gray),
    Swatch(0xFFF8BBD0.toInt(), R.string.color_pink),
    Swatch(0xFFD1C4E9.toInt(), R.string.color_lavender),
    Swatch(0xFFB3E5FC.toInt(), R.string.color_sky),
    Swatch(0xFFC8E6C9.toInt(), R.string.color_mint),
    Swatch(0xFFFFF59D.toInt(), R.string.color_butter),
    Swatch(0xFFFFCCBC.toInt(), R.string.color_peach)
)

/** Color circles ("Transparent" first) and an opacity slider for the widget's background. */
@Composable
fun BackgroundPicker(background: WidgetBackground, enabled: Boolean, onChange: (WidgetBackground) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SwatchCircle(
                    name = stringResource(R.string.color_transparent),
                    selected = background.alpha == 0,
                    enabled = enabled,
                    onClick = { onChange(background.copy(alpha = 0)) }
                ) { Checkerboard() }
            }
            items(Swatches) { swatch ->
                SwatchCircle(
                    name = stringResource(swatch.name),
                    selected = background.alpha > 0 && background.color == swatch.color,
                    enabled = enabled,
                    // Picking a color after "Transparent" makes it visible again (solid).
                    onClick = { onChange(WidgetBackground(swatch.color, if (background.alpha == 0) 255 else background.alpha)) }
                ) {
                    Box(Modifier.fillMaxSize().background(Color(swatch.color)))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.editor_opacity, (background.alpha / 255f * 100).roundToInt()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = background.alpha / 255f,
            onValueChange = { onChange(background.copy(alpha = (it * 255).roundToInt())) },
            enabled = enabled
        )
    }
}

@Composable
private fun SwatchCircle(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    fill: @Composable () -> Unit
) {
    // Selected: a thick ring in the theme color with a small gap; otherwise a thin outline.
    val ring = if (selected) {
        Modifier
            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
            .padding(5.dp)
    } else {
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(1.dp)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name }
            .then(ring)
            .clip(CircleShape)
    ) { fill() }
}

/** The usual gray-and-white checkerboard that means "see-through". */
@Composable
private fun Checkerboard() {
    Canvas(Modifier.fillMaxSize()) {
        val cell = 6.dp.toPx()
        drawRect(Color(0xFFFFFFFF))
        var y = 0
        while (y * cell < size.height) {
            var x = 0
            while (x * cell < size.width) {
                if ((x + y) % 2 == 0) drawRect(Color(0xFFBDBDBD), Offset(x * cell, y * cell), Size(cell, cell))
                x++
            }
            y++
        }
    }
}
