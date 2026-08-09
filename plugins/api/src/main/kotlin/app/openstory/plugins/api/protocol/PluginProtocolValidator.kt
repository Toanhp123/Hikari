package app.openstory.plugins.api.protocol

import app.openstory.plugins.api.protocol.catalog.CatalogDetailsOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogFiltersOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogSearchOutputDto
import app.openstory.plugins.api.protocol.content.ChapterDocumentDto
import app.openstory.plugins.api.protocol.content.ContentReleaseDto
import app.openstory.plugins.api.protocol.content.ContentStoryCandidateDto
import app.openstory.plugins.api.protocol.content.ContentStoryDetailsDto
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

data class ProtocolViolation(val code: String, val field: String)

object PluginProtocolValidator {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
    }

    fun validateOutput(
        operation: PluginOperation,
        payload: JsonElement,
        allowedNetworkHosts: Set<String>,
    ): List<ProtocolViolation> = runCatching {
        when (operation) {
            PluginOperation.CATALOG_HOME -> validateHome(payload, allowedNetworkHosts)
            PluginOperation.CATALOG_SEARCH -> validateSearch(payload, allowedNetworkHosts)
            PluginOperation.CATALOG_DETAILS -> validateDetails(payload, allowedNetworkHosts)
            PluginOperation.CATALOG_FILTERS -> {
                json.decodeFromJsonElement(CatalogFiltersOutputDto.serializer(), payload)
                emptyList()
            }
            PluginOperation.CONTENT_SEARCH -> {
                json.decodeFromJsonElement(
                    PageDto.serializer(ContentStoryCandidateDto.serializer()),
                    payload,
                )
                emptyList()
            }
            PluginOperation.CONTENT_STORY -> {
                json.decodeFromJsonElement(ContentStoryDetailsDto.serializer(), payload)
                emptyList()
            }
            PluginOperation.CONTENT_CHAPTERS -> {
                json.decodeFromJsonElement(PageDto.serializer(ContentReleaseDto.serializer()), payload)
                emptyList()
            }
            PluginOperation.CONTENT_CHAPTER -> {
                json.decodeFromJsonElement(ChapterDocumentDto.serializer(), payload)
                emptyList()
            }
        }
    }.getOrElse { listOf(ProtocolViolation("protocol.invalid_payload", operation.wireName)) }

    private fun validateHome(payload: JsonElement, allowedHosts: Set<String>): List<ProtocolViolation> {
        val output = json.decodeFromJsonElement(CatalogHomeOutputDto.serializer(), payload)
        return output.sections.flatMapIndexed { sectionIndex, section ->
            section.items.mapIndexedNotNull { itemIndex, item ->
                deniedUrl(item.coverUrl, allowedHosts, "sections[$sectionIndex].items[$itemIndex].coverUrl")
            }
        }
    }

    private fun validateSearch(payload: JsonElement, allowedHosts: Set<String>): List<ProtocolViolation> {
        val output = json.decodeFromJsonElement(CatalogSearchOutputDto.serializer(), payload)
        return output.items.mapIndexedNotNull { index, item ->
            deniedUrl(item.coverUrl, allowedHosts, "items[$index].coverUrl")
        }
    }

    private fun validateDetails(payload: JsonElement, allowedHosts: Set<String>): List<ProtocolViolation> {
        val output = json.decodeFromJsonElement(CatalogDetailsOutputDto.serializer(), payload)
        return listOfNotNull(
            deniedUrl(output.sourceUrl, allowedHosts, "sourceUrl"),
            deniedUrl(output.coverUrl, allowedHosts, "coverUrl"),
        )
    }

    private fun deniedUrl(url: String?, allowedHosts: Set<String>, field: String): ProtocolViolation? {
        if (url == null) return null
        val host = URI(url).host.lowercase()
        return if (host in allowedHosts) null else ProtocolViolation("protocol.remote_host_denied", field)
    }
}
