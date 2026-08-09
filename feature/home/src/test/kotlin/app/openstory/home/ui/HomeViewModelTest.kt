package app.openstory.home.ui

import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeCatalogCard
import app.openstory.home.model.HomeCatalogSection
import app.openstory.home.model.HomeRefreshReport
import app.openstory.home.model.HomeUiModel
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedSectionsRemainVisibleDuringRefresh() = runTest(mainDispatcher.scheduler) {
        val cached = cachedHome()
        val homeFlow = MutableStateFlow(cached)
        val refreshStarted = CompletableDeferred<Unit>()
        val allowRefreshToFinish = CompletableDeferred<Unit>()
        val viewModel = HomeViewModel(
            homeFlow = homeFlow,
            refreshAction = {
                refreshStarted.complete(Unit)
                allowRefreshToFinish.await()
                HomeRefreshReport(succeeded = listOf(PluginId("catalog.a")))
            },
            scope = backgroundScope,
        )

        runCurrent()
        assertEquals(cached, viewModel.state.value.home)

        viewModel.refresh()
        refreshStarted.await()
        runCurrent()

        val refreshing = viewModel.state.value
        assertTrue(refreshing.refreshing)
        assertEquals(cached, refreshing.home)
        assertEquals("Trending", refreshing.home.catalogs.single().sections.single().title)

        allowRefreshToFinish.complete(Unit)
        runCurrent()

        assertFalse(viewModel.state.value.refreshing)
        assertEquals(cached, viewModel.state.value.home)
    }

    @Test
    fun sourceSelectionDoesNotMutateCachedHome() = runTest(mainDispatcher.scheduler) {
        val cached = cachedHome()
        val viewModel = HomeViewModel(
            homeFlow = MutableStateFlow(cached),
            refreshAction = { HomeRefreshReport() },
            scope = backgroundScope,
        )

        runCurrent()
        viewModel.selectCatalog(PluginId("catalog.a"))
        runCurrent()

        assertEquals(PluginId("catalog.a"), viewModel.state.value.selectedCatalogId)
        assertEquals(cached, viewModel.state.value.home)

        viewModel.selectCombined()
        runCurrent()

        assertEquals(null, viewModel.state.value.selectedCatalogId)
        assertEquals(cached, viewModel.state.value.home)
    }
}

private fun cachedHome(): HomeUiModel = HomeUiModel(
    combined = emptyList(),
    catalogs = listOf(
        HomeCatalog(
            pluginId = PluginId("catalog.a"),
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                HomeCatalogSection(
                    sourceId = "trending",
                    title = "Trending",
                    items = listOf(
                        HomeCatalogCard(
                            storyId = StoryId("story-1"),
                            pluginId = PluginId("catalog.a"),
                            sourceId = "source-1",
                            title = "Fixture Novel",
                            contentType = ContentType.WEB_NOVEL,
                            authors = setOf("Fixture Author"),
                            coverReference = null,
                            score = 8.4,
                            scoreScale = 10.0,
                        ),
                    ),
                ),
            ),
        ),
    ),
)
