package com.akira.app.core.media

enum class AudioQuality { LOSSLESS, DOLBY_ATMOS, COMPRESSED }

data class AudioQualityInfo(
    val quality: AudioQuality,
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val bitrateBps: Int? = null,
) {
    val description: String get() {
        val rate = sampleRateHz?.takeIf { it > 0 }?.let {
            val decimal = java.math.BigDecimal.valueOf(it.toLong()).movePointLeft(3).stripTrailingZeros().toPlainString()
            "$decimal kHz"
        }
        val bitrate = bitrateBps?.takeIf { it >= 1_000 }?.let { "${it / 1_000} kbps" }
        if (quality == AudioQuality.COMPRESSED) return listOfNotNull(rate, bitrate, codec).joinToString(" · ")
        val depth = bitDepth?.takeIf { it > 0 && quality == AudioQuality.LOSSLESS }?.let { "$it-bit" }
        return listOfNotNull(listOfNotNull(depth, rate).joinToString("/").takeIf { it.isNotEmpty() }, codec)
            .joinToString(" ")
    }
}

/** Describes the encoded source, independently of device effects or the output route. */
fun audioQuality(mimeType: String?, codecs: String? = null): AudioQuality? = when {
    mimeType.equals("audio/eac3-joc", ignoreCase = true) ||
        codecs.orEmpty().split(',').any { it.trim().equals("ec+3", ignoreCase = true) } -> AudioQuality.DOLBY_ATMOS
    mimeType?.lowercase() in setOf("audio/flac", "audio/x-flac", "audio/alac", "audio/raw") -> AudioQuality.LOSSLESS
    compressedAudioCodec(mimeType, codecs) != null -> AudioQuality.COMPRESSED
    else -> null
}


internal fun compressedAudioCodec(mimeType: String?, codecs: String? = null): String? {
    val codecsList = codecs.orEmpty().lowercase(java.util.Locale.ROOT).split(',').map(String::trim)
    return when (mimeType?.lowercase(java.util.Locale.ROOT)) {
        "audio/mp4a-latm", "audio/aac", "audio/aac-adts" -> "AAC"
        "audio/mpeg", "audio/mp3" -> "MP3"
        "audio/opus" -> "Opus"
        "audio/vorbis" -> "Vorbis"
        "audio/ac3" -> "AC-3"
        "audio/eac3" -> "E-AC-3"
        else -> when {
            codecsList.any { it.startsWith("mp4a.40.") } -> "AAC"
            "opus" in codecsList -> "Opus"
            "vorbis" in codecsList -> "Vorbis"
            else -> null
        }
    }
}

internal fun audioBitrateFromMetadata(value: String): Int? {
    val match = Regex("""^(\d+(?:[.,]\d+)?)\s*(kbps|kb/s|bps|b/s)?$""", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim()) ?: return null
    val rate = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
    val bits = if (match.groupValues[2].startsWith("k", ignoreCase = true)) rate * 1_000 else rate
    return bits.takeIf { it in 1_000.0..100_000_000.0 }?.toInt()
}
