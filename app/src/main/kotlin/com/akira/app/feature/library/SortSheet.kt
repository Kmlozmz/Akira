package com.akira.app.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import com.akira.app.core.design.components.*

@Composable
internal fun LibrarySortMenu(current:SortOrder,onSelect:(SortOrder)->Unit,onDismiss:()->Unit,
    originalOrder:Boolean=false,fields:List<SortField> = listOf(SortField.TITLE,SortField.ARTIST,SortField.DATE_ADDED)) {
    val labels=fields.associateWith{field->when(field){
        SortField.TITLE->localizedText("Title","Título")
        SortField.ARTIST->localizedText("Artist","Artista")
        SortField.ALBUM->localizedText("Album","Álbum")
        SortField.DURATION->localizedText("Duration","Duración")
        SortField.DATE_ADDED->if(originalOrder)localizedText("Library order","Orden de la biblioteca")
            else localizedText("Date added","Fecha de incorporación")
    }}
    val choices=fields.map{field->AkiraContextAction(when(field){
        SortField.TITLE->Icons.Rounded.SortByAlpha
        SortField.ARTIST->Icons.Rounded.Person
        SortField.ALBUM->Icons.Rounded.Album
        SortField.DURATION->Icons.Rounded.Timer
        SortField.DATE_ADDED->Icons.Rounded.Schedule
    },labels.getValue(field),selected=current.field==field){
        onSelect(if(current.field==field)current else if(originalOrder && field==SortField.DATE_ADDED)
            SortOrder.of(field,true)else SortOrder.defaultFor(field))
    }}
    val textField=current.field in listOf(SortField.TITLE,SortField.ARTIST,SortField.ALBUM)
    val original=originalOrder && current.field==SortField.DATE_ADDED
    val ascending=if(original)localizedText("Original order","Orden original")else if(textField)localizedText("A to Z","De A a Z")else if(current.field==SortField.DURATION)
        localizedText("Shortest first","Más cortas primero")else localizedText("Oldest first","Más antiguas primero")
    val descending=if(original)localizedText("Reverse order","Orden inverso")else if(textField)localizedText("Z to A","De Z a A")else if(current.field==SortField.DURATION)
        localizedText("Longest first","Más largas primero")else localizedText("Newest first","Más recientes primero")
    AkiraContextMenu(onDismiss=onDismiss,title=localizedText("Sort by","Ordenar por"),
        style=AkiraMenuStyle.Choices,dense=true,groups=listOf(choices,listOf(
            AkiraContextAction(Icons.Rounded.ArrowUpward,ascending,selected=current.ascending){onSelect(SortOrder.of(current.field,true))},
            AkiraContextAction(Icons.Rounded.ArrowDownward,descending,selected=!current.ascending){onSelect(SortOrder.of(current.field,false))})))
}
