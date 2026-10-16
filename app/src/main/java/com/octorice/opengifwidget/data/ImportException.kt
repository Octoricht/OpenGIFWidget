package com.octorice.opengifwidget.data

import androidx.annotation.StringRes
import com.octorice.opengifwidget.R
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * A problem we can explain to the user. [messageRes] is a text from strings.xml,
 * [formatArgs] fill in its blanks (e.g. "This file is %1$s. The limit is %2$s.").
 */
class ImportException(@StringRes val messageRes: Int, vararg val formatArgs: Any) : IOException()

/**
 * Runs [block] and turns anything that goes wrong into an [ImportException] with a friendly
 * message, so a damaged or unusual file can never crash the app. [cleanUp] runs on any failure
 * (e.g. to delete half-written frames).
 */
inline fun <T> friendlyErrors(cleanUp: () -> Unit = {}, block: () -> T): T {
    try {
        return block()
    } catch (e: CancellationException) {
        cleanUp()
        throw e // Not an error: the user cancelled.
    } catch (e: ImportException) {
        cleanUp()
        throw e
    } catch (e: OutOfMemoryError) {
        cleanUp()
        throw ImportException(R.string.error_out_of_memory)
    } catch (e: Exception) {
        cleanUp()
        throw ImportException(if (e.isNoSpaceLeft()) R.string.error_no_space else R.string.error_unsupported)
    }
}

/** "No space left on device" shows up as an IOException mentioning ENOSPC. */
fun Throwable.isNoSpaceLeft(): Boolean {
    var error: Throwable? = this
    while (error != null) {
        val message = error.message.orEmpty()
        if ("ENOSPC" in message || "No space left" in message) return true
        error = error.cause
    }
    return false
}
