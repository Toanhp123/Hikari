package app.openstory.library.mapping

import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import app.openstory.library.matching.ContentMatchDecision
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ContentMappingService @Inject constructor(
    private val repository: ContentMappingRepository,
    private val search: ContentMappingSearchService,
    private val clock: Clock,
) {
    fun observe(storyId: StoryId): Flow<List<ContentMapping>> = repository.observe(storyId)

    fun observeAll(): Flow<List<ContentMapping>> = repository.observeAll()

    suspend fun automate(storyId: StoryId): ContentMappingSearchReport {
        val report = search.searchAll(storyId).withoutRejected(storyId)
        report.candidates
            .asSequence()
            .filter { candidate -> candidate.match.decision == ContentMatchDecision.AUTO_LINK }
            .distinctBy(ContentMappingCandidate::pluginId)
            .forEach { candidate -> saveAutomated(storyId, candidate) }
        return report
    }

    suspend fun searchForReview(storyId: StoryId): ContentMappingSearchReport =
        search.searchAll(storyId).withoutRejectedOrLinked(storyId)

    suspend fun resolveUrl(
        storyId: StoryId,
        url: String,
    ): ContentMappingSearchReport = search.resolveUrl(storyId, url).withoutRejectedOrLinked(storyId)

    suspend fun approve(
        storyId: StoryId,
        candidate: ContentMappingCandidate,
    ): ContentMappingWriteResult = saveProtected(storyId, candidate, ContentMappingOrigin.USER_APPROVED)

    suspend fun acceptUrl(
        storyId: StoryId,
        candidate: ContentMappingCandidate,
    ): ContentMappingWriteResult = saveProtected(storyId, candidate, ContentMappingOrigin.USER_URL)

    suspend fun reject(
        storyId: StoryId,
        candidate: ContentMappingCandidate,
    ) {
        repository.reject(
            ContentMappingRejection(
                storyId = storyId,
                pluginId = candidate.pluginId,
                sourceStoryId = candidate.sourceStoryId,
                policyVersion = candidate.match.policyVersion,
                rejectedAt = clock.nowEpochMillis(),
            ),
        )
    }

    private suspend fun saveAutomated(
        storyId: StoryId,
        candidate: ContentMappingCandidate,
    ): ContentMappingWriteResult = repository.compareAndWrite(
        mapping = candidate.toMapping(storyId, ContentMappingOrigin.AUTOMATED, clock.nowEpochMillis()),
        replaceableOrigins = setOf(ContentMappingOrigin.AUTOMATED),
    )

    private suspend fun saveProtected(
        storyId: StoryId,
        candidate: ContentMappingCandidate,
        origin: ContentMappingOrigin,
    ): ContentMappingWriteResult = repository.compareAndWrite(
        mapping = candidate.toMapping(storyId, origin, clock.nowEpochMillis()),
        replaceableOrigins = ContentMappingOrigin.entries.toSet(),
    )

    private suspend fun ContentMappingSearchReport.withoutRejectedOrLinked(
        storyId: StoryId,
    ): ContentMappingSearchReport {
        val linked = repository.observe(storyId).first()
            .mapTo(mutableSetOf()) { mapping -> mapping.pluginId to mapping.sourceStoryId }
        return copy(
            candidates = candidates.filterNot { candidate ->
                (candidate.pluginId to candidate.sourceStoryId) in linked
            },
        ).withoutRejected(storyId)
    }

    private suspend fun ContentMappingSearchReport.withoutRejected(
        storyId: StoryId,
    ): ContentMappingSearchReport = copy(
        candidates = candidates.filterNot { candidate ->
            repository.isRejected(
                storyId = storyId,
                pluginId = candidate.pluginId,
                sourceStoryId = candidate.sourceStoryId,
                policyVersion = candidate.match.policyVersion,
            )
        },
    )
}

private fun ContentMappingCandidate.toMapping(
    storyId: StoryId,
    origin: ContentMappingOrigin,
    updatedAt: Long,
) = ContentMapping(
    storyId = storyId,
    pluginId = pluginId,
    sourceStoryId = sourceStoryId,
    origin = origin,
    policyVersion = match.policyVersion,
    updatedAt = updatedAt,
)
