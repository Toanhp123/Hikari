package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.identity.SourceKey

private const val HYSTERESIS_COVERAGE_MARGIN = 2

enum class PrimarySelectionReason {
    PINNED,
    PIN_UNAVAILABLE_FALLBACK,
    INITIAL_BEST,
    PREVIOUS_MISSING,
    PREVIOUS_INELIGIBLE,
    BEST_UNCHANGED,
    CHALLENGER_MATERIALLY_BETTER,
    HYSTERESIS_RETAINED,
}

data class PrimarySelectionDecision(
    val selectedSource: SourceKey,
    val previousSource: SourceKey?,
    val challengerSource: SourceKey?,
    val reason: PrimarySelectionReason,
)

internal fun selectPrimary(
    input: FusionInput,
    ranked: List<FusionSource>,
): PrimarySelectionDecision? {
    val pinned = selectPinnedPrimary(input, ranked)
    val automatic = if (pinned == null) {
        selectAutomaticPrimary(input.previousGeneration, ranked)?.let { decision ->
            if (input.preference.mode == CanonicalSourcePreferenceMode.PINNED) {
                decision.copy(reason = PrimarySelectionReason.PIN_UNAVAILABLE_FALLBACK)
            } else {
                decision
            }
        }
    } else {
        null
    }
    return pinned ?: automatic
}

private fun selectPinnedPrimary(
    input: FusionInput,
    ranked: List<FusionSource>,
): PrimarySelectionDecision? {
    val pinned = input.preference
        .takeIf { it.mode == CanonicalSourcePreferenceMode.PINNED }
        ?.pinnedSource
    val selected = pinned?.let { pinnedSource ->
        ranked.firstOrNull { source ->
            source.sourceKey == pinnedSource && source.isEffectivePrimaryEligible()
        }
    }
    return selected?.let { selectedSource ->
        PrimarySelectionDecision(
            selectedSource = selectedSource.sourceKey,
            previousSource = input.previousGeneration?.effectivePrimary,
            challengerSource = ranked.firstOrNull { source ->
                source.sourceKey != selectedSource.sourceKey && source.isEffectivePrimaryEligible()
            }?.sourceKey,
            reason = PrimarySelectionReason.PINNED,
        )
    }
}

private fun selectAutomaticPrimary(
    previousGeneration: CanonicalGeneration?,
    ranked: List<FusionSource>,
): PrimarySelectionDecision? {
    val best = ranked.firstOrNull(FusionSource::isEffectivePrimaryEligible) ?: ranked.firstOrNull() ?: return null
    val previousKey = previousGeneration?.effectivePrimary
    val current = previousKey?.let { key -> ranked.firstOrNull { it.sourceKey == key } }
    val nextBest = ranked.firstOrNull { source ->
        source.sourceKey != best.sourceKey && source.isEffectivePrimaryEligible()
    }?.sourceKey
    return when {
        previousKey == null -> PrimarySelectionDecision(
            selectedSource = best.sourceKey,
            previousSource = null,
            challengerSource = null,
            reason = PrimarySelectionReason.INITIAL_BEST,
        )
        current == null -> PrimarySelectionDecision(
            selectedSource = best.sourceKey,
            previousSource = previousKey,
            challengerSource = best.sourceKey,
            reason = PrimarySelectionReason.PREVIOUS_MISSING,
        )
        !current.isEffectivePrimaryEligible() -> PrimarySelectionDecision(
            selectedSource = best.sourceKey,
            previousSource = previousKey,
            challengerSource = best.sourceKey,
            reason = PrimarySelectionReason.PREVIOUS_INELIGIBLE,
        )
        best.sourceKey == current.sourceKey -> PrimarySelectionDecision(
            selectedSource = current.sourceKey,
            previousSource = previousKey,
            challengerSource = nextBest,
            reason = PrimarySelectionReason.BEST_UNCHANGED,
        )
        challengerMateriallyBetter(best, current) -> PrimarySelectionDecision(
            selectedSource = best.sourceKey,
            previousSource = previousKey,
            challengerSource = best.sourceKey,
            reason = PrimarySelectionReason.CHALLENGER_MATERIALLY_BETTER,
        )
        else -> PrimarySelectionDecision(
            selectedSource = current.sourceKey,
            previousSource = previousKey,
            challengerSource = best.sourceKey,
            reason = PrimarySelectionReason.HYSTERESIS_RETAINED,
        )
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
