package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderDocumentReadResult
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.penalizesSourceHealth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ReaderRouteExecutorAdaptiveTest {
    @Test
    fun remoteValidCompletionUsesPostValidationClockWhileLatencyUsesFetchClock() = runTest {
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val candidate = candidate("release", "source")
        val attempt = remote("a0", "release", "source", AttemptRole.PRIMARY)
        val timestamps = ArrayDeque(listOf(0L, 2_000_000L, 9_000_000L))
        val completions = mutableListOf<ReaderValidCompletion>()
        val observations = mutableListOf<SourceObservation>()
        val identity = ReaderExecutionIdentity(
            sessionId = ReaderSessionId(1),
            generationId = ReaderGenerationId(1),
            planRevision = ReaderPlanRevision(0),
            targetChapterId = app.openstory.common.id.CanonicalChapterId("chapter"),
        ).forAttempt(attempt.attemptId)

        val outcome = executor(
            store = AdaptiveStore(),
            registry = AdaptiveRegistry(listOf(source)),
            monotonicNanos = timestamps::removeFirst,
        ).executeAttempt(
            identity = identity,
            attempt = attempt,
            candidate = candidate,
            remoteSources = ReaderRemoteSourceResolver { mapOf(source.pluginId to source) },
            ownership = ReaderAttemptOwnership(),
            publishValidCompletion = { loaded ->
                ReaderValidCompletion(
                    identity = identity,
                    attempt = attempt,
                    loaded = loaded,
                    completedAtNanos = timestamps.removeFirst(),
                ).also(completions::add)
            },
            onSourceObservation = { _, observation -> observations += observation },
            onLocalInvalidated = { _, _ -> },
        )

        assertIs<ReaderAttemptOutcome.Success>(outcome)
        assertEquals(9_000_000L, completions.single().completedAtNanos)
        assertEquals(
            2L,
            assertIs<SourceObservation.Success.Remote>(observations.single()).latencyMillis,
        )
        assertEquals(0, timestamps.size)
    }

    @Test
    fun nullableStoreReadGetsTypedCompatibilityProjection() = runTest {
        val hitStore = AdaptiveStore(exact = mapOf("release" to document("expected")))
        val missingStore = AdaptiveStore()

        val hit = assertIs<ReaderDocumentReadResult.Hit>(
            hitStore.readResult(ChapterReleaseId("release"), "expected"),
        )
        assertEquals("expected", hit.document.fingerprint)
        assertIs<ReaderDocumentReadResult.Missing>(
            missingStore.readResult(ChapterReleaseId("release"), "expected"),
        )
    }

    @Test
    fun exactLocalAttemptWinsWithoutEnumeratingRemoteSources() = runTest {
        val store = AdaptiveStore(exact = mapOf("selected" to document("expected")))
        val registry = AdaptiveRegistry(emptyList())
        val candidate = candidate("selected", "source")
        val observations = mutableListOf<SourceObservation>()

        val result = executor(store, registry).executeAdaptive(
            attempts = listOf(
                local("a0", "selected", "source", "expected", AttemptRole.PRIMARY),
                remote("a1", "selected", "source"),
            ),
            candidatesByRelease = mapOf(candidate.id to candidate),
            onSourceObservation = { _, observation -> observations += observation },
        )

        assertEquals("selected", assertIs<ReaderLoadResult.Success>(result).release.id.value)
        assertEquals(true, result.fromStore)
        assertEquals(listOf<SourceObservation>(SourceObservation.Success.Local), observations)
        assertEquals(listOf("selected" to "expected"), store.reads)
        assertEquals(0, registry.enabledCalls)
    }

    @Test
    fun sourceScopedFailureSkipsLaterRemoteFromSameSourceButNotLocal() = runTest {
        val store = AdaptiveStore(mapOf("local" to document("fp-local")))
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("first" to ReaderSourceResult.Failure("plugin.execution_timeout", true)),
        )
        val attempts = listOf(
            remote("a0", "first", "source", AttemptRole.PRIMARY),
            local("a1", "local", "source", "fp-local"),
            remote("a2", "later", "source"),
        )
        val candidates = listOf(candidate("first", "source"), candidate("local", "source"), candidate("later", "source"))

        val result = executor(store, AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = attempts,
            candidatesByRelease = candidates.associateBy { it.id },
        )

        assertEquals("local", assertIs<ReaderLoadResult.Success>(result).release.id.value)
        assertEquals(listOf("first"), source.fetches)
        assertEquals(listOf("local" to "fp-local"), store.reads)
    }

    @Test
    fun releaseScopedFailureAllowsAnotherReleaseFromSameSource() = runTest {
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf(
                "first" to ReaderSourceResult.Failure("reader.release_not_found", false),
                "second" to ReaderSourceResult.Success(document("remote")),
            ),
        )
        val candidates = listOf(candidate("first", "source"), candidate("second", "source"))
        val result = executor(AdaptiveStore(), AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                remote("a0", "first", "source", AttemptRole.PRIMARY),
                remote("a1", "second", "source"),
            ),
            candidatesByRelease = candidates.associateBy { it.id },
        )
        assertIs<ReaderLoadResult.Success>(result)
        assertEquals(listOf("first", "second"), source.fetches)
    }

    @Test
    fun explicitReleaseFailuresPreserveFallbackOrderAndFailureSurface() = runTest {
        val newerSource = AdaptiveSource(
            PluginId("newer-source"),
            mutableMapOf("newer" to ReaderSourceResult.Failure("reader.newer_failed", true)),
        )
        val olderSource = AdaptiveSource(
            PluginId("older-source"),
            mutableMapOf("older" to ReaderSourceResult.Failure("reader.older_failed", false)),
        )
        val candidates = listOf(
            candidate("newer", "newer-source"),
            candidate("older", "older-source"),
        )

        val failure = assertIs<ReaderLoadResult.Failure>(
            executor(AdaptiveStore(), AdaptiveRegistry(listOf(newerSource, olderSource))).executeAdaptive(
                attempts = listOf(
                    remote("a0", "newer", "newer-source", AttemptRole.PRIMARY),
                    remote("a1", "older", "older-source"),
                ),
                candidatesByRelease = candidates.associateBy { it.id },
            ),
        )

        assertEquals(listOf("newer", "older"), failure.attempts.map { it.releaseId.value })
        assertEquals(listOf("reader.newer_failed", "reader.older_failed"), failure.attempts.map { it.code })
        assertEquals(listOf(true, false), failure.attempts.map { it.retryable })
    }

    @Test
    fun exactCorruptionQuarantinesLocatorThenRemoteProbeRecovers() = runTest {
        val store = AdaptiveStore(exact = mapOf("release" to document("wrong")))
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val candidate = candidate("release", "source")
        val invalidated = mutableListOf<Pair<String, String>>()
        val observations = mutableListOf<SourceObservation>()

        val result = executor(store, AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                local("a0", "release", "source", "expected", AttemptRole.PRIMARY),
                remote("a1", "release", "source"),
            ),
            candidatesByRelease = mapOf(candidate.id to candidate),
            remoteAttemptKinds = mapOf(candidate.id to RemoteAttemptKind.HALF_OPEN_PROBE),
            onSourceObservation = { _, observation -> observations += observation },
            onLocalInvalidated = { releaseId, fingerprint -> invalidated += releaseId.value to fingerprint },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertEquals(listOf("release" to "expected"), store.quarantines)
        assertEquals(listOf("release" to "expected"), invalidated)
        assertIs<SourceObservation.LocalFailure.FingerprintOrDecodeMismatch>(observations[0])
        assertEquals(false, observations[0].penalizesSourceHealth)
        assertEquals(
            RemoteAttemptKind.HALF_OPEN_PROBE,
            assertIs<SourceObservation.Success.Remote>(observations[1]).kind,
        )
    }

    @Test
    fun missingExactLocalBlobEmitsTypedNonPenalizingMissBeforeRemoteRecovery() = runTest {
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val candidate = candidate("release", "source")
        val observations = mutableListOf<SourceObservation>()

        val result = executor(AdaptiveStore(), AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                local("a0", "release", "source", "expected", AttemptRole.PRIMARY),
                remote("a1", "release", "source"),
            ),
            candidatesByRelease = mapOf(candidate.id to candidate),
            onSourceObservation = { _, observation -> observations += observation },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertIs<SourceObservation.LocalFailure.MissingBlob>(observations[0])
        assertEquals(false, observations[0].penalizesSourceHealth)
        assertIs<SourceObservation.Success.Remote>(observations[1])
    }

    @Test
    fun typedStoreCorruptionIsNotCollapsedIntoMissingBlob() = runTest {
        val store = AdaptiveStore(
            readResultOverride = ReaderDocumentReadResult.FingerprintOrDecodeMismatch,
        )
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val candidate = candidate("release", "source")
        val observations = mutableListOf<SourceObservation>()
        val invalidated = mutableListOf<Pair<String, String>>()

        val result = executor(store, AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                local("a0", "release", "source", "expected", AttemptRole.PRIMARY),
                remote("a1", "release", "source"),
            ),
            candidatesByRelease = mapOf(candidate.id to candidate),
            onSourceObservation = { _, observation -> observations += observation },
            onLocalInvalidated = { releaseId, fingerprint -> invalidated += releaseId.value to fingerprint },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertIs<SourceObservation.LocalFailure.FingerprintOrDecodeMismatch>(observations.first())
        assertEquals(listOf("release" to "expected"), invalidated)
        assertEquals(listOf("release" to "expected"), store.quarantines)
    }

    @Test
    fun typedStoreMissingDoesNotQuarantineOrInvalidateLocator() = runTest {
        val store = AdaptiveStore(readResultOverride = ReaderDocumentReadResult.Missing)
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val candidate = candidate("release", "source")
        val observations = mutableListOf<SourceObservation>()
        val invalidated = mutableListOf<Pair<String, String>>()

        val result = executor(store, AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                local("a0", "release", "source", "expected", AttemptRole.PRIMARY),
                remote("a1", "release", "source"),
            ),
            candidatesByRelease = mapOf(candidate.id to candidate),
            onSourceObservation = { _, observation -> observations += observation },
            onLocalInvalidated = { releaseId, fingerprint -> invalidated += releaseId.value to fingerprint },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertIs<SourceObservation.LocalFailure.MissingBlob>(observations.first())
        assertEquals(emptyList(), invalidated)
        assertEquals(emptyList(), store.quarantines)
    }

    @Test
    fun localStorageIoFailureIsClientScopedAndDoesNotQuarantine() = runTest {
        val store = AdaptiveStore(readFailure = IllegalStateException("disk unavailable"))
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf("release" to ReaderSourceResult.Success(document("remote"))),
        )
        val candidate = candidate("release", "source")
        val observations = mutableListOf<SourceObservation>()

        val result = executor(store, AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                local("a0", "release", "source", "expected", AttemptRole.PRIMARY),
                remote("a1", "release", "source"),
            ),
            candidatesByRelease = mapOf(candidate.id to candidate),
            onSourceObservation = { _, observation -> observations += observation },
        )

        assertIs<ReaderLoadResult.Success>(result)
        assertIs<SourceObservation.RuntimeFailure.Unexpected>(observations[0])
        assertEquals(false, observations[0].penalizesSourceHealth)
        assertEquals(emptyList(), store.quarantines)
        assertIs<SourceObservation.Success.Remote>(observations[1])
    }

    @Test
    fun remoteCancellationIsRethrown() = runTest {
        val source = AdaptiveSource(PluginId("source"), mutableMapOf(), cancel = true)
        val candidate = candidate("release", "source")

        assertFailsWith<CancellationException> {
            executor(AdaptiveStore(), AdaptiveRegistry(listOf(source))).executeAdaptive(
                attempts = listOf(remote("a0", "release", "source", AttemptRole.PRIMARY)),
                candidatesByRelease = mapOf(candidate.id to candidate),
            )
        }
    }

    @Test
    fun remoteSourcesAreEnumeratedOnceForAdaptiveFallbacks() = runTest {
        val first = AdaptiveSource(
            PluginId("first-source"),
            mutableMapOf("first" to ReaderSourceResult.Failure("reader.release_not_found", false)),
        )
        val second = AdaptiveSource(
            PluginId("second-source"),
            mutableMapOf("second" to ReaderSourceResult.Failure("reader.release_not_found", false)),
        )
        val registry = AdaptiveRegistry(listOf(first, second))
        val candidates = listOf(candidate("first", "first-source"), candidate("second", "second-source"))

        executor(AdaptiveStore(), registry).executeAdaptive(
            attempts = listOf(
                remote("a0", "first", "first-source", AttemptRole.PRIMARY),
                remote("a1", "second", "second-source"),
            ),
            candidatesByRelease = candidates.associateBy { it.id },
        )

        assertEquals(1, registry.enabledCalls)
        assertEquals(listOf("first"), first.fetches)
        assertEquals(listOf("second"), second.fetches)
    }

    @Test
    fun persistableRemoteDocumentIsStoredButImagePageIsNot() = runTest {
        val store = AdaptiveStore()
        val source = AdaptiveSource(
            PluginId("source"),
            mutableMapOf(
                "text" to ReaderSourceResult.Success(document("text-fp")),
                "image" to ReaderSourceResult.Success(
                    ReaderDocument(
                        title = null,
                        blocks = listOf(
                            ReaderBlock.ImagePage("image", "hash/image.png", "https://node.example/image.png"),
                        ),
                        fingerprint = "image-fp",
                    ),
                ),
            ),
        )
        val text = candidate("text", "source")
        val image = candidate("image", "source")
        val executor = executor(store, AdaptiveRegistry(listOf(source)))

        executor.executeAdaptive(
            attempts = listOf(remote("a0", "text", "source", AttemptRole.PRIMARY)),
            candidatesByRelease = mapOf(text.id to text),
        )
        executor.executeAdaptive(
            attempts = listOf(remote("a1", "image", "source", AttemptRole.PRIMARY)),
            candidatesByRelease = mapOf(image.id to image),
        )

        assertEquals(listOf("text" to "text-fp"), store.writes)
    }

    @Test
    fun malformedPlanOverRemoteCeilingFailsFast() = runTest {
        val candidates = (0..4).map { candidate("r$it", "s$it") }
        assertFailsWith<IllegalArgumentException> {
            executor(AdaptiveStore(), AdaptiveRegistry(emptyList())).executeAdaptive(
                attempts = candidates.mapIndexed { index, candidate ->
                    remote(
                        "a$index",
                        candidate.id.value,
                        candidate.pluginId.value,
                        if (index == 0) AttemptRole.PRIMARY else AttemptRole.FALLBACK,
                    )
                },
                candidatesByRelease = candidates.associateBy { it.id },
            )
        }
    }

    private fun remote(id: String, release: String, source: String, role: AttemptRole = AttemptRole.FALLBACK) = RouteAttempt(
        attemptId = id,
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        accessMode = AccessMode.REMOTE,
        localFingerprint = null,
        role = role,
    )

    private fun executor(
        store: ReaderDocumentStore = AdaptiveStore(),
        registry: ReaderDocumentSourceRegistry = AdaptiveRegistry(emptyList()),
        limiter: ReaderExecutionTestOwners = ReaderExecutionTestOwners(),
        monotonicNanos: () -> Long = System::nanoTime,
    ) = ReaderRouteExecutor(
        store = store,
        sources = registry,
        sourceLane = limiter.sourceLane,
        fetchArbiter = limiter.fetchArbiter,
        monotonicNanos = monotonicNanos,
    )

    private fun local(
        id: String,
        release: String,
        source: String,
        fingerprint: String,
        role: AttemptRole = AttemptRole.FALLBACK,
    ) = RouteAttempt(
        attemptId = id,
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        accessMode = AccessMode.LOCAL,
        localFingerprint = fingerprint,
        role = role,
    )

    private fun candidate(id: String, source: String) = ChapterRelease(
            id = ChapterReleaseId(id),
            storyId = StoryId("story"),
            pluginId = PluginId(source),
            sourceStoryId = "story",
            sourceReleaseId = id,
            displayLabel = id,
            parsedLabel = ParsedChapterLabel(ChapterKind.NUMBERED, null, null, null, null),
            languageTag = "vi",
            publishedAtEpochMillis = 1L,
            canonicalChapterId = null,
        )

    private fun document(fp: String) = ReaderDocument(null, listOf(ReaderBlock.Paragraph("b", "text")), fp)
}

