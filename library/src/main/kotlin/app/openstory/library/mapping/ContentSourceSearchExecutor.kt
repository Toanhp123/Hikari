package app.openstory.library.mapping

import app.openstory.library.content.ContentSource
import app.openstory.library.content.ContentSourceResult
import app.openstory.library.content.ContentSourceStory
import app.openstory.library.matching.ContentMatchDecision
import app.openstory.library.matching.ContentStoryFeatures
import app.openstory.library.matching.ContentStoryMatcher
import app.openstory.library.matching.DirectContentStoryIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

internal class ContentSourceSearchExecutor(
    private val matcher: ContentStoryMatcher,
    private val policy: ContentMappingSearchPolicy,
) {
    suspend fun searchStage(
        stage: ContentMappingSearchStage,
        canonical: ContentStoryFeatures,
        queries: List<String>,
        selected: List<ContentSource>,
        timeoutMillis: Long,
    ): ContentMappingSearchReport {
        if (selected.isEmpty()) return emptyReport(stage, queries)
        val attempts = supervisorScope {
            selected.map { source ->
                async {
                    isolatedAttempt(source, timeoutMillis) {
                        searchSource(source, canonical, queries)
                    }
                }
            }.awaitAll()
        }
        return attempts.toReport(stage, queries, policy.maxCandidatesPerStage)
    }

    suspend fun resolveUrl(
        canonical: ContentStoryFeatures,
        url: String,
        eligible: List<ContentSource>,
    ): ContentMappingSearchReport {
        val attempts = supervisorScope {
            eligible.map { source ->
                async {
                    isolatedAttempt(source, policy.deferredSourceTimeoutMillis) {
                        resolveSourceUrl(source, canonical, url)
                    }
                }
            }.awaitAll()
        }
        return attempts.toReport(
            stage = ContentMappingSearchStage.URL,
            queries = emptyList(),
            candidateCap = policy.maxCandidatesPerStage,
        )
    }


    private suspend fun isolatedAttempt(
        source: ContentSource,
        timeoutMillis: Long,
        block: suspend () -> SourceAttempt,
    ): SourceAttempt = try {
        withTimeoutOrNull(timeoutMillis) { block() } ?: SourceAttempt.timeout(source)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        SourceAttempt.failed(source, "content.source_failed", true)
    }

    private suspend fun searchSource(
        source: ContentSource,
        canonical: ContentStoryFeatures,
        queries: List<String>,
    ): SourceAttempt {
        val candidates = linkedMapOf<String, ContentMappingCandidate>()
        for (query in queries) {
            when (val result = source.search(query, policy.maxCandidatesPerQuery)) {
                is ContentSourceResult.Failure -> return failedAttempt(source, candidates.values, result)
                is ContentSourceResult.Success -> accumulateCandidates(
                    source = source,
                    canonical = canonical,
                    stories = result.value,
                    candidates = candidates,
                )
            }
        }
        return SourceAttempt(source, candidates.values.toList(), null)
    }

    private fun accumulateCandidates(
        source: ContentSource,
        canonical: ContentStoryFeatures,
        stories: List<ContentSourceStory>,
        candidates: MutableMap<String, ContentMappingCandidate>,
    ) {
        stories.forEach { story ->
            val candidate = score(source, canonical, story)
            val previous = candidates[story.sourceStoryId]
            if (candidate.match.decision != ContentMatchDecision.REJECT &&
                (previous == null || candidate.isBetterThan(previous))
            ) {
                candidates[story.sourceStoryId] = candidate
            }
        }
    }

    private suspend fun resolveSourceUrl(
        source: ContentSource,
        canonical: ContentStoryFeatures,
        url: String,
    ): SourceAttempt = when (val result = source.resolveUrl(url)) {
        is ContentSourceResult.Failure -> failedAttempt(source, emptyList(), result)
        is ContentSourceResult.Success -> SourceAttempt(
            source = source,
            candidates = listOf(score(source, canonical, result.value))
                .filterNot { candidate -> candidate.match.decision == ContentMatchDecision.REJECT },
            failure = null,
        )
    }

    private fun score(
        source: ContentSource,
        canonical: ContentStoryFeatures,
        story: ContentSourceStory,
    ): ContentMappingCandidate {
        val candidateFeatures = ContentStoryFeatures(
            title = story.title,
            aliases = story.aliases,
            authors = story.authors,
            contentType = story.contentType,
            directMappings = setOf(DirectContentStoryIdentity(source.pluginId, story.sourceStoryId)),
        )
        return ContentMappingCandidate(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            sourceStoryId = story.sourceStoryId,
            sourceUrl = story.sourceUrl,
            title = story.title,
            match = matcher.compare(canonical, candidateFeatures),
        )
    }
}

private data class SourceAttempt(
    val source: ContentSource,
    val candidates: List<ContentMappingCandidate>,
    val failure: ContentMappingSearchFailure?,
) {
    companion object {
        fun timeout(source: ContentSource) = failed(source, "content.source_timeout", true)

        fun failed(
            source: ContentSource,
            code: String,
            retryable: Boolean,
        ) = SourceAttempt(
            source = source,
            candidates = emptyList(),
            failure = ContentMappingSearchFailure(source.pluginId, code, retryable),
        )
    }
}

private fun failedAttempt(
    source: ContentSource,
    candidates: Collection<ContentMappingCandidate>,
    result: ContentSourceResult.Failure,
) = SourceAttempt(
    source = source,
    candidates = candidates.toList(),
    failure = ContentMappingSearchFailure(
        pluginId = source.pluginId,
        code = result.failure.code,
        retryable = result.failure.retryable,
    ),
)

private fun List<SourceAttempt>.toReport(
    stage: ContentMappingSearchStage,
    queries: List<String>,
    candidateCap: Int,
): ContentMappingSearchReport = ContentMappingSearchReport(
    stage = stage,
    searchedPluginIds = map { attempt -> attempt.source.pluginId }.distinct(),
    queryVariants = queries,
    candidates = flatMap(SourceAttempt::candidates)
        .groupBy { candidate -> candidate.pluginId to candidate.sourceStoryId }
        .values
        .map { sameIdentity -> sameIdentity.minWith(candidateComparator) }
        .sortedWith(candidateComparator)
        .take(candidateCap),
    failures = mapNotNull(SourceAttempt::failure),
)

private fun emptyReport(
    stage: ContentMappingSearchStage,
    queries: List<String>,
) = ContentMappingSearchReport(stage, emptyList(), queries, emptyList(), emptyList())

private fun ContentMappingCandidate.isBetterThan(other: ContentMappingCandidate): Boolean =
    candidateComparator.compare(this, other) < 0

private val candidateComparator = compareBy<ContentMappingCandidate> { candidate ->
    when (candidate.match.decision) {
        ContentMatchDecision.AUTO_LINK -> 0
        ContentMatchDecision.REVIEW -> 1
        ContentMatchDecision.REJECT -> 2
    }
}.thenByDescending { candidate -> candidate.match.score }
    .thenBy { candidate -> candidate.pluginId.value }
    .thenBy(ContentMappingCandidate::sourceStoryId)
