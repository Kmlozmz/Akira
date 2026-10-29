package com.akira.app.core.audio

import com.akira.app.core.datastore.UserPreferences

data class EqualizerConfiguration(
    val bands: List<Float> = List(5) { 0f },
    val bass: Float = 0f,
    val space: Float = 0f,
    val preamp: Float = 1f,
) {
    fun normalized() = copy(
        bands = List(5) { bands.getOrNull(it).finiteOr(0f).coerceIn(-12f, 12f) },
        bass = bass.finiteOr(0f).coerceIn(0f, 1f),
        space = space.finiteOr(0f).coerceIn(0f, 1f),
        preamp = preamp.finiteOr(1f).coerceIn(.25f, 1f),
    )
}

private fun Float?.finiteOr(fallback: Float) = this?.takeIf { it.isFinite() } ?: fallback

data class EffectiveEqualizer(val enabled: Boolean, val configuration: EqualizerConfiguration,
    val profile: EqOutputProfile? = null) {
    val headroom: Float get() = if (enabled) configuration.preamp else 1f
}

internal fun resolveEqualizer(enabled: Boolean, global: EqualizerConfiguration,
    profiles: List<EqOutputProfile>, assignments: Map<String, String>, output: OutputKey): EffectiveEqualizer {
    val profile = profiles.firstOrNull { it.id == assignments[output.encoded] }
    val configuration = profile?.let { EqualizerConfiguration(it.bands, it.bass, it.space, it.preamp) } ?: global
    return EffectiveEqualizer(enabled, configuration.normalized(), profile)
}

fun UserPreferences.effectiveEqualizer(output: OutputKey): EffectiveEqualizer = resolveEqualizer(eqEnabled,
    EqualizerConfiguration(eqBandGains, bassBoost, virtualizer, eqPreamp),
    OutputProfiles.decode(eqProfilesJson), OutputProfiles.assignments(eqAssignmentsJson), output)
