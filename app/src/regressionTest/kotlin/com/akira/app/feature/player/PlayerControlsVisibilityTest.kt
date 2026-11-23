package com.akira.app.feature.player

import org.junit.Assert.*
import org.junit.Test

class PlayerControlsVisibilityTest {
    @Test fun staleLyricsHideCannotHidePlayerControls() {
        assertTrue(playerControlsVisible(true, false, false, true, true, false))
        assertTrue(playerControlsVisible(true, false, true, false, true, false))
    }
    @Test fun realLyricsCanHideAndRevealControls() {
        assertFalse(playerControlsVisible(true, false, true, true, true, false))
        assertTrue(playerControlsVisible(true, false, true, true, true, true))
    }
    @Test fun queueNeverShowsTransportAndCollapsedPlayerRemainsHidden() {
        assertFalse(playerControlsVisible(true, true, false, false, false, false))
        assertFalse(playerControlsVisible(true, true, false, false, true, true))
        assertFalse(playerControlsVisible(false, false, false, false, false, false))
    }
}
