package app.openstory.reader.assets

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.plugins.api.manifest.ReaderImageIdentityContract
import app.openstory.plugins.api.manifest.ReaderImageLocatorContract
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderDocumentSanitizer
import app.openstory.reader.routing.ReaderSessionId
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.IDN
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

@JvmInline
value class ReaderAssetSourceNamespace private constructor(val value: String) {
    companion object {
        fun fromPluginId(pluginId: PluginId): ReaderAssetSourceNamespace =
            ReaderAssetSourceNamespace(pluginId.value)
    }
}

@JvmInline
value class ReaderAssetKeySchemaVersion(val value: Int) {
    init {
        require(value > 0) { "Reader asset key schema version must be positive" }
    }
}

@JvmInline
value class ReaderAssetKeyHash(val value: String) {
    init {
        requireSha256(value, "Reader asset key hash")
    }
}

@JvmInline
value class ReaderAssetIdentityHash(val value: String) {
    init {
        requireSha256(value, "Reader asset identity hash")
    }
}

@JvmInline
value class ReaderDeliveryLocatorFingerprint(val value: String) {
    init {
        requireSha256(value, "Reader delivery locator fingerprint")
    }
}

@JvmInline
value class ReaderImageSetNamespace(val value: String) {
    init {
        requireSha256(value, "Reader image-set namespace")
    }
}

@JvmInline
value class ReaderRuntimeAssetScopeId(val value: String) {
    init {
        requireSha256(value, "Reader runtime asset scope")
    }
}

enum class ReaderAssetIdentityMode { TRUSTED_STABLE, LOCATOR_BOUND, NON_PERSISTENT }

enum class ReaderAssetPersistenceMode { DURABLE_AUTOMATIC, TRANSIENT_ONLY }

enum class ReaderContentVariant { ORIGINAL }

sealed interface ReaderCacheSecurityScope {
    data object Public : ReaderCacheSecurityScope

    data class AccountScoped(val stableNonSecretNamespace: String) : ReaderCacheSecurityScope {
        init {
            require(stableNonSecretNamespace.isNormalizedIdentityFact()) {
                "Account cache namespace must be normalized and non-blank"
            }
        }
    }

    data object NonPersistentPrivate : ReaderCacheSecurityScope
}

data class ReaderPageAssetKey(
    val schemaVersion: ReaderAssetKeySchemaVersion,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val persistenceMode: ReaderAssetPersistenceMode,
    val imageSetNamespace: ReaderImageSetNamespace,
    val runtimeIsolationScope: ReaderRuntimeAssetScopeId?,
    val pageIdentityHash: ReaderAssetIdentityHash,
    val hash: ReaderAssetKeyHash,
) {
    init {
        requireRuntimeIsolation(persistenceMode, runtimeIsolationScope)
        require(
            persistenceMode != ReaderAssetPersistenceMode.DURABLE_AUTOMATIC ||
                securityScope != ReaderCacheSecurityScope.NonPersistentPrivate,
        ) { "Durable Reader assets require a durable security scope" }
    }
}

data class ReaderAssetKeyContext(
    val sourceNamespace: ReaderAssetSourceNamespace,
    val selectedReleaseId: ChapterReleaseId,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val identityMode: ReaderAssetIdentityMode,
    val persistenceMode: ReaderAssetPersistenceMode,
    val runtimeIsolationScope: ReaderRuntimeAssetScopeId?,
) {
    init {
        requireRuntimeIsolation(persistenceMode, runtimeIsolationScope)
        require(
            persistenceMode != ReaderAssetPersistenceMode.DURABLE_AUTOMATIC ||
                identityMode != ReaderAssetIdentityMode.NON_PERSISTENT,
        ) { "Non-persistent identity cannot use durable storage" }
        require(
            persistenceMode != ReaderAssetPersistenceMode.DURABLE_AUTOMATIC ||
                securityScope != ReaderCacheSecurityScope.NonPersistentPrivate,
        ) { "Durable Reader assets require a durable security scope" }
    }
}

data class ReaderAssetPageIdentityInput(
    val stableAssetId: String,
    val locatorFingerprint: ReaderDeliveryLocatorFingerprint,
) {
    init {
        require(stableAssetId.isNormalizedIdentityFact()) { "Stable Reader asset ID must be normalized and non-blank" }
    }
}

class ReaderRuntimeAssetScopeIdFactory(
    private val nonceFactory: () -> UUID = UUID::randomUUID,
) {
    fun create(
        sessionId: ReaderSessionId,
        sourceNamespace: ReaderAssetSourceNamespace,
    ): ReaderRuntimeAssetScopeId = ReaderRuntimeAssetScopeId(
        taggedHash(
            "domain" to "ricc-runtime-scope-v1",
            "sessionId" to sessionId.value.toString(),
            "sourceNamespace" to sourceNamespace.value,
            "nonce" to nonceFactory().toString(),
        ),
    )
}

