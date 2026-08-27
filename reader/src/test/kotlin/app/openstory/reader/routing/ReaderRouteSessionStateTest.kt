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
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.preferences.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderRouteSessionStateTest {
    @Test
    fun graphOwnsOneDefensiveCopyAndIndexesChapterAndRelease() {
        val releaseA = release("release-a", "chapter-a")
        val releaseB = release("release-b", "chapter-b")
        val releasesA = mutableListOf(releaseA)
        val releaseIdsA = linkedSetOf(releaseA.id)
        val groupA = group("chapter-a", releasesA, releaseIdsA)
        val groupB = group("chapter-b", listOf(releaseB))
        val expectedGroupA = group("chapter-a", listOf(releaseA))
        val sourceGroups = mutableListOf(groupA, groupB)

        val graph = ReaderSessionChapterGraph.create(StoryId("story"), sourceGroups)
        sourceGroups.clear()
        releasesA.clear()
        releaseIdsA.clear()

        assertEquals(0, graph.indexOf(groupA.chapter.id))
        assertEquals(1, graph.indexOf(groupB.chapter.id))
        assertEquals(expectedGroupA, graph.group(groupA.chapter.id))
        assertEquals(expectedGroupA, graph.previousBefore(groupB.chapter.id))
        assertEquals(groupB, graph.nextAfter(groupA.chapter.id))
        assertEquals(releaseA, graph.release(releaseA.id))
        assertEquals(setOf(releaseA.id, releaseB.id), graph.releaseIds)
        assertEquals(listOf(expectedGroupA, groupB), graph.groups)
    }

    @Test
    fun graphIndexesDuplicateIdsUsingFirstOccurrenceSemantics() {
        val firstRelease = release("shared-release", "shared-chapter", languageTag = "en")
        val secondRelease = release("shared-release", "shared-chapter", languageTag = "fr")
        val firstGroup = group("shared-chapter", listOf(firstRelease), displayLabel = "first")
        val secondGroup = group("shared-chapter", listOf(secondRelease), displayLabel = "second")

        val graph = ReaderSessionChapterGraph.create(
            StoryId("story"),
            listOf(firstGroup, secondGroup),
        )

        assertEquals(0, graph.indexOf(firstGroup.chapter.id))
        assertEquals(firstGroup, graph.group(firstGroup.chapter.id))
        assertEquals(firstRelease, graph.release(firstRelease.id))
        assertNull(graph.previousBefore(firstGroup.chapter.id))
        assertEquals(secondGroup, graph.nextAfter(firstGroup.chapter.id))
    }

    @Test
    fun graphRejectsChapterOwnedByAnotherStory() {
        val group = group("chapter-a", storyId = StoryId("other-story"))

        assertFailsWith<IllegalArgumentException> {
            ReaderSessionChapterGraph.create(StoryId("story"), listOf(group))
        }
    }

    @Test
    fun graphRejectsReleaseOwnedByAnotherStory() {
        val release = release("release-a", "chapter-a", storyId = StoryId("other-story"))
        val group = group("chapter-a", listOf(release))

        assertFailsWith<IllegalArgumentException> {
            ReaderSessionChapterGraph.create(StoryId("story"), listOf(group))
        }
    }

    @Test
    fun initialExecutionWaitsForFirstGraphAndPreferencesWithoutReplanning() = runTest {
        val session = session(ReaderSessionId(10))

        val execution = async {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }
        runCurrent()
        assertFalse(execution.isCompleted)

        session.updateChapterGraph(emptyList())
        runCurrent()
        assertFalse(execution.isCompleted)

        session.updateRoutingPreferences(ReaderPreferences())
        assertIs<ReaderForegroundResult.Exhausted>(execution.await())
        val state = assertIs<ReaderExecutionState.Exhausted>(session.executionState)
        assertEquals(ReaderPlanRevision(0), state.identity.planRevision)
    }

    @Test
    fun everyForegroundUserIntentGetsNewGeneration() = runTest {
        val session = session(ReaderSessionId(11))
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        val first = assertIs<ReaderExecutionState.Exhausted>(session.executionState)
        session.execute(ReaderForegroundIntent(chapter("chapter-b")))
        val second = assertIs<ReaderExecutionState.Exhausted>(session.executionState)

        assertEquals(ReaderGenerationId(1), first.identity.generationId)
        assertEquals(ReaderGenerationId(2), second.identity.generationId)
    }

    @Test
    fun hardInvalidationInActiveUncommittedIntentOnlyIncrementsPlanRevision() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        var invalidated = false
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(12),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                seen += context.identity
                if (!invalidated) {
                    invalidated = true
                    owner.hardInvalidate()
                }
                exhausted(context)
            },
        )
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(2, seen.size)
        assertEquals(ReaderGenerationId(1), seen[0].generationId)
        assertEquals(ReaderGenerationId(1), seen[1].generationId)
        assertEquals(ReaderPlanRevision(0), seen[0].planRevision)
        assertEquals(ReaderPlanRevision(1), seen[1].planRevision)
    }

    @Test
    fun hardReplanReusesSameChapterGraphObjectAndEqualEmissionKeepsRevision() = runTest {
        val groups = listOf(
            group("chapter-a", listOf(release("release-a", "chapter-a"))),
            group("chapter-b", listOf(release("release-b", "chapter-b"))),
        )
        val contexts = mutableListOf<ReaderRouteExecutionContext>()
        var invalidated = false
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(30),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                contexts += context
                if (!invalidated) {
                    invalidated = true
                    owner.updateChapterGraph(groups.map { it.copy(releases = it.releases.toList()) })
                    owner.hardInvalidate()
                }
                exhausted(context)
            },
        )
        session.updateChapterGraph(groups)
        session.updateRoutingPreferences(ReaderPreferences())

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(2, contexts.size)
        assertSame(contexts[0].chapterGraph, contexts[1].chapterGraph)
        assertEquals(contexts[0].chapterGraphRevision, contexts[1].chapterGraphRevision)
        assertEquals(ReaderPlanRevision(0), contexts[0].identity.planRevision)
        assertEquals(ReaderPlanRevision(1), contexts[1].identity.planRevision)
    }

    @Test
    fun foregroundAndPrefetchReuseSameChapterGraphForOneRevision() = runTest {
        val foregroundContexts = mutableListOf<ReaderRouteExecutionContext>()
        val prefetchContexts = mutableListOf<ReaderRoutePlanningContext>()
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(31),
            delegate = ReaderRouteExecutionDelegate { _, context ->
                foregroundContexts += context
                committed(context)
            },
            prefetchDelegate = ReaderPrefetchExecutionDelegate { _, context ->
                prefetchContexts += context
            },
            prefetchScope = this,
        )
        session.updateChapterGraph(
            listOf(
                group("chapter-a", listOf(release("release-a", "chapter-a"))),
                group("chapter-b", listOf(release("release-b", "chapter-b"))),
            ),
        )
        session.updateRoutingPreferences(ReaderPreferences())

        assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(chapter("chapter-a"))),
        )
        runCurrent()

        assertEquals(1, foregroundContexts.size)
        assertEquals(1, prefetchContexts.size)
        assertSame(foregroundContexts.single().chapterGraph, prefetchContexts.single().chapterGraph)
        assertEquals(
            foregroundContexts.single().chapterGraphRevision,
            prefetchContexts.single().chapterGraphRevision,
        )
    }

    @Test
    fun acceptedGraphChangePrunesKnownInvalidFingerprintsForRemovedReleases() = runTest {
        val releaseA = release("release-a", "chapter-a")
        val releaseB = release("release-b", "chapter-b")
        val contexts = mutableListOf<ReaderRouteExecutionContext>()
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(32),
            delegate = ReaderRouteExecutionDelegate { _, context ->
                contexts += context
                exhausted(context)
            },
        )
        session.updateChapterGraph(
            listOf(group("chapter-a", listOf(releaseA)), group("chapter-b", listOf(releaseB))),
        )
        session.updateRoutingPreferences(ReaderPreferences())
        session.markKnownInvalidLocal(releaseA.id, "bad-fingerprint")

        session.updateChapterGraph(listOf(group("chapter-b", listOf(releaseB))))
        session.execute(ReaderForegroundIntent(chapter("chapter-b")))

        val context = contexts.single()
        assertFalse(releaseA.id in context.knownInvalidLocalFingerprints)
        assertTrue(context.knownInvalidLocalFingerprints.isEmpty())
    }

    @Test
    fun staleInvalidationCannotReintroduceReleaseRemovedFromCurrentGraph() = runTest {
        val releaseA = release("release-a", "chapter-a")
        val releaseB = release("release-b", "chapter-b")
        val contexts = mutableListOf<ReaderRouteExecutionContext>()
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(33),
            delegate = ReaderRouteExecutionDelegate { _, context ->
                contexts += context
                exhausted(context)
            },
        )
        session.updateChapterGraph(
            listOf(group("chapter-a", listOf(releaseA)), group("chapter-b", listOf(releaseB))),
        )
        session.updateRoutingPreferences(ReaderPreferences())
        session.updateChapterGraph(listOf(group("chapter-b", listOf(releaseB))))

        session.markKnownInvalidLocal(releaseA.id, "stale-fingerprint")
        session.execute(ReaderForegroundIntent(chapter("chapter-b")))

        assertFalse(releaseA.id in contexts.single().knownInvalidLocalFingerprints)
    }

    @Test
    fun retryAfterExhaustionStartsANewGeneration() = runTest {
        val session = session(ReaderSessionId(13))
        ready(session)
        val intent = ReaderForegroundIntent(
            targetChapterId = chapter("chapter-a"),
            explicitReleaseId = ChapterReleaseId("release-explicit"),
        )

        val first = assertIs<ReaderForegroundResult.Exhausted>(session.execute(intent))
        val second = assertIs<ReaderForegroundResult.Exhausted>(session.execute(intent))

        assertEquals(ReaderGenerationId(1), first.identity.generationId)
        assertEquals(ReaderGenerationId(2), second.identity.generationId)
    }

    @Test
    fun twoSessionsMayBothUseGenerationOneWithoutIdentityCollision() = runTest {
        val firstSession = session(ReaderSessionId(21))
        val secondSession = session(ReaderSessionId(22))
        ready(firstSession)
        ready(secondSession)

        val first = assertIs<ReaderForegroundResult.Exhausted>(
            firstSession.execute(ReaderForegroundIntent(chapter("chapter-a"))),
        )
        val second = assertIs<ReaderForegroundResult.Exhausted>(
            secondSession.execute(ReaderForegroundIntent(chapter("chapter-a"))),
        )

        assertEquals(ReaderGenerationId(1), first.identity.generationId)
        assertEquals(ReaderGenerationId(1), second.identity.generationId)
        assertNotEquals(first.identity.sessionId, second.identity.sessionId)
    }

    @Test
    fun routingLanguagePreferenceChangeHardInvalidatesActiveGenerationInM4() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        var changed = false
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(23),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                seen += context.identity
                if (!changed) {
                    changed = true
                    owner.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("fr")))
                }
                exhausted(context)
            },
        )
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(2, seen.size)
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seen.map { it.planRevision })
    }

    @Test
    fun cancellationClosesCurrentGenerationEvenAfterPlanRevisionAdvances() = runTest {
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(24),
            delegate = ReaderRouteExecutionDelegate { owner, _ ->
                owner.hardInvalidate()
                throw CancellationException("cancel active generation")
            },
        )
        ready(session)

        assertFailsWith<CancellationException> {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }

        val state = assertIs<ReaderExecutionState.Cancelled>(session.executionState)
        assertEquals(ReaderGenerationId(1), state.identity.generationId)
        assertEquals(ReaderPlanRevision(1), state.identity.planRevision)
    }

    @Test
    fun executionIdentityHasOnePlanRevisionAndNoOpaqueExecutionRevisionOrPlanHash() {
        val fields = ReaderExecutionIdentity::class.java.declaredFields.map { it.name }.toSet()

        assertFalse(fields.any { it.contains("executionRevision", ignoreCase = true) })
        assertFalse(fields.any { it.contains("planHash", ignoreCase = true) })
    }

    @Test
    fun attemptIdentityContainsExactlyTheForegroundHesTuple() {
        val fields = ReaderAttemptIdentity::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .mapTo(linkedSetOf()) { it.name }

        assertEquals(
            setOf("sessionId", "generationId", "planRevision", "attemptId", "targetChapterId"),
            fields,
        )
    }

    @Test
    fun finalCompletionGateRejectsCommittedChapterGroupOutsideTargetChapter() = runTest {
        val targetRelease = release("release-a", "chapter-a")
        val foreignRelease = release("release-b", "chapter-b")
        val foreignGroup = group("chapter-b", listOf(foreignRelease))
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(37),
            delegate = ReaderRouteExecutionDelegate { _, context ->
                committed(context).copy(chapterGroup = foreignGroup)
            },
        )
        session.updateChapterGraph(
            listOf(
                group("chapter-a", listOf(targetRelease)),
                foreignGroup,
            ),
        )
        session.updateRoutingPreferences(ReaderPreferences())

        assertFailsWith<IllegalStateException> {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }
    }

    @Test
    fun finalCompletionGateRejectsCommittedReleaseOutsideTargetChapter() = runTest {
        val targetRelease = release("release-a", "chapter-a")
        val foreignRelease = release("release-b", "chapter-b")
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(38),
            delegate = ReaderRouteExecutionDelegate { _, context ->
                committed(context).copy(release = foreignRelease)
            },
        )
        session.updateChapterGraph(
            listOf(
                group("chapter-a", listOf(targetRelease)),
                group("chapter-b", listOf(foreignRelease)),
            ),
        )
        session.updateRoutingPreferences(ReaderPreferences())

        assertFailsWith<IllegalStateException> {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }
    }

    @Test
    fun finalCompletionGateRejectsResultIdentityThatDoesNotMatchExecutionContext() = runTest {
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(39),
            delegate = ReaderRouteExecutionDelegate { _, context ->
                ReaderForegroundResult.Exhausted(
                    identity = context.foregroundIdentity.copy(
                        targetChapterId = chapter("chapter-other"),
                    ),
                    code = "reader.no_release_available",
                    retryable = false,
                    attempts = emptyList(),
                )
            },
        )
        ready(session)

        assertFailsWith<IllegalStateException> {
            session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        }
    }

    @Test
    fun validatingGateRejectsIdentityFromAnotherSession() = runTest {
        var acceptedForeignIdentity: Boolean? = null
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(40),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                val foreignIdentity = context.identity
                    .copy(sessionId = ReaderSessionId(41))
                    .forAttempt("attempt-0")
                acceptedForeignIdentity = owner.markValidating(foreignIdentity)
                exhausted(context)
            },
        )
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(false, acceptedForeignIdentity)
    }

    @Test
    fun validatingGateRejectsOldGenerationAndWrongTarget() = runTest {
        val accepted = mutableListOf<Boolean>()
        val session = ReaderRouteSession(
            storyId = StoryId("story"),
            sessionId = ReaderSessionId(42),
            delegate = ReaderRouteExecutionDelegate { owner, context ->
                if (context.identity.generationId == ReaderGenerationId(2)) {
                    accepted += owner.markValidating(
                        context.identity
                            .copy(generationId = ReaderGenerationId(1))
                            .forAttempt("attempt-same"),
                    )
                    accepted += owner.markValidating(
                        context.identity
                            .copy(targetChapterId = chapter("chapter-other"))
                            .forAttempt("attempt-same"),
                    )
                }
                exhausted(context)
            },
        )
        ready(session)

        session.execute(ReaderForegroundIntent(chapter("chapter-a")))
        session.execute(ReaderForegroundIntent(chapter("chapter-a")))

        assertEquals(listOf(false, false), accepted)
    }

    private fun session(id: ReaderSessionId) = ReaderRouteSession(
        storyId = StoryId("story"),
        sessionId = id,
        delegate = ReaderRouteExecutionDelegate { _, context -> exhausted(context) },
    )

    private suspend fun ready(session: ReaderRouteSession) {
        session.updateChapterGraph(emptyList())
        session.updateRoutingPreferences(ReaderPreferences())
    }

    private fun committed(context: ReaderRouteExecutionContext): ReaderForegroundResult.Committed {
        val group = requireNotNull(context.chapterGraph.group(context.identity.targetChapterId))
        val release = group.releases.first()
        return ReaderForegroundResult.Committed(
            identity = context.foregroundIdentity,
            chapterGroup = group,
            release = release,
            document = app.openstory.reader.document.ReaderDocument(
                title = release.displayLabel,
                blocks = listOf(app.openstory.reader.document.ReaderBlock.Paragraph("block", release.displayLabel)),
                fingerprint = "fp-${release.id.value}",
            ),
            fromLocal = false,
            previousChapterId = context.chapterGraph.previousBefore(group.chapter.id)?.chapter?.id,
            nextChapterId = context.chapterGraph.nextAfter(group.chapter.id)?.chapter?.id,
            restoration = null,
        )
    }

    private fun exhausted(context: ReaderRouteExecutionContext) = ReaderForegroundResult.Exhausted(
        identity = context.foregroundIdentity,
        code = "reader.no_release_available",
        retryable = false,
        attempts = emptyList(),
    )

    private fun group(
        value: String,
        releases: List<ChapterRelease> = emptyList(),
        releaseIds: Set<ChapterReleaseId> = releases.mapTo(linkedSetOf(), ChapterRelease::id),
        storyId: StoryId = StoryId("story"),
        displayLabel: String = value,
    ) = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = chapter(value),
            storyId = storyId,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            displayLabel = displayLabel,
            tombstoned = false,
            releaseIds = releaseIds,
        ),
        releases = releases,
    )

    private fun release(
        value: String,
        chapter: String,
        storyId: StoryId = StoryId("story"),
        languageTag: String = "en",
    ) = ChapterRelease(
        id = ChapterReleaseId(value),
        storyId = storyId,
        pluginId = PluginId("plugin-$value"),
        sourceStoryId = "source-story",
        sourceReleaseId = "source-$value",
        displayLabel = value,
        parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
        languageTag = languageTag,
        publishedAtEpochMillis = 1L,
        canonicalChapterId = chapter(chapter),
    )

    private fun chapter(value: String) = CanonicalChapterId(value)
}
