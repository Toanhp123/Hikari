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
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.SourceHealthOrigin
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.preferences.ReaderPreferences
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ReaderCoordinatorModelTest {
    @Test
    fun equalTimestampWinnerIsPrimaryAcrossRecordAndDeliveryPermutations() {
        repeat(512) { seed ->
            val primary = completion("attempt-primary", AttemptRole.PRIMARY, 700L)
            val hedge = completion("attempt-hedge", AttemptRole.HEDGE, 700L)
            val naturalRecordOrders = listOf(listOf(primary, hedge), listOf(hedge, primary))
            val naturalDeliveryOrders = listOf(
                listOf(primary.attempt.attemptId, hedge.attempt.attemptId),
                listOf(hedge.attempt.attemptId, primary.attempt.attemptId),
            )
            val recordOrders = if (Random(seed).nextBoolean()) naturalRecordOrders else naturalRecordOrders.reversed()
            val deliveryOrders = if (Random(seed xor 0x5A5A).nextBoolean()) {
                naturalDeliveryOrders
            } else {
                naturalDeliveryOrders.reversed()
            }

            recordOrders.forEach { recordOrder ->
                deliveryOrders.forEach { deliveryOrder ->
                    val registry = CompetitiveCompletionRegistry()
                    recordOrder.forEach(registry::record)
                    val message = "seed=$seed recordOrder=${recordOrder.map { it.attempt.attemptId }} " +
                        "deliveryOrder=$deliveryOrder"

                    assertEquals(2, deliveryOrder.distinct().size, message)
                    deliveryOrder.forEach { deliveredAttemptId ->
                        assertTrue(registry.contains(deliveredAttemptId), "$message delivered=$deliveredAttemptId")
                        assertEquals(primary, registry.winner(), "$message delivered=$deliveredAttemptId")
                    }
                }
            }
        }
    }

    @Test
    fun `new user intent supersedes an older completion without changing committed state`() = runTest {
        val gates = mutableMapOf<Long, CompletableDeferred<Unit>>()
        val contexts = mutableListOf<ReaderRouteExecutionContext>()
        val session = session(ReaderSessionId(901)) { _, context ->
            contexts += context
            gates.getOrPut(context.identity.generationId.value, ::CompletableDeferred).await()
            committed(context)
        }
        ready(session, listOf(group(RELEASE)))

        val first = async { session.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        val second = async { session.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()

        gates.getValue(1L).complete(Unit)
        runCurrent()
        assertIs<ReaderForegroundResult.Superseded>(first.await())

        gates.getValue(2L).complete(Unit)
        val committed = assertIs<ReaderForegroundResult.Committed>(second.await())
        assertEquals(ReaderGenerationId(2), committed.identity.generationId)
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(ReaderGenerationId(2), state.identity.generationId)
        assertEquals(
            listOf(ReaderGenerationId(1), ReaderGenerationId(2)),
            contexts.map { it.identity.generationId },
        )
    }

    @Test
    fun `hard invalidation rejects stale plan completion and commits only the revised plan`() = runTest {
        val firstPlanGate = CompletableDeferred<Unit>()
        val seenRevisions = mutableListOf<ReaderPlanRevision>()
        val session = session(ReaderSessionId(902)) { _, context ->
            seenRevisions += context.identity.planRevision
            if (context.identity.planRevision == ReaderPlanRevision(0)) firstPlanGate.await()
            committed(context)
        }
        ready(session, listOf(group(RELEASE)))

        val execution = async { session.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        session.hardInvalidate()
        firstPlanGate.complete(Unit)

        val result = assertIs<ReaderForegroundResult.Committed>(execution.await())
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seenRevisions)
        val state = assertIs<ReaderExecutionState.Committed>(session.executionState)
        assertEquals(ReaderPlanRevision(1), state.identity.planRevision)
        assertEquals(ReaderGenerationId(1), result.identity.generationId)
    }

    @Test
    fun staleGenerationAndPlanRevisionCannotCommit() = runTest {
        val generationGates = mutableMapOf<Long, CompletableDeferred<Unit>>()
        val generationSession = session(ReaderSessionId(91)) { _, context ->
            generationGates.getOrPut(context.identity.generationId.value, ::CompletableDeferred).await()
            committed(context)
        }
        ready(generationSession, listOf(group(RELEASE)))

        val first = async { generationSession.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        val second = async { generationSession.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        generationGates.getValue(1L).complete(Unit)
        runCurrent()
        assertIs<ReaderForegroundResult.Superseded>(first.await())
        generationGates.getValue(2L).complete(Unit)
        assertEquals(
            ReaderGenerationId(2),
            assertIs<ReaderForegroundResult.Committed>(second.await()).identity.generationId,
        )

        val revisionGate = CompletableDeferred<Unit>()
        val revisions = mutableListOf<ReaderPlanRevision>()
        val revisionSession = session(ReaderSessionId(92)) { _, context ->
            revisions += context.identity.planRevision
            if (context.identity.planRevision == ReaderPlanRevision(0)) revisionGate.await()
            committed(context)
        }
        ready(revisionSession, listOf(group(RELEASE)))
        val revised = async { revisionSession.execute(ReaderForegroundIntent(CHAPTER_ID)) }
        runCurrent()
        revisionSession.hardInvalidate()
        revisionGate.complete(Unit)

        val revisedResult = assertIs<ReaderForegroundResult.Committed>(revised.await())
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), revisions)
        assertEquals(ReaderGenerationId(1), revisedResult.identity.generationId)
        assertEquals(
            ReaderPlanRevision(1),
            assertIs<ReaderExecutionState.Committed>(revisionSession.executionState).identity.planRevision,
        )
    }

    @Test
    fun staleResultIdentityCannotEnterValidatingOrMutateCommittedState() = runTest {
        var staleAccepted = true
        val revisions = mutableListOf<ReaderPlanRevision>()
        val session = session(ReaderSessionId(921)) { owner, context ->
            revisions += context.identity.planRevision
            if (context.identity.planRevision == ReaderPlanRevision(0)) {
                val staleResultIdentity = context.identity.forAttempt("attempt-same")
                owner.hardInvalidate()
                staleAccepted = owner.markValidating(staleResultIdentity)
            } else {
                assertNull(context.committedIdentity)
            }
            exhausted(context)
        }
        ready(session, listOf(group(RELEASE)))

        val result = session.execute(ReaderForegroundIntent(CHAPTER_ID))

        assertIs<ReaderForegroundResult.Exhausted>(result)
        assertFalse(staleAccepted)
        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), revisions)
        val state = assertIs<ReaderExecutionState.Exhausted>(session.executionState)
        assertEquals(ReaderPlanRevision(1), state.identity.planRevision)
    }

    @Test
    fun hardInvalidationReplansWithoutGenerationIncrement() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        val session = session(ReaderSessionId(93)) { owner, context ->
            seen += context.identity
            if (context.identity.planRevision == ReaderPlanRevision(0)) owner.hardInvalidate()
            committed(context)
        }
        ready(session, listOf(group(RELEASE)))

        val result = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CHAPTER_ID)),
        )

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), seen.map { it.planRevision })
        assertTrue(seen.all { it.generationId == ReaderGenerationId(1) })
        assertEquals(ReaderGenerationId(1), result.identity.generationId)
    }

    @Test
    fun softGraphAdditionDoesNotRevokeActivePlan() = runTest {
        val seen = mutableListOf<ReaderExecutionIdentity>()
        val session = session(ReaderSessionId(94)) { owner, context ->
            seen += context.identity
            val primary = routeAttempt(RELEASE, AttemptRole.PRIMARY)
            assertTrue(owner.recordPlannedRoute(context, RELEASE.id, listOf(primary)))
            owner.updateChapterGraph(listOf(group(RELEASE, SECOND_RELEASE)))
            committed(context)
        }
        ready(session, listOf(group(RELEASE)))

        val result = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CHAPTER_ID)),
        )

        assertEquals(1, seen.size)
        assertEquals(ReaderPlanRevision(0), seen.single().planRevision)
        assertEquals(RELEASE.id, result.release.id)
    }

    @Test
    fun graphRemovalOfPlannedReleaseHardReplansSameGeneration() = runTest {
        val revisions = mutableListOf<ReaderPlanRevision>()
        val session = session(ReaderSessionId(95)) { owner, context ->
            revisions += context.identity.planRevision
            if (context.identity.planRevision == ReaderPlanRevision(0)) {
                val route = listOf(
                    routeAttempt(RELEASE, AttemptRole.PRIMARY),
                    routeAttempt(SECOND_RELEASE, AttemptRole.FALLBACK),
                )
                assertTrue(owner.recordPlannedRoute(context, RELEASE.id, route))
                owner.updateChapterGraph(listOf(group(SECOND_RELEASE)))
            }
            committed(context)
        }
        ready(session, listOf(group(RELEASE, SECOND_RELEASE)))

        val result = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CHAPTER_ID)),
        )

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), revisions)
        assertEquals(ReaderGenerationId(1), result.identity.generationId)
        assertEquals(SECOND_RELEASE.id, result.release.id)
    }

    @Test
    fun graphRemovalBeforePlanRegistrationStillHardReplans() = runTest {
        val revisions = mutableListOf<ReaderPlanRevision>()
        val session = session(ReaderSessionId(951)) { owner, context ->
            revisions += context.identity.planRevision
            if (context.identity.planRevision == ReaderPlanRevision(0)) {
                owner.updateChapterGraph(listOf(group(SECOND_RELEASE)))
                assertEquals(
                    false,
                    owner.recordPlannedRoute(
                        context = context,
                        winnerReleaseId = RELEASE.id,
                        attempts = listOf(routeAttempt(RELEASE, AttemptRole.PRIMARY)),
                    ),
                )
            }
            committed(context)
        }
        ready(session, listOf(group(RELEASE, SECOND_RELEASE)))

        val result = assertIs<ReaderForegroundResult.Committed>(
            session.execute(ReaderForegroundIntent(CHAPTER_ID)),
        )

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), revisions)
        assertEquals(ReaderGenerationId(1), result.identity.generationId)
        assertEquals(SECOND_RELEASE.id, result.release.id)
    }

    @Test
    fun languageOrderChangeHardReplansWithinSameGeneration() = runTest {
        val identities = mutableListOf<ReaderExecutionIdentity>()
        val session = session(ReaderSessionId(96)) { owner, context ->
            identities += context.identity
            if (context.identity.planRevision == ReaderPlanRevision(0)) {
                owner.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("fr", "en")))
            }
            committed(context)
        }
        ready(session, listOf(group(RELEASE)))

        session.execute(ReaderForegroundIntent(CHAPTER_ID))

        assertEquals(listOf(ReaderPlanRevision(0), ReaderPlanRevision(1)), identities.map { it.planRevision })
        assertTrue(identities.all { it.generationId == ReaderGenerationId(1) })
    }

    @Test
    fun navigationSelectionAndRetryEachStartNewGeneration() = runTest {
        val session = session(ReaderSessionId(97)) { _, context -> exhausted(context) }
        ready(session, listOf(group(RELEASE, SECOND_RELEASE)))

        val navigate = session.execute(ReaderForegroundIntent(CHAPTER_ID))
        val select = session.execute(ReaderForegroundIntent(CHAPTER_ID, explicitReleaseId = SECOND_RELEASE.id))
        val retry = session.execute(ReaderForegroundIntent(CHAPTER_ID, explicitReleaseId = SECOND_RELEASE.id))

        assertEquals(
            listOf(ReaderGenerationId(1), ReaderGenerationId(2), ReaderGenerationId(3)),
            listOf(navigate, select, retry).map { it.identity.generationId },
        )
    }

    @Test
    fun twoSessionsShareHealthButKeepGenerationPlanAndCommitStateIsolated() = runTest {
        val health = ReaderSourceHealthRegistry()
        val limiter = ReaderExecutionTestOwners()
        val key = SourceOperationKey(RELEASE.pluginId)
        fun sharedSession(id: ReaderSessionId) = session(id) { _, context ->
            limiter.withRemotePermit(RELEASE.pluginId, ReaderTestRemotePriority.FOREGROUND) {
                health.record(
                    key,
                    SourceObservation.Success.Remote(RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT, context.identity.generationId.value),
                    context.identity.generationId.value,
                )
            }
            committed(context)
        }
        val first = sharedSession(ReaderSessionId(101))
        val second = sharedSession(ReaderSessionId(102))
        ready(first, listOf(group(RELEASE)))
        ready(second, listOf(group(RELEASE)))

        assertIs<ReaderForegroundResult.Committed>(first.execute(ReaderForegroundIntent(CHAPTER_ID)))
        val sharedAfterFirst = health.snapshot(key, 10L)
        assertEquals(SourceHealthOrigin.PROCESS_OBSERVED, sharedAfterFirst.origin)
        assertIs<ReaderForegroundResult.Committed>(second.execute(ReaderForegroundIntent(CHAPTER_ID)))
        assertIs<ReaderForegroundResult.Committed>(first.execute(ReaderForegroundIntent(CHAPTER_ID)))

        val firstState = assertIs<ReaderExecutionState.Committed>(first.executionState)
        val secondState = assertIs<ReaderExecutionState.Committed>(second.executionState)
        assertEquals(ReaderGenerationId(2), firstState.identity.generationId)
        assertEquals(ReaderGenerationId(1), secondState.identity.generationId)
        assertEquals(ReaderPlanRevision(0), firstState.identity.planRevision)
        assertEquals(ReaderPlanRevision(0), secondState.identity.planRevision)
        assertEquals(RELEASE.id, firstState.committed.releaseId)
        assertEquals(RELEASE.id, secondState.committed.releaseId)
        assertEquals(3, health.snapshot(key, 10L).state.recentLatencySamplesMillis.size)
    }

    private fun session(
        id: ReaderSessionId,
        delegate: ReaderRouteExecutionDelegate,
    ) = ReaderRouteSession(
        storyId = STORY_ID,
        sessionId = id,
        delegate = delegate,
    )

    private suspend fun ready(
        session: ReaderRouteSession,
        groups: List<CanonicalChapterGroup>,
    ) {
        session.updateChapterGraph(groups)
        session.updateRoutingPreferences(ReaderPreferences(languageOrder = listOf("en")))
    }

    private fun committed(context: ReaderRouteExecutionContext): ReaderForegroundResult.Committed {
        val group = requireNotNull(context.chapterGraph.group(context.identity.targetChapterId))
        val release = group.releases.first()
        return ReaderForegroundResult.Committed(
            identity = context.foregroundIdentity,
            chapterGroup = group,
            release = release,
            document = document("committed-${context.identity.planRevision.value}-${release.id.value}"),
            fromLocal = false,
            restoration = null,
        )
    }

    private fun exhausted(context: ReaderRouteExecutionContext) = ReaderForegroundResult.Exhausted(
        identity = context.foregroundIdentity,
        code = "reader.exhausted",
        retryable = true,
        attempts = emptyList(),
    )

    private fun routeAttempt(
        release: ChapterRelease,
        role: AttemptRole,
    ) = RouteAttempt(
        attemptId = "${role.name.lowercase()}-${release.id.value}",
        releaseId = release.id,
        sourceId = release.pluginId,
        accessMode = AccessMode.REMOTE,
        localFingerprint = null,
        role = role,
    )

    private fun completion(
        attemptId: String,
        role: AttemptRole,
        completedAtNanos: Long,
    ): ReaderValidCompletion {
        val attempt = RouteAttempt(
            attemptId = attemptId,
            releaseId = RELEASE.id,
            sourceId = RELEASE.pluginId,
            accessMode = AccessMode.REMOTE,
            localFingerprint = null,
            role = role,
        )
        return ReaderValidCompletion(
            identity = ReaderExecutionIdentity(
                sessionId = ReaderSessionId(1),
                generationId = ReaderGenerationId(1),
                planRevision = ReaderPlanRevision(0),
                targetChapterId = CHAPTER_ID,
            ).forAttempt(attempt.attemptId),
            attempt = attempt,
            loaded = ReaderLoadResult.Success(
                release = RELEASE,
                document = document(attemptId),
                fromStore = false,
            ),
            completedAtNanos = completedAtNanos,
        )
    }

    private fun document(fingerprint: String) = ReaderDocument(
        title = null,
        blocks = listOf(ReaderBlock.Paragraph("block", fingerprint)),
        fingerprint = fingerprint,
    )

    private fun group(vararg releases: ChapterRelease): CanonicalChapterGroup = CanonicalChapterGroup(
        chapter = CanonicalChapter(
            id = CHAPTER_ID,
            storyId = STORY_ID,
            parsedLabel = LABEL,
            displayLabel = "chapter",
            tombstoned = false,
            releaseIds = releases.mapTo(linkedSetOf()) { it.id },
        ),
        releases = releases.toList(),
    )

    private companion object {
        val STORY_ID = StoryId("story")
        val CHAPTER_ID = CanonicalChapterId("chapter")
        val LABEL = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null)
        val RELEASE = ChapterRelease(
            id = ChapterReleaseId("release"),
            storyId = STORY_ID,
            pluginId = PluginId("source-primary"),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-release",
            displayLabel = "release",
            parsedLabel = LABEL,
            languageTag = "en",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = CHAPTER_ID,
        )
        val SECOND_RELEASE = ChapterRelease(
            id = ChapterReleaseId("release-second"),
            storyId = STORY_ID,
            pluginId = PluginId("source-second"),
            sourceStoryId = "source-story",
            sourceReleaseId = "source-release-second",
            displayLabel = "release-second",
            parsedLabel = LABEL,
            languageTag = "fr",
            publishedAtEpochMillis = 2L,
            canonicalChapterId = CHAPTER_ID,
        )
    }
}
