package com.akira.app.core.datastore

import java.util.Locale

data class LibraryCategoryLayout(val order:List<String> = emptyList(), val hidden:Set<String> = emptySet()) {
    fun normalized(available:List<String>):LibraryCategoryLayout {
        val keys=available.map{it.lowercase(Locale.ROOT)}.distinct()
        val sorted=(order+keys).filter{it in keys}.distinct()
        var excluded=hidden.intersect(keys.toSet())
        if(sorted.isNotEmpty() && excluded.size==sorted.size)excluded=excluded-sorted.first()
        return LibraryCategoryLayout(sorted,excluded)
    }
    fun toggle(key:String):LibraryCategoryLayout =
        if(key in hidden)copy(hidden=hidden-key)
        else if(order.count{it !in hidden}>1)copy(hidden=hidden+key) else this
    fun move(key:String,direction:Int):LibraryCategoryLayout {
        val index=order.indexOf(key)
        if(index<0 || order.isEmpty())return this
        val next=(index+direction).coerceIn(0,order.lastIndex)
        if(next==index)return this
        val result=order.toMutableList();result[index]=result[next];result[next]=key
        return copy(order=result)
    }
}
