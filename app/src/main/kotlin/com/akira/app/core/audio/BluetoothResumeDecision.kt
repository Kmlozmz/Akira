package com.akira.app.core.audio

object BluetoothResumeDecision {
    fun shouldResume(enabled: Boolean, disconnectedPauseAt: Long?, now: Long, windowMinutes: Int,
        manuallyPaused: Boolean, headphonesAdded: Boolean): Boolean = enabled && headphonesAdded && !manuallyPaused &&
        disconnectedPauseAt != null && now - disconnectedPauseAt in 0..(windowMinutes.coerceIn(1,60) * 60_000L)

    // AudioDeviceInfo constants, kept as integers so policy tests do not require an Android runtime.
    fun supportsHeadphones(type: Int, sdk: Int): Boolean = when (type) {
        3, 4, 7, 8, 22 -> true // wired headset/headphones, SCO, A2DP, USB headset
        23 -> sdk >= 28 // TYPE_HEARING_AID
        26 -> sdk >= 31 // TYPE_BLE_HEADSET
        else -> false
    }
}
