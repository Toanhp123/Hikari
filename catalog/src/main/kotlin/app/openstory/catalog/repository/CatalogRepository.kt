package app.openstory.catalog.repository

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.common.Outcome
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun observeHomes(): Flow<List<CatalogHomeSnapshot>>
    fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?>
    suspend fun matchSnapshot(): CatalogMatchSnapshot
    suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot?
    suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord?
    suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord>
    suspend fun sourceRecords(): List<CatalogSourceRecord>
    suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<Unit, CatalogStoreFailure>
    suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<StoryId, CatalogStoreFailure>
}
