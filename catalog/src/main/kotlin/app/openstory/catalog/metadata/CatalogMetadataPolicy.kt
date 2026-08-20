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
        require(level == CatalogMetadataLevel.Full) {
            "Only Full metadata has a Details TTL"
        }
        if (stamp.pluginVersion != currentPluginVersion) return false
        val age = (clock.nowEpochMillis() - stamp.resolvedAtEpochMillis).coerceAtLeast(0L)
        return age <= FULL_TTL_MILLIS
    }

    fun isRetryCooldownActive(recordedAtEpochMillis: Long): Boolean {
        val age = (clock.nowEpochMillis() - recordedAtEpochMillis).coerceAtLeast(0L)
        return age <= AUTO_RETRY_COOLDOWN_MILLIS
    }

    companion object {
        const val FULL_TTL_MILLIS = 86_400_000L
        const val AUTO_RETRY_COOLDOWN_MILLIS = 300_000L
    }
}
