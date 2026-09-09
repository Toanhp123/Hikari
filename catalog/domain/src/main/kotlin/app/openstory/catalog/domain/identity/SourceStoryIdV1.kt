package app.openstory.catalog.domain.identity

import app.openstory.catalog.domain.limits.domainSeparatedSha256
import app.openstory.common.id.StoryId

object SourceStoryIdV1 {
    fun derive(source: SourceStoryKey): StoryId {
        val sourceKeyBytes = CatalogIdentifierRules.requireValidSourceKey(source.catalogSourceKey.value)
        val sourceStoryIdBytes = CatalogIdentifierRules.requireValidSourceStoryId(source.sourceStoryId)
        val digest = domainSeparatedSha256(
            prefix = "hikari:v2:source-story:v1",
            fields = listOf(sourceKeyBytes, sourceStoryIdBytes),
        )
        return StoryId("source-story:v1:$digest")
    }
}
