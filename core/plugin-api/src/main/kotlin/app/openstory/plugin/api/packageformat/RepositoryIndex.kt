package app.openstory.plugin.api.packageformat

import app.openstory.plugin.api.PluginCapability
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class RepositoryRollbackMetadata(
    val version: String,
    val packageUrl: String,
    val exactPackageSha256: String,
)

@Serializable
data class RepositoryVersionArtifact(
    val pluginId: String,
    val version: String,
    val packageUrl: String,
    val exactPackageSha256: String,
    val signature: PluginPackageSignature?,
    val changelogUrl: String?,
    val declaredCapabilities: Set<PluginCapability>,
    val rollback: RepositoryRollbackMetadata?,
)

@Serializable
data class RepositoryIndex(
    val schemaVersion: Int,
    val repositoryId: String,
    val artifacts: List<RepositoryVersionArtifact>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

class RepositoryIndexDocument internal constructor(
    val index: RepositoryIndex,
    internal val unknownTopLevelFields: Map<String, JsonElement>,
    internal val unknownArtifactFields: List<Map<String, JsonElement>>,
)

object RepositoryIndexJson {

    fun decode(source: String): RepositoryIndexDocument {
        val root = json
            .parseToJsonElement(source)
            .jsonObject

        val index = json.decodeFromJsonElement(
            RepositoryIndex.serializer(),
            root,
        )

        val unknownTopLevelFields = root.filterKeys { key ->
            key !in TOP_LEVEL_FIELDS
        }

        val unknownArtifactFields = root
            .getValue(ARTIFACTS_FIELD)
            .jsonArray
            .map { element ->
                element.jsonObject.filterKeys { key ->
                    key !in ARTIFACT_FIELDS
                }
            }

        return RepositoryIndexDocument(
            index = index,
            unknownTopLevelFields = unknownTopLevelFields,
            unknownArtifactFields = unknownArtifactFields,
        )
    }

    fun encode(document: RepositoryIndexDocument): String {
        val typedRoot = json.encodeToJsonElement(
            RepositoryIndex.serializer(),
            document.index,
        ).jsonObject

        val typedArtifacts = typedRoot
            .getValue(ARTIFACTS_FIELD)
            .jsonArray

        val mergedArtifacts = typedArtifacts.mapIndexed {
                index,
                element,
            ->
            val fields = document.unknownArtifactFields
                .getOrElse(index) {
                    emptyMap()
                }
                .toMutableMap()

            fields.putAll(element.jsonObject)

            JsonObject(fields)
        }

        val rootFields = document.unknownTopLevelFields
            .toMutableMap()

        rootFields.putAll(typedRoot)
        rootFields[ARTIFACTS_FIELD] = JsonArray(mergedArtifacts)

        return JsonObject(rootFields).toString()
    }

    private const val ARTIFACTS_FIELD = "artifacts"

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val TOP_LEVEL_FIELDS = setOf(
        "schemaVersion",
        "repositoryId",
        ARTIFACTS_FIELD,
    )

    private val ARTIFACT_FIELDS = setOf(
        "pluginId",
        "version",
        "packageUrl",
        "exactPackageSha256",
        "signature",
        "changelogUrl",
        "declaredCapabilities",
        "rollback",
    )
}

enum class RepositoryIndexError {
    UNSUPPORTED_SCHEMA_VERSION,
    IMMUTABLE_VERSION_CONFLICT,
}

object RepositoryIndexValidation {

    fun validate(
        index: RepositoryIndex,
    ): List<RepositoryIndexError> = buildList {
        if (index.schemaVersion != RepositoryIndex.CURRENT_SCHEMA_VERSION) {
            add(RepositoryIndexError.UNSUPPORTED_SCHEMA_VERSION)
        }

        val hasImmutableVersionConflict = index.artifacts
            .groupBy { artifact ->
                artifact.pluginId to artifact.version
            }
            .values
            .any { versions ->
                versions
                    .map { artifact ->
                        ImmutableArtifactIdentity(
                            packageUrl = artifact.packageUrl,
                            exactPackageSha256 =
                                artifact.exactPackageSha256,
                            signature = artifact.signature,
                        )
                    }
                    .distinct()
                    .size > 1
            }

        if (hasImmutableVersionConflict) {
            add(RepositoryIndexError.IMMUTABLE_VERSION_CONFLICT)
        }
    }

    private data class ImmutableArtifactIdentity(
        val packageUrl: String,
        val exactPackageSha256: String,
        val signature: PluginPackageSignature?,
    )
}
