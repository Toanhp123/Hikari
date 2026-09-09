package app.openstory.catalog.domain.asset

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.limits.domainSeparatedSha256
import app.openstory.catalog.domain.limits.requireUtf8Bound
import app.openstory.catalog.domain.limits.strictUtf8
import app.openstory.common.id.StoryId

private val COVER_REVISION_PATTERN = Regex("^cover:v1:[0-9a-f]{64}$")

sealed interface AcquisitionCoverInput {
    data class TrustedLocal(
        val logicalAssetId: String,
        val assetVersion: String,
    ) : AcquisitionCoverInput

    data class RemoteHttps(
        val rawUri: String,
        val reviewedStableArtworkToken: String? = null,
    ) : AcquisitionCoverInput
}

@JvmInline
value class CoverRevision(val value: String) {
    init {
        require(COVER_REVISION_PATTERN.matches(value))
    }
}

object CoverRevisionV1 {
    fun local(logicalAssetId: String, assetVersion: String): CoverRevision = revision(
        prefix = "hikari:v2:cover-revision:local:v1",
        fields = listOf(
            requireUtf8Bound(logicalAssetId, CatalogInputLimits.LOCAL_ASSET_ID_UTF8_BYTES, true),
            requireUtf8Bound(assetVersion, CatalogInputLimits.LOCAL_ASSET_VERSION_UTF8_BYTES, true),
        ),
    )

    fun remoteUri(normalizedUri: RemoteHttpsUriV1): CoverRevision = revision(
        prefix = "hikari:v2:cover-revision:remote-uri:v1",
        fields = listOf(strictUtf8(normalizedUri.value)),
    )

    fun remoteStableToken(token: String): CoverRevision = revision(
        prefix = "hikari:v2:cover-revision:remote-token:v1",
        fields = listOf(requireUtf8Bound(token, CatalogInputLimits.STABLE_ARTWORK_TOKEN_UTF8_BYTES, true)),
    )

    private fun revision(prefix: String, fields: List<ByteArray>): CoverRevision =
        CoverRevision("cover:v1:${domainSeparatedSha256(prefix, fields)}")
}

data class CoverAssetKey(
    val storyId: StoryId,
    val coverRevision: CoverRevision,
) {
    val stableCacheKey: String
        get() = "hikari:v2:cover-asset:v1:${storyId.value}:${coverRevision.value}"
}

sealed interface CoverLocator {
    data class TrustedLocalResource(
        val logicalAssetId: String,
        val assetVersion: String,
    ) : CoverLocator

    data class RemoteHttps(
        val catalogSourceKey: CatalogSourceKey,
        val normalizedUri: RemoteHttpsUriV1,
        val revision: CoverRevision,
    ) : CoverLocator
}

fun requireAlignedCover(
    ref: StorySourceRef,
    locator: CoverLocator?,
    key: CoverAssetKey?,
) {
    require((locator == null) == (key == null))
    if (locator == null || key == null) return
    require(key.storyId == ref.storyId)
    when (locator) {
        is CoverLocator.TrustedLocalResource -> require(
            key.coverRevision == CoverRevisionV1.local(locator.logicalAssetId, locator.assetVersion),
        )
        is CoverLocator.RemoteHttps -> {
            require(locator.catalogSourceKey == ref.catalogSourceKey)
            require(key.coverRevision == locator.revision)
        }
    }
}

data class SourceAssetPolicy(
    val catalogSourceKey: CatalogSourceKey,
    val allowedHttpsHosts: Set<String>,
) {
    init {
        allowedHttpsHosts.forEach { host -> require(canonicalizeDnsHost(host) == host) }
    }

    internal fun requireFor(sourceKey: CatalogSourceKey): SourceAssetPolicy {
        require(catalogSourceKey == sourceKey)
        return this
    }
}

fun interface SourceAssetPolicyProvider {
    fun policyFor(catalogSourceKey: CatalogSourceKey): SourceAssetPolicy?
}
