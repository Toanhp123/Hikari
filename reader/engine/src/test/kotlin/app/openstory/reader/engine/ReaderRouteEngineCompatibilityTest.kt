package app.openstory.reader.engine

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ReaderRouteEngineCompatibilityTest {
    private val engine = ReaderRouteEngine.v1()

    @Test
    fun representableLegacyTiersAreAppliedInOrder() {
        assertWinner(
            expected = "explicit",
            candidates = listOf(
                candidate("lower", source = "a", language = "vi", completeness = 10_000, publishedAt = 100L),
                candidate("explicit", source = "z", language = "fr", completeness = 0, publishedAt = 1L),
            ),
            snapshotOverride = { candidates ->
                snapshot(candidates, explicitReleaseId = ChapterReleaseId("explicit"))
            },
            policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi")),
        )

        assertWinner(
            expected = "resume",
            candidates = listOf(
                candidate("resume", source = "z", language = "fr", completeness = 0, publishedAt = 1L),
                candidate("language", source = "a", language = "vi", completeness = 10_000, publishedAt = 100L),
            ),
            snapshotOverride = { candidates ->
                snapshot(
                    candidates,
                    continuity = ReadingContinuity(targetResumeReleaseId = ChapterReleaseId("resume")),
                )
            },
            policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi")),
        )

        assertWinner(
            expected = "group",
            candidates = listOf(
                candidate("group", source = "z", language = "fr", group = "team", completeness = 0, publishedAt = 1L),
                candidate("source", source = "preferred", language = "vi", completeness = 10_000, publishedAt = 100L),
            ),
            snapshotOverride = { candidates ->
                snapshot(
                    candidates,
                    continuity = ReadingContinuity(
                        committedSourceId = PluginId("preferred"),
                        committedSourceGroupKey = SourceGroupKey("team"),
                    ),
                )
            },
            policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi")),
        )

        assertWinner(
            expected = "source",
            candidates = listOf(
                candidate("source", source = "preferred", language = "fr", completeness = 0, publishedAt = 1L),
                candidate("language", source = "other", language = "vi", completeness = 10_000, publishedAt = 100L),
            ),
            snapshotOverride = { candidates ->
                snapshot(candidates, continuity = ReadingContinuity(committedSourceId = PluginId("preferred")))
            },
            policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi")),
        )

        assertWinner(
            expected = "language",
            candidates = listOf(
                candidate("language", source = "z", language = "vi", completeness = 0, publishedAt = 1L),
                candidate("complete", source = "a", language = "en", completeness = 10_000, publishedAt = 100L),
            ),
            policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en")),
        )

        assertWinner(
            expected = "complete",
            candidates = listOf(
                candidate("complete", source = "z", language = "vi", completeness = 10_000, publishedAt = 1L),
                candidate("recent", source = "a", language = "vi", completeness = 9_000, publishedAt = 100L),
            ),
        )

        assertWinner(
            expected = "recent",
            candidates = listOf(
                candidate("recent", source = "z", language = "vi", publishedAt = 100L),
                candidate("older", source = "a", language = "vi", publishedAt = 1L),
            ),
        )

        assertWinner(
            expected = "release-z",
            candidates = listOf(
                candidate("release-a", source = "source-z", language = "vi", publishedAt = 1L),
                candidate("release-z", source = "source-a", language = "vi", publishedAt = 1L),
            ),
        )

        assertWinner(
            expected = "release-a",
            candidates = listOf(
                candidate("release-z", source = "source-a", language = "vi", publishedAt = 1L),
                candidate("release-a", source = "source-a", language = "vi", publishedAt = 1L),
            ),
        )
    }

    @Test
    fun canonicalizedPlanningIsReplayableAndIndependentOfInputOrder() {
        val candidates = listOf(
            candidate("release-c", source = "source-b", language = "en", completeness = 8_000, publishedAt = 2L),
            candidate("release-a", source = "source-a", language = "vi", completeness = 8_000, publishedAt = 2L),
            candidate("release-b", source = "source-a", language = "en", completeness = 9_000, publishedAt = 1L),
        )
        val policy = ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en"))

        val forwards = engine.plan(snapshot(candidates), policy)
        val backwards = engine.plan(snapshot(candidates.reversed()), policy)
        val replay = engine.plan(snapshot(candidates), policy)

        assertEquals(forwards, backwards)
        assertEquals(forwards, replay)
        assertEquals(
            listOf("release-a", "release-b", "release-c"),
            forwards.trace.stableRanking.map { it.value },
        )
    }

    @Test
    fun compatibilityPlanEmitsRemoteSequentialAttemptsAndPreservesPlanRevision() {
        val revision = ReaderPlanRevision(17)
        val decision = engine.plan(
            snapshot(
                candidates = listOf(
                    candidate("release-b", source = "source-b", language = "en", publishedAt = 1L),
                    candidate("release-a", source = "source-a", language = "vi", publishedAt = 2L),
                ),
                planRevision = revision,
            ),
            ReaderRoutingPolicy.v1(languageOrder = listOf("vi", "en")),
        )
        val attempts = buildList {
            decision.competitiveSet.primary?.let(::add)
            addAll(decision.recoveryChain)
        }

        assertEquals(revision, decision.planRevision)
        assertEquals(revision, decision.trace.planRevision)
        assertEquals(listOf("attempt-0", "attempt-1"), attempts.map { it.attemptId })
        assertEquals(listOf(AttemptRole.PRIMARY, AttemptRole.FALLBACK), attempts.map { it.role })
        assertEquals(listOf(AccessMode.REMOTE, AccessMode.REMOTE), attempts.map { it.accessMode })
        assertEquals(listOf(null, null), attempts.map { it.localFingerprint })
        assertNull(decision.competitiveSet.hedge)
        assertEquals(HedgeDirective.Omitted(HedgeOmissionReason.NOT_EVALUATED), decision.hedgeDirective)
        assertEquals(attempts, decision.trace.routeConstruction)
    }

    @Test
    fun emptyInputProducesNoAttemptAndM3AvailabilityFactsStayObservational() {
        val empty = engine.plan(snapshot(emptyList()), ReaderRoutingPolicy.v1())
        assertNull(empty.competitiveSet.primary)
        assertEquals(DecisionReason.NO_ELIGIBLE_CANDIDATE, empty.reason)

        val unavailable = engine.plan(
            snapshot(
                listOf(
                    candidate(
                        id = "release-a",
                        source = "source-a",
                        language = "vi",
                        publishedAt = 1L,
                        remoteAccess = CandidateRemoteAccess.SOURCE_UNAVAILABLE,
                    ),
                ),
            ),
            ReaderRoutingPolicy.v1(),
        )
        assertEquals(ChapterReleaseId("release-a"), unavailable.competitiveSet.primary?.releaseId)
        assertFailsWith<IllegalArgumentException> {
            engine.plan(
                snapshot(
                    listOf(
                        candidate(
                            id = "release-local",
                            source = "source-a",
                            language = "vi",
                            publishedAt = 1L,
                            localAccess = CandidateLocalAccess.AvailableExact("fingerprint"),
                        ),
                    ),
                ),
                ReaderRoutingPolicy.v1(),
            )
        }
    }

    private fun assertWinner(
        expected: String,
        candidates: List<RoutingCandidate>,
        snapshotOverride: (List<RoutingCandidate>) -> ReaderRoutingSnapshot = { snapshot(it) },
        policy: ReaderRoutingPolicy = ReaderRoutingPolicy.v1(),
    ) {
        val decision = engine.plan(snapshotOverride(candidates), policy)
        assertEquals(expected, decision.competitiveSet.primary?.releaseId?.value)
    }

    private fun candidate(
        id: String,
        source: String,
        language: String,
        group: String? = null,
        completeness: Int = 10_000,
        publishedAt: Long?,
        remoteAccess: CandidateRemoteAccess = CandidateRemoteAccess.PERMITTED,
        localAccess: CandidateLocalAccess = CandidateLocalAccess.Miss,
    ) = RoutingCandidate(
        releaseId = ChapterReleaseId(id),
        sourceId = PluginId(source),
        languageTag = language,
        sourceGroupKey = group?.let(::SourceGroupKey),
        publishedAtEpochMillis = publishedAt,
        completeness = BasisPoints(completeness),
        remoteAccess = remoteAccess,
        localAccess = localAccess,
    )

    private fun snapshot(
        candidates: List<RoutingCandidate>,
        continuity: ReadingContinuity = ReadingContinuity(),
        explicitReleaseId: ChapterReleaseId? = null,
        planRevision: ReaderPlanRevision = ReaderPlanRevision(5),
    ) = ReaderRoutingSnapshot.create(
        targetChapterId = CanonicalChapterId("chapter-1"),
        chapterGraphRevision = ReaderChapterGraphRevision(3),
        planRevision = planRevision,
        routingIntent = RoutingIntent.FOREGROUND,
        candidates = candidates,
        sourceHealth = emptyList(),
        continuity = continuity,
        networkClass = ReaderNetworkClass.UNKNOWN,
        explicitReleaseId = explicitReleaseId,
        nowEpochMillis = 123L,
    )
}
