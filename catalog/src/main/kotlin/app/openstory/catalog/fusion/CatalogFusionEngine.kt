package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.evidence.CatalogEvidenceNormalizer
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.common.id.StoryId
import java.security.MessageDigest

private const val HYSTERESIS_COVERAGE_MARGIN = 2
private const val HEX_RADIX = 16
private const val UNSIGNED_BYTE_MASK = 0xFF

data class FusionInput(
    val story: Story,
    val sources: List<FusionSource>,
    val previousGeneration: CanonicalGeneration?,
    val preference: CanonicalSourcePreference,
    val evaluatedAtEpochMillis: Long,
) {
    init {
        require(preference.storyId == story.id)
        require(sources.all { it.record.storyId == story.id })
        require(evaluatedAtEpochMillis >= 0L)
    }
}

data class CanonicalGenerationCandidate(
    val storyId: StoryId,
    val fusionPolicyVersion: Int,
    val primarySelectionPolicyVersion: Int,
    val fusionFingerprint: String,
    val effectivePrimary: SourceKey,
    val metadata: CanonicalMetadata,
    val health: CanonicalHealth,
    val provenance: Map<CanonicalFieldKey, CanonicalFieldProvenance>,
    val createdAtEpochMillis: Long,
    val sourceContentTypes: Map<SourceKey, ContentType> = emptyMap(),
)

class CatalogFusionEngine {
    fun fuse(input: FusionInput): CanonicalGenerationCandidate {
        require(input.sources.isNotEmpty()) { "Canonical fusion requires at least one source" }
        val ranked = input.sources.sortedWith(primaryQualityComparator)
        val effectivePrimary = selectPinnedPrimary(input, ranked)
            ?: selectAutomaticPrimary(input.previousGeneration, ranked)
            ?: error("Canonical fusion requires at least one source")

        val provenance = linkedMapOf<CanonicalFieldKey, CanonicalFieldProvenance>()
        val title = requireNotNull(
            selectPrimaryScalar(
                field = CanonicalFieldKey.TITLE,
                ranked = ranked,
                effectivePrimary = effectivePrimary,
                value = { it.record.entry.title.takeIf(String::isNotBlank) },
            ),
        )
        provenance[title.provenance.field] = title.provenance
        val description = selectPrimaryScalar(
            CanonicalFieldKey.DESCRIPTION,
            ranked,
            effectivePrimary,
        ) { it.record.entry.description?.takeIf(String::isNotBlank) }
        val sourceUrl = selectPrimaryScalar(
            CanonicalFieldKey.SOURCE_URL,
            ranked,
            effectivePrimary,
        ) { it.record.entry.sourceUrl?.takeIf(String::isNotBlank) }
        val popularity = selectPrimaryScalar(
            CanonicalFieldKey.POPULARITY_RANK,
            ranked,
            effectivePrimary,
        ) { it.record.entry.popularityRank }
        val cover = selectCover(ranked, effectivePrimary)

        val aliases = unionTextCollection(
            field = CanonicalFieldKey.ALIASES,
            ranked = ranked,
            values = { source -> sequenceOf(source.record.entry.title) + source.record.entry.aliases.asSequence() },
            excludedNormalizedKey = CatalogEvidenceNormalizer.comparisonKey(title.value),
        )
        val authors = unionTextCollection(
            field = CanonicalFieldKey.AUTHORS,
            ranked = ranked,
            values = { it.record.entry.authors.asSequence() },
        )
        val genres = unionTextCollection(
            field = CanonicalFieldKey.GENRES,
            ranked = ranked,
            values = { it.record.entry.genres.asSequence() },
        )
        val languages = unionTextCollection(
            field = CanonicalFieldKey.LANGUAGE_TAGS,
            ranked = ranked,
            values = { it.record.entry.languageTags.asSequence() },
        )
        val status = selectPublicationStatus(ranked, effectivePrimary)
        val latest = selectLatestUpdate(ranked, effectivePrimary)
        val score = aggregateScore(ranked)

        listOf(description, sourceUrl, popularity, cover, aliases, authors, genres, languages, status, latest, score)
            .forEach { selection ->
                if (selection != null) provenance[selection.provenance.field] = selection.provenance
            }

        return CanonicalGenerationCandidate(
            storyId = input.story.id,
            fusionPolicyVersion = FUSION_POLICY_VERSION,
            primarySelectionPolicyVersion = PRIMARY_SELECTION_POLICY_VERSION,
            fusionFingerprint = combinedFusionFingerprint(ranked, effectivePrimary),
            effectivePrimary = effectivePrimary,
            metadata = CanonicalMetadata(
                title = title.value,
                description = description?.value,
                coverUrl = cover?.value,
                sourceUrl = sourceUrl?.value,
                popularityRank = popularity?.value,
                aliases = aliases?.value.orEmpty(),
                authors = authors?.value.orEmpty(),
                genres = genres?.value.orEmpty(),
                languageTags = languages?.value.orEmpty(),
                publicationStatus = status?.value,
                latestUpdate = latest?.value,
                score = score?.value,
            ),
            health = health(ranked),
            provenance = provenance.toMap(),
            createdAtEpochMillis = input.evaluatedAtEpochMillis,
            sourceContentTypes = ranked.associate { it.sourceKey to it.record.entry.contentType },
        )
    }

