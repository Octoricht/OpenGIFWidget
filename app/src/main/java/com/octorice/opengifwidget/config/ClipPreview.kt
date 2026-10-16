package com.octorice.opengifwidget.config

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Live preview of a video clip for the trim dialog, using Android's built-in MediaPlayer (muted).
 * - [scrubMs] == null: plays [startMs]..[endMs] over and over, like the widget will.
 * - [scrubMs] set (a slider is being dragged): paused on exactly that frame.
 * Drawn in a TextureView, which (unlike a SurfaceView) respects the rounded corners around it.
 * If this phone's player can't play the file, [fallbackFrame] shows a still frame instead.
 */
@Composable
fun ClipPreview(
    uri: Uri,
    startMs: Long,
    endMs: Long,
    scrubMs: Long?,
    fallbackFrame: suspend (timeMs: Long) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) } // set once ready to play
    var failed by remember { mutableStateOf(false) }
    val latestStart by rememberUpdatedState(startMs)
    val latestEnd by rememberUpdatedState(endMs)
    val latestScrub by rememberUpdatedState(scrubMs)

    if (failed) {
        StillFrame(scrubMs ?: startMs, fallbackFrame, modifier)
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        val mediaPlayer = MediaPlayer()
                        tag = mediaPlayer
                        try {
                            mediaPlayer.setDataSource(context, uri)
                            mediaPlayer.setSurface(Surface(texture))
                            mediaPlayer.setVolume(0f, 0f) // a preview never makes sound
                            mediaPlayer.setOnPreparedListener { ready ->
                                ready.seekTo(latestScrub ?: latestStart, MediaPlayer.SEEK_CLOSEST)
                                player = ready
                            }
                            mediaPlayer.setOnErrorListener { _, _, _ ->
                                failed = true
                                true
                            }
                            mediaPlayer.prepareAsync()
                        } catch (e: Exception) {
                            failed = true
                        }
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                        player = null
                        (tag as? MediaPlayer)?.release()
                        tag = null
                        return true
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
        onRelease = { view ->
            player = null
            (view.tag as? MediaPlayer)?.release()
            view.tag = null
        }
    )

    // Scrubbing: pause and jump to the exact frame. Not scrubbing: play the clip in a loop.
    LaunchedEffect(player, scrubMs) {
        val ready = player ?: return@LaunchedEffect
        try {
            if (scrubMs != null) {
                if (ready.isPlaying) ready.pause()
                ready.seekTo(scrubMs, MediaPlayer.SEEK_CLOSEST)
            } else {
                ready.seekTo(latestStart, MediaPlayer.SEEK_CLOSEST)
                ready.start()
                while (isActive) {
                    delay(30)
                    if (ready.currentPosition >= latestEnd) ready.seekTo(latestStart, MediaPlayer.SEEK_CLOSEST)
                }
            }
        } catch (e: IllegalStateException) {
            // The player was released meanwhile (dialog closing): nothing left to do.
        }
    }

    DisposableEffect(Unit) {
        onDispose { player = null }
    }
}

/** A single frame, loaded shortly after [timeMs] stops changing. */
@Composable
private fun StillFrame(timeMs: Long, loadFrame: suspend (Long) -> Bitmap?, modifier: Modifier) {
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(timeMs) {
        delay(150)
        frame = loadFrame(timeMs)
    }
    val shown = frame
    if (shown != null) {
        Image(shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier.fillMaxSize())
    } else {
        CircularProgressIndicator()
    }
}
