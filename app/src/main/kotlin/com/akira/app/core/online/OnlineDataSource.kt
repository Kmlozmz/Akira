package com.akira.app.core.online

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import java.io.EOFException
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException

@UnstableApi
internal class OnlineDataSource(
    private val upstream: DataSource,
    private val resolve: suspend (String, Boolean, Int?) -> OnlineStream,
) : DataSource {
    constructor(upstream: DataSource, repository: OnlineRepository) : this(upstream, repository::resolve)

    private var onlineRequest: DataSpec? = null
    private var stream: OnlineStream? = null
    private var videoId: String? = null
    private var bytesRead = 0L
    private var expectedLength = C.LENGTH_UNSET.toLong()
    private var resumed = false

    override fun addTransferListener(listener: TransferListener) = upstream.addTransferListener(listener)

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri.scheme != "akira-youtube") {
            videoId = null
            stream = null
            return upstream.open(dataSpec)
        }
        val id = dataSpec.uri.host.orEmpty()
        if (!id.matches(onlineVideoIdPattern)) throw IOException("Invalid online song identity")
        if (videoId != id) stream = null
        videoId = id
        val requiredItag = stream?.itag?.takeIf { dataSpec.position > 0 }
        onlineRequest = dataSpec
        bytesRead = 0
        resumed = false
        val source = runBlocking { resolve(id, false, requiredItag) }
        expectedLength = openStream(dataSpec, source)
        return expectedLength
    }

    private fun openStream(dataSpec: DataSpec, source: OnlineStream): Long {
        fun open(stream: OnlineStream): Long {
            val request = dataSpec.withUri(Uri.parse(stream.url))
                .withAdditionalHeaders(stream.headers)
            val length = upstream.open(request)
            if (request.position > 0) {
                val range = upstream.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }
                    ?.value?.firstOrNull().orEmpty()
                if (!range.startsWith("bytes ${request.position}-", ignoreCase = true)) {
                    upstream.close()
                    throw IOException("Stream did not return the requested byte range")
                }
            }
            this.stream = stream
            return length
        }
        return try { open(source) }
        catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode != 403 && error.responseCode != 410) throw error
            upstream.close()
            val requiredItag = source.itag.takeIf { dataSpec.position > 0 }
            val replacement = runBlocking { resolve(checkNotNull(videoId), true, requiredItag) }
            open(replacement)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val request = onlineRequest ?: return upstream.read(buffer, offset, length)
        fun readOnline(): Int {
            val count = upstream.read(buffer, offset, length)
            if (count == C.RESULT_END_OF_INPUT && expectedLength >= 0 && bytesRead < expectedLength) {
                throw EOFException("Online stream ended before the requested data was received")
            }
            if (count > 0) bytesRead += count
            return count
        }
        return try { readOnline() }
        catch (error: IOException) {
            if (resumed || bytesRead == expectedLength || !error.isRecoverableRead() ||
                Thread.currentThread().isInterrupted) throw error
            val source = stream ?: throw error
            resumed = true
            upstream.close()
            openStream(request.subrange(bytesRead), source)
            readOnline()
        }
    }

    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() {
        onlineRequest = null
        upstream.close()
    }

    private fun IOException.isRecoverableRead(): Boolean = generateSequence<Throwable>(this) { it.cause }.any {
        it is EOFException || it is SocketTimeoutException || it is SocketException ||
            it is HttpDataSource.HttpDataSourceException &&
            (it.reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                it.reason == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
    }

}
