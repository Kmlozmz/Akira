package com.akira.innertube.utils

import com.akira.innertube.OfflineModeException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.Authenticator

internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }

internal suspend fun <T> retryRequest(
    isOffline: () -> Boolean,
    maxAttempts: Int = 3,
    initialDelay: Long = 500L,
    block: suspend () -> T,
): T {
    require(maxAttempts > 0)
    var attempts = 0
    var waitMillis = initialDelay
    while (true) {
        currentCoroutineContext().ensureActive()
        if (isOffline()) throw OfflineModeException()
        try {
            return block()
        } catch (failure: IOException) {
            currentCoroutineContext().ensureActive()
            if (failure is OfflineModeException || ++attempts >= maxAttempts) throw failure
            delay(waitMillis)
            waitMillis *= 2
        }
    }
}

internal fun currentProxyAuthenticator(credentials: () -> String?): Authenticator =
    Authenticator { _, response ->
        val auth = credentials() ?: return@Authenticator null
        if (response.request.header("Proxy-Authorization") == auth) return@Authenticator null
        response.request.newBuilder().header("Proxy-Authorization", auth).build()
    }
