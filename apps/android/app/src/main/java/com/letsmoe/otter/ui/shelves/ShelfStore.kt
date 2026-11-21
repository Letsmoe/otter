package com.letsmoe.otter.ui.shelves

import com.letsmoe.otter.api.OtterApi
import com.letsmoe.otter.api.dataOrThrow
import com.letsmoe.otter.graphql.AddToShelfMutation
import com.letsmoe.otter.graphql.CreateShelfMutation
import com.letsmoe.otter.graphql.DeleteShelfMutation
import com.letsmoe.otter.graphql.RemoveFromShelfMutation
import com.letsmoe.otter.graphql.RenameShelfMutation
import com.letsmoe.otter.graphql.ReorderShelfTitlesMutation
import com.letsmoe.otter.graphql.ReorderShelvesMutation
import com.letsmoe.otter.graphql.ShelvesQuery
import com.letsmoe.otter.profiles.ProfileSession
import com.letsmoe.otter.toasts.ToastKind
import com.letsmoe.otter.toasts.Toasts
import com.letsmoe.otter.ui.media.RailItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

/**
 * The active profile's pinned shelves, shared by Home, the shelf pages and the preview sheet so
 * they all show the same thing. Every change goes to the backend, then reloads the shelves.
 */
object ShelfStore {

    private val mutableShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = mutableShelves

    private var loadedFor: String? = null

    private fun profileId(): String = ProfileSession.activeProfile.value?.id.orEmpty()

    /** Loads the shelves; keeps what is shown when the backend can't be reached. */
    suspend fun refresh() {
        val profileId = profileId()
        if (loadedFor != profileId) {
            mutableShelves.value = emptyList()
            loadedFor = profileId
        }
        try {
            val data = OtterApi.client.query(ShelvesQuery(profileId)).execute().dataOrThrow()
            mutableShelves.value = data.shelves.map { shelf -> shelf.shelfFields.toShelf() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return
        }
    }

    /** Creates a shelf at the end; returns it, or null (with a toast) when that fails. */
    suspend fun create(name: String): Shelf? {
        val cleaned = cleanShelfName(name)
        if (cleaned == null) {
            return null
        }
        var created: Shelf? = null
        attempt("Could not create the shelf") {
            val data = OtterApi.client.mutation(CreateShelfMutation(profileId(), cleaned)).execute().dataOrThrow()
            created = data.createShelf.shelfFields.toShelf()
        }
        return created
    }

    suspend fun rename(shelfId: String, name: String): Boolean {
        val cleaned = cleanShelfName(name)
        if (cleaned == null) {
            return false
        }
        return attempt("Could not rename the shelf") {
            OtterApi.client.mutation(RenameShelfMutation(profileId(), shelfId, cleaned)).execute().dataOrThrow()
        }
    }

    suspend fun delete(shelfId: String): Boolean = attempt("Could not delete the shelf") {
        OtterApi.client.mutation(DeleteShelfMutation(profileId(), shelfId)).execute().dataOrThrow()
    }

    suspend fun add(shelfId: String, titleId: String): Boolean = attempt("Could not add to the shelf") {
        OtterApi.client.mutation(AddToShelfMutation(profileId(), shelfId, titleId)).execute().dataOrThrow()
    }

    suspend fun remove(shelfId: String, titleId: String): Boolean = attempt("Could not remove from the shelf") {
        OtterApi.client.mutation(RemoveFromShelfMutation(profileId(), shelfId, titleId)).execute().dataOrThrow()
    }

    /** Moves a shelf one place up (-1) or down (1). */
    suspend fun moveShelf(shelfId: String, offset: Int) {
        val current = mutableShelves.value
        val reordered = current.movedBy(current.indexOfFirst { shelf -> shelf.id == shelfId }, offset)
        if (reordered == current) {
            return
        }
        mutableShelves.value = reordered
        attempt("Could not reorder the shelves") {
            OtterApi.client.mutation(ReorderShelvesMutation(profileId(), reordered.map { shelf -> shelf.id })).execute().dataOrThrow()
        }
    }

    /** Moves a title one place up (-1) or down (1) within its shelf. */
    suspend fun moveTitle(shelfId: String, titleId: String, offset: Int) {
        val shelf = mutableShelves.value.firstOrNull { candidate -> candidate.id == shelfId }
        if (shelf == null) {
            return
        }
        val reordered = shelf.items.movedBy(shelf.items.indexOfFirst { item -> item.id == titleId }, offset)
        if (reordered == shelf.items) {
            return
        }
        mutableShelves.update { all -> all.map { candidate -> replaceItems(candidate, shelfId, reordered) } }
        attempt("Could not reorder the shelf") {
            val mediaIds = reordered.map { item -> item.id }
            OtterApi.client.mutation(ReorderShelfTitlesMutation(profileId(), shelfId, mediaIds)).execute().dataOrThrow()
        }
    }

    private fun replaceItems(shelf: Shelf, shelfId: String, items: List<RailItem>): Shelf {
        if (shelf.id != shelfId) {
            return shelf
        }
        return shelf.copy(items = items)
    }

    // Runs one backend change and reloads the shelves either way, so the screen ends up true to the backend.
    private suspend fun attempt(failureMessage: String, change: suspend () -> Unit): Boolean {
        var succeeded = true
        try {
            change()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            succeeded = false
            Toasts.push(failureMessage, ToastKind.ERROR)
        }
        refresh()
        return succeeded
    }
}
