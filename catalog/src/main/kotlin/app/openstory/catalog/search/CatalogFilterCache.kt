package app.openstory.catalog.search

import app.openstory.common.id.PluginId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogFilterCache @Inject constructor() {
    private val groups = linkedMapOf<CatalogFilterCacheKey, CatalogSearchFilterGroup>()

    @Synchronized
    fun get(key: CatalogFilterCacheKey): CatalogSearchFilterGroup? = groups[key]

    @Synchronized
    fun put(key: CatalogFilterCacheKey, group: CatalogSearchFilterGroup) {
        groups[key] = group
    }

    @Synchronized
    fun retainEnabled(keys: Set<CatalogFilterCacheKey>) {
        groups.keys.retainAll(keys)
    }
}

data class CatalogFilterCacheKey(
    val pluginId: PluginId,
    val version: String,
)
