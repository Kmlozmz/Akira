package com.akira.app.core.startup

/** The first authoritative read resolves startup; later writes belong to the active screen. */
internal class StartupNavigationGate {
    private var resolved = false

    fun resolve(onboardingCompleted: Boolean?): StartupDestination? {
        if (resolved || onboardingCompleted == null) return null
        resolved = true
        return if (onboardingCompleted) StartupDestination.LIBRARY else StartupDestination.SETUP
    }
}
