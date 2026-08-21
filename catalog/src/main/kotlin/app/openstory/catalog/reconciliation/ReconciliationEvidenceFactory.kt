package app.openstory.catalog.reconciliation

import app.openstory.catalog.evidence.CatalogEvidenceFingerprints
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.matching.TitleNormalizer
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType

object ReconciliationEvidenceFactory {
    fun fromRecord(record: CatalogSourceRecord): ReconciliationEvidence = ReconciliationEvidence(
        sourceKey = record.key,
        currentStoryId = record.storyId,
        contentType = record.entry.contentType,
        comparisonTitles = comparisonTitles(record.entry),
        comparisonAuthors = comparisonAuthors(record.entry),
        identifiers = record.entry.externalIdentifiers,
        lineageTokens = emptySet(),
        identityEvidenceFingerprint = record.identityFingerprint,
    )

    fun incoming(
        sourceKey: SourceKey,
        contentType: ContentType,
        titles: Set<String>,
        authors: Set<String>,
        identifiers: Set<ExternalIdentifier>,
        lineageTokens: Set<String> = emptySet(),
    ): ReconciliationEvidence {
        val normalizedTitles = normalize(titles)
        val normalizedAuthors = normalize(authors)
        val normalizedLineage = normalize(lineageTokens)
        val identityEntry = CatalogEntry(
            storyId = app.openstory.common.id.StoryId("incoming:evidence"),
            pluginId = sourceKey.pluginId,
            sourceId = sourceKey.sourceId,
            title = titles.firstOrNull { it.isNotBlank() } ?: normalizedTitles.firstOrNull() ?: "unknown",
            aliases = titles.drop(1).toSet(),
            authors = authors,
            contentType = contentType,
            externalIdentifiers = identifiers,
        )
        return ReconciliationEvidence(
            sourceKey = sourceKey,
            currentStoryId = null,
            contentType = contentType,
            comparisonTitles = normalizedTitles,
            comparisonAuthors = normalizedAuthors,
            identifiers = identifiers,
            lineageTokens = normalizedLineage,
            identityEvidenceFingerprint = CatalogEvidenceFingerprints.identity(identityEntry),
        )
    }

    private fun comparisonTitles(entry: CatalogEntry): Set<String> = normalize(setOf(entry.title) + entry.aliases)

    private fun comparisonAuthors(entry: CatalogEntry): Set<String> = normalize(entry.authors)

    private fun normalize(values: Collection<String>): Set<String> = values.asSequence()
        .map(TitleNormalizer::normalize)
        .filter(String::isNotBlank)
        .toSortedSet()
}
