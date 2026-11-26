package com.akira.app.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerArtworkEdgeFadeTest {
    @Test fun fullBleedArtworkFinishesFeatheringBeforeItsBoundsSettle() {
        assertEquals(1f, playerArtworkEdgeFade(.75f, .65f), 0f)
        assertEquals(1f, playerArtworkEdgeFade(.95f, .93f), 0f)
        assertEquals(1f, playerArtworkEdgeFade(1f, 1f), 0f)
        assertEquals(0f, playerArtworkEdgeFade(.18f, .60f), 0f)
        assertEquals(0f, playerArtworkEdgeFade(.8f, 0f), 0f)
    }
}
