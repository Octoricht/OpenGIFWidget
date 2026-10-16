package com.octorice.opengifwidget.data

import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.text.format.Formatter
import com.octorice.opengifwidget.R
import com.octorice.opengifwidget.gif.FrameFiles
import java.io.File
import java.io.IOException

/**
 * Where everything is stored:
 * - files/frames/<widgetId>/<version>/frame_000.webp ... : frame sets (master frames and widget frames)
 * - cache/import-<widgetId> : a picked file, only while it's being decoded
 * Original files are not kept: the master frames replace them.
 */
/** A picked file copied into the cache: its name, size, and whether it's a GIF (else a still picture). */
data class ImportedFile(val name: String, val sizeBytes: Long, val isGif: Boolean)

object GifStorage {

    /** Biggest picture or GIF we accept. */
    private const val MAX_FILE_BYTES = 50_000_000L // "50 MB" as Android displays sizes
    /** Biggest video we accept. Videos are read in place (never copied), so this can be large. */
    private const val MAX_VIDEO_BYTES = 1_000_000_000L // "1 GB"
    private const val MAX_COUNT_BYTES = 4L * 1024 * 1024 * 1024
    private const val MIN_FREE_BYTES = 30L * 1024 * 1024

    /** All frame folders of one widget: files/frames/<widgetId>/ */
    private fun widgetFramesDir(context: Context, appWidgetId: Int): File =
        File(File(context.filesDir, "frames"), "$appWidgetId")

    /** One set of frames: files/frames/<widgetId>/<version>/ */
    fun framesDir(context: Context, appWidgetId: Int, version: Long): File =
        File(widgetFramesDir(context, appWidgetId), "$version")

    fun frameFile(context: Context, appWidgetId: Int, version: Long, index: Int): File =
        File(framesDir(context, appWidgetId, version), FrameFiles.frameFileName(index))

    /** A picked file waiting to be decoded. Deleted right after. */
    fun importCopyFile(context: Context, appWidgetId: Int): File =
        File(context.cacheDir, "import-$appWidgetId")

    /** How much space one widget uses: all its frames. */
    fun widgetStorageBytes(context: Context, appWidgetId: Int): Long =
        widgetFramesDir(context, appWidgetId).walk().filter { it.isFile }.sumOf { it.length() }

    /** Deletes every frame folder of this widget except the ones in [keepVersions]. */
    fun deleteFramesExcept(context: Context, appWidgetId: Int, keepVersions: Set<Long>) {
        widgetFramesDir(context, appWidgetId).listFiles()
            ?.filter { it.name.toLongOrNull() !in keepVersions }
            ?.forEach { it.deleteRecursively() }
    }

    /** Deletes everything stored for one widget. */
    fun deleteWidgetFiles(context: Context, appWidgetId: Int) {
        widgetFramesDir(context, appWidgetId).deleteRecursively()
        importCopyFile(context, appWidgetId).delete()
    }

    /**
     * Safety net: deletes frames of widgets that no longer exist.
     * Folders are named after their widget id ("frames/19/"), so anything whose id
     * isn't in [activeIds] is a leftover (e.g. Android didn't tell us a widget was removed).
     * Also removes the "gifs" folder that older versions used for original files.
     */
    fun deleteAllExcept(context: Context, activeIds: Set<Int>) {
        File(context.filesDir, "gifs").deleteRecursively()
        File(context.filesDir, "frames").listFiles().orEmpty().forEach { folder ->
            val id = folder.name.toIntOrNull()
            if (id == null || id !in activeIds) folder.deleteRecursively()
        }
    }

    /**
     * Copies a picked file to [importCopyFile] (so it can be decoded) and says what it is.
     * Checks the size limit (before and while copying) and free storage first.
     * Slow, so call it off the main thread. Throws [ImportException] with a friendly message.
     */
    fun importFile(context: Context, uri: Uri, appWidgetId: Int): ImportedFile {
        val target = importCopyFile(context, appWidgetId)
        return friendlyErrors(cleanUp = { target.delete() }) {
            val (name, reportedSize) = nameAndSize(context, uri)
            if (reportedSize != null && reportedSize > MAX_FILE_BYTES) throw tooLarge(context, reportedSize, MAX_FILE_BYTES)
            // Room for the copy plus the frames made from it, with some margin.
            val needed = (reportedSize ?: 0L) * 2 + MIN_FREE_BYTES
            if (availableBytes(context, context.cacheDir) < needed || availableBytes(context, context.filesDir) < needed) {
                throw ImportException(R.string.error_no_space)
            }

            val input = try {
                context.contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                null // permission lost, file deleted meanwhile, ...
            } ?: throw ImportException(R.string.error_cannot_open)

            var total = 0L
            input.use { inp ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = inp.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FILE_BYTES) {
                            // The picker didn't tell us the size: count the rest for the message.
                            while (true) {
                                val more = inp.read(buffer)
                                if (more < 0 || total > MAX_COUNT_BYTES) break
                                total += more
                            }
                            throw tooLarge(context, total, MAX_FILE_BYTES)
                        }
                        out.write(buffer, 0, read)
                    }
                }
            }
            if (total == 0L) throw ImportException(R.string.error_unsupported)
            ImportedFile(name, total, isGif(target))
        }
    }

    /** Free space for [dir], counting space Android can free by clearing other apps' caches. */
    private fun availableBytes(context: Context, dir: File): Long = try {
        val storage = context.getSystemService(StorageManager::class.java)
        storage.getAllocatableBytes(storage.getUuidForPath(dir))
    } catch (e: Exception) {
        @Suppress("UsableSpace") // fallback if the storage service can't answer
        dir.usableSpace
    }

    private fun tooLarge(context: Context, size: Long, limit: Long) = ImportException(
        R.string.error_too_large,
        Formatter.formatShortFileSize(context, size),
        Formatter.formatShortFileSize(context, limit)
    )

    /** True if the picked file is a video (the picker tells us its type). */
    fun isVideo(context: Context, uri: Uri): Boolean =
        try {
            context.contentResolver.getType(uri)?.startsWith("video/") == true
        } catch (e: Exception) {
            false
        }

    /**
     * Checks a picked video before reading it: its size (1 GB limit) and free storage for its frames.
     * Returns its name and size. Throws [ImportException] with a friendly message.
     */
    fun checkVideo(context: Context, uri: Uri): ImportedFile {
        val (name, size) = nameAndSize(context, uri)
        if (size != null && size > MAX_VIDEO_BYTES) throw tooLarge(context, size, MAX_VIDEO_BYTES)
        // Up to 150 frames of a few hundred KB each, with some margin.
        if (availableBytes(context, context.filesDir) < MIN_FREE_BYTES + 60L * 1024 * 1024) {
            throw ImportException(R.string.error_no_space)
        }
        return ImportedFile(name, size ?: 0L, isGif = false)
    }

    /** Every GIF file starts with the letters "GIF87a" or "GIF89a". */
    private fun isGif(file: File): Boolean {
        val header = ByteArray(6)
        val read = file.inputStream().use { it.read(header) }
        return read == 6 && String(header, Charsets.US_ASCII).startsWith("GIF8")
    }

    /** The file's name and size, as the picker reports them (the size may be unknown). */
    private fun nameAndSize(context: Context, uri: Uri): Pair<String, Long?> {
        var name = "Picture"
        var size: Long? = null
        try {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.let { name = it }
                    if (!cursor.isNull(1)) size = cursor.getLong(1)
                }
            }
        } catch (e: Exception) {
            // Some file providers don't answer; the name and size are just nice to have.
        }
        return name to size
    }
}
