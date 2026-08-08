package app.openstory.home.domain

import app.openstory.database.repository.CatalogRepository
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogHomeSnapshot
import kotlinx.coroutines.flow.first

fun interface CanonicalStoryCandidates {
    suspend fun load(): List<CanonicalStory>
}

internal class CachedHomeCanonicalCandidates(
    private val repository: CatalogRepository,
) : CanonicalStoryCandidates {
    override suspend fun load(): List<CanonicalStory> = repository.observeCatalogHomes()
        .first()
        .toCanonicalCandidates()
}

private fun List<CatalogHomeSnapshot>.toCanonicalCandidates(): List<CanonicalStory> = asSequence()
    .flatMap { snapshot -> snapshot.sections.asSequence() }
    .flatMap { section -> section.items.asSequence() }
    .groupBy { item -> item.storyId }
    .map { (storyId, items) ->
        val entries = items
            .map { item -> item.entry }
            .distinctBy { entry -> entry.catalogPluginId to entry.externalStoryId }
            .sortedWith(
                compareBy<CatalogEntry> { entry -> entry.catalogPluginId.value }
                    .thenBy(CatalogEntry::externalStoryId),
            )
        val primary = entries.first()
        CanonicalStory(
            id = storyId,
            contentType = primary.contentType,
            preferredTitle = primary.title,
            aliases = entries.flatMap(CatalogEntry::aliases).toSet(),
            catalogEntries = entries,
        )
    }
    .sortedBy { candidate -> candidate.id.value }
