package app.openstory.reader.assets

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.plugins.api.manifest.ReaderImagePersistenceContract
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.routing.ReaderSessionId

class ReaderAssetManifestFactory(
    private val runtimeScopeIdFactory: ReaderRuntimeAssetScopeIdFactory = ReaderRuntimeAssetScopeIdFactory(),
) {
    fun create(
        sessionId: ReaderSessionId,
        storyId: StoryId,
        canonicalChapterId: CanonicalChapterId,
        selectedRelease: ChapterRelease,
        graphRevision: ReaderChapterGraphRevision,
        document: ReaderDocument,
        imageSourcePolicy: ReaderImageSourcePolicy,
        sourcePluginId: PluginId,
    ): ReaderAssetChapterManifest? {
        val images = document.blocks.filterIsInstance<ReaderBlock.ImagePage>()
        if (images.isEmpty()) return null

        require(selectedRelease.storyId == storyId) { "Reader asset release must belong to the session story." }
        require(selectedRelease.canonicalChapterId == canonicalChapterId) {
            "Reader asset release must belong to the committed chapter."
        }
        require(selectedRelease.pluginId == sourcePluginId) {
            "Reader asset producing source must match the selected release."
        }

        val sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(sourcePluginId)
        val securityScope = imageSourcePolicy.resolveSecurityScope()
        val identityInputs = images.map { image ->
            ReaderAssetPageIdentityInput(
                stableAssetId = image.stableAssetId,
                locatorFingerprint = ReaderAssetIdentity.locatorFingerprint(image.imageUrl),
            )
        }
        val identityMode = ReaderAssetIdentity.resolveMode(
            policy = imageSourcePolicy,
            stableAssetIds = identityInputs.map(ReaderAssetPageIdentityInput::stableAssetId),
        )
        val persistenceMode = ReaderAssetIdentity.resolvePersistence(
            policy = imageSourcePolicy,
            identityMode = identityMode,
            securityScope = securityScope,
        )
        val runtimeScope = if (persistenceMode == ReaderAssetPersistenceMode.TRANSIENT_ONLY) {
            runtimeScopeIdFactory.create(sessionId, sourceNamespace)
        } else {
            null
        }
        val context = ReaderAssetKeyContext(
            sourceNamespace = sourceNamespace,
            selectedReleaseId = selectedRelease.id,
            securityScope = securityScope,
            contentVariant = ReaderContentVariant.ORIGINAL,
            identityMode = identityMode,
            persistenceMode = persistenceMode,
            runtimeIsolationScope = runtimeScope,
        )
        val imageSetNamespace = ReaderAssetIdentity.imageSetNamespace(context, identityInputs)
        val descriptors = images.mapIndexed { ordinal, image ->
            ReaderPageAssetDescriptor(
                key = ReaderAssetIdentity.pageKey(context, imageSetNamespace, ordinal, identityInputs[ordinal]),
                uiBlockId = image.id,
                stableAssetId = image.stableAssetId,
                imageOrdinal = ordinal,
                deliveryLocator = image.imageUrl,
                locatorFingerprint = identityInputs[ordinal].locatorFingerprint,
            )
        }
        return ReaderAssetChapterManifest(
            sessionId = sessionId,
            storyId = storyId,
            canonicalChapterId = canonicalChapterId,
            selectedReleaseId = selectedRelease.id,
            sourceNamespace = sourceNamespace,
            securityScope = securityScope,
            contentVariant = ReaderContentVariant.ORIGINAL,
            identityMode = identityMode,
            persistenceMode = persistenceMode,
            graphRevision = graphRevision,
            imageSetNamespace = imageSetNamespace,
            runtimeIsolationScope = runtimeScope,
            descriptors = descriptors,
        )
    }
}

private fun ReaderImageSourcePolicy.resolveSecurityScope(): ReaderCacheSecurityScope =
    if (persistenceContract == ReaderImagePersistenceContract.PUBLIC) {
        ReaderCacheSecurityScope.Public
    } else {
        ReaderCacheSecurityScope.NonPersistentPrivate
    }
