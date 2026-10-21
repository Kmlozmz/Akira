package com.akira.app.core.lyrics

import kotlinx.coroutines.*

internal data class LyricsQuality(val timing: Int, val characters: Int, val timedWords: Int): Comparable<LyricsQuality> {
    override fun compareTo(other: LyricsQuality)=compareValuesBy(this,other,{it.timing},{it.characters},{it.timedWords})
}
private fun LyricsQuality.withCoverage(size: Int)=if(timing==3 && characters*2<size)copy(timing=2) else this
internal fun compareLyrics(first: LoadedLyrics, second: LoadedLyrics, request: LyricsRequest): Int {
    val a=lyricsQuality(first,request);val b=lyricsQuality(second,request)
    val size=maxOf(if(a.timing>=2)a.characters else 0,if(b.timing>=2)b.characters else 0)
    return a.withCoverage(size).compareTo(b.withCoverage(size))
}

/** Rank real parsed timing; a non-empty syncedLyrics field alone is not evidence. */
internal fun lyricsQuality(lyrics: LoadedLyrics, request: LyricsRequest): LyricsQuality {
    val lines=lyrics.synced.filter {it.text.isNotBlank()}
    if(lines.isNotEmpty()) {
        if(request.durationMs>0 && lines.any {it.timeMs>request.durationMs+5000})return LyricsQuality(0,0,0)
        val wordLines=lines.filter {line -> line.words.size>1 &&
            line.words.zipWithNext().all {(a,b)->b.timeMs>a.timeMs} &&
            line.words.all {it.timeMs>=line.timeMs && (request.durationMs<=0 || it.timeMs<=request.durationMs+5000)}}
        val characters=lines.sumOf {it.text.length}
        val wordCharacters=wordLines.sumOf {it.text.length}
        return LyricsQuality(if(wordCharacters>0 && wordCharacters*2>=characters)3 else 2,characters,wordLines.sumOf {it.words.size})
    }
    val text=lyrics.plain.filter {it.isNotBlank()}
    return LyricsQuality(if(text.isEmpty())0 else 1,text.sumOf {it.length},0)
}

/** Every online source is consulted concurrently; completion order does not pick the winner. */
class AutomaticLyricsFetcher internal constructor(
    private val sources: List<Pair<LyricsProvider,LyricsFetcher>>,
    private val timeoutMs: Long=12_000
): LyricsFetcher {
    constructor(version: String): this(LyricsProvider.entries.filter {it!=LyricsProvider.AUTO}.map {it to it.createFetcher(version)})

    override fun fetch(request: LyricsRequest): LyricsFetchResult=runBlocking {fetchCancellable(request)}
    override suspend fun fetchCancellable(request: LyricsRequest): LyricsFetchResult=supervisorScope {
        val results=sources.map {(_,fetcher) -> async {
            withTimeoutOrNull(timeoutMs) {
                try {fetcher.fetchCancellable(request)}
                catch(cancelled: CancellationException) {throw cancelled}
                catch(_: Exception) {LyricsFetchResult.Failed}
            } ?: LyricsFetchResult.Failed
        }}.awaitAll()
        val candidates=results.filterIsInstance<LyricsFetchResult.Found>()
            .map {it to lyricsQuality(it.payload.parse(),request)}.filter {it.second.timing>0}
        val syncedSize=candidates.filter {it.second.timing>=2}.maxOfOrNull {it.second.characters} ?: 0
        // A tiny word-timed fragment must not displace an otherwise complete synced lyric.
        val best=candidates.maxByOrNull {(_,quality)->
            quality.withCoverage(syncedSize)
        }?.first
        best ?: if(results.any {it==LyricsFetchResult.Failed})LyricsFetchResult.Failed else LyricsFetchResult.Missing
    }
}
