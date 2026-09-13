package app.openstory.catalog.feature.plugin

import android.content.Context
import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.CatalogAcquisitionSource
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.DiscoverAcquisitionSection
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import app.openstory.plugins.api.manifest.PluginManifest
import app.openstory.plugins.api.protocol.PluginOperation
import app.openstory.plugins.api.protocol.PluginProtocolValidator
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogDetailsRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeOutputDto
import app.openstory.plugins.api.protocol.catalog.CatalogHomeRequestDto
import app.openstory.plugins.api.protocol.catalog.CatalogItemDto
import app.openstory.plugins.api.protocol.catalog.WireCatalogFeedKind
import app.openstory.plugins.api.protocol.catalog.WireContentType
import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class ReferencePluginBridge private constructor(
    private val manifest: PluginManifest,
    private val source: String,
    private val executor: ReferencePluginExecutor,
    private val transport: ControlledPluginTransport,
) : CatalogAcquisitionSource, AutoCloseable {
    private val allowedHosts = manifest.capabilities.network?.hosts.orEmpty()
    private val rawContentTypes = mutableMapOf<String, WireContentType>()
    private val conflictingRawContentTypes = mutableSetOf<String>()
    private val ineligibleRawContentTypes = mutableSetOf<String>()

    override suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition {
        val operation = PluginOperation.CATALOG_HOME
        val payload = execute(
            operation,
            Json.encodeToJsonElement(
                CatalogHomeRequestDto.serializer(),
                CatalogHomeRequestDto(contentTypes = setOf(mediaType.toWireType())),
            ),
        )
        val output = Json.decodeFromJsonElement(CatalogHomeOutputDto.serializer(), payload)
        return DiscoverAcquisition(
            sections = output.sections.map { section ->
                DiscoverAcquisitionSection(
                    kind = section.kind.toDomainKind(),
                    items = section.items.mapNotNull { item ->
                        item.toAcquisitionItem(mediaType, acceptsRawContentType(item.sourceId, item.contentType))
                    },
                )
            },
        )
    }

    override suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition {
        val operation = PluginOperation.CATALOG_DETAILS
        val payload = execute(
            operation,
            Json.encodeToJsonElement(
                CatalogDetailsRequestDto.serializer(),
                CatalogDetailsRequestDto(ref.sourceStoryId),
            ),
        )
        val output = Json.decodeFromJsonElement(CatalogDetailsOutputDto.serializer(), payload)
        if (!acceptsRawContentType(output.sourceId, output.contentType)) {
            validationFailure("contentType", CatalogValidationReason.AUTHORITY_MISMATCH)
        }
        return StoryDetailAcquisition(
            sourceStoryId = output.sourceId,
            title = output.title,
            contentType = output.contentType.toDomainType()
                ?: validationFailure("contentType", CatalogValidationReason.MALFORMED),
            cover = output.coverUrl?.let { AcquisitionCoverInput.RemoteHttps(it) },
            rating = output.score?.let { CatalogRating(it.value, it.scale) },
            publicationStatusSummary = output.publicationStatus?.name,
            latestUpdateEpochMs = output.latestUpdate?.atEpochMillis,
            description = output.description,
            authors = output.authors.toList(),
            artists = emptyList(),
            genres = output.genres.toList(),
            publicationStatus = output.publicationStatus?.name,
            language = output.languageTags.sorted().firstOrNull(),
        )
    }

    override fun close() {
        executor.close()
    }

    private suspend fun execute(
        operation: PluginOperation,
        input: JsonElement,
    ): JsonElement {
        check(manifest.supports(operation))
        val output = executor.execute(source, operation, input, ::handleBridgeMessage)
        if (PluginProtocolValidator.validateOutput(operation, output, allowedHosts).isNotEmpty()) {
            throw ReferencePluginExecutionException(ReferencePluginFailureCode.PROTOCOL_INVALID)
        }
        return output
    }

    private suspend fun handleBridgeMessage(message: String): String {
        val envelope = runCatching { Json.parseToJsonElement(message).jsonObject }
            .getOrElse { return errorResponse("", ReferencePluginFailureCode.CAPABILITY_DENIED) }
        val id = envelope["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (envelope["method"]?.jsonPrimitive?.contentOrNull != "http.execute") {
            return errorResponse(id, ReferencePluginFailureCode.CAPABILITY_DENIED)
        }
        val request = runCatching { envelope.getValue("payload").jsonObject.toControlledRequest() }
            .getOrElse { return errorResponse(id, ReferencePluginFailureCode.CAPABILITY_DENIED) }
        if (!request.isAllowed(allowedHosts)) {
            return errorResponse(id, ReferencePluginFailureCode.CAPABILITY_DENIED)
        }
        val response = transport.execute(request)
        if (response.status in 200..299) recordRawContentTypes(response.body)
        return buildJsonObject {
            put("id", id)
            put(
                "result",
                buildJsonObject {
                    put("status", response.status)
                    put("body", response.body)
                },
            )
        }.toString()
    }

    private fun recordRawContentTypes(body: String) {
        val payload = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return
        synchronized(rawContentTypes) { recordRawContentTypes(payload) }
    }

    private fun recordRawContentTypes(element: JsonElement) {
        when (element) {
            is JsonArray -> element.forEach(::recordRawContentTypes)
            is JsonObject -> {
                val sourceId = element["series_id"]?.jsonPrimitive?.contentOrNull
                    ?: element["seriesId"]?.jsonPrimitive?.contentOrNull
                    ?: element["id"]?.jsonPrimitive?.contentOrNull
                val rawContentType = element["type"]?.jsonPrimitive?.contentOrNull
                if (sourceId != null && rawContentType != null) {
                    val contentType = rawContentType.toRawContentType()
                    if (contentType == null) {
                        ineligibleRawContentTypes += sourceId
                    } else {
                        val previous = rawContentTypes.putIfAbsent(sourceId, contentType)
                        if (previous != null && previous != contentType) conflictingRawContentTypes += sourceId
                    }
                }
                element.values.forEach(::recordRawContentTypes)
            }
            else -> Unit
        }
    }

    private fun acceptsRawContentType(sourceId: String, outputType: WireContentType): Boolean =
        synchronized(rawContentTypes) {
            sourceId !in conflictingRawContentTypes &&
                sourceId !in ineligibleRawContentTypes &&
                rawContentTypes[sourceId]?.let { it == outputType } != false
        }

    companion object {
        fun fromAssets(
            context: Context,
            transport: ControlledPluginTransport,
            executor: ReferencePluginExecutor = ReferencePluginExecutor(context.applicationContext),
        ): ReferencePluginBridge {
            val manifestBytes = context.assets.open("reference-plugins/mangaupdates/manifest.json")
                .use { it.readBytes() }
            val sourceBytes = context.assets.open("reference-plugins/mangaupdates/main.js")
                .use { it.readBytes() }
            require(manifestBytes.sha256() == EXPECTED_MANIFEST_SHA256)
            require(sourceBytes.sha256() == EXPECTED_SOURCE_SHA256)
            val manifestText = manifestBytes.decodeToString()
            val source = sourceBytes.decodeToString()
            val manifest = Json.decodeFromString<PluginManifest>(manifestText)
            require(manifest.id == "org.openstory.catalog.mangaupdates")
            require(manifest.version == "1.1.4")
            require(manifest.protocol.major == 1)
            require(manifest.capabilities.network?.hosts == EXPECTED_HOSTS)
            return ReferencePluginBridge(
                manifest = manifest,
                source = source,
                executor = executor,
                transport = transport,
            )
        }

        private val EXPECTED_HOSTS = setOf(
            "api.mangaupdates.com",
            "cdn.mangaupdates.com",
            "mangaupdates.com",
            "www.mangaupdates.com",
        )
        private const val EXPECTED_MANIFEST_SHA256 =
            "777d257590ca8d1b1791956bed135c5029c62e244807f155a63911db627a2cd5"
        private const val EXPECTED_SOURCE_SHA256 =
            "b144ef4fd6ab3c0c319c6f9c92c787bb7796f07559ebaf06ce85d6e11f0e3202"
    }
}

