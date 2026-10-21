package com.akira.app.core.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado compartido (mismo proceso) entre MusicLibraryService y la UI.
 *
 * Durante un crossfade de doble deck el player sigue siendo el deck saliente, así que
 * MediaController sigue reportando la canción anterior. Para que el reproductor
 * muestre la canción entrante en cuanto empieza el crossfade (sin alterar el
 * crossfade ni el estado real del player) el servicio publica aquí el
 * MediaItem entrante y la UI lo toma como "preview".
 *
 * Se limpia cuando el handover se completa.
 */
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
