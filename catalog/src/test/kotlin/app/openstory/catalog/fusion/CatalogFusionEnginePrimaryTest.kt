package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogFusionEnginePrimaryTest {
    private val storyId = StoryId("story:1")

    @Test
    fun initialSelectionUsesHighestQualityAndIsProviderOrderInvariant() {
        val summary = source("provider.a", full = false)
        val full = source("provider.b", full = true)
        val engine = CatalogFusionEngine()

        assertEquals(full.sourceKey, engine.fuse(input(listOf(summary, full))).effectivePrimary)
        assertEquals(full.sourceKey, engine.fuse(input(listOf(full, summary))).effectivePrimary)
    }

    @Test
    fun equalQualityUsesStableSourceKeyAscending() {
        val b = source("provider.b")
        val a = source("provider.a")
        assertEquals(a.sourceKey, CatalogFusionEngine().fuse(input(listOf(b, a))).effectivePrimary)
    }

    @Test
    fun hysteresisSwitchesForEachMaterialImprovementRule() {
        val current = source("provider.current", coverage = 3)
        val cases = listOf(
            current.copy(usability = CatalogSourceUsability.STALE) to source("provider.challenger", coverage = 3),
            source("provider.current", full = false, coverage = 3) to source("provider.challenger", full = true, coverage = 3),
            source("provider.current", freshness = CatalogSourceFreshness.STALE, coverage = 3) to
                source("provider.challenger", freshness = CatalogSourceFreshness.FRESH, coverage = 3),
            source("provider.current", coverage = 3) to source("provider.challenger", coverage = 5),
            source("provider.current", usability = CatalogSourceUsability.UNAVAILABLE, coverage = 9) to
                source("provider.challenger", coverage = 1),
        )
        cases.forEach { (old, challenger) ->
            val candidate = CatalogFusionEngine().fuse(
                input(
                    sources = listOf(old, challenger),
                    previous = generation(old.sourceKey),
                ),
            )
            assertEquals(challenger.sourceKey, candidate.effectivePrimary)
        }
    }

    @Test
    fun hysteresisKeepsCurrentForMarginalOrTieBreakOnlyAdvantages() {
        val current = source("provider.z", coverage = 3)
        val oneMore = source("provider.a", coverage = 4)
        val fresherButLowerCoverage = source(
            "provider.a",
            freshness = CatalogSourceFreshness.FRESH,
            coverage = 2,
        )
        val staleCurrent = current.copy(freshness = CatalogSourceFreshness.STALE)
        val tiePreferredByKey = source("provider.a", coverage = 3)

        assertEquals(
            current.sourceKey,
            CatalogFusionEngine().fuse(input(listOf(current, oneMore), generation(current.sourceKey))).effectivePrimary,
        )
        assertEquals(
            staleCurrent.sourceKey,
            CatalogFusionEngine().fuse(
                input(listOf(staleCurrent, fresherButLowerCoverage), generation(staleCurrent.sourceKey)),
            ).effectivePrimary,
        )
        assertEquals(
            current.sourceKey,
            CatalogFusionEngine().fuse(
                input(listOf(current, tiePreferredByKey), generation(current.sourceKey)),
            ).effectivePrimary,
        )
    }

    @Test
    fun rankedEligibleSourceKeysUsesSameEffectivePrimaryAndExcludesUnavailableSources() {
        val current = source("provider.z", coverage = 3)
        val marginallyBetter = source("provider.a", coverage = 4)
        val unavailable = source("provider.best", coverage = 9, usability = CatalogSourceUsability.UNAVAILABLE)
        val input = input(
            sources = listOf(marginallyBetter, unavailable, current),
            previous = generation(current.sourceKey),
        )

        assertEquals(
            listOf(current.sourceKey, marginallyBetter.sourceKey),
            CatalogFusionEngine().rankedEligibleSourceKeys(input),
        )
    }

    @Test
    fun pinUsesUsableSourceFallsBackWithoutDeletingPinAndReturnsWhenUsableAgain() {
        val automatic = source("provider.auto", coverage = 8)
        val pinned = source("provider.pin", coverage = 1)
        val preference = CanonicalSourcePreference(
            storyId,
            CanonicalSourcePreferenceMode.PINNED,
            pinned.sourceKey,
            revision = 2,
        )
        val engine = CatalogFusionEngine()

        assertEquals(pinned.sourceKey, engine.fuse(input(listOf(automatic, pinned), preference = preference)).effectivePrimary)
        assertEquals(
            automatic.sourceKey,
            engine.fuse(
                input(
                    listOf(automatic, pinned.copy(usability = CatalogSourceUsability.UNAVAILABLE)),
                    preference = preference,
                ),
            ).effectivePrimary,
        )
        assertEquals(pinned.sourceKey, engine.fuse(input(listOf(automatic, pinned), preference = preference)).effectivePrimary)
        assertEquals(pinned.sourceKey, preference.pinnedSource)
    }

    private fun input(
        sources: List<FusionSource>,
        previous: CanonicalGeneration? = null,
        preference: CanonicalSourcePreference = CanonicalSourcePreference(
            storyId,
            CanonicalSourcePreferenceMode.AUTO,
            null,
            0,
        ),
    ) = FusionInput(
        story = Story(storyId, ContentType.MANGA),
        sources = sources,
        previousGeneration = previous,
        preference = preference,
        evaluatedAtEpochMillis = 100L,
    )

    private fun generation(primary: SourceKey) = CanonicalGeneration(
        id = "gen:old",
        storyId = storyId,
        fusionPolicyVersion = FUSION_POLICY_VERSION,
        primarySelectionPolicyVersion = PRIMARY_SELECTION_POLICY_VERSION,
        fusionFingerprint = "old-fingerprint",
        effectivePrimary = primary,
        metadata = CanonicalMetadata(
            title = "Old",
            description = null,
            coverUrl = null,
            sourceUrl = null,
            popularityRank = null,
            aliases = emptyList(),
            authors = emptyList(),
            genres = emptyList(),
            languageTags = emptyList(),
            publicationStatus = null,
            latestUpdate = null,
            score = null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = emptyMap(),
        createdAtEpochMillis = 50L,
    )

    private fun source(
        plugin: String,
        full: Boolean = true,
        freshness: CatalogSourceFreshness = CatalogSourceFreshness.FRESH,
        usability: CatalogSourceUsability = CatalogSourceUsability.ACTIVE,
        coverage: Int = 0,
    ): FusionSource {
        val key = SourceKey(PluginId(plugin), "source")
        val optional = (1..coverage).toSet()
        val entry = CatalogEntry(
            storyId = storyId,
            pluginId = key.pluginId,
            sourceId = key.sourceId,
            title = "Title $plugin",
            aliases = if (1 in optional) setOf("Alias") else emptySet(),
            authors = if (2 in optional) setOf("Author") else emptySet(),
            description = if (3 in optional) "Description" else null,
            genres = if (4 in optional) setOf("Genre") else emptySet(),
            contentType = ContentType.MANGA,
            coverUrl = if (5 in optional) "https://example.test/$plugin.jpg" else null,
            sourceUrl = if (6 in optional) "https://example.test/$plugin" else null,
            latestUpdate = if (7 in optional) {
                app.openstory.catalog.model.CatalogLatestUpdate(123L, "Ch. 7")
            } else {
                null
            },
            publicationStatus = if (8 in optional) app.openstory.catalog.model.PublicationStatus.ONGOING else null,
            score = if (9 in optional) app.openstory.catalog.model.Score(8.0, 10.0) else null,
        )
        return FusionSource(
            CatalogSourceRecord(
                key,
                storyId,
                entry,
                CatalogMetadataStamp("1", 1L),
                if (full) CatalogMetadataStamp("1", 2L) else null,
                "identity:$plugin",
                "fusion:$plugin",
            ),
            usability,
            freshness,
        )
    }
}