private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private fun JsonObject.toControlledRequest(): ControlledPluginRequest = ControlledPluginRequest(
    url = getValue("url").jsonPrimitive.content,
    method = get("method")?.jsonPrimitive?.contentOrNull ?: "GET",
    headers = get("headers")?.jsonObject?.mapValues { (_, value) -> value.jsonPrimitive.content }.orEmpty(),
    body = get("body")?.jsonPrimitive?.contentOrNull,
)

private fun ControlledPluginRequest.isAllowed(allowedHosts: Set<String>): Boolean {
    val target = runCatching { URI(url) }.getOrNull() ?: return false
    return target.scheme == "https" && target.userInfo == null && target.fragment == null &&
        target.host?.lowercase() in allowedHosts
}

private fun errorResponse(id: String, code: ReferencePluginFailureCode): String = buildJsonObject {
    put("id", id)
    put(
        "error",
        buildJsonObject {
            put("code", code.name.lowercase())
            put("retryable", false)
        },
    )
}.toString()

private fun CatalogMediaType.toWireType(): WireContentType = when (this) {
    CatalogMediaType.MANGA -> WireContentType.MANGA
    CatalogMediaType.LIGHT_NOVEL -> WireContentType.LIGHT_NOVEL
}

private fun WireContentType.toDomainType(): CatalogMediaType? = when (this) {
    WireContentType.MANGA -> CatalogMediaType.MANGA
    WireContentType.LIGHT_NOVEL -> CatalogMediaType.LIGHT_NOVEL
    WireContentType.WEB_NOVEL,
    WireContentType.ANIME,
    -> null
}