object ReaderAssetIdentity {
    val KEY_SCHEMA_VERSION = ReaderAssetKeySchemaVersion(1)

    fun resolveMode(
        policy: ReaderImageSourcePolicy,
        stableAssetIds: List<String>,
    ): ReaderAssetIdentityMode {
        val trustedStableIds = stableAssetIds.isNotEmpty() &&
            stableAssetIds.all(String::isNormalizedIdentityFact) &&
            stableAssetIds.distinct().size == stableAssetIds.size
        return when {
            policy.identityContract == ReaderImageIdentityContract.STABLE_ID_CHANGES_WITH_CONTENT &&
                trustedStableIds -> ReaderAssetIdentityMode.TRUSTED_STABLE
            policy.locatorContract == ReaderImageLocatorContract.LOCATOR_CHANGES_WITH_CONTENT ->
                ReaderAssetIdentityMode.LOCATOR_BOUND
            else -> ReaderAssetIdentityMode.NON_PERSISTENT
        }
    }

    fun resolvePersistence(
        policy: ReaderImageSourcePolicy,
        identityMode: ReaderAssetIdentityMode,
        securityScope: ReaderCacheSecurityScope,
    ): ReaderAssetPersistenceMode {
        if (identityMode == ReaderAssetIdentityMode.NON_PERSISTENT) {
            return ReaderAssetPersistenceMode.TRANSIENT_ONLY
        }
        val durable = when (policy.persistenceContract) {
            ReaderImagePersistenceContract.NON_PERSISTENT -> false
            ReaderImagePersistenceContract.PUBLIC -> securityScope == ReaderCacheSecurityScope.Public
            ReaderImagePersistenceContract.ACCOUNT_SCOPED -> securityScope is ReaderCacheSecurityScope.AccountScoped
        }
        return if (durable) {
            ReaderAssetPersistenceMode.DURABLE_AUTOMATIC
        } else {
            ReaderAssetPersistenceMode.TRANSIENT_ONLY
        }
    }

