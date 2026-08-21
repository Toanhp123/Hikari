package app.openstory.catalog.identity

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.reconciliation.ReconciliationEvidenceFactory
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogStoryIdFactoryTest {
    private val factory = CatalogStoryIdFactory()

    @Test
    fun preservesLegacySingleSourceSemanticId() {
        val evidence = ReconciliationEvidenceFactory.incoming(
            sourceKey = SourceKey(PluginId("p"), "s"),
            contentType = ContentType.MANGA,
            titles = linkedSetOf("Reborn", "Reborn!"),
            authors = linkedSetOf("Author B", "Author A"),
            identifiers = emptySet(),
        )

        val created = factory.create(evidence, emptySet())
        val expected = legacyId(
            ContentType.MANGA,
            evidence.comparisonTitles,
            evidence.comparisonAuthors,
            evidence.sourceKey,
        )

        assertEquals(expected, created.id)
    }

    @Test
    fun collisionUsesMonotonicNumericSuffix() {
        val evidence = ReconciliationEvidenceFactory.incoming(
            sourceKey = SourceKey(PluginId("p"), "s"),
            contentType = ContentType.MANGA,
            titles = setOf("Reborn"),
            authors = setOf("Author"),
            identifiers = emptySet(),
        )
        val base = factory.create(evidence, emptySet()).id

        assertEquals(
            StoryId("${base.value}:2"),
            factory.create(evidence, setOf(base)).id,
        )
        assertEquals(
            StoryId("${base.value}:3"),
            factory.create(evidence, setOf(base, StoryId("${base.value}:2"))).id,
        )
    }

    @Test
    fun providerInputOrderingDoesNotAlterId() {
        val sourceKey = SourceKey(PluginId("plugin"), "source")
        val first = ReconciliationEvidenceFactory.incoming(
            sourceKey = sourceKey,
            contentType = ContentType.MANGA,
            titles = linkedSetOf("Beta", "Alpha"),
            authors = linkedSetOf("Writer B", "Writer A"),
            identifiers = emptySet(),
        )
        val second = ReconciliationEvidenceFactory.incoming(
            sourceKey = sourceKey,
            contentType = ContentType.MANGA,
            titles = linkedSetOf("Alpha", "Beta"),
            authors = linkedSetOf("Writer A", "Writer B"),
            identifiers = emptySet(),
        )

        assertEquals(factory.create(first, emptySet()).id, factory.create(second, emptySet()).id)
    }

    private fun legacyId(
        contentType: ContentType,
        titles: Set<String>,
        authors: Set<String>,
        sourceKey: SourceKey,
    ): StoryId {
        val semantic = listOf(
            contentType.name,
            titles.sorted().joinToString("|"),
            authors.sorted().joinToString("|"),
            "${sourceKey.pluginId.value}:${sourceKey.sourceId}",
        ).joinToString("#")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(semantic.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
        return StoryId("catalog:$digest")
    }
}
