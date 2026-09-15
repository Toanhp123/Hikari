package app.openstory.story.feature

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPresentationSnapshot
import app.openstory.library.runtime.LibraryRuntime
import kotlinx.coroutines.flow.Flow

interface StoryLibraryFacet {
    fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?>

    suspend fun add(
        ref: StorySourceRef,
        originMediaContext: CatalogMediaType,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult

    suspend fun remove(ref: StorySourceRef): LibraryMutationResult

    suspend fun enrichSnapshot(
        ref: StorySourceRef,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult
}

class RuntimeStoryLibraryFacet(
    private val runtime: LibraryRuntime,
) : StoryLibraryFacet {
    override fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> =
        runtime.observeMembership(ref)

    override suspend fun add(
        ref: StorySourceRef,
        originMediaContext: CatalogMediaType,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult = runtime.mutations.add(ref, originMediaContext, snapshot)

    override suspend fun remove(ref: StorySourceRef): LibraryMutationResult =
        runtime.mutations.remove(ref)

    override suspend fun enrichSnapshot(
        ref: StorySourceRef,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult = runtime.mutations.enrichSnapshot(ref, snapshot)
}
