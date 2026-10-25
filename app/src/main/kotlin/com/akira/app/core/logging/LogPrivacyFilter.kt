package com.akira.app.core.logging

import java.net.URI

internal object LogPrivacyFilter {
    private val urls = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    private val credentials = Regex(
        "\\b(?:authorization|proxy-authorization|cookie|set-cookie)\\s*[:=].*$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )
    private val secrets = Regex(
        "\\b(?:access_token|refresh_token|id_token|token|sapisid|sid|session|password|secret|api[_-]?key)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)",
        RegexOption.IGNORE_CASE,
    )
    private val paths = Regex(
        "(?:file://|content://|/storage/|/sdcard(?:/|\\b)|/data/(?:user/|data/)|(?<![A-Za-z0-9])[A-Za-z]:[\\\\/]|<(?:storage|app_data)>/)[^\\r\\n\"'<>]*",
        RegexOption.IGNORE_CASE,
    )
    private val audioFiles = Regex(
        "[^\\r\\n/\\\\\"'=<>]+\\.(mp3|flac|m4a|wav|ogg|aac|opus|wma)(?=$|[\\s\"',;:)\\]])",
        RegexOption.IGNORE_CASE,
    )
    private val titles = Regex("title\\s*=\\s*(['\"])[^'\"]*['\"]", RegexOption.IGNORE_CASE)

    fun sanitize(text: String, includeTrackTitles: Boolean): String {
        var filtered = credentials.replace(text, "<credentials>")
        filtered = urls.replace(filtered) { match ->
            val uri = runCatching { URI(match.value) }.getOrNull()
            uri?.host?.let { "${uri.scheme}://$it/<redacted>" } ?: "<url>"
        }
        filtered = secrets.replace(filtered, "<credential>")
        if (!includeTrackTitles) filtered = titles.replace(filtered, "title=\"<hidden_track>\"")
        // An unquoted path can contain spaces; redact its remainder rather than guess a filename boundary.
        filtered = paths.replace(filtered, "<path>")
        if (!includeTrackTitles) {
            filtered = audioFiles.replace(filtered) { "<track.${it.groupValues[1].lowercase()}>" }
        }
        return filtered
    }
}
