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
import java.util.regex.Pattern

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

/**
 * Sistema de diagnóstico y registro en memoria con búfer circular acotado a 512 KB.
 *
 * Captura eventos de Media3, CrossfadeEngine, escaneos de MediaStore y excepciones.
 * Permite exportar a archivo anonimizado para soporte o depuración.
 */
object AkiraLog {
    const val MAX_BUFFER_BYTES = 512 * 1024 // 512 KB máx.

    private val entries = ArrayDeque<AkiraLogEntry>()
    private var currentBytes = 0
    private val lock = Any()

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US)
    private val exportFilenameFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private val storagePattern = Pattern.compile("(/storage/emulated/0|/storage/[A-Za-z0-9_-]+|/sdcard)")
    private val appDataPattern = Pattern.compile("(/data/user/0/[a-zA-Z0-9._-]+|/data/data/[a-zA-Z0-9._-]+)")
    private val trackFilePattern = Pattern.compile("([^/\\\\\\s]+\\.(mp3|flac|m4a|wav|ogg|aac|opus|wma))", Pattern.CASE_INSENSITIVE)
    private val titleAttrPattern = Pattern.compile("(title=['\"][^'\"]+['\"])", Pattern.CASE_INSENSITIVE)

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

    /**
     * Sanitiza el texto del mensaje para proteger la privacidad del usuario:
     * - Oculta rutas completas con almacenamiento local (/storage/emulated/0 -> <storage>)
     * - Oculta rutas de datos de la app (/data/user/0/... -> <app_data>)
     * - Si includeTrackTitles es falso, anonimiza nombres de archivos de canciones y atributos de títulos
     */
    fun sanitize(text: String, includeTrackTitles: Boolean): String {
        var result = storagePattern.matcher(text).replaceAll("<storage>")
        result = appDataPattern.matcher(result).replaceAll("<app_data>")

        if (!includeTrackTitles) {
            val fileMatcher = trackFilePattern.matcher(result)
            val sb = StringBuffer()
            while (fileMatcher.find()) {
                val fullMatch = fileMatcher.group(1) ?: ""
                val ext = fileMatcher.group(2) ?: "audio"
                val shortHash = Integer.toHexString(fullMatch.hashCode()).take(6)
                fileMatcher.appendReplacement(sb, "<track-$shortHash.$ext>")
            }
            fileMatcher.appendTail(sb)
            result = sb.toString()

            result = titleAttrPattern.matcher(result).replaceAll("title=\"<hidden_track>\"")
        }

        return result
    }

    /**
     * Exporta el contenido del registro en memoria a un archivo de texto en cacheDir.
     */
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
                writer.write(entry.format(sanitizedMsg))
            }
        }
        return file
    }

    /**
     * Registra un interceptor de excepciones no controladas para que ningún crash quede sin rastrear.
     */
    fun installUncaughtExceptionHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("UncaughtException", "Fatal exception in thread '${thread.name}': ${throwable.message}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }
}
