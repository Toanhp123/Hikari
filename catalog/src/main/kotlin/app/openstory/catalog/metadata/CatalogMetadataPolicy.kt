package app.openstory.catalog.metadata

import app.openstory.common.Clock
import javax.inject.Inject

class CatalogMetadataPolicy @Inject constructor(
    private val clock: Clock,
) {
    fun isFresh(
        level: CatalogMetadataLevel,
        stamp: CatalogMetadataStamp,
        currentPluginVersion: String,
    ): Boolean {
        require(level != CatalogMetadataLevel.Summary) {
            "Summary has no details TTL"
        }
        if (stamp.pluginVersion != currentPluginVersion) return false
        val age = (clock.nowEpochMillis() - stamp.resolvedAtEpochMillis).coerceAtLeast(0L)
        return age <= ttlMillis(level)
    }

    fun isRetryCooldownActive(recordedAtEpochMillis: Long): Boolean {
        val age = (clock.nowEpochMillis() - recordedAtEpochMillis).coerceAtLeast(0L)
        return age <= AUTO_RETRY_COOLDOWN_MILLIS
    }

    private fun ttlMillis(level: CatalogMetadataLevel): Long = when (level) {
        CatalogMetadataLevel.Artwork -> ARTWORK_TTL_MILLIS
        CatalogMetadataLevel.Full -> FULL_TTL_MILLIS
        CatalogMetadataLevel.Summary -> error("Summary has no details TTL")
    }

    companion object {
        const val ARTWORK_TTL_MILLIS = 604_800_000L
        const val FULL_TTL_MILLIS = 86_400_000L
        const val AUTO_RETRY_COOLDOWN_MILLIS = 300_000L
    }
}
