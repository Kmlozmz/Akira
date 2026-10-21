package com.akira.app.core.i18n

data class LibraryStrings(
    val lists: String = "Playlists", val newList: String = "New playlist",
    val addToList: String = "Add to playlist", val name: String = "Name", val rename: String = "Rename",
    val duplicate: String = "Duplicate", val unavailable: String = "Unavailable", val listDeleted: String = "Playlist deleted",
    val recentlyAdded: String = "Recently added", val mostPlayed: String = "Most played",
    val neverPlayed: String = "Never played", val forgottenFavorites: String = "Forgotten favorites",
    val saveAsList: String = "Save as playlist", val normalization: String = "Volume normalization",
    val track: String = "Track", val album: String = "Album", val off: String = "Off",
    val profiles: String = "Output profiles", val globalProfile: String = "Global equalizer",
    val assignOutput: String = "Assign to current output", val profileApplied: String = "Equalizer profile applied",
    val bluetoothResume: String = "Resume on Bluetooth reconnect", val resumeWindow: String = "Resume window (minutes)",
    val shareLyrics: String = "Share lyric card", val watermark: String = "Akira watermark",
    val share: String = "Share", val selectLines: String = "Select 1–5 consecutive lines",
    val onlineLyrics: String = "Search lyrics online", val onlineNotice: String = "When local lyrics are unavailable, sends title, artist, album and duration to LRCLIB.",
    val enabled: String = "Enabled", val save: String = "Save", val added: String = "Added to playlist",
    val emptyList: String = "No songs in this playlist", val output: String = "Output", val minutes: String = "minutes",
    val reset: String = "Reset", val error: String = "Could not complete the operation",
    val bass: String = "Bass", val space: String = "Spatial effect", val reorder: String = "Reorder"
)

val LibraryEnglish = LibraryStrings()
val LibrarySpanish = LibraryStrings(
    lists = "Listas", newList = "Nueva lista", addToList = "Añadir a lista", name = "Nombre", rename = "Renombrar",
    duplicate = "Duplicar", unavailable = "No disponible", listDeleted = "Lista eliminada",
    recentlyAdded = "Añadidas recientemente", mostPlayed = "Más escuchadas", neverPlayed = "Nunca escuchadas",
    forgottenFavorites = "Favoritas olvidadas", saveAsList = "Guardar como lista", normalization = "Normalización de volumen",
    track = "Pista", album = "Álbum", off = "Desactivado", profiles = "Perfiles por salida", globalProfile = "Ecualizador global",
    assignOutput = "Asignar a la salida actual", profileApplied = "Perfil de ecualizador aplicado",
    bluetoothResume = "Reanudar al reconectar Bluetooth", resumeWindow = "Ventana de reanudación (minutos)",
    shareLyrics = "Compartir tarjeta de letra", watermark = "Marca de agua Akira", share = "Compartir",
    selectLines = "Selecciona de 1 a 5 líneas seguidas", onlineLyrics = "Buscar letras en línea",
    onlineNotice = "Si no hay letras locales, envía título, artista, álbum y duración a LRCLIB.", enabled = "Activado",
    save = "Guardar", added = "Añadido a la lista", emptyList = "Esta lista no tiene canciones", output = "Salida",
    minutes = "minutos", reset = "Restablecer", error = "No se pudo completar la operación",
    bass = "Graves", space = "Espacialidad", reorder = "Reordenar"
)
