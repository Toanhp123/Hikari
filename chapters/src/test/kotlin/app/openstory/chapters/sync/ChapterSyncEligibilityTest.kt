package app.openstory.chapters.sync

import app.openstory.chapters.aggregation.ChapterAggregationEngine
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.normalization.ChapterLabelParser
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.chapters.source.ChapterSource
import app.openstory.chapters.source.ChapterSourcePage
import app.openstory.chapters.source.ChapterSourceRegistry
import app.openstory.chapters.source.ChapterSourceRelease
import app.openstory.chapters.source.ChapterSourceRequest
import app.openstory.chapters.source.ChapterSourceResult
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingWriteResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChapterSyncEligibilityTest {
    @Test
    fun mappingProtectionDoesNotImplyAuthenticationAndIneligibleSourceDoesNotBlockPublicSource() = runTest {
        val protectedPlugin = PluginId("org.example.protected")
        val publicPlugin = PluginId("org.example.public")
        val protectedMapping = mapping(protectedPlugin, ContentMappingOrigin.USER_APPROVED)
        val publicMapping = mapping(publicPlugin, ContentMappingOrigin.AUTOMATED)
        val protectedSource = CountingSource(protectedPlugin)
        val publicSource = CountingSource(publicPlugin)
        val repository = EligibilityChapterRepository()
        val service = ChapterSyncService(
            mappings = EligibilityMappingRepository(listOf(protectedMapping, publicMapping)),
            sources = object : ChapterSourceRegistry {
                override suspend fun enabled(): List<ChapterSource> = listOf(protectedSource, publicSource)
            },
            chapters = repository,
            aggregation = ChapterAggregationEngine(),
            parser = ChapterLabelParser(),
            clock = FakeClock(100),
            eligibility = ChapterSourceEligibilityResolver { current ->
                if (current.pluginId == protectedPlugin) {
                    ChapterSourceEligibility(current.pluginId, current.sourceStoryId, false, "auth.required")
                } else {
                    ChapterSourceEligibility(current.pluginId, current.sourceStoryId, true, null)
                }
            },
        )

        val report = assertIs<ChapterSyncReport.Failure>(service.sync(TEST_STORY_ID))

        assertTrue(protectedMapping.origin.isProtected)
        assertEquals(0, protectedSource.calls)
        assertEquals(2, publicSource.calls)
        assertTrue(repository.commits.all { it.syncState?.pluginId == publicPlugin })
        assertEquals("auth.required", report.failures.single().code)
    }

    private fun mapping(pluginId: PluginId, origin: ContentMappingOrigin) = ContentMapping(
        storyId = TEST_STORY_ID,
        pluginId = pluginId,
        sourceStoryId = "source-${pluginId.value}",
        origin = origin,
        policyVersion = 1,
        updatedAt = 1,
    )
}

private class CountingSource(
    override val pluginId: PluginId,
) : ChapterSource {
    override val version = "1.0.0"
    var calls = 0

    override suspend fun chapters(request: ChapterSourceRequest): ChapterSourceResult {
        calls += 1
        return ChapterSourceResult.Success(
            ChapterSourcePage(
                releases = listOf(
                    ChapterSourceRelease("release-$calls", "Chapter $calls", "$calls", "en", calls.toLong()),
                ),
                nextToken = null,
            ),
        )
    }
}

private class EligibilityMappingRepository(
    private val mappings: List<ContentMapping>,
) : ContentMappingRepository {
    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = flowOf(mappings)
    override fun observeAll(): Flow<List<ContentMapping>> = flowOf(mappings)
    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult = ContentMappingWriteResult.Written(mapping, true)
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean = false
}

private class EligibilityChapterRepository : ChapterRepository {
    val commits = mutableListOf<ChapterMutation>()

    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = flowOf(emptyList())
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = flowOf(emptyList())
    override suspend fun snapshot(storyId: StoryId) = ChapterGraphSnapshot(emptyList(), emptyList(), emptyList())
    override suspend fun commit(mutation: ChapterMutation): ChapterCommitResult {
        commits += mutation
        return ChapterCommitResult.Success
    }
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) = Unit
    override suspend fun syncState(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
    ): ChapterSyncState? = commits.lastOrNull { it.syncState?.pluginId == pluginId }?.syncState
}

private val TEST_STORY_ID = StoryId("story:eligibility")
