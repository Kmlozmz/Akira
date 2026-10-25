package com.akira.app.feature.library

import androidx.compose.runtime.Composable
import com.akira.app.core.design.components.*

@Composable
internal fun LibrarySortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit, originalOrder: Boolean = false) {
    val options = listOf(
        SortField.TITLE to localizedText("Title", "Título"),
        SortField.DATE_ADDED to if (originalOrder) localizedText("Library order", "Orden original")
            else localizedText("Date Added", "Fecha de incorporación"),
        SortField.ARTIST to localizedText("Artist", "Artista"))
    AkiraContextMenu(onDismiss, style = AkiraMenuStyle.Choices, dense = true,
        groups = listOf(options.map { (field, label) ->
            AkiraContextAction(null, label, selected = current.field == field) {
                onSelect(if (current.field == field) SortOrder.of(field, !current.ascending)
                    else SortOrder.defaultFor(field))
            }
        }))
}