    private fun selectPinnedPrimary(input: FusionInput, ranked: List<FusionSource>): SourceKey? =
        input.preference
            .takeIf { it.mode == CanonicalSourcePreferenceMode.PINNED }
            ?.pinnedSource
            ?.let { pinned ->
                ranked.firstOrNull { source ->
                    source.sourceKey == pinned && source.isEffectivePrimaryEligible()
                }?.sourceKey
            }

    private fun selectAutomaticPrimary(
        previousGeneration: CanonicalGeneration?,
        ranked: List<FusionSource>,
    ): SourceKey? {
        val best = ranked.firstOrNull(FusionSource::isEffectivePrimaryEligible) ?: ranked.firstOrNull()
        val previousKey = previousGeneration?.effectivePrimary
        val current = previousKey?.let { key -> ranked.firstOrNull { it.sourceKey == key } }
        return when {
            best == null -> null
            previousKey == null || current == null -> best.sourceKey
            !current.isEffectivePrimaryEligible() -> best.sourceKey
            best.sourceKey == current.sourceKey -> current.sourceKey
            challengerMateriallyBetter(best, current) -> best.sourceKey
            else -> current.sourceKey
        }
    }

    private fun challengerMateriallyBetter(challenger: FusionSource, current: FusionSource): Boolean {
        val challengerQuality = challenger.primaryQuality()
        val currentQuality = current.primaryQuality()
        return when {
            challengerQuality.usability.rank() != currentQuality.usability.rank() ->
                challengerQuality.usability.rank() > currentQuality.usability.rank()

            challengerQuality.metadataLevel.rank() != currentQuality.metadataLevel.rank() ->
                challengerQuality.metadataLevel.rank() > currentQuality.metadataLevel.rank()

            challengerQuality.freshness.rank() > currentQuality.freshness.rank() &&
                challengerQuality.primaryFieldCoverage >= currentQuality.primaryFieldCoverage -> true

            challengerQuality.freshness != currentQuality.freshness -> false
            else -> challengerQuality.primaryFieldCoverage - currentQuality.primaryFieldCoverage >=
                HYSTERESIS_COVERAGE_MARGIN
        }
    }
}

private data class FieldSelection<T>(
    val value: T,
    val provenance: CanonicalFieldProvenance,
)

private fun <T> selectPrimaryScalar(
    field: CanonicalFieldKey,
    ranked: List<FusionSource>,
    effectivePrimary: SourceKey,
    value: (FusionSource) -> T?,
): FieldSelection<T>? {
    val ordered = ranked.sortedWith(
        compareByDescending<FusionSource> { it.sourceKey == effectivePrimary }
            .then(primaryQualityComparator),
    )
    val source = ordered.firstOrNull { value(it) != null } ?: return null
    return FieldSelection(
        value = requireNotNull(value(source)),
        provenance = provenance(
            field,
            CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
            listOf(source),
            if (source.sourceKey == effectivePrimary) "effective-primary" else "qualified-fallback",
        ),
    )
}

private fun selectCover(
    ranked: List<FusionSource>,
    effectivePrimary: SourceKey,
): FieldSelection<String>? = selectPrimaryScalar(
    CanonicalFieldKey.COVER_URL,
    ranked,
    effectivePrimary,
) { it.record.entry.coverUrl?.trim()?.takeIf(String::isNotEmpty) }

private fun unionTextCollection(
    field: CanonicalFieldKey,
    ranked: List<FusionSource>,
    values: (FusionSource) -> Sequence<String>,
    excludedNormalizedKey: String? = null,
): FieldSelection<List<String>>? {
    val candidates = ranked.flatMap { source ->
        values(source).mapNotNull { raw ->
            val display = raw.trim().replace(Regex("\\s+"), " ").takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val key = CatalogEvidenceNormalizer.comparisonKey(display)
            if (key == excludedNormalizedKey) null else TextContribution(key, display, source)
        }.toList()
    }
    if (candidates.isEmpty()) return null
    val grouped = candidates.groupBy(TextContribution::key)
    val selected = grouped.entries
        .sortedBy(Map.Entry<String, List<TextContribution>>::key)
        .map { (_, group) ->
            group.minWith(
                compareBy(
                    TextContribution::display,
                    { it.source.sourceKey.pluginId.value },
                    { it.source.sourceKey.sourceId },
                ),
            )
        }
    val contributors = candidates.map(TextContribution::source).distinctBy(FusionSource::sourceKey)
    return FieldSelection(
        value = selected.map(TextContribution::display),
        provenance = provenance(
            field,
            CanonicalFieldStrategy.NORMALIZED_UNION,
            contributors,
            "normalized-union",
        ),
    )
}

