package app.openstory.reader.routing

import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.preferences.ReaderPreferences
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ReaderChapterGraphInvalidationTest {
    @Test
    fun distinctGraphEmissionsAdvanceGraphRevisionButIdenticalEmissionDoesNot() = runTest {
        val seen = mutableListOf<ReaderChapterGraphRevision>()
        val session = session { _, context ->
            seen += context.chapterGraphRevision
            exhausted(context)
        }
        val first = group(listOf(release("r1")))
        session.updateChapterGraph(listOf(first))
        session.updateRoutingPreferences(ReaderPreferences())
        session.execute(ReaderForegroundIntent(chapterId))

        session.updateChapterGraph(listOf(first.copy()))
        session.execute(ReaderForegroundIntent(chapterId))

        session.updateChapterGraph(listOf(first.copy(chapter = first.chapter.copy(displayLabel = "renamed"))))
        session.execute(ReaderForegroundIntent(chapterId))

        assertEquals(
            listOf(ReaderChapterGraphRevision(1), ReaderChapterGraphRevision(1), ReaderChapterGraphRevision(2)),
            seen,
        )
    }

    @Test
    fun targetDisappearanceHardInvalidatesEvenBeforeAPlanIsRecorded() = runTest {
        val seen = mutableListOf<ReaderPlanRevision>()
        var changed = false
        val session = session { owner, context ->
            seen += context.identity.planRevision
            if (!changed) {
                changed = true
                owner.updateChapterGraph(emptyList())
            }
            exhausted(context)
        }
        ready(session, listOf(release("r1")))

        session.execute(ReaderForegroundIntent(chapterId))

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seen)
    }

    @Test
    fun targetBecomingEmptyOrTombstonedHardInvalidatesBeforePlanCommit() = runTest {
        suspend fun revisionsFor(next: CanonicalChapterGroup): List<ReaderPlanRevision> {
            val seen = mutableListOf<ReaderPlanRevision>()
            var changed = false
            val session = session { owner, context ->
                seen += context.identity.planRevision
                if (!changed) {
                    changed = true
                    owner.updateChapterGraph(listOf(next))
                }
                exhausted(context)
            }
            ready(session, listOf(release("r1")))
            session.execute(ReaderForegroundIntent(chapterId))
            return seen
        }

        assertEquals(
            listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)),
            revisionsFor(group(emptyList())),
        )
        val tombstoned = group(listOf(release("r1"))).let { current ->
            current.copy(chapter = current.chapter.copy(tombstoned = true))
        }
        assertEquals(
            listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)),
            revisionsFor(tombstoned),
        )
    }

    @Test
    fun removingAPlannedReleaseHardInvalidatesActiveUncommittedPlan() = runTest {
        val seen = mutableListOf<ReaderPlanRevision>()
        var changed = false
        val session = session { owner, context ->
            seen += context.identity.planRevision
            if (!changed) {
                changed = true
                owner.recordPlannedRoute(context, winnerReleaseId = releaseId, attempts = listOf(localAttempt()))
                owner.updateChapterGraph(listOf(group(emptyList())))
            }
            exhausted(context)
        }
        ready(session, listOf(release("r1")))

        session.execute(ReaderForegroundIntent(chapterId))

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seen)
    }

    @Test
    fun plannedReleaseReboundOutsideTargetGroupHardInvalidatesActivePlan() = runTest {
        val seen = mutableListOf<ReaderPlanRevision>()
        var changed = false
        val session = session { owner, context ->
            seen += context.identity.planRevision
            if (!changed) {
                changed = true
                owner.recordPlannedRoute(context, winnerReleaseId = releaseId, attempts = listOf(localAttempt()))
                owner.updateChapterGraph(
                    listOf(
                        group(
                            listOf(
                                release("r1").copy(canonicalChapterId = CanonicalChapterId("other-chapter")),
                            ),
                        ),
                    ),
                )
            }
            exhausted(context)
        }
        ready(session, listOf(release("r1")))

        session.execute(ReaderForegroundIntent(chapterId))

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seen)
    }

    @Test
    fun lowerRankedCandidateAdditionIsSoftForActivePlan() = runTest {
        val seen = mutableListOf<ReaderPlanRevision>()
        var changed = false
        val session = session { owner, context ->
            seen += context.identity.planRevision
            if (!changed) {
                changed = true
                owner.recordPlannedRoute(context, winnerReleaseId = releaseId, attempts = listOf(localAttempt()))
                owner.updateChapterGraph(listOf(group(listOf(release("r1"), release("r2")))))
            }
            exhausted(context)
        }
        ready(session, listOf(release("r1")))

        session.execute(ReaderForegroundIntent(chapterId))

        assertEquals(listOf(ReaderPlanRevision(0)), seen)
    }

    @Test
    fun labelOnlyGraphChangeIsSoftForActivePlan() = runTest {
        val seen = mutableListOf<ReaderPlanRevision>()
        var changed = false
        val session = session { owner, context ->
            seen += context.identity.planRevision
            if (!changed) {
                changed = true
                owner.recordPlannedRoute(context, winnerReleaseId = releaseId, attempts = listOf(localAttempt()))
                val current = group(listOf(release("r1")))
                owner.updateChapterGraph(listOf(current.copy(chapter = current.chapter.copy(displayLabel = "new label"))))
            }
            exhausted(context)
        }
        ready(session, listOf(release("r1")))

        session.execute(ReaderForegroundIntent(chapterId))

        assertEquals(listOf(ReaderPlanRevision(0)), seen)
    }

    @Test
    fun languageOrderChangeHardInvalidatesButFontScaleChangeIsSoft() = runTest {
        val languageSeen = mutableListOf<ReaderPlanRevision>()
        var languageChanged = false
        val languageSession = session { owner, context ->
            languageSeen += context.identity.planRevision
            if (!languageChanged) {
                languageChanged = true
                owner.recordPlannedRoute(context, winnerReleaseId = releaseId, attempts = listOf(localAttempt()))
                owner.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("fr")))
            }
            exhausted(context)
        }
        ready(languageSession, listOf(release("r1")))
        languageSession.execute(ReaderForegroundIntent(chapterId))
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), languageSeen)

        val fontSeen = mutableListOf<ReaderPlanRevision>()
        var fontChanged = false
        val fontSession = session { owner, context ->
            fontSeen += context.identity.planRevision
            if (!fontChanged) {
                fontChanged = true
                owner.recordPlannedRoute(context, winnerReleaseId = releaseId, attempts = listOf(localAttempt()))
                owner.updateRoutingPreferences(ReaderPreferences(fontScale = 1.3f))
            }
            exhausted(context)
        }
        ready(fontSession, listOf(release("r1")))
        fontSession.execute(ReaderForegroundIntent(chapterId))
        assertEquals(listOf(ReaderPlanRevision(0)), fontSeen)
    }

    @Test
    fun postCommitGraphRemovalDoesNotRevokeCommittedSessionState() = runTest {
        val committedRelease = release("r1")
        val committedDocument = ReaderDocument(
            title = null,
            blocks = listOf(ReaderBlock.Paragraph("block", "text")),
            fingerprint = "fp-r1",
        )
        val session = session { _, context ->
            ReaderForegroundResult.Committed(
                identity = context.foregroundIdentity,
                chapterGroup = group(listOf(committedRelease)),
                release = committedRelease,
                document = committedDocument,
                fromLocal = true,
                previousChapterId = null,
                nextChapterId = null,
                restoration = null,
            )
        }
        ready(session, listOf(committedRelease))

        val result = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(chapterId)),
        )
        session.updateChapterGraph(emptyList())

        assertEquals("fp-r1", result.document.fingerprint)
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(releaseId, state.committed.releaseId)
        assertEquals(ReaderPlanRevision(0), state.identity.planRevision)
    }

    private fun session(delegate: suspend (ReaderRouteSession, ReaderRouteExecutionContext) -> ReaderForegroundResult) =
        ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(91),
            delegate = ReaderRouteExecutionDelegate(delegate),
        )

    private suspend fun ready(session: ReaderRouteSession, releases: List<ChapterRelease>) {
        session.updateChapterGraph(listOf(group(releases)))
        session.updateRoutingPreferences(ReaderPreferences())
    }

    private fun exhausted(context: ReaderRouteExecutionContext) = ReaderForegroundResult.Exhausted(
        identity = context.foregroundIdentity,
        code = "reader.no_release_available",
        retryable = false,
        attempts = emptyList(),
    )

    private fun localAttempt() = RouteAttempt(
        attemptId = "attempt-0",
        releaseId = releaseId,
        sourceId = sourceId,
        accessMode = AccessMode.LOCAL,
        localFingerprint = "fp",
        role = AttemptRole.PRIMARY,
    )

    private fun group(releases: List<ChapterRelease>) = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = chapterId,
            storyId = StoryId("story"),
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            displayLabel = "chapter",
            tombstoned = false,
            releaseIds = releases.map { it.id }.toSet(),
        ),
        releases = releases,
    )

    private fun release(id: String) = ChapterRelease(
        id = ChapterReleaseId(id),
        storyId = StoryId("story"),
        pluginId = sourceId,
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$id",
        displayLabel = id,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = "en",
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapterId,
    )

    private companion object {
        val chapterId = CanonicalChapterId("chapter")
        val releaseId = ChapterReleaseId("r1")
        val sourceId = PluginId("source")
    }
}
