package app.openstory.catalog.reconciliation

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

    override fun rebuild(records: Collection<ReconciliationEvidence>) {
        recordsBySource.clear()
        records.sortedBy { it.sourceKey.stableKey() }.forEach { record ->
            recordsBySource[record.sourceKey] = record
        }
    }

    override fun upsert(record: ReconciliationEvidence) {
        recordsBySource[record.sourceKey] = record
    }

    override fun remove(sourceKey: SourceKey) {
        recordsBySource.remove(sourceKey)
    }

    override fun candidatesFor(incoming: ReconciliationEvidence): List<StoryId> = recordsBySource.values
        .asSequence()
        .mapNotNull { record ->
            val storyId = record.currentStoryId ?: return@mapNotNull null
            retrievalStrength(incoming, record).takeIf { it > 0 }?.let { strength -> storyId to strength }
        }
        .groupBy(Pair<StoryId, Int>::first)
        .mapValues { (_, hits) -> hits.maxOf(Pair<StoryId, Int>::second) }
        .entries
        .sortedWith(compareByDescending<Map.Entry<StoryId, Int>> { it.value }.thenBy { it.key.value })
        .map(Map.Entry<StoryId, Int>::key)

    private fun retrievalStrength(left: ReconciliationEvidence, right: ReconciliationEvidence): Int = when {
        sharesWorkIdentifier(left, right) -> WORK_IDENTIFIER_STRENGTH
        titleTokens(left).intersect(titleTokens(right)).isNotEmpty() -> TITLE_TOKEN_STRENGTH
        left.comparisonAuthors.intersect(right.comparisonAuthors).isNotEmpty() -> AUTHOR_STRENGTH
        else -> 0
    }

    private fun sharesWorkIdentifier(left: ReconciliationEvidence, right: ReconciliationEvidence): Boolean {
        val leftWork = left.identifiers.filterTo(hashSetOf()) { it.scope == ExternalIdentifierScope.WORK }
        val rightWork = right.identifiers.filterTo(hashSetOf()) { it.scope == ExternalIdentifierScope.WORK }
        return leftWork.intersect(rightWork).isNotEmpty()
    }

    private fun titleTokens(evidence: ReconciliationEvidence): Set<String> = evidence.comparisonTitles.asSequence()
        .flatMap { title -> TitleNormalizer.tokensOfNormalized(title).asSequence() }
        .toSet()

    private fun SourceKey.stableKey(): String = "${pluginId.value}:$sourceId"

    private companion object {
        const val WORK_IDENTIFIER_STRENGTH = 3
        const val TITLE_TOKEN_STRENGTH = 2
        const val AUTHOR_STRENGTH = 1
    }
}
