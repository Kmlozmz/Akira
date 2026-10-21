package com.akira.app.core.media

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.akira.app.core.model.Song

/**
 * Convierte una URI externa ("Abrir con...") en un [Song] reproducible.
 *
 * Reglas del flujo:
 *  - el archivo NO entra en la biblioteca: se reproduce solo y la cola es de un
 *    elemento, para no contaminar el escaneo;
 *  - los metadatos salen del propio archivo con MediaMetadataRetriever, no de
 *    MediaStore, porque el archivo puede venir de Descargas, del gestor de
 *    archivos o de otra app y puede no estar indexado;
 *  - el permiso de URI vale mientras viva el proceso que lo recibio, asi que
 *    hay que.takePersistableUriPermission cuando se puede. Si falla, se
 *    reproduce igualmente y se avisa.
 */
object ExternalSongLoader {

    private const val TAG = "ExternalSongLoader"

    /** Extrae el titulo del nombre de archivo cuando no hay metadatos. */
    private fun fallbackTitle(uri: Uri): String {
        val last = uri.lastPathSegment ?: return "Audio"
        val base = last.substringAfterLast('/')
        val name = if (base.isBlank()) last else base
        return name.substringBeforeLast('.', name).replace('_', ' ').trim().ifBlank { "Audio" }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Intenta persistir el permiso del URI. Sin esto, el archivo deja de poder
     * leerse en cuanto el proceso que lo FilesProvider temple se reinicia.
     */
    fun persistPermission(context: Context, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // No todos los emisores ofrecen permiso persistente; no es fatal.
            Log.w(TAG, "no se pudo persistir el permiso de $uri")
        }
    }

    /**
     * Devuelve null si el archivo no existe, no se puede leer o no es audio
     * decodificable. Nunca lanza: un archivo inválido no debe tumbar la app.
     */
    fun load(context: Context, uri: Uri): Song? {
        if (uri.scheme !in setOf("content", "file")) return null
        return try {
            loadReadable(context, uri)
        } catch (error: Exception) {
            com.akira.app.core.logging.AkiraLog.w(TAG, "External audio could not be read", error)
            null
        }
    }

    private fun loadReadable(context: Context, uri: Uri): Song? {
        val resolver = context.contentResolver
        val mmr = MediaMetadataRetriever()
        var durationMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        try {
            mmr.setDataSource(context, uri)
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            // Hay extractores que solo devuelven la caratula si se pide el frame.
            mmr.embeddedPicture
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo leer metadatos de $uri", e)
            mmr.release()
            return null
        }
        mmr.release()

        // Sin duracion no hay nada que reproducir: el decoder no abriria el URI.
        if (durationMs <= 0L) {
            Log.w(TAG, "duracion invalida en $uri; se descarta")
            return null
        }

        val name = displayName(resolver, uri)
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: fallbackTitle(name?.let { Uri.parse(it) } ?: uri)
        val resolvedArtist = artist?.takeIf { it.isNotBlank() } ?: "Artista desconocido"
        val resolvedAlbum = album?.takeIf { it.isNotBlank() } ?: "Single"

        val mime = resolver.getType(uri) ?: "audio/*"
        val format = when {
            mime.contains("flac", true) -> "FLAC"
            mime.contains("wav", true) -> "WAV"
            mime.contains("ogg", true) -> "OGG"
            mime.contains("aac", true) -> "AAC"
            mime.contains("mp4", true) || mime.contains("m4a", true) -> "M4A"
            else -> "MP3"
        }

        return Song(
            // Id con prefijo: evita colisionar con el id de MediaStore de un
            // archivo que ya este en la biblioteca.
            id = "external:$uri",
            title = resolvedTitle,
            artist = resolvedArtist,
            album = resolvedAlbum,
            durationMs = durationMs,
            contentUri = uri.toString(),
            format = format,
            mimeType = mime,
            // No tiene ruta en disco accesible de forma fiable: se deja vacia
            // para que el escaner y las letras no la intenten resolver.
            path = ""
        )
    }
}
