package com.akira.app.core.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal fun lyricSearchTitle(title: String): String = title
    .replace(Regex("[\\[(](?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]+[)\\]]",RegexOption.IGNORE_CASE),"")
    .replace(Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*",RegexOption.IGNORE_CASE),"").trim()
internal fun lyricNormalized(value: String): String = Normalizer.normalize(value,Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim()
internal data class LrclibCandidate(val title: String,val artist: String,val durationSeconds: Double,val payload: LyricsPayload)

internal fun lyricMatchDistance(request: LyricsRequest, candidateTitle: String, candidateArtist: String, durationSeconds: Double): Double? {
    if(lyricNormalized(lyricSearchTitle(candidateTitle))!=lyricNormalized(lyricSearchTitle(request.title)))return null
    val artists=request.artist.split(Regex("\\s*(?:,|&|;| feat\\.? | ft\\.? | featuring )\\s*",RegexOption.IGNORE_CASE))
        .map(::lyricNormalized).filter {it.isNotEmpty()}
    val artist=lyricNormalized(candidateArtist)
    if(artists.none {it==artist || artist.startsWith("$it ") || artist.endsWith(" $it")})return null
    val difference=abs(request.durationMs/1000.0-durationSeconds)
    if(!difference.isFinite() || (request.durationMs>0 && durationSeconds>0 && difference>12))return null
    return difference
}

/** Never select the first result blindly, or exchange a remix/live take. */
internal fun bestLyricMatch(request: LyricsRequest,candidates: List<LrclibCandidate>): LrclibCandidate? {
    return candidates.mapNotNull { candidate ->
        val difference=lyricMatchDistance(request,candidate.title,candidate.artist,candidate.durationSeconds) ?: return@mapNotNull null
        if(candidate.payload.syncedLyrics.isBlank() && candidate.payload.plainLyrics.isBlank())return@mapNotNull null
        candidate to ((if(candidate.payload.parse().isSynced)100 else 0)+(if(difference<=3)30 else if(difference<=8)15 else 0))
    }.maxByOrNull {it.second}?.first
}
