package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.document.ReaderDocumentSanitizer
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.routing.ReaderSessionId

data class ReaderPageAssetDescriptor(
    val key: ReaderPageAssetKey,
    val uiBlockId: String,
    val stableAssetId: String,
    val imageOrdinal: Int,
    val deliveryLocator: String,
    val locatorFingerprint: ReaderDeliveryLocatorFingerprint,
) {
    init {
        require(uiBlockId.isNotBlank()) { "Reader image UI block ID must not be blank" }
        require(stableAssetId.isNotBlank()) { "Reader stable asset ID must not be blank" }
        require(imageOrdinal >= 0) { "Reader image ordinal must be non-negative" }
        require(deliveryLocator.isNotBlank()) { "Reader delivery locator must not be blank" }
        require(ReaderAssetIdentity.locatorFingerprint(deliveryLocator) == locatorFingerprint) {
            "Reader delivery locator fingerprint must match the runtime locator"
        }
    }
}

data class ReaderAssetChapterManifest(
    val sessionId: ReaderSessionId,
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val selectedReleaseId: ChapterReleaseId,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScope: ReaderCacheSecurityScope,
    val contentVariant: ReaderContentVariant,
    val identityMode: ReaderAssetIdentityMode,
    val persistenceMode: ReaderAssetPersistenceMode,
    val graphRevision: ReaderChapterGraphRevision,
    val imageSetNamespace: ReaderImageSetNamespace,
    val runtimeIsolationScope: ReaderRuntimeAssetScopeId?,
    val descriptors: List<ReaderPageAssetDescriptor>,
) {
    init {
        require(descriptors.isNotEmpty() && descriptors.size <= ReaderDocumentSanitizer.MAX_BLOCKS) {
            "Reader asset manifest must be finite and non-empty"
        }
        require(
            (persistenceMode == ReaderAssetPersistenceMode.TRANSIENT_ONLY) == (runtimeIsolationScope != null),
        ) { "Transient Reader manifests require one runtime scope; durable manifests forbid it" }
        require(
            persistenceMode != ReaderAssetPersistenceMode.DURABLE_AUTOMATIC ||
                identityMode != ReaderAssetIdentityMode.NON_PERSISTENT,
        ) { "Durable Reader manifests require stable identity" }
        require(
            persistenceMode != ReaderAssetPersistenceMode.DURABLE_AUTOMATIC ||
                securityScope != ReaderCacheSecurityScope.NonPersistentPrivate,
        ) { "Durable Reader manifests require a durable security scope" }

        val context = ReaderAssetKeyContext(
            sourceNamespace = sourceNamespace,
            selectedReleaseId = selectedReleaseId,
            securityScope = securityScope,
            contentVariant = contentVariant,
            identityMode = identityMode,
            persistenceMode = persistenceMode,
            runtimeIsolationScope = runtimeIsolationScope,
        )
        val identityInputs = descriptors.map { descriptor ->
            ReaderAssetPageIdentityInput(descriptor.stableAssetId, descriptor.locatorFingerprint)
        }
        require(ReaderAssetIdentity.imageSetNamespace(context, identityInputs) == imageSetNamespace) {
            "Reader asset manifest image-set namespace does not match its descriptors"
        }
        descriptors.forEachIndexed { ordinal, descriptor ->
            require(descriptor.imageOrdinal == ordinal) { "Reader image ordinals must be contiguous" }
            require(
                descriptor.key == ReaderAssetIdentity.pageKey(
                    context,
                    imageSetNamespace,
                    ordinal,
                    identityInputs[ordinal],
                ),
            ) { "Reader page key does not match manifest identity facts" }
        }
    }
}

data class ReaderPageAssetRequest(
    val sessionId: ReaderSessionId,
    val manifestRevision: Long,
    val descriptor: ReaderPageAssetDescriptor,
) {
    init {
        require(manifestRevision > 0L) { "Reader asset manifest revision must be positive" }
    }
}
