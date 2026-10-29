package com.akira.app.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Share the incoming MediaItem between MusicLibraryService and the UI during crossfade, while MediaController still reports the outgoing deck. Clear the preview after handover. */
object CrossfadePreviewBus {
    private val _incomingSongId = MutableStateFlow<String?>(null)
    val incomingSongId: StateFlow<String?> = _incomingSongId.asStateFlow()

    fun setIncoming(mediaId: String) {
        _incomingSongId.value = mediaId
    }

    fun clear() {
        _incomingSongId.value = null
    }
}
