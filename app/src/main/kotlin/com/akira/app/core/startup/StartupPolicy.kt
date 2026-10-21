package com.akira.app.core.startup

enum class StartupDestination { WAIT, SETUP, LIBRARY }

/** Preferences may arrive after the first frame; navigation must still progress. */
object StartupPolicy {
    const val PREFERENCES_WAIT_MS = 3_000L

    fun destination(onboardingCompleted: Boolean?, audioPermission: Boolean, timedOut: Boolean): StartupDestination =
        when {
            onboardingCompleted == true -> StartupDestination.LIBRARY
            onboardingCompleted == false -> StartupDestination.SETUP
            !timedOut -> StartupDestination.WAIT
            // A restored permission is not evidence that this installation
            // completed setup. The real preference may promote it to Library.
            else -> StartupDestination.SETUP
        }

    fun canFinishIntro(animationFinished: Boolean, destinationReady: Boolean): Boolean =
        animationFinished && destinationReady
}
