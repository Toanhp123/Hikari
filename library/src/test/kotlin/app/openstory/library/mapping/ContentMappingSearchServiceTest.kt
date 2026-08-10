package app.openstory.library.mapping

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.content.ContentSource
import app.openstory.library.content.ContentSourceFailure
import app.openstory.library.content.ContentSourceRegistry
import app.openstory.library.content.ContentSourceResult
import app.openstory.library.content.ContentSourceStory
import app.openstory.library.matching.ContentMatchDecision
import app.openstory.library.matching.ContentStoryMatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class ContentMappingSearchServiceTest {
    @Test
    fun quickStageUsesPreferredSourcesAndTimesOutSlowPeer() = runTest {
        val fast = source("org.example.fast") { query ->
            ContentSourceResult.Success(listOf(candidate("fast-$query")))
        }
        val slow = source("org.example.slow") { _ ->
            delay(1_000L)
            ContentSourceResult.Success(listOf(candidate("slow")))
        }
        val deferred = source("org.example.deferred") { _ ->
            ContentSourceResult.Success(listOf(candidate("deferred")))
        }
        val service = service(
            sources = listOf(deferred, slow, fast),
            policy = ContentMappingSearchPolicy(
                quickSourceCount = 2,
                maxQueryVariants = 1,
                quickSourceTimeoutMillis = 100L,
            ),
        )

        val report = service.quick(
            storyId = STORY_ID,
            preferredPluginIds = listOf(fast.pluginId, slow.pluginId),
        )

        assertEquals(listOf(fast.pluginId, slow.pluginId), report.searchedPluginIds)
        assertFalse(deferred.searchQueries.isNotEmpty())
        assertEquals(listOf("content.source_timeout"), report.failures.map { it.code })
        assertEquals(listOf(fast.pluginId), report.candidates.map { it.pluginId })
    }

    @Test
    fun deferredStageIsolatesSourceFailureAndKeepsPeerCandidate() = runTest {
        val quick = source("org.example.quick") { _ -> ContentSourceResult.Success(emptyList()) }
        val failing = source("org.example.failing") { _ ->
            ContentSourceResult.Failure(ContentSourceFailure("plugin.http_request_failed", true))
        }
        val healthy = source("org.example.healthy") { _ ->
            ContentSourceResult.Success(listOf(candidate("healthy")))
        }
        val service = service(
            sources = listOf(healthy, quick, failing),
            policy = ContentMappingSearchPolicy(quickSourceCount = 1, maxQueryVariants = 1),
        )

        val report = service.deferred(STORY_ID, preferredPluginIds = listOf(quick.pluginId))

        assertEquals(listOf(failing.pluginId, healthy.pluginId), report.searchedPluginIds)
        assertEquals(listOf("plugin.http_request_failed"), report.failures.map { it.code })
        assertEquals(listOf(healthy.pluginId), report.candidates.map { it.pluginId })
        assertEquals(ContentMatchDecision.AUTO_LINK, report.candidates.single().match.decision)
    }

    @Test
    fun thrownPeerFailureIsIsolatedFromHealthySource() = runTest {
        val throwing = source("org.example.throwing") { _ -> error("source bug") }
        val healthy = source("org.example.healthy") { _ ->
            ContentSourceResult.Success(listOf(candidate("healthy")))
        }
        val service = service(
            sources = listOf(throwing, healthy),
            policy = ContentMappingSearchPolicy(quickSourceCount = 2, maxQueryVariants = 1),
        )

        val report = service.quick(STORY_ID)

        assertEquals(listOf("content.source_failed"), report.failures.map { it.code })
        assertEquals(listOf(healthy.pluginId), report.candidates.map { it.pluginId })
    }

    @Test
    fun queryVariantsAreDeterministicDeduplicatedAndCapped() = runTest {
        val source = source("org.example.query") { _ -> ContentSourceResult.Success(emptyList()) }
        val projection = projection(
            aliases = setOf("Zeta", "alpha", "ALPHA", "Beta"),
        )
        val service = service(
            sources = listOf(source),
            projection = projection,
            policy = ContentMappingSearchPolicy(quickSourceCount = 1, maxQueryVariants = 3),
        )

        val report = service.quick(STORY_ID)

        assertEquals(listOf("The Story", "ALPHA", "Beta"), report.queryVariants)
        assertEquals(report.queryVariants, source.searchQueries)
    }

    @Test
    fun urlResolutionInvokesOnlyMatchingHostsAndIsolatesUnsupportedOperation() = runTest {
        val unsupported = source(
            id = "org.example.unsupported",
            allowedHosts = setOf("reader.example"),
            resolve = {
                ContentSourceResult.Failure(ContentSourceFailure("content.url_resolution_unsupported", false))
            },
        ) { _ -> ContentSourceResult.Success(emptyList()) }
        val healthy = source(
            id = "org.example.resolver",
            allowedHosts = setOf("reader.example"),
            resolve = { ContentSourceResult.Success(candidate("resolved", "https://reader.example/story/resolved")) },
        ) { _ -> ContentSourceResult.Success(emptyList()) }
        val unrelated = source(
            id = "org.example.other",
            allowedHosts = setOf("other.example"),
            resolve = { ContentSourceResult.Success(candidate("wrong")) },
        ) { _ -> ContentSourceResult.Success(emptyList()) }
        val service = service(listOf(unrelated, unsupported, healthy))

        val report = service.resolveUrl(STORY_ID, "https://reader.example/story/resolved")

        assertEquals(listOf(healthy.pluginId, unsupported.pluginId), report.searchedPluginIds)
        assertEquals(1, healthy.resolveCalls)
        assertEquals(1, unsupported.resolveCalls)
        assertEquals(0, unrelated.resolveCalls)
        assertEquals(listOf("content.url_resolution_unsupported"), report.failures.map { it.code })
        assertEquals(listOf(healthy.pluginId), report.candidates.map { it.pluginId })
    }

    @Test
    fun invalidOrUnclaimedUrlNeverInvokesPlugin() = runTest {
        val source = source(
            id = "org.example.reader",
            allowedHosts = setOf("reader.example"),
        ) { _ -> ContentSourceResult.Success(emptyList()) }
        val service = service(listOf(source))

        val invalid = service.resolveUrl(STORY_ID, "http://reader.example/story/1")
        val oversized = service.resolveUrl(STORY_ID, "https://reader.example/" + "a".repeat(5_000))
        val unclaimed = service.resolveUrl(STORY_ID, "https://other.example/story/1")

        assertEquals("content.url_invalid", invalid.failures.single().code)
        assertEquals("content.url_invalid", oversized.failures.single().code)
        assertEquals("content.url_host_unclaimed", unclaimed.failures.single().code)
        assertEquals(0, source.resolveCalls)
    }
}

