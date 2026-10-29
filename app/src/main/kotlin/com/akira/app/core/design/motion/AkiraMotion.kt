package com.akira.app.core.design.motion

import android.content.Context
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object InteractionPreferences {
    private const val Preferences = "akira_interaction"
    var hapticsEnabled by mutableStateOf(true)
        private set
    var reducedMotion by mutableStateOf(false)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
        val previous = context.getSharedPreferences("akira_glass", Context.MODE_PRIVATE)
        hapticsEnabled = saved.getBoolean("haptics", previous.getBoolean("haptics", true))
        reducedMotion = saved.getBoolean("reducedMotion", previous.getBoolean("reducedMotion", false))
    }

    fun setHaptics(context: Context, value: Boolean) {
        hapticsEnabled = value
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit().putBoolean("haptics", value).apply()
    }

    fun setReducedMotion(context: Context, value: Boolean) {
        reducedMotion = value
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE).edit().putBoolean("reducedMotion", value).apply()
    }
}

object AkiraMotion {
    fun <T> settle(): SpringSpec<T> = spring(1f, 850f)
    fun <T> press(): SpringSpec<T> = spring(.88f, 1100f)
    fun <T> release(): SpringSpec<T> = spring(.84f, 650f)
    fun <T> fold(): SpringSpec<T> = spring(1f, 800f)
}
