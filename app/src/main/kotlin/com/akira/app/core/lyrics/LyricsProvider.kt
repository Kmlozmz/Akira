package com.akira.app.core.lyrics

enum class LyricsProvider(val displayName: String, val cacheDirectory: String) {
    AUTO("Automatic", "lyrics_cache/automatic_v1"),
    LRCLIB("LRCLIB", "lyrics_cache"),
    LYRICS_OVH("Lyrics.ovh", "lyrics_cache/lyrics_ovh"),
    KUGOU("KuGou", "lyrics_cache/kugou"),
    NETEASE("NetEase", "lyrics_cache/netease");

    fun createFetcher(version: String): LyricsFetcher = when(this) {
        AUTO -> AutomaticLyricsFetcher(version)
        LRCLIB -> LrclibFetcher(version)
        LYRICS_OVH -> LyricsOvhFetcher()
        KUGOU -> KugouFetcher(version)
        NETEASE -> NeteaseFetcher(version)
    }
    companion object {
        fun fromId(id: String?): LyricsProvider = entries.firstOrNull { it.name == id } ?: AUTO
    }
}
