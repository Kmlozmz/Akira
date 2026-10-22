package com.akira.app.feature.search

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

@Stable
internal class SearchSession(query: String = "", editing: Boolean = false, library: Boolean = false) {
    var value by mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    var editing by mutableStateOf(editing)
    var library by mutableStateOf(library)
    var kind by mutableStateOf("all")
    var submit by mutableIntStateOf(0)
    val query get() = value.text.trim()

    fun search(text: String) {
        value = TextFieldValue(text, TextRange(text.length))
        editing = true
        submit++
    }

    fun close() { editing = false; value = TextFieldValue(""); kind = "all" }

    companion object {
        val Saver = listSaver<SearchSession, Any>(
            save = { listOf(it.value.text, it.editing, it.library, it.kind) },
            restore = { SearchSession(it[0] as String, it[1] as Boolean, it[2] as Boolean).apply { kind = it[3] as String } })
    }
}

@Composable internal fun rememberSearchSession() = rememberSaveable(saver = SearchSession.Saver) { SearchSession() }
internal val LocalSearchSession = staticCompositionLocalOf<SearchSession?> { null }
