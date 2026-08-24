package app.openstory.catalog.evidence

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CatalogEvidenceFingerprintsTest {
    @Test
    fun identityFingerprintIgnoresPresentationOnlyChanges() {
        val base = entry(
            title = "Berserk",
            coverUrl = "https://example.test/a.jpg",
            score = Score(8.0, 10.0),
            latestUpdate = CatalogLatestUpdate(100L, "Ch. 1"),
        )
        val changed = base.copy(
            description = "Changed description",
            genres = setOf("Drama"),
            languageTags = setOf("ja"),
            coverUrl = "https://example.test/b.jpg",
            sourceUrl = "https://example.test/work",
            score = Score(9.0, 10.0),
            popularityRank = 1,
            publicationStatus = PublicationStatus.COMPLETED,
            latestUpdate = CatalogLatestUpdate(200L, "Ch. 2"),
        )

        assertEquals(
            CatalogEvidenceFingerprints.identity(base),
            CatalogEvidenceFingerprints.identity(changed),
        )
    }

    @Test
    fun identityFingerprintChangesForAliasAuthorTypeOrIdentifier() {
        val base = entry(title = "One")
        val identifier = ExternalIdentifier(
            namespace = "work",
            value = "123",
            scope = ExternalIdentifierScope.WORK,
        )

        assertNotEquals(
            CatalogEvidenceFingerprints.identity(base),
            CatalogEvidenceFingerprints.identity(base.copy(aliases = setOf("Uno"))),
        )
        assertNotEquals(
            CatalogEvidenceFingerprints.identity(base),
            CatalogEvidenceFingerprints.identity(base.copy(authors = setOf("Author"))),
        )
        assertNotEquals(
            CatalogEvidenceFingerprints.identity(base),
            CatalogEvidenceFingerprints.identity(base.copy(contentType = ContentType.LIGHT_NOVEL)),
        )
        assertNotEquals(
            CatalogEvidenceFingerprints.identity(base),
            CatalogEvidenceFingerprints.identity(base.copy(externalIdentifiers = setOf(identifier))),
        )
    }

    @Test
    fun identityFingerprintIsIndependentOfSetIterationOrderAndDisplayWhitespaceCase() {
        val first = entry(
            title = "  One   Piece  ",
            aliases = linkedSetOf("Wan Pisu", "ONE PIECE"),
            authors = linkedSetOf("Eiichiro Oda", "ODA  Eiichiro"),
            externalIdentifiers = linkedSetOf(
                ExternalIdentifier("work", "2", ExternalIdentifierScope.WORK),
                ExternalIdentifier("work", "1", ExternalIdentifierScope.WORK),
            ),
        )
        val second = first.copy(
            title = "one piece",
            aliases = linkedSetOf("one piece", "Wan   Pisu"),
            authors = linkedSetOf("oda eiichiro", "EIICHIRO ODA"),
            externalIdentifiers = linkedSetOf(
                ExternalIdentifier("work", "1", ExternalIdentifierScope.WORK),
                ExternalIdentifier("work", "2", ExternalIdentifierScope.WORK),
            ),
        )

        assertEquals(
            CatalogEvidenceFingerprints.identity(first),
            CatalogEvidenceFingerprints.identity(second),
        )
    }

    @Test
    fun fusionFingerprintChangesForPresentationAndMetadataProvenance() {
        val base = snapshot(entry(title = "One"))
        val presentationVariants = listOf(
            base.copy(entry = base.entry.copy(title = "ONE")),
            base.copy(entry = base.entry.copy(description = "Description")),
            base.copy(entry = base.entry.copy(coverUrl = "https://example.test/cover.jpg")),
            base.copy(entry = base.entry.copy(sourceUrl = "https://example.test/work")),
            base.copy(entry = base.entry.copy(aliases = setOf("Uno"))),
            base.copy(entry = base.entry.copy(authors = setOf("Author"))),
            base.copy(entry = base.entry.copy(genres = setOf("Drama"))),
            base.copy(entry = base.entry.copy(languageTags = setOf("en"))),
            base.copy(entry = base.entry.copy(score = Score(8.0, 10.0))),
            base.copy(entry = base.entry.copy(popularityRank = 4)),
            base.copy(entry = base.entry.copy(publicationStatus = PublicationStatus.ONGOING)),
            base.copy(entry = base.entry.copy(latestUpdate = CatalogLatestUpdate(100L, "Ch. 1"))),
            base.copy(summary = CatalogMetadataStamp("2.0.0", 10L)),
            base.copy(full = CatalogMetadataStamp("1.0.0", 20L)),
        )

        presentationVariants.forEach { changed ->
            assertNotEquals(
                CatalogEvidenceFingerprints.fusion(base),
                CatalogEvidenceFingerprints.fusion(changed),
            )
        }
    }

    @Test
    fun fusionFingerprintIsIndependentOfCollectionIterationOrder() {
        val first = snapshot(
            entry(
                title = "One",
                aliases = linkedSetOf("B", "A"),
                authors = linkedSetOf("Y", "X"),
                genres = linkedSetOf("Fantasy", "Action"),
                languageTags = linkedSetOf("ja", "en"),
            ),
        )
        val second = first.copy(
            entry = first.entry.copy(
                aliases = linkedSetOf("A", "B"),
                authors = linkedSetOf("X", "Y"),
                genres = linkedSetOf("Action", "Fantasy"),
                languageTags = linkedSetOf("en", "ja"),
            ),
        )

        assertEquals(
            CatalogEvidenceFingerprints.fusion(first),
            CatalogEvidenceFingerprints.fusion(second),
        )
    }

    @Test
    fun fusionFingerprintIgnoresRefreshTimestampsButRetainsMetadataLevelProvenance() {
        val base = snapshot(entry(title = "One")).copy(
            full = CatalogMetadataStamp("1.0.0", 20L),
        )
        val refreshed = base.copy(
            summary = base.summary.copy(resolvedAtEpochMillis = 100L),
            full = requireNotNull(base.full).copy(resolvedAtEpochMillis = 200L),
        )

        assertEquals(CatalogEvidenceFingerprints.fusion(base), CatalogEvidenceFingerprints.fusion(refreshed))
        assertNotEquals(
            CatalogEvidenceFingerprints.fusion(base),
            CatalogEvidenceFingerprints.fusion(base.copy(summary = CatalogMetadataStamp("2.0.0", 10L))),
        )
        assertNotEquals(
            CatalogEvidenceFingerprints.fusion(base),
            CatalogEvidenceFingerprints.fusion(base.copy(full = null)),
        )
    }

    @Test
    fun sourceRecordFactoryCarriesProvenanceAndComputedFingerprints() {
        val snapshot = snapshot(
            entry(
                title = "One",
                externalIdentifiers = setOf(
                    ExternalIdentifier("work", "123", ExternalIdentifierScope.WORK),
                ),
            ),
        ).copy(full = CatalogMetadataStamp("1.0.0", 20L))

        val record = snapshot.toSourceRecord()

        assertEquals(snapshot.entry, record.entry)
        assertEquals(snapshot.summary, record.summary)
        assertEquals(snapshot.full, record.full)
        assertEquals(CatalogEvidenceFingerprints.identity(snapshot.entry), record.identityFingerprint)
        assertEquals(CatalogEvidenceFingerprints.fusion(snapshot), record.fusionFingerprint)
    }

    private fun snapshot(entry: CatalogEntry) = CatalogMetadataSnapshot(
        entry = entry,
        summary = CatalogMetadataStamp("1.0.0", 10L),
        full = null,
    )

    private fun entry(
        title: String,
        aliases: Set<String> = emptySet(),
        authors: Set<String> = emptySet(),
        description: String? = null,
        genres: Set<String> = emptySet(),
        languageTags: Set<String> = emptySet(),
        coverUrl: String? = null,
        sourceUrl: String? = null,
        score: Score? = null,
        popularityRank: Long? = null,
        publicationStatus: PublicationStatus? = null,
        latestUpdate: CatalogLatestUpdate? = null,
        externalIdentifiers: Set<ExternalIdentifier> = emptySet(),
    ) = CatalogEntry(
        storyId = StoryId("story:1"),
        pluginId = PluginId("plugin:test"),
        sourceId = "source:1",
        title = title,
        aliases = aliases,
        authors = authors,
        description = description,
        genres = genres,
        contentType = ContentType.MANGA,
        languageTags = languageTags,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        score = score,
        popularityRank = popularityRank,
        publicationStatus = publicationStatus,
        latestUpdate = latestUpdate,
        externalIdentifiers = externalIdentifiers,
    )
}