private class AdaptiveStore(
    private val exact: Map<String, ReaderDocument> = emptyMap(),
    private val readFailure: Throwable? = null,
    private val readResultOverride: ReaderDocumentReadResult? = null,
) : ReaderDocumentStore {
    val reads = mutableListOf<Pair<String, String>>()
    val writes = mutableListOf<Pair<String, String>>()
    val quarantines = mutableListOf<Pair<String, String>>()

    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? {
        reads += releaseId.value to fingerprint
        readFailure?.let { throw it }
        return exact[releaseId.value]
    }

    override suspend fun readResult(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): ReaderDocumentReadResult = readResultOverride
        ?: read(releaseId, fingerprint)
            ?.let(ReaderDocumentReadResult::Hit)
        ?: ReaderDocumentReadResult.Missing

    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null

    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) {
        writes += releaseId.value to fingerprint
    }

    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) {
        quarantines += releaseId.value to fingerprint
    }
}

private class AdaptiveRegistry(private val values: List<ReaderDocumentSource>) : ReaderDocumentSourceRegistry {
    var enabledCalls: Int = 0

    override suspend fun enabled(): List<ReaderDocumentSource> {
        enabledCalls += 1
        return values
    }
}

private class AdaptiveSource(
    override val pluginId: PluginId,
    private val results: MutableMap<String, ReaderSourceResult>,
    private val cancel: Boolean = false,
) : ReaderDocumentSource {
    val fetches = mutableListOf<String>()

    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetches += release.id.value
        if (cancel) throw CancellationException("cancelled test source")
        return results[release.id.value] ?: ReaderSourceResult.Failure("reader.source_failed", true)
    }
}
