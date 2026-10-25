package com.akira.app.core.media

enum class AudioQuality { LOSSLESS, DOLBY_ATMOS }

data class AudioQualityInfo(
    val quality: AudioQuality,
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
) {
    val description: String get() {
        val rate = sampleRateHz?.takeIf { it > 0 }?.let {
            val decimal = java.math.BigDecimal.valueOf(it.toLong()).movePointLeft(3).stripTrailingZeros().toPlainString()
            "$decimal kHz"
        }
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
    else -> null
}
