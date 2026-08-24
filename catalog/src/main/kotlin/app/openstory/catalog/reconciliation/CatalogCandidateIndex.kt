package app.openstory.catalog.reconciliation

import app.openstory.catalog.evidence.CatalogEvidenceNormalizer
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.matching.TitleNormalizer
import app.openstory.common.id.StoryId

interface CatalogCandidateIndex {
    fun rebuild(records: Collection<ReconciliationEvidence>)
    fun upsert(record: ReconciliationEvidence)
    fun remove(sourceKey: SourceKey)
    fun candidatesFor(incoming: ReconciliationEvidence): List<StoryId>
}

class InMemoryCatalogCandidateIndex : CatalogCandidateIndex {
    private val recordsBySource = linkedMapOf<SourceKey, ReconciliationEvidence>()
    private val contributionsBySource = mutableMapOf<SourceKey, IndexContribution>()
    private val sourcesByStory = mutableMapOf<StoryId, MutableSet<SourceKey>>()
    private val workIdentifierSources = mutableMapOf<ExternalIdentifier, MutableSet<SourceKey>>()
    private val titleTokenSources = mutableMapOf<String, MutableSet<SourceKey>>()
    private val authorSources = mutableMapOf<String, MutableSet<SourceKey>>()

    override fun rebuild(records: Collection<ReconciliationEvidence>) {
        recordsBySource.clear()
        contributionsBySource.clear()
        sourcesByStory.clear()
        workIdentifierSources.clear()
        titleTokenSources.clear()
        authorSources.clear()
        records.sortedBy { it.sourceKey.stableKey() }.forEach(::upsert)
    }

    override fun upsert(record: ReconciliationEvidence) {
        remove(record.sourceKey)
        recordsBySource[record.sourceKey] = record
        val storyId = record.currentStoryId ?: return
        val contribution = IndexContribution(
            storyId = storyId,
            workIdentifiers = workIdentifiers(record),
            titleTokens = titleTokens(record),
            authors = normalizedAuthors(record),
        )
        contributionsBySource[record.sourceKey] = contribution
        sourcesByStory.getOrPut(storyId) { linkedSetOf() }.add(record.sourceKey)
        contribution.workIdentifiers.forEach { workIdentifierSources.add(it, record.sourceKey) }
        contribution.titleTokens.forEach { titleTokenSources.add(it, record.sourceKey) }
        contribution.authors.forEach { authorSources.add(it, record.sourceKey) }
    }

    override fun remove(sourceKey: SourceKey) {
        recordsBySource.remove(sourceKey)
        val contribution = contributionsBySource.remove(sourceKey) ?: return
        sourcesByStory.removeFromBucket(contribution.storyId, sourceKey)
        contribution.workIdentifiers.forEach { workIdentifierSources.removeFromBucket(it, sourceKey) }
        contribution.titleTokens.forEach { titleTokenSources.removeFromBucket(it, sourceKey) }
        contribution.authors.forEach { authorSources.removeFromBucket(it, sourceKey) }
    }

    override fun candidatesFor(incoming: ReconciliationEvidence): List<StoryId> {
        val strengthByStory = mutableMapOf<StoryId, Int>()
        workIdentifiers(incoming).forEach { identifier ->
            workIdentifierSources[identifier].orEmpty().forEach { sourceKey ->
                strengthByStory.record(sourceKey, WORK_IDENTIFIER_STRENGTH)
            }
        }
        titleTokens(incoming).forEach { token ->
            titleTokenSources[token].orEmpty().forEach { sourceKey ->
                strengthByStory.record(sourceKey, TITLE_TOKEN_STRENGTH)
            }
        }
        normalizedAuthors(incoming).forEach { author ->
            authorSources[author].orEmpty().forEach { sourceKey ->
                strengthByStory.record(sourceKey, AUTHOR_STRENGTH)
            }
        }
        return strengthByStory.entries
            .sortedWith(compareByDescending<Map.Entry<StoryId, Int>> { it.value }.thenBy { it.key.value })
            .map(Map.Entry<StoryId, Int>::key)
    }

    fun evidenceFor(storyIds: Set<StoryId>): List<ReconciliationEvidence> = storyIds.asSequence()
        .sortedBy(StoryId::value)
        .flatMap { storyId -> sourcesByStory[storyId].orEmpty().asSequence() }
        .distinct()
        .sortedBy { it.stableKey() }
        .mapNotNull(recordsBySource::get)
        .toList()

    private fun MutableMap<StoryId, Int>.record(sourceKey: SourceKey, strength: Int) {
        val storyId = contributionsBySource[sourceKey]?.storyId ?: return
        this[storyId] = maxOf(this[storyId] ?: 0, strength)
    }

    private fun workIdentifiers(evidence: ReconciliationEvidence): Set<ExternalIdentifier> = evidence.identifiers
        .filterTo(linkedSetOf()) { it.scope == ExternalIdentifierScope.WORK }

    private fun titleTokens(evidence: ReconciliationEvidence): Set<String> = evidence.comparisonTitles.asSequence()
        .flatMap { title -> TitleNormalizer.tokensOfNormalized(title).asSequence() }
        .toSet()

    private fun normalizedAuthors(evidence: ReconciliationEvidence): Set<String> =
        evidence.comparisonAuthors.asSequence()
            .map(CatalogEvidenceNormalizer::comparisonKey)
            .filter(String::isNotBlank)
            .toSet()

    private fun <K> MutableMap<K, MutableSet<SourceKey>>.add(key: K, sourceKey: SourceKey) {
        getOrPut(key) { linkedSetOf() }.add(sourceKey)
    }

    private fun <K> MutableMap<K, MutableSet<SourceKey>>.removeFromBucket(key: K, sourceKey: SourceKey) {
        val bucket = this[key] ?: return
        bucket.remove(sourceKey)
        if (bucket.isEmpty()) remove(key)
    }

    private fun SourceKey.stableKey(): String = "${pluginId.value}:$sourceId"

    private data class IndexContribution(
        val storyId: StoryId,
        val workIdentifiers: Set<ExternalIdentifier>,
        val titleTokens: Set<String>,
        val authors: Set<String>,
    )

    private companion object {
        const val WORK_IDENTIFIER_STRENGTH = 3
        const val TITLE_TOKEN_STRENGTH = 2
        const val AUTHOR_STRENGTH = 1
    }
}
