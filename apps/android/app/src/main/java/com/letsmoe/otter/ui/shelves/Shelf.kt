package com.letsmoe.otter.ui.shelves

import com.letsmoe.otter.graphql.fragment.ShelfFields
import com.letsmoe.otter.ui.media.RailItem
import com.letsmoe.otter.ui.media.toRailItem

/** One of the profile's own Home rows: a named, ordered list of movies and series. */
data class Shelf(val id: String, val name: String, val items: List<RailItem>) {
    fun holds(titleId: String): Boolean = items.any { item -> item.id == titleId }
}

fun ShelfFields.toShelf(): Shelf = Shelf(id, name, titles.map { title -> title.titleCard.toRailItem() })

/** The list with the element at [index] swapped with its neighbour [offset] places away (-1 up, 1 down); unchanged at the ends. */
fun <T> List<T>.movedBy(index: Int, offset: Int): List<T> {
    val target = index + offset
    if (index !in indices || target !in indices) {
        return this
    }
    val moved = toMutableList()
    moved[index] = this[target]
    moved[target] = this[index]
    return moved
}

private const val MAX_SHELF_NAME_LENGTH = 40

/** A shelf name as the backend takes it: trimmed, 1 to 40 characters; null when it can't be used. */
fun cleanShelfName(name: String): String? {
    val cleaned = name.trim()
    if (cleaned.isEmpty() || cleaned.length > MAX_SHELF_NAME_LENGTH) {
        return null
    }
    return cleaned
}
