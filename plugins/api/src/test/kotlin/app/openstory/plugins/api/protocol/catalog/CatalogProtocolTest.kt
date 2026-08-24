package app.openstory.plugins.api.protocol.catalog

import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.PluginProtocolValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogProtocolTest {
    private val json = Json

    @Test
    fun catalogSectionRejectsDuplicateSourceIds() {
        val item = CatalogItemDto(sourceId = "1", title = "One", contentType = WireContentType.MANGA)
        assertFailsWith<IllegalArgumentException> {
            CatalogSectionDto("top", "Top", listOf(item, item))
        }
    }

    @Test
    fun scoreMustCarryPositiveScale() {
        assertFailsWith<IllegalArgumentException> { ScoreDto(value = 8.0, scale = 0.0) }
    }

    @Test
    fun oldHomeSectionDefaultsToOtherAndMissingMetadata() {
        val section = json.decodeFromString<CatalogSectionDto>(
            """{"sourceId":"top","title":"Top","items":[{"sourceId":"1","title":"One","contentType":"MANGA"}]}""",
        )

        assertEquals(WireCatalogFeedKind.OTHER, section.kind)
        assertTrue(section.items.single().genres.isEmpty())
        assertNull(section.items.single().popularityRank)
        assertNull(section.items.single().publicationStatus)
        assertNull(section.items.single().latestUpdate)
    }

    @Test
    fun externalIdentifierRequiresBoundedStableNamespaceAndValue() {
        assertFailsWith<IllegalArgumentException> {
            CatalogExternalIdentifierDto(
                namespace = " ",
                value = "123",
                scope = WireCatalogIdentifierScope.WORK,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogExternalIdentifierDto(
                namespace = "isbn",
                value = " ",
                scope = WireCatalogIdentifierScope.EDITION,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogExternalIdentifierDto(
                namespace = "n".repeat(129),
                value = "123",
                scope = WireCatalogIdentifierScope.WORK,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogExternalIdentifierDto(
                namespace = "isbn",
                value = "v".repeat(257),
                scope = WireCatalogIdentifierScope.EDITION,
            )
        }
    }

    @Test
    fun catalogItemCarriesAllExternalIdentifierScopesAndRoundTrips() {
        val identifiers = setOf(
            CatalogExternalIdentifierDto("work", "w1", WireCatalogIdentifierScope.WORK),
            CatalogExternalIdentifierDto("publication", "p1", WireCatalogIdentifierScope.PUBLICATION),
            CatalogExternalIdentifierDto("edition", "e1", WireCatalogIdentifierScope.EDITION),
            CatalogExternalIdentifierDto("provider", "r1", WireCatalogIdentifierScope.PROVIDER_RECORD),
        )
        val item = CatalogItemDto(
            sourceId = "1",
            title = "One",
            contentType = WireContentType.MANGA,
            externalIdentifiers = identifiers,
        )

        assertEquals(identifiers, item.externalIdentifiers)
        assertEquals(item, json.decodeFromString(json.encodeToString(item)))
    }

    @Test
    fun catalogPayloadsDefaultExternalIdentifiersToEmpty() {
        val item = json.decodeFromString<CatalogItemDto>(
            """{"sourceId":"1","title":"One","contentType":"MANGA"}""",
        )

        assertTrue(item.externalIdentifiers.isEmpty())
    }

    @Test
    fun catalogItemRejectsMoreThanThirtyTwoExternalIdentifiers() {
        val identifiers = (1..33).map { index ->
            CatalogExternalIdentifierDto(
                namespace = "work",
                value = "id-$index",
                scope = WireCatalogIdentifierScope.WORK,
            )
        }.toSet()

        assertFailsWith<IllegalArgumentException> {
            CatalogItemDto(
                sourceId = "1",
                title = "One",
                contentType = WireContentType.MANGA,
                externalIdentifiers = identifiers,
            )
        }
    }

    @Test
    fun richHomeMetadataRoundTrips() {
        val item = CatalogItemDto(
            sourceId = "manga-1",
            title = "Manga One",
            contentType = WireContentType.MANGA,
            genres = setOf("Action", "Fantasy"),
            popularityRank = 3,
            publicationStatus = WirePublicationStatus.ONGOING,
            latestUpdate = CatalogLatestUpdateDto(1234L, "128"),
        )
        val section = CatalogSectionDto(
            sourceId = "popular",
            title = "Popular",
            items = listOf(item),
            kind = WireCatalogFeedKind.POPULAR,
        )

        assertEquals(section, json.decodeFromString(json.encodeToString(section)))
    }

    @Test
    fun latestUpdateReleaseLabelIsOpaqueCompleteText() {
        val dto = CatalogLatestUpdateDto(
            atEpochMillis = 1234L,
            releaseLabel = "Vol. 4 Ch. 56",
        )

        assertEquals("Vol. 4 Ch. 56", dto.releaseLabel)
    }

    @Test
    fun latestUpdateRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> { CatalogLatestUpdateDto(-1L, "128") }
        assertFailsWith<IllegalArgumentException> { CatalogLatestUpdateDto(1L, " ") }
    }

    @Test
    fun validatorRejectsReturnedUrlOutsideDeclaredHosts() {
        val output = CatalogSearchOutputDto(
            items = listOf(
                CatalogItemDto(
                    sourceId = "1",
                    title = "One",
                    contentType = WireContentType.MANGA,
                    coverUrl = "https://cdn.example/one.jpg",
                ),
            ),
        )
        val payload = Json.parseToJsonElement(Json.encodeToString(output))

        assertEquals(
            listOf("protocol.remote_host_denied"),
            PluginProtocolValidator.validateOutput(
                PluginOperation.CATALOG_SEARCH,
                payload,
                allowedNetworkHosts = setOf("api.example"),
            ).map { it.code },
        )
    }
}
