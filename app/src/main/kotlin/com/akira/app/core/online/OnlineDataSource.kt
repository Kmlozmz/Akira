package com.akira.app.core.online

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlinx.coroutines.runBlocking
import java.io.IOException

/** Resolve virtual identities on the loading thread, retrying an expired signed URL once. */
@UnstableApi
internal class OnlineDataSource(private val upstream: DataSource, private val repository: OnlineRepository) : DataSource {
    override fun addTransferListener(listener: TransferListener) = upstream.addTransferListener(listener)
    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.uri.scheme != "akira-youtube") return upstream.open(dataSpec)
        val id = dataSpec.uri.host.orEmpty()
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) throw IOException("Invalid online song identity")
        fun resolve(refresh: Boolean): DataSpec {
            val stream = runBlocking { repository.resolve(id, refresh) }
            return dataSpec.withUri(Uri.parse(stream.url)).withRequestHeaders(dataSpec.httpRequestHeaders + stream.headers)
        }
        return try { upstream.open(resolve(false)) }
        catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode !in setOf(403, 410)) throw error
            upstream.close()
            upstream.open(resolve(true))
        }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = upstream.read(buffer, offset, length)
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() = upstream.close()
}
