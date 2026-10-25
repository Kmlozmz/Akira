package com.akira.innertube.utils

import com.akira.innertube.YouTube
import com.akira.innertube.pages.LibraryPage
import com.akira.innertube.pages.PlaylistPage
import com.akira.innertube.pages.PaginationPage
import com.akira.innertube.pages.collectPages
import java.security.MessageDigest

@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatchingCancellable {
    val page = getOrThrow()
    val songs = collectPages(PaginationPage(page.songs, page.songsContinuation)) { token ->
        val next = YouTube.playlistContinuation(token).getOrThrow()
        PaginationPage(next.songs, next.continuation)
    }
    page.copy(songs = songs, songsContinuation = null)
}

@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatchingCancellable {
    val page = getOrThrow()
    val items = collectPages(PaginationPage(page.items, page.continuation)) { token ->
        val next = YouTube.libraryContinuation(token).getOrThrow()
        PaginationPage(next.items, next.continuation)
    }
    page.copy(items = items, continuation = null)
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie.split("; ")
        .filter { it.isNotEmpty() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) null
            else part.substring(0, splitIndex) to part.substring(splitIndex + 1)
        }
        .toMap()

// Some languages write durations with a dot instead of a colon (Indonesian "3.45", Finnish,
// Danish), so either separator is accepted.
private val DURATION_REGEX = Regex("^(?:(\\p{Nd}+)[:.])?(\\p{Nd}+)[:.](\\p{Nd}{2})$")

fun String.parseTime(): Int? {
    val (hours, minutes, seconds) = DURATION_REGEX.matchEntire(trim())?.destructured ?: return null
    return try {
        (hours.toIntOrNull() ?: 0) * 3600 + minutes.toInt() * 60 + seconds.toInt()
    } catch (e: NumberFormatException) {
        null
    }
}

fun isPrivateId(browseId: String): Boolean {
    return browseId.contains("privately")
}
