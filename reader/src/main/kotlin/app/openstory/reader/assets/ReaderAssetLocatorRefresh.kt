package app.openstory.reader.assets

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.content.ReaderImageSourcePolicy
import app.openstory.reader.document.ReaderDocument

sealed interface ReaderSelectedReleaseRefreshResult {
    data class Refreshed(
        val selectedRelease: ChapterRelease,
        val document: ReaderDocument,
        val imageSourcePolicy: ReaderImageSourcePolicy,
    ) : ReaderSelectedReleaseRefreshResult

    data object Superseded : ReaderSelectedReleaseRefreshResult
    data object RouteInvalidated : ReaderSelectedReleaseRefreshResult
    data class Failure(val failure: ReaderAssetFailure) : ReaderSelectedReleaseRefreshResult
}

interface ReaderSelectedReleaseRefreshPort {
    suspend fun refreshSelectedRelease(
        expectedManifestRevision: Long,
        expectedReleaseId: ChapterReleaseId,
    ): ReaderSelectedReleaseRefreshResult
}

internal sealed interface ReaderRefreshedManifestDecision {
    data object Unchanged : ReaderRefreshedManifestDecision
    data class Changed(val manifest: ReaderAssetChapterManifest) : ReaderRefreshedManifestDecision
    data object RouteInvalidated : ReaderRefreshedManifestDecision
}

/**
 * Compares a newly materialized delivery manifest against the currently committed semantic image
 * set. Only locator/runtime delivery facts may change here. Route/source/security/page identity
 * drift is not something the asset layer is allowed to reinterpret.
 */
internal object ReaderAssetLocatorRefresh {
    fun compare(
        current: ReaderAssetChapterManifest,
        refreshed: ReaderAssetChapterManifest,
    ): ReaderRefreshedManifestDecision {
        if (!current.hasCompatibleSemanticIdentity(refreshed)) {
            return ReaderRefreshedManifestDecision.RouteInvalidated
        }
        val currentLocators = current.descriptors.map(ReaderPageAssetDescriptor::locatorFingerprint)
        val refreshedLocators = refreshed.descriptors.map(ReaderPageAssetDescriptor::locatorFingerprint)
        return if (currentLocators == refreshedLocators) {
            // A transient manifest factory deliberately rotates runtime scope. That alone is not a
            // delivery change and must never publish a new revision/key set.
            ReaderRefreshedManifestDecision.Unchanged
        } else {
            ReaderRefreshedManifestDecision.Changed(refreshed)
        }
    }

    private fun ReaderAssetChapterManifest.hasCompatibleSemanticIdentity(
        refreshed: ReaderAssetChapterManifest,
    ): Boolean = refreshed.sessionId == sessionId &&
        refreshed.storyId == storyId &&
        refreshed.canonicalChapterId == canonicalChapterId &&
        refreshed.selectedReleaseId == selectedReleaseId &&
        refreshed.sourceNamespace == sourceNamespace &&
        refreshed.securityScope == securityScope &&
        refreshed.contentVariant == contentVariant &&
        refreshed.identityMode == identityMode &&
        refreshed.persistenceMode == persistenceMode &&
        refreshed.descriptors.size == descriptors.size &&
        descriptors.zip(refreshed.descriptors).all { (currentPage, refreshedPage) ->
            currentPage.imageOrdinal == refreshedPage.imageOrdinal &&
                currentPage.uiBlockId == refreshedPage.uiBlockId &&
                currentPage.stableAssetId == refreshedPage.stableAssetId
        }
}
