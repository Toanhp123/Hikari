package app.openstory.reader.content

class ExclusiveReaderDocumentSourceRegistry(
    private val fallback: ReaderDocumentSourceRegistry,
    exclusive: Set<ReaderDocumentSource>,
    private val fallbackAvailability: ReaderSourceAvailability? = fallback as? ReaderSourceAvailability,
) : ReaderDocumentSourceRegistry, ReaderSourceAvailability {
    private val exclusiveByPluginId = exclusive.associateBy(ReaderDocumentSource::pluginId)

    override suspend fun enabled(): List<ReaderDocumentSource> = if (exclusiveByPluginId.isEmpty()) {
        fallback.enabled()
    } else {
        exclusiveByPluginId.values.sortedBy { it.pluginId.value }
    }

    override suspend fun enabledPluginIds() = if (exclusiveByPluginId.isEmpty()) {
        fallbackAvailability?.enabledPluginIds()
            ?: fallback.enabled().mapTo(linkedSetOf(), ReaderDocumentSource::pluginId)
    } else {
        exclusiveByPluginId.keys
    }

    override suspend fun offlineDownloadPluginIds() = if (exclusiveByPluginId.isEmpty()) {
        fallbackAvailability?.offlineDownloadPluginIds()
            ?: fallback.enabled().mapTo(linkedSetOf(), ReaderDocumentSource::pluginId)
    } else {
        exclusiveByPluginId.keys
    }
}
