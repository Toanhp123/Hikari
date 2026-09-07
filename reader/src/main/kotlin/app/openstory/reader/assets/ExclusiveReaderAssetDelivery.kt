package app.openstory.reader.assets

interface ReaderAssetDeliverySource : ReaderAssetDeliveryPort {
    val id: String
    fun matches(request: ReaderAssetDeliveryRequest): Boolean
}

class ExclusiveReaderAssetDelivery(
    private val fallback: ReaderAssetDeliveryPort,
    exclusive: Set<ReaderAssetDeliverySource>,
) : ReaderAssetDeliveryPort {
    private val exclusive = exclusive.sortedBy(ReaderAssetDeliverySource::id)

    override suspend fun fetch(request: ReaderAssetDeliveryRequest): ReaderAssetDeliveryResult {
        var match: ReaderAssetDeliverySource? = null
        exclusive.forEach { source ->
            if (source.matches(request)) {
                check(match == null) { "Reader asset delivery locator matched multiple exclusive sources." }
                match = source
            }
        }
        return match?.fetch(request) ?: fallback.fetch(request)
    }
}
