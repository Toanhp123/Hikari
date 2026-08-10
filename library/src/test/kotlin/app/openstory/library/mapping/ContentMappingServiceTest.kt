package app.openstory.library.mapping

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.content.ContentSource
import app.openstory.library.content.ContentSourceFailure
import app.openstory.library.content.ContentSourceRegistry
import app.openstory.library.content.ContentSourceResult
import app.openstory.library.content.ContentSourceStory
import app.openstory.library.matching.ContentMatchDecision
import app.openstory.library.matching.ContentMatchExplanation
import app.openstory.library.matching.ContentMatchResult
import app.openstory.library.matching.ContentStoryMatcher
import app.openstory.library.matching.ContentTitleEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ContentMappingServiceTest {
    @Test
    fun automationWritesOnlyBestAutoLinkPerPluginAndCannotReplaceProtectedOrigins() = runTest {
        val repository = RecordingRepository()
        val service = service(
            repository = repository,
            stories = listOf(
                contentStory("preferred", title = "The Story"),
                contentStory("also-match", title = "The Story"),
            ),
        )

        service.automate(STORY_ID)

        assertEquals(1, repository.writes.size)
        assertEquals("also-match", repository.writes.single().mapping.sourceStoryId)
        assertEquals(ContentMappingOrigin.AUTOMATED, repository.writes.single().mapping.origin)
        assertEquals(setOf(ContentMappingOrigin.AUTOMATED), repository.writes.single().replaceableOrigins)
    }

    @Test
    fun approvalAndUrlAcceptanceCreateProtectedOrigins() = runTest {
        val repository = RecordingRepository()
        val clock = FakeClock(50L)
        val service = service(repository = repository, clock = clock)
        val candidate = candidate(policyVersion = 3)

        service.approve(STORY_ID, candidate)
        clock.advanceBy(1L)
        service.acceptUrl(STORY_ID, candidate)

        assertEquals(ContentMappingOrigin.USER_APPROVED, repository.writes[0].mapping.origin)
        assertEquals(ContentMappingOrigin.USER_URL, repository.writes[1].mapping.origin)
        assertEquals(ContentMappingOrigin.entries.toSet(), repository.writes[0].replaceableOrigins)
        assertTrue(repository.writes.all { write -> write.mapping.policyVersion == 3 })
    }

    @Test
    fun rejectionSuppressesOnlyTheSameCandidateAndPolicyVersion() = runTest {
        val repository = RecordingRepository()
        val service = service(repository = repository)
        val rejected = candidate(policyVersion = 1)
        repository.rejections += rejected.rejectionKey(STORY_ID)

        val samePolicy = service.searchForReview(STORY_ID)
        assertFalse(samePolicy.candidates.any { it.sourceStoryId == rejected.sourceStoryId })

        repository.rejections.clear()
        repository.rejections += rejected.rejectionKey(STORY_ID).copy(policyVersion = 2)
        val otherPolicy = service.searchForReview(STORY_ID)
        assertTrue(otherPolicy.candidates.any { it.sourceStoryId == rejected.sourceStoryId })
    }

    @Test
    fun rejectRecordsCandidatePolicyVersion() = runTest {
        val repository = RecordingRepository()
        val clock = FakeClock(70L)
        val service = service(repository = repository, clock = clock)

        service.reject(STORY_ID, candidate(policyVersion = 4))

        assertEquals(4, repository.recordedRejections.single().policyVersion)
        assertEquals(70L, repository.recordedRejections.single().rejectedAt)
    }
}

private val STORY_ID = StoryId("story:mapping-service")
private val PLUGIN_ID = PluginId("org.example.reader")

