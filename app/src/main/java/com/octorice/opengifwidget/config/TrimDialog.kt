package com.octorice.opengifwidget.config

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.gif.VideoInfo
import com.octorice.opengifwidget.gif.VideoSource
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

/** Longest clip a video widget can have. */
const val MAX_CLIP_MS = 5_000L
private const val MIN_CLIP_MS = 500L

/**
 * Picks which part of a video becomes the widget: a start time and a length (up to 5 s),
 * with a thumbnail of the clip's first frame. [loadThumbnail] returns that frame (slow, so it's
 * only asked for once the slider stops moving).
 */
@Composable
fun TrimDialog(
    info: VideoInfo,
    fpsCap: Int,
    loadThumbnail: suspend (timeMs: Long) -> Bitmap?,
    onCancel: () -> Unit,
    onConfirm: (startMs: Long, lengthMs: Long) -> Unit
) {
    val duration = info.durationMs
    val minLength = min(MIN_CLIP_MS, duration)
    var startMs by remember { mutableFloatStateOf(0f) }
    var lengthMs by remember { mutableFloatStateOf(min(MAX_CLIP_MS, duration).toFloat()) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    val maxStart = (duration - minLength).coerceAtLeast(0L).toFloat()
    val maxLength = min(MAX_CLIP_MS.toFloat(), duration - startMs).coerceAtLeast(minLength.toFloat())

    // Update the thumbnail shortly after the start slider stops moving.
    LaunchedEffect(startMs) {
        delay(150)
        thumbnail = loadThumbnail(startMs.toLong())
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(stringResource(R.string.trim_title), style = MaterialTheme.typography.headlineSmall)

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .aspectRatio(info.width / info.height.toFloat(), matchHeightConstraintsFirst = info.height > info.width)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    val shown = thumbnail
                    if (shown != null) {
                        Image(
                            shown.asImageBitmap(),
                            contentDescription = stringResource(R.string.trim_start_frame),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }

                Text(stringResource(R.string.trim_start, formatTime(startMs.toLong())))
                Slider(
                    value = startMs,
                    onValueChange = {
                        startMs = it
                        // Keep the clip inside the video.
                        lengthMs = lengthMs.coerceAtMost((duration - it).coerceAtLeast(minLength.toFloat()))
                    },
                    valueRange = 0f..maxStart.coerceAtLeast(0.01f),
                    enabled = maxStart > 0f
                )

                val frames = VideoSource.frameCount(lengthMs.toLong(), fpsCap)
                Text(pluralStringResource(R.plurals.trim_length, frames, formatSeconds(lengthMs.toLong()), frames))
                Slider(
                    value = lengthMs.coerceIn(minLength.toFloat(), maxLength),
                    onValueChange = { lengthMs = it },
                    valueRange = minLength.toFloat()..maxLength.coerceAtLeast(minLength + 0.01f),
                    enabled = maxLength > minLength
                )

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.trim_cancel)) }
                    Button(onClick = {
                        onConfirm(startMs.toLong(), lengthMs.toLong().coerceIn(minLength, min(MAX_CLIP_MS, duration)))
                    }) { Text(stringResource(R.string.trim_use)) }
                }
            }
        }
    }
}

/** "1:05.3" (minutes:seconds.tenths), in the phone's number format. */
private fun formatTime(ms: Long): String {
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1000.0
    val format = NumberFormat.getNumberInstance().apply {
        minimumIntegerDigits = 2
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return String.format(Locale.getDefault(), "%d:%s", minutes, format.format(seconds))
}

/** "4.0" (seconds), in the phone's number format. */
private fun formatSeconds(ms: Long): String {
    val format = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }
    return format.format(ms / 1000.0)
}
