package com.akira.app.feature.online

import android.content.Context
import android.net.Uri
import com.akira.app.R
import java.util.Locale

/** Includes Google's regional cookie handoff without accepting arbitrary suffix matches.
 * Country domains: https://www.google.com/supported_domains (2026-10-21). */
internal class GoogleLoginNavigation(context: Context) {
    private val googleRoots = context.resources.openRawResource(R.raw.google_sign_in_domains)
        .bufferedReader().use { it.readText() }.trim().split(Regex("\\s+")).toSet()
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "music.youtube.com", "accounts.youtube.com", "consent.youtube.com")
    private val googleHandoffHosts = setOf("gds.google.com")
    private val googlePrefixes = listOf("", "www.", "accounts.", "consent.", "myaccount.")

    fun allows(uri: Uri): Boolean {
        if(uri.scheme?.lowercase(Locale.ROOT) != "https" || uri.port !in listOf(-1, 443)) return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        return host in youtubeHosts || host in googleHandoffHosts || googlePrefixes.any { prefix ->
            host.startsWith(prefix) && host.removePrefix(prefix) in googleRoots
        }
    }
}
