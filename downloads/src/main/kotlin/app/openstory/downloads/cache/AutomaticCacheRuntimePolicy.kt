package app.openstory.downloads.cache

data class AutomaticCacheRuntimePolicy(
    val highWatermarkBasisPoints: Int = DEFAULT_HIGH_WATERMARK_BASIS_POINTS,
    val lowWatermarkBasisPoints: Int = DEFAULT_LOW_WATERMARK_BASIS_POINTS,
    val assetAccessTouchIntervalMillis: Long = DEFAULT_ASSET_ACCESS_TOUCH_INTERVAL_MILLIS,
    val maxEnospcEvictionVictims: Int = DEFAULT_MAX_ENOSPC_EVICTION_VICTIMS,
) {
    init {
        require(highWatermarkBasisPoints in 1..MAX_BASIS_POINTS)
        require(lowWatermarkBasisPoints in 0 until highWatermarkBasisPoints)
        require(assetAccessTouchIntervalMillis > 0L)
        require(maxEnospcEvictionVictims > 0)
    }
}

private const val MAX_BASIS_POINTS = 10_000
private const val DEFAULT_HIGH_WATERMARK_BASIS_POINTS = 10_000
private const val DEFAULT_LOW_WATERMARK_BASIS_POINTS = 9_000
private const val DEFAULT_ASSET_ACCESS_TOUCH_INTERVAL_MILLIS = 300_000L
private const val DEFAULT_MAX_ENOSPC_EVICTION_VICTIMS = 32