    fun locatorFingerprint(locator: String): ReaderDeliveryLocatorFingerprint {
        val uri = runCatching { URI(locator) }.getOrNull()
        require(uri != null && uri.scheme.equals("https", ignoreCase = true)) { "Reader asset locator must be HTTPS" }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null) { "Reader asset locator must have a safe host" }
        val host = IDN.toASCII(uri.host).lowercase(Locale.ROOT)
        val port = uri.port.takeIf { it != -1 && it != HTTPS_DEFAULT_PORT }?.toString()
        val path = uri.rawPath.takeUnless(String::isNullOrEmpty) ?: "/"
        return ReaderDeliveryLocatorFingerprint(
            taggedHash(
                "domain" to "ricc-locator-v1",
                "scheme" to "https",
                "host" to host,
                "port" to port,
                "path" to path,
                "query" to uri.rawQuery,
            ),
        )
    }

    fun imageSetNamespace(
        context: ReaderAssetKeyContext,
        pages: List<ReaderAssetPageIdentityInput>,
    ): ReaderImageSetNamespace {
        require(pages.isNotEmpty() && pages.size <= ReaderDocumentSanitizer.MAX_BLOCKS) {
            "Reader image set must be finite and non-empty"
        }
        val fields = mutableListOf(
            "domain" to "ricc-image-set-v1",
            "sourceNamespace" to context.sourceNamespace.value,
            "selectedReleaseId" to context.selectedReleaseId.value,
            "contentVariant" to context.contentVariant.name,
            "identityMode" to context.identityMode.name,
            "persistenceMode" to context.persistenceMode.name,
            "pageCount" to pages.size.toString(),
        )
        context.runtimeIsolationScope?.let { fields += "runtimeIsolationScope" to it.value }
        pages.forEachIndexed { ordinal, page ->
            fields += "ordinal" to ordinal.toString()
            when (context.identityMode) {
                ReaderAssetIdentityMode.TRUSTED_STABLE -> fields += "stableAssetId" to page.stableAssetId
                ReaderAssetIdentityMode.LOCATOR_BOUND ->
                    fields += "locatorFingerprint" to page.locatorFingerprint.value
                ReaderAssetIdentityMode.NON_PERSISTENT -> fields += "runtimePageIdentity" to taggedHash(
                    "domain" to "ricc-runtime-page-input-v1",
                    "stableAssetId" to page.stableAssetId,
                    "locatorFingerprint" to page.locatorFingerprint.value,
                )
            }
        }
        return ReaderImageSetNamespace(taggedHash(fields))
    }

    fun pageKey(
        context: ReaderAssetKeyContext,
        imageSetNamespace: ReaderImageSetNamespace,
        ordinal: Int,
        page: ReaderAssetPageIdentityInput,
    ): ReaderPageAssetKey {
        require(ordinal >= 0) { "Reader image ordinal must be non-negative" }
        val pageIdentity = pageIdentityHash(context, imageSetNamespace, ordinal, page)
        val hash = ReaderAssetKeyHash(
            taggedHash(
                "domain" to "ricc-key-v1",
                "schemaVersion" to KEY_SCHEMA_VERSION.value.toString(),
                "sourceNamespace" to context.sourceNamespace.value,
                "securityScopeHash" to securityScopeHash(context.securityScope),
                "contentVariant" to context.contentVariant.name,
                "persistenceMode" to context.persistenceMode.name,
                "imageSetNamespace" to imageSetNamespace.value,
                "runtimeIsolationScope" to context.runtimeIsolationScope?.value,
                "pageIdentityHash" to pageIdentity.value,
            ),
        )
        return ReaderPageAssetKey(
            schemaVersion = KEY_SCHEMA_VERSION,
            sourceNamespace = context.sourceNamespace,
            securityScope = context.securityScope,
            contentVariant = context.contentVariant,
            persistenceMode = context.persistenceMode,
            imageSetNamespace = imageSetNamespace,
            runtimeIsolationScope = context.runtimeIsolationScope,
            pageIdentityHash = pageIdentity,
            hash = hash,
        )
    }

    private fun pageIdentityHash(
        context: ReaderAssetKeyContext,
        imageSetNamespace: ReaderImageSetNamespace,
        ordinal: Int,
        page: ReaderAssetPageIdentityInput,
    ): ReaderAssetIdentityHash = ReaderAssetIdentityHash(
        when (context.identityMode) {
            ReaderAssetIdentityMode.TRUSTED_STABLE -> taggedHash(
                "domain" to "ricc-page-trusted-v1",
                "imageSetNamespace" to imageSetNamespace.value,
                "ordinal" to ordinal.toString(),
                "stableAssetId" to page.stableAssetId,
            )
            ReaderAssetIdentityMode.LOCATOR_BOUND -> taggedHash(
                "domain" to "ricc-page-locator-v1",
                "imageSetNamespace" to imageSetNamespace.value,
                "ordinal" to ordinal.toString(),
                "locatorFingerprint" to page.locatorFingerprint.value,
            )
            ReaderAssetIdentityMode.NON_PERSISTENT -> taggedHash(
                "domain" to "ricc-page-runtime-v1",
                "runtimeIsolationScope" to checkNotNull(context.runtimeIsolationScope).value,
                "imageSetNamespace" to imageSetNamespace.value,
                "ordinal" to ordinal.toString(),
                "stableAssetId" to page.stableAssetId,
                "locatorFingerprint" to page.locatorFingerprint.value,
            )
        },
    )

    private fun securityScopeHash(scope: ReaderCacheSecurityScope): String = when (scope) {
        ReaderCacheSecurityScope.Public -> taggedHash(
            "domain" to "ricc-security-scope-v1",
            "kind" to "public",
        )
        is ReaderCacheSecurityScope.AccountScoped -> taggedHash(
            "domain" to "ricc-security-scope-v1",
            "kind" to "account",
            "namespace" to scope.stableNonSecretNamespace,
        )
        ReaderCacheSecurityScope.NonPersistentPrivate -> taggedHash(
            "domain" to "ricc-security-scope-v1",
            "kind" to "non-persistent-private",
        )
    }
}

fun ReaderPageAssetKey.isSupportedSchema(): Boolean =
    schemaVersion == ReaderAssetIdentity.KEY_SCHEMA_VERSION

private fun taggedHash(vararg fields: Pair<String, String?>): String =
    taggedHash(fields.asList())

private fun taggedHash(fields: List<Pair<String, String?>>): String {
    val bytes = ByteArrayOutputStream().use { output ->
        DataOutputStream(output).use { framed ->
            fields.forEach { (tag, value) ->
                framed.writeLengthFramed(tag)
                if (value == null) {
                    framed.writeInt(NULL_LENGTH)
                } else {
                    framed.writeLengthFramed(value)
                }
            }
        }
        output.toByteArray()
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes).toLowerHex()
}

private fun DataOutputStream.writeLengthFramed(value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    writeInt(bytes.size)
    write(bytes)
}

private fun requireRuntimeIsolation(
    persistenceMode: ReaderAssetPersistenceMode,
    runtimeIsolationScope: ReaderRuntimeAssetScopeId?,
) {
    require(
        (persistenceMode == ReaderAssetPersistenceMode.TRANSIENT_ONLY) == (runtimeIsolationScope != null),
    ) { "Transient Reader assets require one runtime isolation scope; durable assets forbid it" }
}

private fun String.isNormalizedIdentityFact(): Boolean =
    isNotBlank() && this == trim() && none(Char::isISOControl)

private fun requireSha256(value: String, label: String) {
    require(SHA256_PATTERN.matches(value)) { "$label must be lowercase SHA-256" }
}

internal fun ByteArray.toLowerHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(Locale.ROOT, byte.toInt() and BYTE_MASK)
}

private const val HTTPS_DEFAULT_PORT = 443
private const val NULL_LENGTH = -1
private const val BYTE_MASK = 0xff
private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
