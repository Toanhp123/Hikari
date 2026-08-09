package app.openstory.story.ui

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.common.AppResult
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {
    @Test
    fun observesCatalogSnapshotAndRetainsSourceIdentity() = runTest {
        val storyId = StoryId("story-1")
        val pluginId = PluginId("catalog.a")
        val story = StoryCatalogSnapshot(
            Story(storyId, ContentType.WEB_NOVEL),
            listOf(CatalogEntry(storyId, pluginId, "source-1", "Novel", contentType = ContentType.WEB_NOVEL)),
        )
        val viewModel = StoryDetailViewModel(
            initialStoryId = storyId,
            observeStory = { flowOf(story) },
            enrichAction = { AppResult.Success(storyId) },
            scope = backgroundScope,
        )
        runCurrent()
        assertEquals(storyId, viewModel.state.value.story?.storyId)
        assertEquals("source-1", viewModel.state.value.story?.sources?.single()?.sourceId)
    }

    @Test
    fun observesCanonicalStoryIdReturnedByEnrichment() = runTest {
        val transientId = StoryId("transient")
        val canonicalId = StoryId("canonical")
        val observedIds = mutableListOf<StoryId>()
        val releaseEnrichment = CompletableDeferred<Unit>()
        val stories = mapOf(
            transientId to MutableStateFlow<StoryCatalogSnapshot?>(null),
            canonicalId to MutableStateFlow(snapshot(canonicalId, "Canonical")),
        )

        val viewModel = StoryDetailViewModel(
            initialStoryId = transientId,
            observeStory = { storyId ->
                observedIds += storyId
                stories.getValue(storyId)
            },
            enrichAction = {
                releaseEnrichment.await()
                AppResult.Success(canonicalId)
            },
            scope = backgroundScope,
        )
        runCurrent()

        assertEquals(listOf(transientId), observedIds)
        assertEquals(null, viewModel.state.value.story)
        releaseEnrichment.complete(Unit)
        runCurrent()

        assertEquals(listOf(transientId, canonicalId), observedIds)
        assertEquals(canonicalId, viewModel.state.value.story?.storyId)
        assertEquals("Canonical", viewModel.state.value.story?.preferredTitle)
    }

    private fun snapshot(storyId: StoryId, title: String) = StoryCatalogSnapshot(
        Story(storyId, ContentType.WEB_NOVEL),
        listOf(
            CatalogEntry(
                storyId,
                PluginId("catalog.a"),
                "source-${storyId.value}",
                title,
                contentType = ContentType.WEB_NOVEL,
            ),
        ),
    )
}
