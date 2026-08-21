package app.openstory.catalog.identity

import app.openstory.catalog.model.Story
import app.openstory.catalog.reconciliation.ReconciliationEvidence
import app.openstory.common.id.StoryId
import java.security.MessageDigest

class CatalogStoryIdFactory {
    fun create(
        evidence: ReconciliationEvidence,
        existingStoryIds: Set<StoryId>,
    ): Story {
        val semantic = listOf(
            evidence.contentType.name,
            evidence.comparisonTitles.sorted().joinToString("|"),
            evidence.comparisonAuthors.sorted().joinToString("|"),
            "${evidence.sourceKey.pluginId.value}:${evidence.sourceKey.sourceId}",
        ).joinToString("#")
        val base = "catalog:${digest(semantic)}"
        val id = generateSequence(1) { it + 1 }
            .map { suffix -> StoryId(if (suffix == 1) base else "$base:$suffix") }
            .first { candidate -> candidate !in existingStoryIds }
        return Story(id, evidence.contentType)
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .take(DIGEST_BYTES)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val DIGEST_BYTES = 8
    }
}
