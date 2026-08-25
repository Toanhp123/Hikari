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
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.selection.ReleaseCandidate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ReaderRouteExecutorAdaptiveTest {
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

        val result = ReaderRouteExecutor(store, AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = attempts,
            candidatesByRelease = candidates.associateBy { it.release.id },
        )

        assertEquals("local", assertIs<ReaderLoadResult.Success>(result).release.release.id.value)
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
        val result = ReaderRouteExecutor(AdaptiveStore(), AdaptiveRegistry(listOf(source))).executeAdaptive(
            attempts = listOf(
                remote("a0", "first", "source", AttemptRole.PRIMARY),
                remote("a1", "second", "source"),
            ),
            candidatesByRelease = candidates.associateBy { it.release.id },
        )
        assertIs<ReaderLoadResult.Success>(result)
        assertEquals(listOf("first", "second"), source.fetches)
    }

    @Test
    fun malformedPlanOverRemoteCeilingFailsFast() = runTest {
        val candidates = (0..4).map { candidate("r$it", "s$it") }
        assertFailsWith<IllegalArgumentException> {
            ReaderRouteExecutor(AdaptiveStore(), AdaptiveRegistry(emptyList())).executeAdaptive(
                attempts = candidates.mapIndexed { index, candidate ->
                    remote("a$index", candidate.release.id.value, candidate.release.pluginId.value, if (index == 0) AttemptRole.PRIMARY else AttemptRole.FALLBACK)
                },
                candidatesByRelease = candidates.associateBy { it.release.id },
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

    private fun local(id: String, release: String, source: String, fingerprint: String) = RouteAttempt(
        attemptId = id,
        releaseId = ChapterReleaseId(release),
        sourceId = PluginId(source),
        accessMode = AccessMode.LOCAL,
        localFingerprint = fingerprint,
        role = AttemptRole.FALLBACK,
    )

    private fun candidate(id: String, source: String) = ReleaseCandidate(
        ChapterRelease(
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
        ),
    )

    private fun document(fp: String) = ReaderDocument(null, listOf(ReaderBlock.Paragraph("b", "text")), fp)
}

private class AdaptiveStore(
    private val exact: Map<String, ReaderDocument> = emptyMap(),
) : ReaderDocumentStore {
    val reads = mutableListOf<Pair<String, String>>()
    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? {
        reads += releaseId.value to fingerprint
        return exact[releaseId.value]
    }
    override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = null
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}

private class AdaptiveRegistry(private val values: List<ReaderDocumentSource>) : ReaderDocumentSourceRegistry {
    override suspend fun enabled(): List<ReaderDocumentSource> = values
}

private class AdaptiveSource(
    override val pluginId: PluginId,
    private val results: MutableMap<String, ReaderSourceResult>,
) : ReaderDocumentSource {
    val fetches = mutableListOf<String>()
    override suspend fun fetch(release: ChapterRelease): ReaderSourceResult {
        fetches += release.id.value
        return results[release.id.value] ?: ReaderSourceResult.Failure("reader.source_failed", true)
    }
}
