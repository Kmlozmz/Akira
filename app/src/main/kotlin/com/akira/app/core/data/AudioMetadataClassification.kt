package com.akira.app.core.data

internal data class AudioMetadataClassification(val isLossless: Boolean, val isHiRes: Boolean)

internal fun classifyAudioMetadata(codec: String, container: String, sampleRateHz: Int, bitDepth: Int): AudioMetadataClassification {
    val lossless = codec == "ALAC" || codec == "FLAC" || container == "FLAC" ||
        container == "WAV" || codec.contains("PCM", ignoreCase = true)
    return AudioMetadataClassification(lossless, lossless && (sampleRateHz > 48_000 || bitDepth > 16))
}

internal fun audioMetadataFolder(path: String): String? {
    if (path.isBlank()) return null
    val normalized = path.replace('\\', '/')
    val relative = if (normalized.contains("/storage/emulated/0/")) {
        normalized.substringAfter("/storage/emulated/0/")
    } else {
        normalized.removePrefix("/")
    }
    val directory = relative.substringBeforeLast('/', "")
    return if (directory.isNotBlank()) "$directory/" else "/"
}

internal fun indexedAudioFormat(mime: String): String = when {
    mime.contains("flac", ignoreCase = true) -> "FLAC"
    mime.contains("wav", ignoreCase = true) -> "WAV"
    mime.contains("ogg", ignoreCase = true) -> "OGG"
    mime.contains("aac", ignoreCase = true) -> "AAC"
    mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> "M4A"
    else -> "MP3"
}
