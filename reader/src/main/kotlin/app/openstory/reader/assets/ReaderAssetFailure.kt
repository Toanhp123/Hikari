package app.openstory.reader.assets

sealed interface ReaderAssetFailure {
    data class TransportUnavailable(val retryable: Boolean) : ReaderAssetFailure
    data class DeliveryRejected(val httpStatus: Int) : ReaderAssetFailure {
        init {
            require(httpStatus in HTTP_STATUS_RANGE) { "Invalid HTTP status: $httpStatus" }
        }
    }

    data object DeliveryLocatorStale : ReaderAssetFailure
    data object Unauthorized : ReaderAssetFailure
    data object AssetNotFound : ReaderAssetFailure
    data object AssetTooLarge : ReaderAssetFailure
    data object InvalidPayload : ReaderAssetFailure
    data object CacheCorrupt : ReaderAssetFailure
    data object CacheStorageUnavailable : ReaderAssetFailure
    data object Cancelled : ReaderAssetFailure
    data object Preempted : ReaderAssetFailure
    data object Superseded : ReaderAssetFailure
    data object RouteInvalidated : ReaderAssetFailure
}

private val HTTP_STATUS_RANGE = 100..599
