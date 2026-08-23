package app.openstory.catalog.orchestration

/**
 * Recovers catalog changes that were committed transactionally with their canonical queue rows.
 * Implementations acknowledge only events whose queue representation is durable.
 */
interface CatalogChangeOutboxRepository {
    val persistsCatalogChanges: Boolean get() = false
    suspend fun materializePending(limit: Int): Int
}

object NoOpCatalogChangeOutboxRepository : CatalogChangeOutboxRepository {
    override suspend fun materializePending(limit: Int): Int {
        require(limit > 0)
        return 0
    }
}
