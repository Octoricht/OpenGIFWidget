package com.octorice.wuxiagifwidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.octorice.wuxiagifwidget.gif.FrameExtractor
import java.io.File
import java.io.IOException
import kotlin.math.max

/**
 * Copies picked GIFs into the app's private storage (files/gifs/<widgetId>.gif).
 * We copy right away because the permission to read a picked file is temporary:
 * after a reboot the original Uri would no longer work.
 */
object GifStorage {

    private const val MAX_GIF_BYTES = 50L * 1024 * 1024

    fun gifFile(context: Context, appWidgetId: Int): File =
        File(File(context.filesDir, "gifs"), "$appWidgetId.gif")

    /** All frame folders of one widget: files/frames/<widgetId>/ */
    private fun widgetFramesDir(context: Context, appWidgetId: Int): File =
        File(File(context.filesDir, "frames"), "$appWidgetId")

    /** One set of frames: files/frames/<widgetId>/<version>/ */
    fun framesDir(context: Context, appWidgetId: Int, version: Long): File =
        File(widgetFramesDir(context, appWidgetId), "$version")

    fun frameFile(context: Context, appWidgetId: Int, version: Long, index: Int): File =
        File(framesDir(context, appWidgetId, version), FrameExtractor.frameFileName(index))

    /** Deletes everything stored for one widget: its GIF copy and all its frames. */
    fun deleteWidgetFiles(context: Context, appWidgetId: Int) {
        File(File(context.filesDir, "gifs"), "$appWidgetId.gif").delete()
        File(File(context.filesDir, "gifs"), "$appWidgetId.tmp").delete()
        widgetFramesDir(context, appWidgetId).deleteRecursively()
    }

    /**
     * Safety net: deletes GIFs and frames of widgets that no longer exist.
     * Files are named after their widget id ("19.gif", "frames/19/"), so anything whose id
     * isn't in [activeIds] is a leftover (e.g. Android didn't tell us a widget was removed).
     */
    fun deleteAllExcept(context: Context, activeIds: Set<Int>) {
        val gifsDir = File(context.filesDir, "gifs")
        val framesDir = File(context.filesDir, "frames")
        val files = gifsDir.listFiles().orEmpty().toList() + framesDir.listFiles().orEmpty().toList()
        for (file in files) {
            val id = file.name.substringBefore('.').toIntOrNull()
            if (id == null || id !in activeIds) file.deleteRecursively()
        }
    }

    /** Deletes frames from GIFs this widget used before, keeping only [keepVersion]. */
    fun deleteOldFrames(context: Context, appWidgetId: Int, keepVersion: Long) {
        widgetFramesDir(context, appWidgetId).listFiles()
            ?.filter { it.name != "$keepVersion" }
            ?.forEach { it.deleteRecursively() }
    }

    /** Copies the GIF and returns its info. Slow, so call it off the main thread. */
    fun importGif(context: Context, uri: Uri, appWidgetId: Int): StoredGif {
        val target = gifFile(context, appWidgetId)
        target.parentFile?.mkdirs()
        // Copy to a temp file first, so a failed copy never replaces a working GIF.
        val temp = File(target.parentFile, "$appWidgetId.tmp")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Couldn't open that file")
            var total = 0L
            input.use { inp ->
                temp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = inp.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_GIF_BYTES) throw IOException("That GIF is bigger than 50 MB")
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (!isGif(temp)) throw IOException("That file isn't a GIF")
            if (!temp.renameTo(target)) throw IOException("Couldn't save the GIF")
            return StoredGif(displayName(context, uri), total)
        } finally {
            temp.delete()
        }
    }

    /**
     * Loads a saved frame shrunk to roughly [maxSidePx] (halving the size until it fits),
     * which uses much less memory than loading it full size. Returns null if missing.
     */
    fun loadFrame(file: File, maxSidePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxSidePx) sampleSize *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    /** Every GIF file starts with the letters "GIF87a" or "GIF89a". */
    private fun isGif(file: File): Boolean {
        val header = ByteArray(6)
        val read = file.inputStream().use { it.read(header) }
        return read == 6 && String(header, Charsets.US_ASCII).startsWith("GIF8")
    }

    private fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.let { return it }
            }
        return "GIF"
    }
}