private fun service(
    repository: RecordingRepository,
    clock: FakeClock = FakeClock(10L),
    stories: List<ContentSourceStory> = listOf(contentStory("source-1")),
): ContentMappingService {
    val search = ContentMappingSearchService(
        projections = FakeMappingProjectionRepository,
        sources = FakeSourceRegistry(FakeSource(stories)),
        matcher = ContentStoryMatcher(),
        policy = ContentMappingSearchPolicy(quickSourceCount = 1, maxQueryVariants = 1),
    )
    return ContentMappingService(repository, search, clock)
}

private object FakeMappingProjectionRepository : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(
        listOf(
            CatalogStoryProjection(
                storyId = STORY_ID,
                title = "The Story",
                contentType = ContentType.WEB_NOVEL,
                coverUrl = null,
                aliases = setOf("Story"),
                authors = setOf("Author"),
            ),
        ),
    )
}

private class FakeSourceRegistry(private val source: ContentSource) : ContentSourceRegistry {
    override suspend fun enabled(): List<ContentSource> = listOf(source)
}

private class FakeSource(private val stories: List<ContentSourceStory>) : ContentSource {
    override val pluginId = PLUGIN_ID
    override val version = "1.0.0"
    override val allowedHosts = setOf("reader.example")

    override suspend fun search(
        query: String,
        limit: Int,
    ): ContentSourceResult<List<ContentSourceStory>> = ContentSourceResult.Success(stories.take(limit))

    override suspend fun resolveUrl(url: String): ContentSourceResult<ContentSourceStory> =
        stories.firstOrNull()?.let { ContentSourceResult.Success(it) }
            ?: ContentSourceResult.Failure(ContentSourceFailure("content.not_found", false))
}

private data class RecordedWrite(
    val mapping: ContentMapping,
    val replaceableOrigins: Set<ContentMappingOrigin>,
)

private class RecordingRepository : ContentMappingRepository {
    val writes = mutableListOf<RecordedWrite>()
    val recordedRejections = mutableListOf<ContentMappingRejection>()
    val rejections = mutableSetOf<ContentMappingRejection>()

    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = flowOf(emptyList())

    override fun observeAll(): Flow<List<ContentMapping>> = flowOf(emptyList())

    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult {
        writes += RecordedWrite(mapping, replaceableOrigins)
        return ContentMappingWriteResult.Written(mapping, changed = true)
    }

    override suspend fun reject(rejection: ContentMappingRejection) {
        recordedRejections += rejection
        rejections += rejection
    }

    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean = rejections.any { rejection ->
        rejection.storyId == storyId &&
            rejection.pluginId == pluginId &&
            rejection.sourceStoryId == sourceStoryId &&
            rejection.policyVersion == policyVersion
    }
}

private fun contentStory(
    sourceStoryId: String,
    title: String = "The Story",
) = ContentSourceStory(
    sourceStoryId = sourceStoryId,
    title = title,
    aliases = setOf("Story"),
    authors = setOf("Author"),
    contentType = ContentType.WEB_NOVEL,
    sourceUrl = "https://reader.example/story/$sourceStoryId",
)

private fun candidate(policyVersion: Int) = ContentMappingCandidate(
    pluginId = PLUGIN_ID,
    pluginVersion = "1.0.0",
    sourceStoryId = "source-1",
    sourceUrl = "https://reader.example/story/source-1",
    title = "The Story",
    match = ContentMatchResult(
        score = 1.0,
        decision = ContentMatchDecision.AUTO_LINK,
        explanation = ContentMatchExplanation(
            directEvidence = false,
            title = ContentTitleEvidence(1.0, "The Story", "The Story"),
            authorSimilarity = 1.0,
            authorConflict = false,
            contentTypeMatch = true,
            contentTypeConflict = false,
            reasons = listOf("decision:auto_link"),
        ),
        policyVersion = policyVersion,
    ),
)

private fun ContentMappingCandidate.rejectionKey(storyId: StoryId) = ContentMappingRejection(
    storyId = storyId,
    pluginId = pluginId,
    sourceStoryId = sourceStoryId,
    policyVersion = match.policyVersion,
    rejectedAt = 0L,
)
