package com.akira.app.core.model

enum class AppearancePreset(val id: String, val background: Long,
    val space: Boolean = false, val atmosphere: Boolean = false) {
    MIDNIGHT("MIDNIGHT", 0xFF000000),
    GRAPHITE("GRAPHITE", 0xFF181A1E),
    AURORA("AURORA", 0xFF071114, atmosphere = true),
    FOREST("FOREST", 0xFF06130F, atmosphere = true),
    ROSE("ROSE", 0xFF160D13, atmosphere = true),
    SUNSET("SUNSET", 0xFF180E09, atmosphere = true),
    SPACE("SPACE", 0xFF000000, true, true),
    SPACE_OLED("SPACE_OLED", 0xFF000000, true, true),
    SPACE_NEBULA("SPACE_NEBULA", 0xFF080711, true, true),
    SPACE_DUSK("SPACE_DUSK", 0xFF03080E, true, true),
    SPACE_ICE("SPACE_ICE", 0xFF030F15, true, true),
    SPACE_ORBIT("SPACE_ORBIT", 0xFF0B0618, true, true),
    TOKYO_NIGHT("TOKYO_NIGHT", 0xFF1A1B26),
    TOKYO_STORM("TOKYO_STORM", 0xFF24283B),
    TOKYO_MOON("TOKYO_MOON", 0xFF222436),
    TOKYO_DAY("TOKYO_DAY", 0xFFE1E2E7),
    CAT_MOCHA("CAT_MOCHA", 0xFF1E1E2E),
    CAT_MACCHIATO("CAT_MACCHIATO", 0xFF24273A),
    CAT_FRAPPE("CAT_FRAPPE", 0xFF303446),
    CAT_LATTE("CAT_LATTE", 0xFFEFF1F5),
    EVER_HARD("EVER_HARD", 0xFF272E33),
    EVER_MEDIUM("EVER_MEDIUM", 0xFF2D353B),
    EVER_SOFT("EVER_SOFT", 0xFF333C43);

    val family: String get() = when {
        id.startsWith("TOKYO_") -> "TOKYO"
        id.startsWith("CAT_") -> "CAT"
        id.startsWith("EVER_") -> "EVER"
        space -> "SPACE"
        else -> "AKIRA"
    }

    companion object {
        fun fromId(id: String) = entries.firstOrNull { it.id == id } ?: MIDNIGHT
    }
}
