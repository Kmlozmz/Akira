package com.akira.app.feature.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.akira.app.core.online.OnlineFailure
import com.akira.app.core.online.OnlinePage
import kotlinx.coroutines.Job

internal class OnlineSearchRequestState {
    var page by mutableStateOf<OnlinePage?>(null)
    var loading by mutableStateOf(false)
    var failure by mutableStateOf<OnlineFailure?>(null)
    var loadingMore by mutableStateOf(false)
    var moreFailed by mutableStateOf(false)
    var paginationJob: Job? = null
    var active = true
        private set

    fun cancel() {
        active = false
        paginationJob?.cancel()
    }
}
