package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.model.Story
import app.openstory.catalog.identity.SourceKey

class CanonicalGenerationValidator {
    fun validate(
        story: Story,
        ownedSources: Set<SourceKey>,
        candidate: CanonicalGenerationCandidate,
    ): List<String> = buildList {
        if (candidate.storyId != story.id) add("story-id-mismatch")
        if (candidate.effectivePrimary !in ownedSources) add("effective-primary-not-owned")
        if (candidate.provenance.values.any { provenance ->
                provenance.contributors.any { it.sourceKey !in ownedSources }
            }
        ) {
            add("provenance-contributor-not-owned")
        }
        if (candidate.provenance[CanonicalFieldKey.LATEST_UPDATE]?.contributors?.size?.let { it > 1 } == true) {
            add("latest-update-must-have-one-contributor")
        }
        if (candidate.sourceContentTypes.values.any { it != story.contentType }) {
            add("content-type-contradiction")
        }
        if (candidate.metadata.score?.isInvalid() == true) {
            add("canonical-score-invalid")
        }
        if (candidate.provenance[CanonicalFieldKey.TITLE] == null) add("title-provenance-missing")
    }
}

private fun CanonicalScore.isInvalid(): Boolean =
    !normalizedValue.isFinite() ||
        normalizedValue !in 0.0..1.0 ||
        contributorCount <= 0
