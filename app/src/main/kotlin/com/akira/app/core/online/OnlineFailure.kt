package com.akira.app.core.online

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

enum class OnlineFailure { CONNECTION, SESSION, SERVICE }

fun Throwable.onlineFailure(): OnlineFailure {
    val causes = generateSequence(this) { it.cause }.take(8).toList()
    if(causes.filterIsInstance<ResponseException>().any { it.response.status.value in setOf(401, 403) }) return OnlineFailure.SESSION
    if(causes.any { it is IOException || it is TimeoutCancellationException }) return OnlineFailure.CONNECTION
    return OnlineFailure.SERVICE
}
