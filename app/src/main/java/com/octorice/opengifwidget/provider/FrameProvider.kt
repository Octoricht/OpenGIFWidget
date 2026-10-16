package com.octorice.opengifwidget.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.octorice.opengifwidget.data.GifStorage
import java.io.FileNotFoundException

/**
 * Hands out the saved WebP frames to the launcher.
 *
 * The widget is drawn by the launcher (a different app), which can't read our private files.
 * Instead we give it addresses like content://com.octorice.opengifwidget.frames/42/1712345/7
 * (widget 42, version 1712345, frame 7) and the launcher asks this provider to open them.
 *
 * It's read-only and only serves frame files: every part of the address must be a number,
 * so nobody can use it to reach any other file.
 */
class FrameProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Frames are read-only")
        val context = context ?: throw FileNotFoundException(uri.toString())

        val segments = uri.pathSegments
        val appWidgetId = segments.getOrNull(0)?.toIntOrNull()
        val version = segments.getOrNull(1)?.toLongOrNull()
        val index = segments.getOrNull(2)?.toIntOrNull()
        if (segments.size != 3 || appWidgetId == null || version == null || index == null || index < 0) {
            throw FileNotFoundException(uri.toString())
        }

        val file = GifStorage.frameFile(context, appWidgetId, version, index)
        if (!file.isFile) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/webp"

    // We only serve files, so the database-style methods do nothing.
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        fun frameUri(context: Context, appWidgetId: Int, version: Long, index: Int): Uri =
            Uri.Builder()
                .scheme("content")
                .authority("${context.packageName}.frames")
                .appendPath("$appWidgetId")
                .appendPath("$version")
                .appendPath("$index")
                .build()
    }
}