private val STORY_ID = StoryId("story:content-search")

private fun service(
    sources: List<FakeContentSource>,
    projection: CatalogStoryProjection = projection(),
    policy: ContentMappingSearchPolicy = ContentMappingSearchPolicy(),
) = ContentMappingSearchService(
    projections = FakeProjectionRepository(projection),
    sources = FakeContentSourceRegistry(sources),
    matcher = ContentStoryMatcher(),
    policy = policy,
)

private fun projection(
    aliases: Set<String> = setOf("Story Alias"),
) = CatalogStoryProjection(
    storyId = STORY_ID,
    title = "The Story",
    contentType = ContentType.WEB_NOVEL,
    coverUrl = null,
    aliases = aliases,
    authors = setOf("Author"),
)

private fun candidate(
    sourceStoryId: String,
    sourceUrl: String? = "https://reader.example/story/$sourceStoryId",
) = ContentSourceStory(
    sourceStoryId = sourceStoryId,
    title = "The Story",
    aliases = setOf("Story Alias"),
    authors = setOf("Author"),
    contentType = ContentType.WEB_NOVEL,
    sourceUrl = sourceUrl,
)

private class FakeProjectionRepository(
    private val projection: CatalogStoryProjection,
) : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(listOf(projection))
}

private class FakeContentSourceRegistry(
    private val sources: List<ContentSource>,
) : ContentSourceRegistry {
    override suspend fun enabled(): List<ContentSource> = sources
}

private class FakeContentSource(
    override val pluginId: PluginId,
    override val version: String,
    override val allowedHosts: Set<String>,
    private val searchResult: suspend (String) -> ContentSourceResult<List<ContentSourceStory>>,
    private val resolveResult: suspend (String) -> ContentSourceResult<ContentSourceStory>,
) : ContentSource {
    val searchQueries = mutableListOf<String>()
    var resolveCalls = 0
        private set

    override suspend fun search(
        query: String,
        limit: Int,
    ): ContentSourceResult<List<ContentSourceStory>> {
        assertTrue(limit in 1..200)
        searchQueries += query
        return searchResult(query)
    }

    override suspend fun resolveUrl(url: String): ContentSourceResult<ContentSourceStory> {
        resolveCalls += 1
        return resolveResult(url)
    }
}

private fun source(
    id: String,
    allowedHosts: Set<String> = setOf("reader.example"),
    resolve: suspend (String) -> ContentSourceResult<ContentSourceStory> = {
        ContentSourceResult.Failure(ContentSourceFailure("content.url_resolution_unsupported", false))
    },
    search: suspend (String) -> ContentSourceResult<List<ContentSourceStory>>,
) = FakeContentSource(
    pluginId = PluginId(id),
    version = "1.0.0",
    allowedHosts = allowedHosts,
    searchResult = search,
    resolveResult = resolve,
)