private data class TextContribution(
    val key: String,
    val display: String,
    val source: FusionSource,
)

private fun selectPublicationStatus(
    ranked: List<FusionSource>,
    effectivePrimary: SourceKey,
): FieldSelection<PublicationStatus>? {
    val source = ranked.filter { it.record.entry.publicationStatus != null }
        .sortedWith(
            compareByDescending<FusionSource> { it.isEffectivePrimaryEligible() }
                .thenByDescending { it.metadataLevel.rank() }
                .thenByDescending { it.freshness.rank() }
                .thenByDescending { it.sourceKey == effectivePrimary }
                .thenBy { it.sourceKey.pluginId.value }
                .thenBy { it.sourceKey.sourceId },
        )
        .firstOrNull() ?: return null
    return FieldSelection(
        requireNotNull(source.record.entry.publicationStatus),
        provenance(
            CanonicalFieldKey.PUBLICATION_STATUS,
            CanonicalFieldStrategy.FRESHEST_QUALIFIED_VALUE,
            listOf(source),
            "qualified-status",
        ),
    )
}

private fun selectLatestUpdate(
    ranked: List<FusionSource>,
    effectivePrimary: SourceKey,
): FieldSelection<CatalogLatestUpdate>? {
    val source = ranked.filter { it.record.entry.latestUpdate != null }
        .sortedWith(
            compareByDescending<FusionSource> { it.isEffectivePrimaryEligible() }
                .thenByDescending { requireNotNull(it.record.entry.latestUpdate).atEpochMillis }
                .thenByDescending { it.sourceKey == effectivePrimary }
                .thenBy { it.sourceKey.pluginId.value }
                .thenBy { it.sourceKey.sourceId },
        )
        .firstOrNull() ?: return null
    return FieldSelection(
        requireNotNull(source.record.entry.latestUpdate),
        provenance(
            CanonicalFieldKey.LATEST_UPDATE,
            CanonicalFieldStrategy.FRESHEST_COHERENT_OBJECT,
            listOf(source),
            "latest-coherent-object",
        ),
    )
}

private fun aggregateScore(ranked: List<FusionSource>): FieldSelection<CanonicalScore>? {
    val sources = ranked.filter { source ->
        source.isEffectivePrimaryEligible() && source.record.entry.score != null
    }
    if (sources.isEmpty()) return null
    val values = sources.map { source ->
        val score = requireNotNull(source.record.entry.score)
        score.value / score.scale
    }
    return FieldSelection(
        CanonicalScore(values.average(), values.size),
        provenance(
            CanonicalFieldKey.SCORE,
            CanonicalFieldStrategy.NORMALIZED_MEAN,
            sources,
            "unweighted-normalized-mean",
        ),
    )
}

private fun provenance(
    field: CanonicalFieldKey,
    strategy: CanonicalFieldStrategy,
    sources: List<FusionSource>,
    reason: String,
): CanonicalFieldProvenance = CanonicalFieldProvenance(
    field = field,
    strategy = strategy,
    contributors = sources.distinctBy(FusionSource::sourceKey).map { source ->
        CanonicalFieldContributor(
            sourceKey = source.sourceKey,
            fusionFingerprint = source.record.fusionFingerprint,
            metadataLevel = source.metadataLevel,
        )
    },
    reasonCodes = listOf(reason),
    policyVersion = FUSION_POLICY_VERSION,
)

private fun health(sources: List<FusionSource>): CanonicalHealth = when {
    sources.any { it.usability == CatalogSourceUsability.ACTIVE && it.freshness == CatalogSourceFreshness.FRESH } ->
        CanonicalHealth.FRESH
    sources.any(FusionSource::isEffectivePrimaryEligible) -> CanonicalHealth.STALE
    else -> CanonicalHealth.DEGRADED
}

private fun combinedFusionFingerprint(sources: List<FusionSource>, effectivePrimary: SourceKey): String {
    val canonicalSources = sources.sortedWith(compareBy({ it.sourceKey.pluginId.value }, { it.sourceKey.sourceId }))
        .joinToString(separator = "\n") {
            "${it.sourceKey.pluginId.value}:${it.sourceKey.sourceId}:${it.record.fusionFingerprint}:" +
                "${it.usability.name}:${it.freshness.name}"
        }
    val canonical = "primary=${effectivePrimary.pluginId.value}:${effectivePrimary.sourceId}\n$canonicalSources"
    return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        .joinToString("") { byte ->
            (byte.toInt() and UNSIGNED_BYTE_MASK).toString(HEX_RADIX).padStart(2, '0')
        }
}
