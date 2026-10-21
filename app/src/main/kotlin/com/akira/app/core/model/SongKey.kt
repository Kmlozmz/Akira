package com.akira.app.core.model

/** Identidad local independiente de los IDs de MediaStore. */
data class SongKey(val relativePath: String, val fileName: String, val durationMs: Long) {
    val encoded: String get() = "v1|${relativePath.length}:$relativePath${fileName.length}:$fileName|$durationMs"

    companion object {
        fun of(song: Song): SongKey {
            val path = song.path.replace('\\', '/').removePrefix("/storage/emulated/0/")
                .removePrefix("/sdcard/").trimStart('/')
            val fileName = path.substringAfterLast('/').ifBlank { song.contentUri.substringAfterLast('/') }
            return SongKey(path.substringBeforeLast('/', ""), fileName, song.durationMs)
        }
    }
}
