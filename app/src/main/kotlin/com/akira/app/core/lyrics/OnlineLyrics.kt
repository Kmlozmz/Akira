package com.akira.app.core.lyrics

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import java.io.*
import java.security.MessageDigest

data class LyricsRequest(val songKey: String, val title: String, val artist: String, val album: String, val durationMs: Long)
data class LyricsPayload(val syncedLyrics: String = "", val plainLyrics: String = "") {
    fun parse(): LoadedLyrics {
        val synced = LyricsParser.parseLrcText(syncedLyrics)
        return if (synced.synced.isNotEmpty()) synced else LoadedLyrics(plain = plainLyrics.lines().filter { it.isNotBlank() })
    }
}
sealed interface LyricsFetchResult {
    data class Found(val payload: LyricsPayload): LyricsFetchResult
    data object Missing: LyricsFetchResult
    data object Failed: LyricsFetchResult
}
fun interface LyricsFetcher {
    fun fetch(request: LyricsRequest): LyricsFetchResult
    suspend fun fetchCancellable(request: LyricsRequest): LyricsFetchResult =
        kotlinx.coroutines.runInterruptible(Dispatchers.IO) {fetch(request)}
}
enum class LyricsLookupStatus { LOCAL, FOUND, MISSING, OFFLINE, FAILED }
data class LyricsResolution(val lyrics: LoadedLyrics,val status: LyricsLookupStatus)
/** A cancelled lookup can finish after its replacement has already started. */
internal class LyricsLookupGeneration {
    private var current=0L
    fun begin(): Long=++current
    fun owns(token: Long): Boolean=token==current
}

class OnlineLyricsRepository(private val directory: File, private val fetcher: LyricsFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    companion object { const val NEGATIVE_TTL_MS = 30 * 60_000L; private const val MAX_TEXT = 2 * 1024 * 1024 }
    suspend fun resolve(enabled: Boolean, local: LoadedLyrics, request: LyricsRequest, forceRefresh: Boolean = false,
        networkAvailable: Boolean = true): LoadedLyrics = resolveDetailed(enabled,local,request,forceRefresh,networkAvailable).lyrics

    suspend fun resolveDetailed(enabled: Boolean, local: LoadedLyrics, request: LyricsRequest, forceRefresh: Boolean = false,
        networkAvailable: Boolean = true): LyricsResolution {
        if (!enabled || (local.hasLyrics && fetcher !is AutomaticLyricsFetcher)) return LyricsResolution(local,LyricsLookupStatus.LOCAL)
        return withContext(dispatcher) {
            val hash = MessageDigest.getInstance("SHA-256").digest(request.songKey.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(directory,"$hash.cache")
            var previousPositive: LyricsPayload? = null
            fun readText(input: DataInputStream): String {
                val length = input.readInt()
                require(length in 0..MAX_TEXT)
                return ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
            }
            val saved = if (forceRefresh) null else runCatching {
                DataInputStream(file.inputStream()).use { input ->
                    require(input.readInt() == 1)
                    val savedAt = input.readLong()
                    val positive = input.readBoolean()
                    if (!positive && clock()-savedAt !in 0..NEGATIVE_TTL_MS) null
                    else if (positive) {
                        val payload=LyricsPayload(readText(input),readText(input))
                        previousPositive=payload
                        val timing=lyricsQuality(payload.parse(),request).timing
                        val ttl=when(timing) {3 -> 24*60*60_000L;2 -> 60*60_000L;else -> 5*60_000L}
                        if(fetcher is AutomaticLyricsFetcher && networkAvailable && clock()-savedAt !in 0..ttl)null
                        else LyricsFetchResult.Found(payload)
                    }
                    else LyricsFetchResult.Missing
                }
            }.getOrNull()
            currentCoroutineContext().ensureActive()
            if (saved == null && !networkAvailable) return@withContext LyricsResolution(local,LyricsLookupStatus.OFFLINE)
            suspend fun fetch(): LyricsFetchResult = try {
                currentCoroutineContext().ensureActive()
                fetcher.fetchCancellable(request)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { LyricsFetchResult.Failed }
            var attempt=saved ?: fetch()
            if(saved==null && (attempt==LyricsFetchResult.Failed || (forceRefresh && attempt==LyricsFetchResult.Missing))) {
                // One automatic retry replaces repeated taps after a transient failure.
                // Failed requests never poison the negative-result cache.
                kotlinx.coroutines.delay(if(attempt==LyricsFetchResult.Missing)750 else 500)
                attempt=fetch()
            }
            val result=attempt.let {
                val previous=previousPositive
                if(previous!=null && (it !is LyricsFetchResult.Found || compareLyrics(previous.parse(),it.payload.parse(),request)>0))LyricsFetchResult.Found(previous)
                else if(it is LyricsFetchResult.Found && !it.payload.parse().hasLyrics)LyricsFetchResult.Missing else it
            }
            currentCoroutineContext().ensureActive()
            if (saved == null && result != LyricsFetchResult.Failed) runCatching {
                directory.mkdirs()
                val temporary = File.createTempFile("$hash.",".tmp",directory)
                try {
                    DataOutputStream(temporary.outputStream()).use { output ->
                        output.writeInt(1); output.writeLong(clock()); output.writeBoolean(result is LyricsFetchResult.Found)
                        if (result is LyricsFetchResult.Found) for (text in listOf(result.payload.syncedLyrics,result.payload.plainLyrics)) {
                            val bytes = text.toByteArray(Charsets.UTF_8); require(bytes.size <= MAX_TEXT)
                            output.writeInt(bytes.size); output.write(bytes)
                        }
                    }
                    java.nio.file.Files.move(temporary.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } finally { temporary.delete() }
            }
            when(val final=result) {
                is LyricsFetchResult.Found -> {
                    val online=final.payload.parse()
                    if(local.hasLyrics && compareLyrics(local,online,request)>=0)LyricsResolution(local,LyricsLookupStatus.LOCAL)
                    else LyricsResolution(online,LyricsLookupStatus.FOUND)
                }
                LyricsFetchResult.Failed -> LyricsResolution(local,LyricsLookupStatus.FAILED)
                LyricsFetchResult.Missing -> LyricsResolution(local,LyricsLookupStatus.MISSING)
            }
        }
    }
}
