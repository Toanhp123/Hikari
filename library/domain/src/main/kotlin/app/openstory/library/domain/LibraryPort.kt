package app.openstory.library.domain

import app.openstory.catalog.domain.identity.StorySourceRef
import kotlinx.coroutines.flow.Flow

interface LibraryPort {
    fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?>

    fun observeWindow(query: LibraryQuery): Flow<LibraryWindow>

    suspend fun add(entry: LibraryEntry): LibraryMutationResult

    suspend fun remove(ref: StorySourceRef): LibraryMutationResult

    suspend fun enrichSnapshot(
        ref: StorySourceRef,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult
}
