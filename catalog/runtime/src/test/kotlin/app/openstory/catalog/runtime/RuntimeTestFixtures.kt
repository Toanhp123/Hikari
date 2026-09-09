package app.openstory.catalog.runtime

import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.source.CatalogAcquisitionSource
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.DiscoverAcquisitionSection
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal val TEST_SOURCE_KEY = CatalogSourceKey("fixture.source")
internal val TEST_BINDING = CatalogSourceBinding(TEST_SOURCE_KEY, "host-v7")

internal fun testRef(
    sourceStoryId: String = "story-one",
    sourceKey: CatalogSourceKey = TEST_SOURCE_KEY,
) = StorySourceRef(
    storyId = SourceStoryIdV1.derive(SourceStoryKey(sourceKey, sourceStoryId)),
    catalogSourceKey = sourceKey,
    sourceStoryId = sourceStoryId,
)

internal fun testDiscoverAcquisition(
    mediaType: CatalogMediaType = CatalogMediaType.MANGA,
    sourceStoryId: String = "story-one",
) = DiscoverAcquisition(
    sections = listOf(
        DiscoverAcquisitionSection(
            kind = CatalogSectionKind.POPULAR,
            items = listOf(
                DiscoverAcquisitionItem(
                    sourceStoryId = sourceStoryId,
                    title = "Story One",
                    contentType = mediaType,
                    cover = null,
                    rating = null,
                    publicationStatusSummary = null,
                    latestUpdateEpochMs = null,
                ),
            ),
        ),
    ),
)

internal fun testStoryAcquisition(sourceStoryId: String = "story-one") = StoryDetailAcquisition(
    sourceStoryId = sourceStoryId,
    title = "Story One",
    contentType = CatalogMediaType.MANGA,
    cover = null,
    rating = null,
    publicationStatusSummary = null,
    latestUpdateEpochMs = null,
    description = "Description",
    authors = listOf("Author"),
    artists = listOf("Artist"),
    genres = listOf("Genre"),
    publicationStatus = "Ongoing",
    language = "English",
)

internal object RuntimeTestData {
    fun publishedCards(mediaType: CatalogMediaType): List<DiscoverCard> =
        testDiscoverAcquisition(mediaType).sections.single().items.mapIndexed { index, item ->
            DiscoverCard(
                ref = testRef(item.sourceStoryId),
                sectionKind = CatalogSectionKind.POPULAR,
                itemPosition = index,
                title = item.title,
                contentType = mediaType,
                sourceVersion = "persisted",
                coverLocator = null,
                coverAssetKey = null,
                rating = null,
                publicationStatusSummary = null,
                latestUpdateEpochMs = null,
            )
        }
}

internal class RuntimeFakeStorage : CatalogRuntimeStore {
    val discoverFlows = CatalogMediaType.entries.associateWith {
        MutableSharedFlow<DiscoverPersistenceState>(replay = 1, extraBufferCapacity = 16)
    }
    private val storyFlows = mutableMapOf<StorySourceRef, MutableSharedFlow<StoryDetailProjection?>>()
    val discoverCommands = mutableListOf<DiscoverPublicationCommand>()
    val storyCommands = mutableListOf<StoryDetailPublicationCommand>()
    val touches = mutableListOf<Pair<StorySourceRef, Long>>()
    val releases = mutableListOf<StorySourceRef>()
    var discoverReadFailure: Throwable? = null
    var storyReadFailure: Throwable? = null
    var discoverPublishFailure: Throwable? = null
    var touchFailure: Throwable? = null
    var discoverObserveCount = 0
    var storyObserveCount = 0
    var closeCount = 0

    fun storyFlow(ref: StorySourceRef): MutableSharedFlow<StoryDetailProjection?> =
        storyFlows.getOrPut(ref) { MutableSharedFlow(replay = 1, extraBufferCapacity = 16) }

    override fun observe(
        catalogSourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
    ): Flow<DiscoverPersistenceState> {
        require(catalogSourceKey == TEST_SOURCE_KEY)
        discoverObserveCount += 1
        discoverReadFailure?.let { throw it }
        return discoverFlows.getValue(mediaType)
    }

    override fun observe(ref: StorySourceRef): Flow<StoryDetailProjection?> {
        storyObserveCount += 1
        storyReadFailure?.let { throw it }
        return storyFlow(ref)
    }

    override suspend fun publishDiscover(
        command: DiscoverPublicationCommand,
        retentionProtectedStoryIds: Set<StoryId>,
    ): CatalogMutationDiagnostics {
        discoverPublishFailure?.let { throw it }
        discoverCommands += command
        discoverFlows.getValue(command.mediaType).emit(
            DiscoverPersistenceState.Published(
                generation = discoverCommands.size.toLong(),
                provenance = command.provenance,
                cards = command.cards,
            ),
        )
        return CatalogMutationDiagnostics(command.cards.mapTo(linkedSetOf()) { it.ref.storyId })
    }

    override suspend fun publishStoryDetail(command: StoryDetailPublicationCommand) {
        storyCommands += command
    }

    override suspend fun touchStoryAccess(ref: StorySourceRef, accessedAtEpochMs: Long) {
        touchFailure?.let { throw it }
        touches += ref to accessedAtEpochMs
    }

    override suspend fun releaseStoryDemand(
        ref: StorySourceRef,
        retentionProtectedStoryIds: Set<StoryId>,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics {
        releases += ref
        return CatalogMutationDiagnostics(setOf(ref.storyId))
    }

    override fun close() {
        closeCount += 1
    }
}

internal class RecordingSource(
    private val discover: suspend (CatalogMediaType) -> DiscoverAcquisition = { testDiscoverAcquisition(it) },
    private val story: suspend (StorySourceRef) -> StoryDetailAcquisition = { testStoryAcquisition(it.sourceStoryId) },
) : CatalogAcquisitionSource {
    val discoverCalls = mutableListOf<CatalogMediaType>()
    val storyCalls = mutableListOf<StorySourceRef>()

    override suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition {
        discoverCalls += mediaType
        return discover(mediaType)
    }

    override suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition {
        storyCalls += ref
        return story(ref)
    }
}

internal suspend fun assertCatalogFailure(
    block: suspend () -> Unit,
): CatalogFailureException = try {
    block()
    throw AssertionError("Expected CatalogFailureException")
} catch (error: CatalogFailureException) {
    error
}
