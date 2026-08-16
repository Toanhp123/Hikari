package app.openstory.catalog.matching

import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId

/**
 * Copyable in-memory index for one deterministic catalog projection pass.
 *
 * Forks isolate mutations so an invalid source page can be discarded atomically.
 * Direct source identity is O(1); evidence scoring is shortlisted by normalized
 * title tokens before the more expensive similarity comparison runs.
 */
class CatalogMatchIndex(
    private val matcher: StoryMatcher,
    candidates: List<CatalogMatchCandidate>,
) {
    private val candidatesByStory = linkedMapOf<StoryId, CatalogMatchCandidate>()
    private val preparedByStory = linkedMapOf<StoryId, List<PreparedCatalogMatchCandidate>>()
    private val storyBySource = hashMapOf<SourceKey, StoryId>()
    private val storyIdsByTitleToken = hashMapOf<String, MutableSet<StoryId>>()

    init {
        candidates.forEach(::registerMerged)
    }

    private constructor(
        matcher: StoryMatcher,
        candidatesByStory: LinkedHashMap<StoryId, CatalogMatchCandidate>,
        preparedByStory: LinkedHashMap<StoryId, List<PreparedCatalogMatchCandidate>>,
        storyBySource: HashMap<SourceKey, StoryId>,
        storyIdsByTitleToken: HashMap<String, MutableSet<StoryId>>,
    ) : this(matcher, emptyList()) {
        this.candidatesByStory.putAll(candidatesByStory)
        this.preparedByStory.putAll(preparedByStory)
        this.storyBySource.putAll(storyBySource)
        storyIdsByTitleToken.forEach { (token, storyIds) ->
            this.storyIdsByTitleToken[token] = LinkedHashSet(storyIds)
        }
    }

    fun resolve(source: CatalogMatchCandidate): StoryResolution {
        val directStoryId = source.sourceKeys.asSequence()
            .mapNotNull(storyBySource::get)
            .minByOrNull(StoryId::value)
        if (directStoryId != null) return StoryResolution.Existing(directStoryId)

        val preparedSource = matcher.prepare(source)
        return when (val resolution = matcher.resolvePrepared(
            preparedSource,
            evidenceCandidates(preparedSource),
            preparedByStory.keys.asSequence(),
        )) {
            is StoryResolution.Existing -> resolution
            is StoryResolution.Create -> resolution.also {
                val created = source.copy(story = resolution.story)
                register(created, matcher.prepare(created).matchingVariants())
            }
        }
    }

    fun story(storyId: StoryId): Story = candidatesByStory.getValue(storyId).story

    fun fork(): CatalogMatchIndex = CatalogMatchIndex(
        matcher = matcher,
        candidatesByStory = LinkedHashMap(candidatesByStory),
        preparedByStory = LinkedHashMap(preparedByStory),
        storyBySource = HashMap(storyBySource),
        storyIdsByTitleToken = HashMap(storyIdsByTitleToken),
    )

    private fun evidenceCandidates(source: PreparedCatalogMatchCandidate): Sequence<PreparedCatalogMatchCandidate> {
        if (!matcher.canSkipZeroTitleOverlap()) {
            return preparedByStory.values.asSequence().flatMap { it.asSequence() }
        }
        val storyIds = linkedSetOf<StoryId>()
        source.titleTokens.forEach { token ->
            storyIdsByTitleToken[token]?.let(storyIds::addAll)
        }
        return storyIds.asSequence().flatMap { storyId ->
            preparedByStory[storyId].orEmpty().asSequence()
        }
    }

    private fun registerMerged(candidate: CatalogMatchCandidate) {
        val existing = candidatesByStory[candidate.story.id]
        val merged = if (existing == null) {
            candidate
        } else {
            existing.copy(
                titles = existing.titles + candidate.titles,
                authors = existing.authors + candidate.authors,
                sourceKeys = existing.sourceKeys + candidate.sourceKeys,
                evidence = existing.evidence + candidate.evidence,
            )
        }
        register(merged, matcher.prepare(merged).matchingVariants())
    }

    private fun register(
        candidate: CatalogMatchCandidate,
        prepared: List<PreparedCatalogMatchCandidate>,
    ) {
        val previousPrepared = preparedByStory[candidate.story.id].orEmpty()
        previousPrepared.asSequence().flatMap { it.titleTokens.asSequence() }.distinct().forEach { token ->
            storyIdsByTitleToken[token]?.let { storyIds ->
                storyIds -= candidate.story.id
                if (storyIds.isEmpty()) storyIdsByTitleToken.remove(token)
            }
        }
        candidatesByStory[candidate.story.id] = candidate
        preparedByStory[candidate.story.id] = prepared
        candidate.sourceKeys.forEach { sourceKey ->
            val previous = storyBySource[sourceKey]
            if (previous == null || candidate.story.id.value < previous.value) {
                storyBySource[sourceKey] = candidate.story.id
            }
        }
        prepared.asSequence().flatMap { it.titleTokens.asSequence() }.distinct().forEach { token ->
            storyIdsByTitleToken.getOrPut(token) { linkedSetOf() } += candidate.story.id
        }
    }
}

private fun PreparedCatalogMatchCandidate.matchingVariants(): List<PreparedCatalogMatchCandidate> =
    if (evidence.size <= 1) {
        listOf(this)
    } else {
        evidence.map { variant ->
            copy(
                evidence = listOf(variant),
                titleTokens = variant.titles.asSequence().flatMap { it.tokens.asSequence() }.toSet(),
            )
        }
    }
