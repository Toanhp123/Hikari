package app.openstory.work

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMappingCandidate
import app.openstory.library.mapping.ContentMappingSearchFailure
import app.openstory.library.mapping.ContentMappingSearchReport
import app.openstory.library.mapping.ContentMappingSearchStage
import app.openstory.library.matching.ContentMatchDecision
import app.openstory.library.matching.ContentMatchExplanation
import app.openstory.library.matching.ContentMatchResult
import app.openstory.library.matching.ContentTitleEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class LibraryMappingWorkerTest {
    @Test
    fun workerDelegatesStableStoryIdToLibrarySearch() = runTest {
        var received: StoryId? = null

        val decision = runLibraryMappingWork("story:worker") { storyId ->
            received = storyId
            report()
        }

        assertEquals(StoryId("story:worker"), received)
        assertEquals(LibraryMappingWorkDecision.SUCCESS, decision)
    }

    @Test
    fun missingOrInvalidStoryIdFailsWithoutDelegation() = runTest {
        var calls = 0
        val search: suspend (StoryId) -> ContentMappingSearchReport = {
            calls += 1
            report()
        }

        assertEquals(LibraryMappingWorkDecision.FAILURE, runLibraryMappingWork(null, search))
        assertEquals(LibraryMappingWorkDecision.FAILURE, runLibraryMappingWork(" ", search))
        assertEquals(0, calls)
    }

    @Test
    fun uniqueWorkNameIsStableAndStoryScoped() {
        assertEquals("library-mapping:story:one", uniqueWorkName(StoryId("story:one")))
        assertEquals("library-mapping:story:two", uniqueWorkName(StoryId("story:two")))
    }

    @Test
    fun allRetryableSourceFailuresRequestRetry() = runTest {
        val source = PluginId("org.example.content")
        val decision = runLibraryMappingWork("story:retry") {
            report(
                searched = listOf(source),
                failures = listOf(ContentMappingSearchFailure(source, "plugin.timeout", true)),
            )
        }

        assertEquals(LibraryMappingWorkDecision.RETRY, decision)
    }

    @Test
    fun partialCandidateKeepsPeerFailureFromFailingWorker() = runTest {
        val healthy = PluginId("org.example.healthy")
        val failing = PluginId("org.example.failing")
        val decision = runLibraryMappingWork("story:partial") {
            report(
                searched = listOf(failing, healthy),
                failures = listOf(ContentMappingSearchFailure(failing, "plugin.timeout", true)),
                candidates = listOf(candidate(healthy)),
            )
        }

        assertEquals(LibraryMappingWorkDecision.SUCCESS, decision)
    }
}

private fun report(
    searched: List<PluginId> = emptyList(),
    failures: List<ContentMappingSearchFailure> = emptyList(),
    candidates: List<ContentMappingCandidate> = emptyList(),
) = ContentMappingSearchReport(
    stage = ContentMappingSearchStage.ALL,
    searchedPluginIds = searched,
    queryVariants = emptyList(),
    candidates = candidates,
    failures = failures,
)

private fun candidate(pluginId: PluginId) = ContentMappingCandidate(
    pluginId = pluginId,
    pluginVersion = "1.0.0",
    sourceStoryId = "source-1",
    sourceUrl = null,
    title = "Story",
    match = ContentMatchResult(
        score = 1.0,
        decision = ContentMatchDecision.AUTO_LINK,
        explanation = ContentMatchExplanation(
            directEvidence = false,
            title = ContentTitleEvidence(1.0, "Story", "Story"),
            authorSimilarity = null,
            authorConflict = false,
            contentTypeMatch = null,
            contentTypeConflict = false,
            reasons = listOf("decision:auto_link"),
        ),
        policyVersion = 1,
    ),
)