private fun WireCatalogFeedKind.toDomainKind(): CatalogSectionKind = when (this) {
    WireCatalogFeedKind.POPULAR -> CatalogSectionKind.POPULAR
    WireCatalogFeedKind.LATEST_UPDATES -> CatalogSectionKind.LATEST_UPDATES
    WireCatalogFeedKind.TOP_RATED -> CatalogSectionKind.TOP_RATED
    WireCatalogFeedKind.OTHER -> validationFailure("sections.kind", CatalogValidationReason.MALFORMED)
}

private fun CatalogItemDto.toAcquisitionItem(
    requestedMediaType: CatalogMediaType,
    rawContentTypeAccepted: Boolean,
): DiscoverAcquisitionItem? {
    if (!rawContentTypeAccepted) return null
    val mediaType = contentType.toDomainType() ?: return null
    if (mediaType != requestedMediaType) return null
    return DiscoverAcquisitionItem(
        sourceStoryId = sourceId,
        title = title,
        contentType = mediaType,
        cover = coverUrl?.let { AcquisitionCoverInput.RemoteHttps(it) },
        rating = score?.let { CatalogRating(it.value, it.scale) },
        publicationStatusSummary = publicationStatus?.name,
        latestUpdateEpochMs = latestUpdate?.atEpochMillis,
    )
}

private fun String.toRawContentType(): WireContentType? {
    val tokens = RAW_CONTENT_TYPE_TOKEN.findAll(lowercase()).map { it.value }.toList()
    return when {
        tokens.containsAdjacent("web", "novel") -> WireContentType.WEB_NOVEL
        tokens == listOf("novel") || tokens.containsAdjacent("light", "novel") -> WireContentType.LIGHT_NOVEL
        "anime" in tokens -> WireContentType.ANIME
        tokens.any(MANGA_LIKE_CONTENT_TOKENS::contains) -> WireContentType.MANGA
        else -> null
    }
}

private fun List<String>.containsAdjacent(first: String, second: String): Boolean =
    (0 until lastIndex).any { index -> this[index] == first && this[index + 1] == second }

private val RAW_CONTENT_TYPE_TOKEN = Regex("[a-z0-9]+")
private val MANGA_LIKE_CONTENT_TOKENS = setOf("manga", "manhwa", "manhua", "oel")

private fun validationFailure(
    field: String,
    reason: CatalogValidationReason,
): Nothing = throw CatalogFailureException(CatalogFailure.Validation(field, reason))
