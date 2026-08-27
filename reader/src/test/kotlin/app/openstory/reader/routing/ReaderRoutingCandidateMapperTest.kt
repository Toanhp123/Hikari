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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderRoutingCandidateMapperTest {
    @Test
    fun productionCandidateMapsOnlyOwnedFactsAndDoesNotInventGroupOrCompleteness() {
        val release = ChapterRelease(
            id = ChapterReleaseId("release-a"),
            storyId = StoryId("story"),
            pluginId = PluginId("source-a"),
            sourceStoryId = "source-story",
            sourceReleaseId = "remote-a",
            displayLabel = "A",
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "vi",
            publishedAtEpochMillis = 42L,
            canonicalChapterId = CanonicalChapterId("chapter-a"),
        )

        val mapped = ReaderRoutingCandidateMapper.productionCandidate(
            release = release,
            remoteAccess = CandidateRemoteAccess.PERMITTED,
            localAccess = CandidateLocalAccess.AvailableExact("fp-a"),
        )

        assertEquals(release.id, mapped.releaseId)
        assertEquals(release.pluginId, mapped.sourceId)
        assertEquals(release.languageTag, mapped.languageTag)
        assertEquals(release.publishedAtEpochMillis, mapped.publishedAtEpochMillis)
        assertNull(mapped.sourceGroupKey)
        assertEquals(BasisPoints(10_000), mapped.completeness)
        assertEquals(CandidateRemoteAccess.PERMITTED, mapped.remoteAccess)
        assertEquals(CandidateLocalAccess.AvailableExact("fp-a"), mapped.localAccess)
    }
}
