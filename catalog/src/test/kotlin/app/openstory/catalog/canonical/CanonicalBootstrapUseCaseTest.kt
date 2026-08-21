package app.openstory.catalog.canonical

import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CanonicalBootstrapUseCaseTest {
    @Test
    fun alreadyReadyDoesNotRebuild() = runTest {
        val repository = FakeCanonicalRepository(readyState("story:1"))
        val calls = mutableListOf<StoryId>()
        val useCase = CanonicalBootstrapUseCase(repository, CanonicalGenerationRebuilder { storyId, _ ->
            calls += storyId
            CanonicalFusionResult.Preparing(storyId)
        })

        assertIs<CanonicalStoryState.Ready>(useCase.ensureReady(StoryId("story:1")))
        assertEquals(emptyList(), calls)
    }

    @Test
    fun preparingStoryRebuildsOnceAndReturnsNewReadyState() = runTest {
        val storyId = StoryId("story:1")
        val repository = FakeCanonicalRepository(preparingState(storyId.value, sourceCount = 1))
        val calls = mutableListOf<Pair<StoryId, CanonicalFusionReason>>()
        val useCase = CanonicalBootstrapUseCase(repository, CanonicalGenerationRebuilder { id, reason ->
            calls += id to reason
            repository.replace(readyState(id.value))
            CanonicalFusionResult.Promoted((repository.current() as CanonicalStoryState.Ready).generation)
        })

        assertIs<CanonicalStoryState.Ready>(useCase.ensureReady(storyId))
        assertEquals(listOf(storyId to CanonicalFusionReason.BOOTSTRAP), calls)
    }

    @Test
    fun preparingWithoutLocalEvidenceRemainsPreparingAndCanBecomeDegraded() = runTest {
        val storyId = StoryId("story:1")
        val repository = FakeCanonicalRepository(preparingState(storyId.value, sourceCount = 0))
        val useCase = CanonicalBootstrapUseCase(repository, CanonicalGenerationRebuilder { id, _ ->
            repository.markHealth(id, CanonicalHealth.DEGRADED)
            CanonicalFusionResult.Preparing(id)
        })

        val state = assertIs<CanonicalStoryState.Preparing>(useCase.ensureReady(storyId))
        assertEquals(CanonicalHealth.DEGRADED, state.health)
    }

    @Test
    fun prewarmPreservesInputOrderAndLimit() = runTest {
        val repository = FakeCanonicalRepository(preparingState("story:1", sourceCount = 1))
        listOf("story:2", "story:3").forEach { repository.put(preparingState(it, sourceCount = 1)) }
        val calls = mutableListOf<StoryId>()
        val useCase = CanonicalBootstrapUseCase(repository, CanonicalGenerationRebuilder { id, _ ->
            calls += id
            CanonicalFusionResult.Preparing(id)
        })

        useCase.prewarm(listOf(StoryId("story:2"), StoryId("story:1"), StoryId("story:3")), limit = 2)

        assertEquals(listOf(StoryId("story:2"), StoryId("story:1")), calls)
    }

    @Test
    fun bootstrapConstructorHasNoFetchingOrMetadataCoordinatorDependency() {
        val constructorTypes = CanonicalBootstrapUseCase::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
        assertFalse(constructorTypes.any { it.name.contains("CatalogSourceRegistry") })
        assertFalse(constructorTypes.any { it.name.contains("CatalogMetadataCoordinator") })
    }

    private class FakeCanonicalRepository(initial: CanonicalStoryState) : CanonicalCatalogRepository {
        private val states = linkedMapOf(initial.story.id to MutableStateFlow<CanonicalStoryState?>(initial))

        fun put(state: CanonicalStoryState) {
            states[state.story.id] = MutableStateFlow(state)
        }

        fun replace(state: CanonicalStoryState) {
            states.getValue(state.story.id).value = state
        }

        fun current(): CanonicalStoryState = requireNotNull(states.values.first().value)

        override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> = states.getValue(storyId)
        override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = error("not used")
        override suspend fun state(storyId: StoryId): CanonicalStoryState? = states[storyId]?.value
        override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
        override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? =
            (state(storyId) as? CanonicalStoryState.Ready)?.generation
        override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference =
            requireNotNull(state(storyId)).preference
        override suspend fun setSourcePreference(preference: CanonicalSourcePreference) = Unit
        override suspend fun persistCandidate(candidate: CanonicalGeneration, expectedActiveGenerationId: String?) = false
        override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) {
            val current = requireNotNull(state(storyId))
            states.getValue(storyId).value = when (current) {
                is CanonicalStoryState.Preparing -> current.copy(health = health)
                is CanonicalStoryState.Ready -> current.copy(health = health)
            }
        }
        override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
    }

    private fun preparingState(id: String, sourceCount: Int): CanonicalStoryState.Preparing {
        val storyId = StoryId(id)
        return CanonicalStoryState.Preparing(
            story = Story(storyId, ContentType.MANGA),
            health = CanonicalHealth.REEVALUATING,
            preference = CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
            sources = List(sourceCount) { index ->
                val plugin = PluginId("plugin:${index + 1}")
                val sourceKey = SourceKey(plugin, "source:${index + 1}")
                CanonicalSourceSummary(
                    sourceKey = sourceKey,
                    entry = app.openstory.catalog.model.CatalogEntry(
                        storyId, plugin, sourceKey.sourceId, "Title", contentType = ContentType.MANGA,
                    ),
                    summary = app.openstory.catalog.metadata.CatalogMetadataStamp("1.0.0", 1),
                    full = null,
                    identityFingerprint = "identity:$index",
                    fusionFingerprint = "fusion:$index",
                )
            },
        )
    }

    private fun readyState(id: String): CanonicalStoryState.Ready {
        val preparing = preparingState(id, sourceCount = 1)
        val source = preparing.sources.single().sourceKey
        val generation = CanonicalGeneration(
            id = "gen:$id",
            storyId = preparing.story.id,
            fusionPolicyVersion = 1,
            primarySelectionPolicyVersion = 1,
            fusionFingerprint = "fusion:$id",
            effectivePrimary = source,
            metadata = CanonicalMetadata(
                "Title", null, null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), null, null, null,
            ),
            health = CanonicalHealth.FRESH,
            provenance = mapOf(
                CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                    CanonicalFieldKey.TITLE,
                    CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                    listOf(CanonicalFieldContributor(source, "fusion:source", CatalogMetadataLevel.Summary)),
                    listOf("primary"),
                    1,
                ),
            ),
            createdAtEpochMillis = 1,
        )
        return CanonicalStoryState.Ready(
            preparing.story,
            CanonicalHealth.FRESH,
            preparing.preference,
            preparing.sources,
            generation,
        )
    }
}
