package com.akira.app.core.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.akira.app.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

data class AkiraLogEntry(
    val timestamp: String,
    val thread: String,
    val level: String,
    val tag: String,
    val message: String
) {
    val estimatedBytes: Int = format().toByteArray(Charsets.UTF_8).size

    fun format(sanitizedMessage: String = message): String =
        "$timestamp [$thread] $level/$tag: $sanitizedMessage\n"
}

/** Bounded in-memory diagnostics. Export filters paths, URLs and credential fields. */
object AkiraLog {
    const val MAX_BUFFER_BYTES = 512 * 1024 // Maximum size: 512 KB.

    private val entries = ArrayDeque<AkiraLogEntry>()
    private var currentBytes = 0
    private val lock = Any()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    private val exportFilenameFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)


    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        log("W", tag, msg)
    }
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val msg = if (throwable != null) "$message\n${Log.getStackTraceString(throwable)}" else message
        log("E", tag, msg)
    }

    private fun log(level: String, tag: String, message: String) {
        val entry = AkiraLogEntry(
            timestamp = synchronized(isoFormat) { isoFormat.format(Date()) },
            thread = Thread.currentThread().name,
            level = level,
            tag = tag,
            message = message
        )
        synchronized(lock) {
            entries.addLast(entry)
            currentBytes += entry.estimatedBytes
            while (currentBytes > MAX_BUFFER_BYTES && entries.isNotEmpty()) {
                val removed = entries.removeFirst()
                currentBytes -= removed.estimatedBytes
            }
        }
    }

    fun getSnapshot(): List<AkiraLogEntry> = synchronized(lock) {
        entries.toList()
    }

    fun getCurrentSizeBytes(): Int = synchronized(lock) {
        currentBytes
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        currentBytes = 0
    }

    fun sanitize(text: String, includeTrackTitles: Boolean): String =
        LogPrivacyFilter.sanitize(text, includeTrackTitles)

    /** Export the in-memory log to a text file in cacheDir. */
    fun exportToFile(context: Context, includeTrackTitles: Boolean): File {
        val dateStr = synchronized(exportFilenameFormat) { exportFilenameFormat.format(Date()) }
        val directory = File(context.cacheDir, "exports/logs").apply { mkdirs() }
        val file = File.createTempFile("akira-diagnostico-$dateStr-", ".txt", directory)

        val snapshot = getSnapshot()
        file.bufferedWriter().use { writer ->
            writer.write("=== AKIRA MUSIC DIAGNOSTIC LOG ===\n")
            writer.write("App Version: ${BuildConfig.VERSION_NAME} (Package: ${context.packageName})\n")
            writer.write("Android OS: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            writer.write("Generated: ${synchronized(isoFormat) { isoFormat.format(Date()) }}\n")
            writer.write("Entries Count: ${snapshot.size}\n")
            writer.write("Buffer Size: ${getCurrentSizeBytes()} bytes\n")
            writer.write("Privacy Filter: includeTrackTitles=$includeTrackTitles\n")
            writer.write("================================\n\n")

            for (entry in snapshot) {
                val sanitizedMsg = sanitize(entry.message, includeTrackTitles)
                writer.write(entry.copy(
                    thread = sanitize(entry.thread, false),
                    tag = sanitize(entry.tag, false),
                ).format(sanitizedMsg))
            }
        }
        return file
    }

    /** Register an uncaught-exception handler to capture crashes. */
    fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("UncaughtException", "Fatal exception in thread '${thread.name}': ${throwable.message}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
