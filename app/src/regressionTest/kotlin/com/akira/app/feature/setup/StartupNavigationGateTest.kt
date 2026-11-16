package com.akira.app.feature.setup

import com.akira.app.core.startup.StartupDestination
import com.akira.app.core.startup.StartupNavigationGate
import org.junit.Assert.*
import org.junit.Test

class StartupNavigationGateTest {
    @Test fun `a completion save cannot bypass the active onboarding departure animation`() {
        val gate = StartupNavigationGate()
        assertEquals(StartupDestination.SETUP, gate.resolve(false))
        // DataStore emits true before SetupController completes and before its visual handoff.
        assertNull(gate.resolve(true))
        assertNull(gate.resolve(true))
    }
    @Test fun `delayed preferences can still promote the startup fallback to library`() {
        val gate = StartupNavigationGate()
        assertNull(gate.resolve(null))
        assertNull(gate.resolve(null))
        assertEquals(StartupDestination.LIBRARY, gate.resolve(true))
    }
    @Test fun `a restored gate reads the real completion flag again`() {
        StartupNavigationGate().resolve(false)
        assertEquals(StartupDestination.LIBRARY, StartupNavigationGate().resolve(true))
    }
    @Test fun `preference updates after startup never reroute a live destination`() {
        val gate = StartupNavigationGate()
        assertEquals(StartupDestination.LIBRARY, gate.resolve(true))
        assertNull(gate.resolve(false))
        assertNull(gate.resolve(null))
    }
}
