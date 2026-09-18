package app.universalmedia.core.domain

import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.MediaKind
import app.universalmedia.core.model.RepresentationFamily
import app.universalmedia.core.model.RootId
import app.universalmedia.core.model.ScanRunId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.core.model.SourceId

data class MaterializedLocalVideo(
    val mediaId: MediaId,
    val sourceId: SourceId,
    val bindingId: SourceBindingId,
    val assetId: AssetId,
    val assetRevision: AssetRevision,
)

interface LocalMediaMaterializer {
    /**
     * Atomically reuses exact root/provider/current-locator evidence or generates app-owned IDs,
     * writes representation + run-positive evidence, and admits Library unless suppressed.
     * Reject stale/finalized runs and observations outside the captured root/config scope.
     * Only provider-declared video/mp4 is recognized here; no rename/move matching is implied.
     */
    suspend fun commitRecognizedLocalVideo(
        runId: ScanRunId,
        observation: LocalDocumentObservation,
    ): MaterializedLocalVideo
}

data class LibraryCard(val mediaId: MediaId, val kind: MediaKind, val fallbackDisplayName: String?)

/** Locator-free durable summary for the Library's registered-folder controls. */
data class LibraryRootSummary(
    val rootId: RootId,
    val access: LocalAccessState,
    val hasRun: Boolean,
    val outcome: ScanOutcome?,
)

interface LibraryQueries {
    /** Emits initial and changed membership projections until caller cancellation; no provider locators. */
    suspend fun observeLibraryCards(onCards: suspend (List<LibraryCard>) -> Unit)

    suspend fun observeLibraryRoots(onRoots: suspend (List<LibraryRootSummary>) -> Unit)
}

/** Durable catalog context to resolve later, not an opened/runtime ResolvedContent. */
data class LocalSourceContext(
    val target: ConsumptionTargetRef,
    val sourceId: SourceId,
    val bindingId: SourceBindingId,
    val assetId: AssetId,
    val assetRevision: AssetRevision,
    val family: RepresentationFamily,
    val root: StorageRoot,
    val locator: LocalDocumentLocator,
)

sealed interface LocalSourceLookup {
    data class Found(val context: LocalSourceContext) : LocalSourceLookup

    data object NotFound : LocalSourceLookup

    data object UnsupportedRepresentation : LocalSourceLookup
}

interface LocalSourceCatalog {
    suspend fun load(target: ConsumptionTargetRef): LocalSourceLookup
}
