package com.akira.app.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompressedAudioQualityTest {
    @Test
    fun identifiesEncodedAudioWithoutGuessingFromItsContainer() {
        assertEquals(AudioQuality.COMPRESSED, audioQuality("audio/mp4a-latm"))
        assertEquals("AAC", compressedAudioCodec("audio/mp4", "mp4a.40.2"))
        assertEquals("Opus", compressedAudioCodec("audio/opus"))
        assertEquals(AudioQuality.DOLBY_ATMOS, audioQuality("audio/eac3", "ec+3"))
        assertEquals(AudioQuality.LOSSLESS, audioQuality("audio/flac"))
        assertNull(audioQuality("audio/mp4"))
    }

    @Test
    fun formatsOnlyBitratesWithKnownUnits() {
        assertEquals(256_000, audioBitrateFromMetadata("256 kbps"))
        assertEquals(128_000, audioBitrateFromMetadata("128000"))
        assertNull(audioBitrateFromMetadata("128"))
        assertNull(audioBitrateFromMetadata("unknown"))
        assertEquals(
            "44.1 kHz · 256 kbps · AAC",
            AudioQualityInfo(AudioQuality.COMPRESSED, "AAC", 44_100, bitrateBps = 256_000).description,
        )
        assertEquals("AAC", AudioQualityInfo(AudioQuality.COMPRESSED, "AAC").description)
    }
}
