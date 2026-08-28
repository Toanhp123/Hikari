package app.openstory.catalog.ui.discover

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiscoverCanonicalBootstrapPipelineTest {
    @Test
    fun emptyStoryIdsEmitTerminalEmptySettlementSnapshot() = runTest {
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(
                DiscoverCanonicalRepository(emptyList()),
                CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) },
            ),
            TestProjectionRepository(emptyList()),
        )

        val emissions = pipeline.settle(emptyList(), ContentType.MANGA).toList()

        assertEquals(listOf(emptyMap()), emissions)
    }

    @Test
    fun duplicateStoryIdsSettleOnceInFirstSeenOrder() = runTest {
        val first = StoryId("story:first")
        val second = StoryId("story:second")
        val canonical = DiscoverCanonicalRepository(listOf(readyDiscoverState(first), readyDiscoverState(second)))
        val findCalls = mutableListOf<StoryId>()
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(
                canonical,
                CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) },
            ),
            TestProjectionRepository(
                seed = emptyList(),
                findValues = mapOf(first to projection(first, "First"), second to projection(second, "Second")),
                onFind = findCalls::add,
            ),
        )

        val emissions = pipeline.settle(listOf(first, second, first), ContentType.MANGA).toList()

        assertEquals(listOf(first, second), findCalls)
        assertEquals(listOf(first, second), emissions.last().keys.toList())
    }

    @Test
    fun seedCancellationIsRethrown() = runTest {
        val storyId = StoryId("story:seed-cancelled")
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(
                DiscoverCanonicalRepository(readyDiscoverState(storyId)),
                CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) },
            ),
            TestProjectionRepository(emptyList(), cancelSeed = true),
        )

        assertFailsWith<CancellationException> {
            pipeline.settle(listOf(storyId), ContentType.MANGA).toList()
        }
    }

    @Test
    fun bootstrapCancellationIsRethrown() = runTest {
        val storyId = StoryId("story:bootstrap-cancelled")
        val canonical = DiscoverCanonicalRepository(preparingDiscoverState(storyId))
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(
                canonical,
                CanonicalGenerationRebuilder { _, _ -> throw CancellationException("cancel bootstrap") },
            ),
            TestProjectionRepository(emptyList()),
        )

        assertFailsWith<CancellationException> {
            pipeline.settle(listOf(storyId), ContentType.MANGA).toList()
        }
    }

    @Test
    fun projectionLookupCancellationIsRethrown() = runTest {
        val storyId = StoryId("story:projection-cancelled")
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(
                DiscoverCanonicalRepository(readyDiscoverState(storyId)),
                CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) },
            ),
            TestProjectionRepository(emptyList(), cancelFind = setOf(storyId)),
        )

        assertFailsWith<CancellationException> {
            pipeline.settle(listOf(storyId), ContentType.MANGA).toList()
        }
    }

    @Test
    fun existingProjectionSettlesWithoutBootstrap() = runTest {
        val storyId = StoryId("story:existing")
        val canonical = DiscoverCanonicalRepository(preparingDiscoverState(storyId))
        val calls = mutableListOf<StoryId>()
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { id, _ ->
                calls += id
                CanonicalFusionResult.Preparing(id)
            },
        )
        val projection = projection(storyId, "Existing")
        val pipeline = pipeline(bootstrap, TestProjectionRepository(listOf(projection)))

        val emissions = pipeline.settle(listOf(storyId), ContentType.MANGA).toList()

        assertEquals(listOf(storyId), emissions.single().keys.toList())
        assertEquals(projection, assertIs<DiscoverCanonicalSettlement.Projected>(emissions.single()[storyId]).projection)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun existingLaterProjectionIsSeededBeforeEarlierMissingStoryBootstrapCompletes() = runTest {
        val first = StoryId("story:first")
        val second = StoryId("story:second")
        val canonical = DiscoverCanonicalRepository(listOf(preparingDiscoverState(first), preparingDiscoverState(second)))
        val calls = mutableListOf<StoryId>()
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { id, _ ->
                calls += id
                CanonicalFusionResult.Preparing(id)
            },
        )
        val secondProjection = projection(second, "Second")
        val pipeline = pipeline(bootstrap, TestProjectionRepository(listOf(secondProjection)))

        val seed = pipeline.settle(listOf(first, second), ContentType.MANGA).first()

        assertEquals(listOf(second), seed.keys.toList())
        assertEquals(secondProjection, assertIs<DiscoverCanonicalSettlement.Projected>(seed[second]).projection)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun canonicalReadyWithMatchingProjectionSettlesProjected() = runTest {
        val storyId = StoryId("story:ready")
        val canonical = DiscoverCanonicalRepository(readyDiscoverState(storyId))
        val expected = projection(storyId, "Ready")
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(emptyList(), findValues = mapOf(storyId to expected)),
        )

        val final = pipeline.settle(listOf(storyId), ContentType.MANGA).toList().last()

        assertEquals(expected, assertIs<DiscoverCanonicalSettlement.Projected>(final[storyId]).projection)
    }

    @Test
    fun canonicalReadyProjectionLookupWithDifferentContentTypeSettlesExcluded() = runTest {
        val storyId = StoryId("story:projection-anime")
        val canonical = DiscoverCanonicalRepository(readyDiscoverState(storyId))
        val animeProjection = CatalogStoryProjection(
            storyId = storyId,
            title = "Anime projection",
            contentType = ContentType.ANIME,
            coverUrl = null,
        )
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(
                canonical,
                CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) },
            ),
            TestProjectionRepository(emptyList(), findValues = mapOf(storyId to animeProjection)),
        )

        val final = pipeline.settle(listOf(storyId), ContentType.MANGA).toList().last()

        val excluded = assertIs<DiscoverCanonicalSettlement.ResolvedExcluded>(final[storyId])
        assertEquals(DiscoverExclusionReason.CONTENT_TYPE_MISMATCH, excluded.reason)
    }

    @Test
    fun canonicalReadyWithDifferentContentTypeSettlesExcluded() = runTest {
        val storyId = StoryId("story:anime")
        val ready = readyDiscoverState(storyId).copy(story = Story(storyId, ContentType.ANIME))
        val canonical = DiscoverCanonicalRepository(ready)
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(emptyList()),
        )

        val final = pipeline.settle(listOf(storyId), ContentType.MANGA).toList().last()

        val excluded = assertIs<DiscoverCanonicalSettlement.ResolvedExcluded>(final[storyId])
        assertEquals(DiscoverExclusionReason.CONTENT_TYPE_MISMATCH, excluded.reason)
    }

    @Test
    fun ensureReadyReturningPreparingSettlesFailedInsteadOfPermanentPending() = runTest {
        val storyId = StoryId("story:preparing")
        val canonical = DiscoverCanonicalRepository(preparingDiscoverState(storyId))
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(emptyList()),
        )

        val final = pipeline.settle(listOf(storyId), ContentType.MANGA).toList().last()

        val failed = assertIs<DiscoverCanonicalSettlement.Failed>(final[storyId])
        assertEquals("catalog.discover.canonical_still_preparing", failed.failure.code)
        assertEquals(false, failed.failure.retryable)
    }

    @Test
    fun canonicalReadyWithoutProjectionSettlesFailed() = runTest {
        val storyId = StoryId("story:missing-projection")
        val canonical = DiscoverCanonicalRepository(readyDiscoverState(storyId))
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(emptyList()),
        )

        val final = pipeline.settle(listOf(storyId), ContentType.MANGA).toList().last()

        val failed = assertIs<DiscoverCanonicalSettlement.Failed>(final[storyId])
        assertEquals("catalog.discover.projection_missing", failed.failure.code)
        assertEquals(true, failed.failure.retryable)
    }

    @Test
    fun projectionLookupFailureIsNotMislabelledAsBootstrapFailure() = runTest {
        val storyId = StoryId("story:lookup-failure")
        val canonical = DiscoverCanonicalRepository(readyDiscoverState(storyId))
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(emptyList(), findFailures = setOf(storyId)),
        )

        val final = pipeline.settle(listOf(storyId), ContentType.MANGA).toList().last()

        val failed = assertIs<DiscoverCanonicalSettlement.Failed>(final[storyId])
        assertEquals("catalog.discover.projection_lookup_failed", failed.failure.code)
        assertEquals(true, failed.failure.retryable)
    }

    @Test
    fun seedSnapshotFailureFallsBackToPerStorySettlement() = runTest {
        val storyId = StoryId("story:seed-failure")
        val canonical = DiscoverCanonicalRepository(readyDiscoverState(storyId))
        val expected = projection(storyId, "Recovered")
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(
                seed = emptyList(),
                findValues = mapOf(storyId to expected),
                failSeed = true,
            ),
        )

        val emissions = pipeline.settle(listOf(storyId), ContentType.MANGA).toList()

        assertTrue(emissions.first().isEmpty())
        assertEquals(expected, assertIs<DiscoverCanonicalSettlement.Projected>(emissions.last()[storyId]).projection)
    }

    @Test
    fun oneStoryFailureDoesNotPreventLaterStorySettlement() = runTest {
        val first = StoryId("story:broken")
        val second = StoryId("story:healthy")
        val canonical = DiscoverCanonicalRepository(listOf(preparingDiscoverState(first), readyDiscoverState(second)))
        val expected = projection(second, "Healthy")
        val bootstrap = CanonicalBootstrapUseCase(
            canonical,
            CanonicalGenerationRebuilder { id, _ ->
                if (id == first) error("broken local evidence")
                CanonicalFusionResult.Preparing(id)
            },
        )
        val pipeline = pipeline(
            bootstrap,
            TestProjectionRepository(emptyList(), findValues = mapOf(second to expected)),
        )

        val final = pipeline.settle(listOf(first, second), ContentType.MANGA).toList().last()

        val failed = assertIs<DiscoverCanonicalSettlement.Failed>(final[first])
        assertEquals("catalog.discover.canonical_bootstrap_failed", failed.failure.code)
        assertEquals(true, failed.failure.retryable)
        assertEquals(expected, assertIs<DiscoverCanonicalSettlement.Projected>(final[second]).projection)
    }

    @Test
    fun settlementEmitsProgressivelyInInputOrder() = runTest {
        val first = StoryId("story:first")
        val second = StoryId("story:second")
        val canonical = DiscoverCanonicalRepository(listOf(readyDiscoverState(first), readyDiscoverState(second)))
        val pipeline = pipeline(
            CanonicalBootstrapUseCase(canonical, CanonicalGenerationRebuilder { id, _ -> CanonicalFusionResult.Preparing(id) }),
            TestProjectionRepository(
                seed = emptyList(),
                findValues = mapOf(first to projection(first, "First"), second to projection(second, "Second")),
            ),
        )

        val emissions = pipeline.settle(listOf(first, second), ContentType.MANGA).toList()

        assertEquals(
            listOf(
                emptyList(),
                listOf(first),
                listOf(first, second),
            ),
            emissions.map { it.keys.toList() },
        )
    }

    private fun kotlinx.coroutines.test.TestScope.pipeline(
        bootstrap: CanonicalBootstrapUseCase,
        projections: CatalogStoryProjectionRepository,
    ): DiscoverCanonicalBootstrapPipeline {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DiscoverCanonicalBootstrapPipeline(
            bootstrap,
            projections,
            FixedAppDispatchers(dispatcher, dispatcher, dispatcher),
        )
    }
}

private class TestProjectionRepository(
    private val seed: List<CatalogStoryProjection>,
    private val findValues: Map<StoryId, CatalogStoryProjection> = seed.associateBy(CatalogStoryProjection::storyId),
    private val findFailures: Set<StoryId> = emptySet(),
    private val failSeed: Boolean = false,
    private val cancelSeed: Boolean = false,
    private val cancelFind: Set<StoryId> = emptySet(),
    private val onFind: (StoryId) -> Unit = {},
) : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(seed)

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>> = flow {
        if (cancelSeed) throw CancellationException("cancel seed")
        if (failSeed) error("seed lookup unavailable")
        emit(seed.filter { it.storyId in storyIds })
    }

    override suspend fun find(storyId: StoryId): CatalogStoryProjection? {
        onFind(storyId)
        if (storyId in cancelFind) throw CancellationException("cancel projection lookup")
        if (storyId in findFailures) error("projection lookup unavailable")
        return findValues[storyId]
    }
}

private fun projection(storyId: StoryId, title: String) = CatalogStoryProjection(
    storyId = storyId,
    title = title,
    contentType = ContentType.MANGA,
    coverUrl = null,
)
