package app.openstory.artwork.cache

import coil3.memory.MemoryCache

interface ArtworkDecodedMemoryCache {
    val coilMemoryCache: MemoryCache
    val maxSizeBytes: Long

    fun clear()
}

internal class CoilArtworkDecodedMemoryCache(
    override val coilMemoryCache: MemoryCache,
) : ArtworkDecodedMemoryCache {
    override val maxSizeBytes: Long
        get() = coilMemoryCache.maxSize

    override fun clear() = coilMemoryCache.clear()
}
