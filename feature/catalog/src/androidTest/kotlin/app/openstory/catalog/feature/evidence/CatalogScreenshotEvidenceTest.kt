package app.openstory.catalog.feature.evidence

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.discover.DiscoverCardUi
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverScreen
import app.openstory.catalog.feature.discover.TEST_DISCOVER_SECTION_LABELS
import app.openstory.catalog.feature.discover.DiscoverSectionUi
import app.openstory.catalog.feature.discover.previewDescriptor
import app.openstory.catalog.feature.discover.DiscoverTestTags
import app.openstory.catalog.feature.discover.DiscoverUiState
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.designsystem.theme.HikariTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogScreenshotEvidenceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var surface by mutableStateOf<EvidenceSurface>(
        EvidenceSurface.Discover("discover-loading", CatalogMediaType.MANGA, loadingDiscoverState()),
    )
    private val capturedFiles = mutableListOf<File>()

    @Test
    fun capturesAcceptedCatalogSurfaceMatrixWithDeterministicNames() {
        composeRule.setContent { EvidenceContent(surface) }

        captureDiscoverMatrix()

        assertEquals(EXPECTED_SCREENSHOT_COUNT, capturedFiles.size)
        assertTrue(capturedFiles.all(File::isFile))
        assertTrue(capturedFiles.all { it.length() > 0L })
        assertEquals(capturedFiles.size, capturedFiles.map(File::getName).toSet().size)
    }

    private fun captureDiscoverMatrix() {
        showDiscover("discover-loading", loadingDiscoverState())
        scrollDiscoverTo(hasTestTag(DiscoverTestTags.POPULAR_SKELETON))
        composeRule.onNodeWithTag(DiscoverTestTags.POPULAR_SKELETON).assertIsDisplayed()
        capture("discover-loading")

        capturePublishedMedia(CatalogMediaType.MANGA, "manga")
        capturePublishedMedia(CatalogMediaType.LIGHT_NOVEL, "light-novel")

        showDiscover("discover-empty", DiscoverUiState(content = DiscoverContentState.Empty()))
        scrollDiscoverTo(hasTestTag(DiscoverTestTags.EMPTY))
        composeRule.onNodeWithTag(DiscoverTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).assertExists()
        capture("discover-empty")

        showDiscover("discover-refreshing", publishedDiscoverState(refreshing = true))
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Refreshing"),
        ).assertIsDisplayed()
        capture("discover-refreshing")

        showDiscover(
            "discover-refresh-issue",
            publishedDiscoverState(
                issue = CatalogIssueUi(CatalogIssueKind.ACQUISITION_FAILED, retryable = true),
            ),
        )
        scrollDiscoverTo(hasText("Try again"))
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        capture("discover-refresh-issue")
    }

    private fun capturePublishedMedia(mediaType: CatalogMediaType, mediaName: String) {
        showDiscover("discover-$mediaName", publishedDiscoverState(mediaType = mediaType), mediaType)
        listOf(
            CatalogSectionKind.POPULAR to "popular",
            CatalogSectionKind.LATEST_UPDATES to "latest",
            CatalogSectionKind.TOP_RATED to "top-rated",
        ).forEach { (kind, sectionName) ->
            val sectionTag = DiscoverTestTags.section(kind)
            scrollDiscoverTo(hasTestTag(sectionTag))
            composeRule.onNodeWithTag(sectionTag).assertIsDisplayed()
            capture("discover-$mediaName-$sectionName")
        }
    }

    private fun showDiscover(
        name: String,
        state: DiscoverUiState,
        mediaType: CatalogMediaType = CatalogMediaType.MANGA,
    ) {
        show(EvidenceSurface.Discover(name, mediaType, state))
    }

    private fun show(next: EvidenceSurface) {
        composeRule.runOnIdle { surface = next }
        composeRule.waitForIdle()
    }

    private fun scrollDiscoverTo(matcher: SemanticsMatcher) {
        composeRule.onNodeWithTag(DiscoverTestTags.ROOT).performScrollToNode(matcher)
    }

    private fun capture(scenarioName: String) {
        val file = ScreenshotEvidence.capture(
            root = composeRule.onRoot(useUnmergedTree = true),
            scenarioName = scenarioName,
        )
        val expectedWidthClass = if (screenWidthDp() >= WIDE_WIDTH_DP) "wide" else "compact"
        assertEquals("$expectedWidthClass-$scenarioName.png", file.name)
        assertEquals(EVIDENCE_DIRECTORY_NAME, file.parentFile?.name)
        assertTrue(file.isFile)
        assertTrue(file.length() > 0L)
        println("SCREENSHOT_EVIDENCE=${file.absolutePath}")
        capturedFiles += file
    }

    private fun screenWidthDp(): Int =
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .resources.configuration.screenWidthDp

    @Composable
    private fun EvidenceContent(current: EvidenceSurface) {
        HikariTheme(darkTheme = false) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                key(current.name) {
                    when (current) {
                        is EvidenceSurface.Discover -> DiscoverScreen(
                            mediaType = current.mediaType,
                            state = current.state,
                            listState = rememberLazyListState(),
                            sectionLabels = TEST_DISCOVER_SECTION_LABELS,
                            onStorySelected = { _ -> },
                            onRefresh = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }
    }

    private sealed interface EvidenceSurface {
        val name: String

        data class Discover(
            override val name: String,
            val mediaType: CatalogMediaType,
            val state: DiscoverUiState,
        ) : EvidenceSurface
    }

    private companion object {
        const val EXPECTED_SCREENSHOT_COUNT = 10
        const val WIDE_WIDTH_DP = 600
        const val EVIDENCE_DIRECTORY_NAME = "catalog-screenshot-evidence"
        val SOURCE_KEY = CatalogSourceKey("screenshot-evidence")

        fun loadingDiscoverState() = DiscoverUiState(
            content = DiscoverContentState.NoContentLoading,
        )

        fun publishedDiscoverState(
            mediaType: CatalogMediaType = CatalogMediaType.MANGA,
            refreshing: Boolean = false,
            issue: CatalogIssueUi? = null,
        ) = DiscoverUiState(
            content = DiscoverContentState.Content(
                sections = listOf(
                    section(mediaType, CatalogSectionKind.POPULAR, 5),
                    section(mediaType, CatalogSectionKind.LATEST_UPDATES, 9),
                    section(mediaType, CatalogSectionKind.TOP_RATED, 5),
                ),
                refreshing = refreshing,
                issue = issue,
            ),
        )

        fun section(
            mediaType: CatalogMediaType,
            kind: CatalogSectionKind,
            size: Int,
        ) = DiscoverSectionUi(
            descriptor = kind.previewDescriptor(),
            cards = List(size) { position -> card(mediaType, kind, position) },
        )

        fun card(
            mediaType: CatalogMediaType,
            kind: CatalogSectionKind,
            position: Int,
        ) = DiscoverCardUi(
            ref = storyRef(mediaType, kind, position),
            title = "${mediaType.evidenceLabel} ${kind.evidenceLabel} ${position + 1}",
            coverAssetKey = null,
            ratingLabel = if (kind == CatalogSectionKind.TOP_RATED) "8.$position" else null,
            supportingLabel = if (kind == CatalogSectionKind.LATEST_UPDATES) "Updated today" else null,
        )

        fun storyRef(
            mediaType: CatalogMediaType,
            kind: CatalogSectionKind,
            position: Int,
        ): StorySourceRef {
            val sourceStoryId = "${mediaType.name.lowercase()}-${kind.name.lowercase()}-$position"
            val sourceKey = SourceStoryKey(SOURCE_KEY, sourceStoryId)
            return StorySourceRef(
                storyId = SourceStoryIdV1.derive(sourceKey),
                catalogSourceKey = SOURCE_KEY,
                sourceStoryId = sourceStoryId,
            )
        }

        val CatalogMediaType.evidenceLabel: String
            get() = when (this) {
                CatalogMediaType.MANGA -> "Manga"
                CatalogMediaType.LIGHT_NOVEL -> "Light Novel"
            }

        val CatalogSectionKind.evidenceLabel: String
            get() = when (this) {
                CatalogSectionKind.POPULAR -> "popular"
                CatalogSectionKind.LATEST_UPDATES -> "latest"
                CatalogSectionKind.TOP_RATED -> "top rated"
            }
    }
}
