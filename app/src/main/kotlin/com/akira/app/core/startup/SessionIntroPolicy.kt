package com.akira.app.core.startup

import java.util.concurrent.atomic.AtomicBoolean

/** One intro per process, including launches that create another Activity instance. */
internal class SessionIntroPolicy {
    private val opened = AtomicBoolean(false)
    fun onLaunch(fromWidget: Boolean, restored: Boolean): Boolean =
        !opened.getAndSet(true) && !fromWidget && !restored
}

internal val processIntroSession = SessionIntroPolicy()
