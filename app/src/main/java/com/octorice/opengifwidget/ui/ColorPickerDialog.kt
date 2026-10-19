package com.octorice.opengifwidget.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.octorice.opengifwidget.R
import java.util.Locale
import android.graphics.Color as AndroidColor

/** The rainbow used by the hue bar and the "Custom color" circle: red, yellow, green, cyan, blue, magenta, red. */
val RainbowColors = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it % 360f, 1f, 1f) }

/**
 * Pick any color: a square for how colorful (left to right) and how bright (top to bottom),
 * a rainbow bar for the hue, and a box for typing a hex code like #FF8800.
 * [initial] and the result are opaque colors as Android ints (opacity is the editor's slider).
 */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    // What's typed in the hex box. Updated when you drag; a valid code moves the square and bar.
    var hexText by remember { mutableStateOf(toHex(initial)) }

    fun setFromDrag(h: Float = hue, s: Float = saturation, v: Float = value) {
        hue = h
        saturation = s
        value = v
        hexText = toHex(AndroidColor.HSVToColor(floatArrayOf(h, s, v)))
    }

    // Older Android versions put the focus on the first text box when a dialog opens, which
    // pops up the keyboard. Start with the focus on the dialog itself instead.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .focusRequester(focus)
                    .focusable()
                    .verticalScroll(rememberScrollState())
            ) {
                SaturationValueSquare(hue, saturation, value) { s, v -> setFromDrag(s = s, v = v) }
                HueBar(hue) { setFromDrag(h = it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { typed ->
                            val clean = typed.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }.take(7)
                            hexText = clean
                            parseHex(clean)?.let { parsed ->
                                val hsv = FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
                                // Gray has no hue: keep the bar where it is instead of jumping to red.
                                if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text(stringResource(R.string.color_picker_hex)) },
                        singleLine = true,
                        isError = parseHex(hexText) == null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color) }) { Text(stringResource(R.string.color_picker_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.trim_cancel)) }
        }
    )
}

/** Left to right: gray to full color. Top to bottom: bright to black. A ring marks the current spot. */
@Composable
private fun SaturationValueSquare(hue: Float, saturation: Float, value: Float, onChange: (Float, Float) -> Unit) {
    // The colors are drawn a little inside the edges, so the ring stays whole at the far ends.
    val inset = with(LocalDensity.current) { RING_ROOM.toPx() }
    fun pick(position: Offset, width: Int, height: Int) = onChange(
        ((position.x - inset) / (width - 2 * inset)).coerceIn(0f, 1f),
        1f - ((position.y - inset) / (height - 2 * inset)).coerceIn(0f, 1f)
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width, size.height) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position, size.width, size.height)
                }
            }
    ) {
        val area = Size(size.width - 2 * inset, size.height - 2 * inset)
        val topLeft = Offset(inset, inset)
        val corner = CornerRadius(12.dp.toPx())
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f)), startX = inset, endX = inset + area.width),
            topLeft, area, corner
        )
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = inset, endY = inset + area.height),
            topLeft, area, corner
        )
        val spot = Offset(inset + saturation * area.width, inset + (1f - value) * area.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = spot, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 10.5.dp.toPx(), center = spot, style = Stroke(1.dp.toPx()))
    }
}

/** The rainbow bar for the hue, with a ring on the current one. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    // The ring's center stops one radius before each end, so it never sticks out.
    val radius = with(LocalDensity.current) { 12.dp.toPx() }
    fun pick(x: Float, width: Int) = onChange(((x - radius) / (width - 2 * radius)).coerceIn(0f, 1f) * 359.9f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    pick(change.position.x, size.width)
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(RainbowColors, startX = radius, endX = size.width - radius))
        val center = Offset(radius + hue / 360f * (size.width - 2 * radius), size.height / 2)
        drawCircle(Color.hsv(hue, 1f, 1f), radius = 10.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 10.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
    }
}

/** Room around the color square for its ring. */
private val RING_ROOM = 12.dp

private fun toHex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

/** "#FF8800" or "FF8800" -> an opaque color; anything else -> null. */
private fun parseHex(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
}
