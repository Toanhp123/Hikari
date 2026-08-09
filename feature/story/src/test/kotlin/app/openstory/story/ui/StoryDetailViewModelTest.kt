package app.openstory.story.ui

import app.openstory.catalog.model.*
import app.openstory.common.AppResult
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoryDetailViewModelTest {
    @Test
    fun observesCatalogSnapshotAndRetainsSourceIdentity() = runTest {
        val storyId = StoryId("story-1")
        val pluginId = PluginId("catalog.a")
        val viewModel = StoryDetailViewModel(
            storyFlow = flowOf(
                StoryCatalogSnapshot(
                    Story(storyId, ContentType.WEB_NOVEL),
                    listOf(CatalogEntry(storyId, pluginId, "source-1", "Novel", contentType = ContentType.WEB_NOVEL)),
                ),
            ),
            enrichAction = { AppResult.Success(Unit) },
            scope = backgroundScope,
        )
        advanceUntilIdle()
        assertEquals(storyId, viewModel.state.value.story?.storyId)
        assertEquals("source-1", viewModel.state.value.story?.sources?.single()?.sourceId)
    }
}
