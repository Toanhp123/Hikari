package app.openstory.reader.selection

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class ReleaseSelectorTest {
    private val selector = ReleaseSelector()

    @Test
    fun explicitReleaseWinsAllOtherPreferences() {
        val selected = selected(
            listOf(candidate("a", "en", 20), candidate("b", "vi", 10)),
            ReleaseSelectionPolicy(
                explicitReleaseId = ChapterReleaseId("b"),
                languageOrder = listOf("en", "vi"),
            ),
        )
        assertEquals("b", selected.candidate.release.id.value)
        assertEquals(SelectionReason.EXPLICIT_RELEASE, selected.reason)
    }

    @Test
    fun previousSourceThenLanguageHealthCompletenessAndRecencyAreDeterministic() {
        val candidates = listOf(
            candidate("z", "en", 100, plugin = "other", health = ReleaseHealth.HEALTHY),
            candidate("b", "vi", 20, plugin = "preferred", completeness = 80),
            candidate("a", "vi", 10, plugin = "preferred", completeness = 80),
        )
        val policy = ReleaseSelectionPolicy(
            previousPluginId = PluginId("preferred"),
            languageOrder = listOf("en", "vi"),
        )
        val forwards = selected(candidates, policy)
        val backwards = selected(candidates.reversed(), policy)
        assertEquals("b", forwards.candidate.release.id.value)
        assertEquals(forwards, backwards)
    }

    @Test
    fun everyPolicyTierOutranksLowerTiers() {
        val previousRelease = candidate("previous-release", "fr", 1, health = ReleaseHealth.UNAVAILABLE)
        assertSelected(
            previousRelease,
            listOf(previousRelease, candidate("newer", "en", 100)),
            ReleaseSelectionPolicy(previousReleaseId = previousRelease.release.id, languageOrder = listOf("en")),
            SelectionReason.PREVIOUS_RELEASE,
        )

        val previousGroup = candidate("group", "fr", 1, group = "team")
        assertSelected(
            previousGroup,
            listOf(previousGroup, candidate("language", "en", 100)),
            ReleaseSelectionPolicy(previousSourceGroup = "team", languageOrder = listOf("en")),
            SelectionReason.PREVIOUS_SOURCE_GROUP,
        )

        val language = candidate("language", "vi", 1, health = ReleaseHealth.DEGRADED)
        assertSelected(
            language,
            listOf(language, candidate("healthy", "en", 100)),
            ReleaseSelectionPolicy(languageOrder = listOf("vi", "en")),
            SelectionReason.LANGUAGE_ORDER,
        )

        val healthy = candidate("healthy", "en", 1, health = ReleaseHealth.HEALTHY, completeness = 10)
        assertSelected(
            healthy,
            listOf(healthy, candidate("complete", "en", 100, health = ReleaseHealth.DEGRADED)),
            ReleaseSelectionPolicy(),
            SelectionReason.HEALTH_AND_COMPLETENESS,
        )

        val complete = candidate("complete", "en", 1, completeness = 100)
        assertSelected(
            complete,
            listOf(complete, candidate("newer", "en", 100, completeness = 90)),
            ReleaseSelectionPolicy(),
            SelectionReason.HEALTH_AND_COMPLETENESS,
        )

        val recent = candidate("recent", "en", 100)
        assertSelected(
            recent,
            listOf(recent, candidate("older", "en", 1)),
            ReleaseSelectionPolicy(),
            SelectionReason.RECENCY,
        )
    }

    @Test
    fun stableIdTierOrdersSourceIdBeforeReleaseIdForMigrationEnvelope() {
        val sourceAReleaseZ = candidate("release-z", "en", 1, plugin = "source-a")
        val sourceZReleaseA = candidate("release-a", "en", 1, plugin = "source-z")

        val result = selected(listOf(sourceZReleaseA, sourceAReleaseZ))

        assertEquals(sourceAReleaseZ, result.candidate)
        assertEquals(listOf(sourceZReleaseA), result.alternates)
        assertEquals(SelectionReason.STABLE_ID, result.reason)
    }

    @Test
    fun emptyInputAndStableIdTieBreakAreExplained() {
        assertSame(ReleaseSelectionResult.NoneAvailable, selector.select(emptyList()))
        val selected = selected(listOf(candidate("z", "en", 1), candidate("a", "en", 1)))
        assertEquals("a", selected.candidate.release.id.value)
        assertEquals(SelectionReason.STABLE_ID, selected.reason)
    }

    @Test
    fun explanationNamesTheTierThatBeatTheRunnerUp() {
        val selected = selected(
            listOf(
                candidate("selected", "en", 1, health = ReleaseHealth.HEALTHY),
                candidate("runner-up", "en", 1, health = ReleaseHealth.DEGRADED),
                candidate("different-language", "vi", 1, health = ReleaseHealth.HEALTHY),
            ),
            ReleaseSelectionPolicy(languageOrder = listOf("en", "vi")),
        )

        assertEquals("selected", selected.candidate.release.id.value)
        assertEquals(SelectionReason.HEALTH_AND_COMPLETENESS, selected.reason)
    }

    private fun selected(
        candidates: List<ReleaseCandidate>,
        policy: ReleaseSelectionPolicy = ReleaseSelectionPolicy(),
    ) = assertIs<ReleaseSelectionResult.Selected>(selector.select(candidates, policy))

    private fun assertSelected(
        expected: ReleaseCandidate,
        candidates: List<ReleaseCandidate>,
        policy: ReleaseSelectionPolicy,
        reason: SelectionReason,
    ) {
        val result = selected(candidates, policy)
        assertEquals(expected, result.candidate)
        assertEquals(reason, result.reason)
    }

    private fun candidate(
        id: String,
        language: String,
        updatedAt: Long,
        plugin: String = "plugin",
        health: ReleaseHealth = ReleaseHealth.HEALTHY,
        completeness: Int = 100,
        group: String? = null,
    ) = ReleaseCandidate(
        release = ChapterRelease(
            id = ChapterReleaseId(id),
            storyId = StoryId("story"),
            pluginId = PluginId(plugin),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-$id",
            displayLabel = id,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = language,
            publishedAtEpochMillis = updatedAt,
            canonicalChapterId = null,
        ),
        health = health,
        completeness = completeness,
        sourceGroup = group,
    )
}
