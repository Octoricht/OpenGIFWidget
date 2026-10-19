package com.octorice.opengifwidget.config

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.gif.VideoInfo
import com.octorice.opengifwidget.gif.VideoSource
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

private const val MIN_CLIP_MS = 500L

/** Which slider is being dragged right now, if any. */
private enum class Scrubbing { START, END }

/**
 * Picks which part of a video becomes the widget: a start time and a length (up to 2-3 s, by phone tier).
 * The preview plays the chosen clip in a loop; while you drag a slider it stops and shows the
 * exact frame you're on (the first frame for Start, the last frame for Length).
 * [loadFrame] gives a still frame, used only if this phone can't play the video live.
 */
@Composable
fun TrimDialog(
    uri: Uri,
    info: VideoInfo,
    fpsCap: Int,
    maxClipMs: Long, // longest clip this phone allows (2-3 s, see DeviceTier)
    loadFrame: suspend (timeMs: Long) -> Bitmap?,
    onCancel: () -> Unit,
    onConfirm: (startMs: Long, lengthMs: Long) -> Unit
) {
    val duration = info.durationMs
    val minLength = min(MIN_CLIP_MS, duration)
    var startMs by remember { mutableFloatStateOf(0f) }
    var lengthMs by remember { mutableFloatStateOf(min(maxClipMs, duration).toFloat()) }
    var scrubbing by remember { mutableStateOf<Scrubbing?>(null) }
    val maxStart = (duration - minLength).coerceAtLeast(0L).toFloat()
    val maxLength = min(maxClipMs.toFloat(), duration - startMs).coerceAtLeast(minLength.toFloat())
    val endMs = (startMs + lengthMs).toLong().coerceAtMost(duration)
    // The frame to hold while dragging; slightly before the end so it's a real picture.
    val scrubMs = when (scrubbing) {
        Scrubbing.START -> startMs.toLong()
        Scrubbing.END -> (endMs - 1).coerceAtLeast(startMs.toLong())
        null -> null
    }

    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Scrolls when the screen is short (e.g. a phone in landscape).
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
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
                    ClipPreview(
                        uri = uri,
                        startMs = startMs.toLong(),
                        endMs = endMs,
                        scrubMs = scrubMs,
                        fallbackFrame = loadFrame,
                        modifier = Modifier.fillMaxSize()
                    )
                    // While dragging, say which end of the clip this frame is.
                    scrubbing?.let { which ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.6f),
                            contentColor = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                        ) {
                            Text(
                                stringResource(if (which == Scrubbing.START) R.string.trim_first_frame else R.string.trim_last_frame),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Text(stringResource(R.string.trim_start, formatTime(startMs.toLong())))
                Slider(
                    value = startMs,
                    onValueChange = {
                        scrubbing = Scrubbing.START
                        startMs = it
                        // Keep the clip inside the video.
                        lengthMs = lengthMs.coerceAtMost((duration - it).coerceAtLeast(minLength.toFloat()))
                    },
                    onValueChangeFinished = { scrubbing = null }, // let go: play the clip again
                    valueRange = 0f..maxStart.coerceAtLeast(0.01f),
                    enabled = maxStart > 0f
                )

                val frames = VideoSource.frameCount(lengthMs.toLong(), fpsCap)
                Text(pluralStringResource(R.plurals.trim_length, frames, formatSeconds(lengthMs.toLong()), frames))
                Slider(
                    value = lengthMs.coerceIn(minLength.toFloat(), maxLength),
                    onValueChange = {
                        scrubbing = Scrubbing.END
                        lengthMs = it
                    },
                    onValueChangeFinished = { scrubbing = null },
                    valueRange = minLength.toFloat()..maxLength.coerceAtLeast(minLength + 0.01f),
                    enabled = maxLength > minLength
                )

                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.trim_cancel)) }
                    Button(onClick = {
                        onConfirm(startMs.toLong(), lengthMs.toLong().coerceIn(minLength, min(maxClipMs, duration)))
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
