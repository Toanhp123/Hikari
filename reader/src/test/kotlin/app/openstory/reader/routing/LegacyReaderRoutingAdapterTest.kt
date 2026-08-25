package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.engine.BasisPoints
import app.openstory.reader.engine.CandidateLocalAccess
import app.openstory.reader.engine.CandidateRemoteAccess
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.SourceGroupKey
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LegacyReaderRoutingAdapterTest {
    @Test
    fun productionCandidateDoesNotInventSourceGroupOrCompleteness() {
        val release = release(id = "release-a", plugin = "source-a", language = "vi", publishedAt = 42L)

        val mapped = LegacyReaderRoutingAdapter.productionCandidate(
            release = release,
            remoteAccess = CandidateRemoteAccess.PERMITTED,
        )

        assertEquals(release.id, mapped.releaseId)
        assertEquals(release.pluginId, mapped.sourceId)
        assertEquals(release.languageTag, mapped.languageTag)
        assertEquals(release.publishedAtEpochMillis, mapped.publishedAtEpochMillis)
        assertNull(mapped.sourceGroupKey)
        assertEquals(BasisPoints(10_000), mapped.completeness)
        assertEquals(CandidateRemoteAccess.PERMITTED, mapped.remoteAccess)
        assertEquals(CandidateLocalAccess.Unknown, mapped.localAccess)
    }

    @Test
    fun differentialCandidateMapsOnlyExplicitFixtureCompletenessAndTrustedGroup() {
        val legacy = ReleaseCandidate(
            release = release(id = "release-b", plugin = "source-b", language = "en", publishedAt = 7L),
            sourceGroup = "trusted-team",
            completeness = 73,
        )

        val mapped = LegacyReaderRoutingAdapter.differentialCandidate(legacy)

        assertEquals(SourceGroupKey("trusted-team"), mapped.sourceGroupKey)
        assertEquals(BasisPoints(7_300), mapped.completeness)
        assertEquals(CandidateRemoteAccess.PERMITTED, mapped.remoteAccess)
        assertEquals(CandidateLocalAccess.Miss, mapped.localAccess)
    }

    @Test
    fun compatibilitySnapshotMapsLegacyPolicyIntoExplicitAndContinuityFacts() {
        val resume = release(id = "resume", plugin = "resume-source", language = "vi", publishedAt = 10L)
        val candidate = ReleaseCandidate(
            release = resume,
            sourceGroup = "trusted-team",
            completeness = 91,
        )
        val selectionPolicy = ReleaseSelectionPolicy(
            explicitReleaseId = ChapterReleaseId("explicit"),
            previousReleaseId = resume.id,
            previousPluginId = PluginId("previous-source"),
            previousSourceGroup = "trusted-team",
            languageOrder = listOf("VI", "en_US"),
        )

        val snapshot = LegacyReaderRoutingAdapter.compatibilitySnapshot(
            targetChapterId = CanonicalChapterId("chapter-a"),
            candidates = listOf(candidate),
            selectionPolicy = selectionPolicy,
            chapterGraphRevision = ReaderChapterGraphRevision(4),
            planRevision = ReaderPlanRevision(8),
        )
        val routingPolicy = LegacyReaderRoutingAdapter.compatibilityPolicy(selectionPolicy)

        assertEquals(CanonicalChapterId("chapter-a"), snapshot.targetChapterId)
        assertEquals(ReaderChapterGraphRevision(4), snapshot.chapterGraphRevision)
        assertEquals(ReaderPlanRevision(8), snapshot.planRevision)
        assertEquals(selectionPolicy.explicitReleaseId, snapshot.explicitReleaseId)
        assertEquals(selectionPolicy.previousReleaseId, snapshot.continuity.targetResumeReleaseId)
        assertEquals(selectionPolicy.previousPluginId, snapshot.continuity.committedSourceId)
        assertEquals(SourceGroupKey("trusted-team"), snapshot.continuity.committedSourceGroupKey)
        assertEquals(listOf("vi", "en-us"), routingPolicy.languageOrder)
    }

    private fun release(
        id: String,
        plugin: String,
        language: String,
        publishedAt: Long?,
    ) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = StoryId("story"),
        pluginId = PluginId(plugin),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$id",
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = language,
        publishedAtEpochMillis = publishedAt,
        canonicalChapterId = CanonicalChapterId("chapter-a"),
    )
}
