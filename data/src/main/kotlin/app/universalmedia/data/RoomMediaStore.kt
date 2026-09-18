package app.universalmedia.data

import app.universalmedia.core.domain.AssetRevision
import app.universalmedia.core.domain.CompletionState
import app.universalmedia.core.domain.CoverageGap
import app.universalmedia.core.domain.DeclaredScanScope
import app.universalmedia.core.domain.IncompleteReason
import app.universalmedia.core.domain.LibraryCard
import app.universalmedia.core.domain.LibraryQueries
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalDocumentLocator
import app.universalmedia.core.domain.LocalDocumentObservation
import app.universalmedia.core.domain.LocalMediaMaterializer
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.LocalSourceCatalog
import app.universalmedia.core.domain.LocalSourceContext
import app.universalmedia.core.domain.LocalSourceLookup
import app.universalmedia.core.domain.MaterializedLocalVideo
import app.universalmedia.core.domain.ProgressStore
import app.universalmedia.core.domain.ProgressWriteResult
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.ScanCoverage
import app.universalmedia.core.domain.ScanFinalization
import app.universalmedia.core.domain.ScanJournal
import app.universalmedia.core.domain.ScanRun
import app.universalmedia.core.domain.SeenLocalAsset
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.domain.VideoProgressCheckpoint
import app.universalmedia.core.domain.VideoProgressState
import app.universalmedia.core.domain.VideoResumeAnchor
import app.universalmedia.core.domain.VideoResumeContext
import app.universalmedia.core.model.AssetId
import app.universalmedia.core.model.ConsumptionTargetRef
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.MediaKind
import app.universalmedia.core.model.RepresentationFamily
import app.universalmedia.core.model.RootId
import app.universalmedia.core.model.ScanRunId
import app.universalmedia.core.model.SourceBindingId
import app.universalmedia.core.model.SourceId
import java.util.UUID
import java.util.concurrent.Callable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class RoomMediaStore(private val database: UniversalMediaDatabase) :
    StorageRootStore,
    ScanJournal,
    LocalMediaMaterializer,
    LibraryQueries {
    private val dao = database.catalog()

    private suspend fun <T> transaction(block: () -> T): T = withContext(Dispatchers.IO) {
        database.runInTransaction(Callable { block() })
    }

    override suspend fun registerOrReauthorize(evidence: RootRegistrationEvidence): StorageRoot =
        transaction {
            val descriptor = evidence.descriptor
            require(
                descriptor.providerAuthority.isNotBlank() && descriptor.treeLocator.isNotBlank(),
            )
            val previous = dao.rootByDescriptor(
                descriptor.providerAuthority,
                descriptor.treeLocator,
            )
            val row = RootRow(
                previous?.rootId ?: RootId.generate().value.toString(),
                descriptor.providerAuthority,
                descriptor.treeLocator,
                previous?.let { Math.addExact(it.generation, 1) } ?: 1,
                if (evidence.persistedReadAccess) "READABLE" else "ACCESS_LOST",
                evidence.observedAtEpochMs,
            )
            if (previous == null) dao.insert(row) else dao.update(row)
            supersede(row.rootId, evidence.observedAtEpochMs)
            row.domain()
        }

    override suspend fun get(rootId: RootId): StorageRoot? = transaction {
        dao.root(rootId.value.toString())?.domain()
    }

    override suspend fun beginRun(scope: DeclaredScanScope, startedAtEpochMs: Long): ScanRun =
        transaction {
            val root = requireNotNull(dao.root(scope.rootId.value.toString()))
            require(root.generation == scope.configGeneration && root.access == "READABLE")
            supersede(root.rootId, startedAtEpochMs)
            val id = ScanRunId.generate()
            dao.insert(RunRow(id.value.toString(), startedAtEpochMs, null, null))
            dao.insert(ScopeRow(id.value.toString(), root.rootId, root.generation, "UNKNOWN", "[]"))
            ScanRun(id, scope, startedAtEpochMs)
        }

    private fun supersede(root: String, at: Long) {
        dao.activeRuns(root).forEach {
            dao.update(it.copy(outcome = "INTERRUPTED", finalizedAt = at))
            val scope = requireNotNull(dao.scope(it.runId))
            dao.update(
                scope.copy(
                    coverage = "INCOMPLETE",
                    gaps = encodeGaps(listOf(CoverageGap(null, IncompleteReason.SUPERSEDED))),
                ),
            )
        }
    }

    private fun activeScope(run: String): ScopeRow {
        check(requireNotNull(dao.run(run)).outcome == null) { "Scan is no longer active" }
        val scope = requireNotNull(dao.scope(run))
        val root = requireNotNull(dao.root(scope.rootId))
        check(root.generation == scope.generation && root.access == "READABLE") {
            "Scan scope is stale"
        }
        return scope
    }

    private fun validate(scope: ScopeRow, observation: LocalDocumentObservation) {
        val root = requireNotNull(dao.root(scope.rootId))
        require(observation.locator.rootId.value.toString() == scope.rootId)
        require(observation.locator.providerAuthority == root.authority)
        require(observation.locator.documentLocator.isNotBlank())
        require(observation.mimeType == "video/mp4")
        require(observation.sizeBytes?.let { it >= 0 } != false)
        require(observation.lastModifiedEpochMs?.let { it >= 0 } != false)
    }

    override suspend fun commitRecognizedLocalVideo(
        runId: ScanRunId,
        observation: LocalDocumentObservation,
    ): MaterializedLocalVideo = transaction {
        val run = runId.value.toString()
        val scope = activeScope(run)
        validate(scope, observation)
        val locator = dao.locator(
            scope.rootId,
            observation.locator.providerAuthority,
            observation.locator.documentLocator,
        )
        val asset: AssetRow
        val binding: BindingRow
        val media: String
        if (locator == null) {
            media = MediaId.generate().value.toString()
            dao.insert(MediaRow(media, "VIDEO"))
            val target = dao.insert(TargetRow(targetKind = "MEDIA", mediaId = media, unitId = null))
            val source =
                dao.localSource()
                    ?: SourceRow(SourceId.generate().value.toString(), "LOCAL").also {
                        dao.insert(it)
                    }
            binding =
                BindingRow(SourceBindingId.generate().value.toString(), target, source.sourceId)
            dao.insert(binding)
            asset =
                AssetRow(
                    AssetId.generate().value.toString(),
                    binding.bindingId,
                    0,
                    "VIDEO",
                    observation.displayName,
                    observation.sizeBytes,
                    observation.lastModifiedEpochMs,
                    observation.observedAtEpochMs,
                    "PRESENT",
                )
            dao.insert(asset)
            dao.insert(
                LocatorRow(
                    UUID.randomUUID().toString(),
                    asset.assetId,
                    scope.rootId,
                    observation.locator.providerAuthority,
                    observation.locator.documentLocator,
                    observation.observedAtEpochMs,
                ),
            )
        } else {
            val old = requireNotNull(dao.asset(locator.assetId))
            binding = requireNotNull(dao.binding(old.bindingId))
            media = requireNotNull(requireNotNull(dao.target(binding.targetRowId)).mediaId)
            // Replayed older evidence must not roll representation facts/revision backwards.
            asset = if (observation.observedAtEpochMs < old.observedAt) {
                old
            } else {
                old.copy(
                    revision = representationRevision(
                        old.revision,
                        old.sizeBytes,
                        old.modifiedAt,
                        observation.sizeBytes,
                        observation.lastModifiedEpochMs,
                    ),
                    displayName = observation.displayName,
                    sizeBytes = observation.sizeBytes,
                    modifiedAt = observation.lastModifiedEpochMs,
                    observedAt = observation.observedAtEpochMs,
                    presence = "PRESENT",
                )
            }
            dao.update(asset)
            dao.update(
                locator.copy(observedAt = maxOf(locator.observedAt, observation.observedAtEpochMs)),
            )
        }
        dao.seen(SeenRow(run, asset.assetId, observation.observedAtEpochMs))
        dao.admit(LibraryRow(media, "ACTIVE", observation.observedAtEpochMs))
        MaterializedLocalVideo(
            MediaId(UUID.fromString(media)),
            SourceId(UUID.fromString(binding.sourceId)),
            SourceBindingId(UUID.fromString(binding.bindingId)),
            AssetId(UUID.fromString(asset.assetId)),
            AssetRevision(asset.revision),
        )
    }

    override suspend fun recordPositiveBatch(
        runId: ScanRunId,
        observations: List<SeenLocalAsset>,
    ): Unit = transaction {
        val run = runId.value.toString()
        val scope = activeScope(run)
        observations.forEach {
            validate(scope, it.observation)
            val locator =
                requireNotNull(
                    dao.locator(
                        scope.rootId,
                        it.observation.locator.providerAuthority,
                        it.observation.locator.documentLocator,
                    ),
                )
            require(locator.assetId == it.assetId.value.toString())
            dao.seen(SeenRow(run, locator.assetId, it.observation.observedAtEpochMs))
        }
    }

    override suspend fun finalizeRun(runId: ScanRunId, finalization: ScanFinalization): Unit =
        transaction {
            val id = runId.value.toString()
            val scope = activeScope(id)
            val run = requireNotNull(dao.run(id))
            val coverage = finalization.coverage
            val gaps = if (coverage is ScanCoverage.Incomplete) coverage.gaps else emptyList()
            gaps.forEach { gap ->
                gap.locator?.let {
                    require(it.rootId.value.toString() == scope.rootId)
                    require(
                        it.providerAuthority == requireNotNull(dao.root(scope.rootId)).authority,
                    )
                }
            }
            dao.update(
                run.copy(
                    outcome = finalization.outcome.name,
                    finalizedAt = finalization.finalizedAtEpochMs,
                ),
            )
            dao.update(
                scope.copy(
                    coverage = when (coverage) {
                        ScanCoverage.Complete -> "COMPLETE"
                        ScanCoverage.Unknown -> "UNKNOWN"
                        is ScanCoverage.Incomplete -> "INCOMPLETE"
                    },
                    gaps = encodeGaps(gaps),
                ),
            )
        }

    override suspend fun observeLibraryCards(onCards: suspend (List<LibraryCard>) -> Unit) {
        dao.cards().collect { rows ->
            onCards(
                rows.map {
                    LibraryCard(
                        MediaId(UUID.fromString(it.mediaId)),
                        MediaKind.valueOf(it.kind),
                        it.fallbackDisplayName,
                    )
                },
            )
        }
    }

    private fun target(ref: ConsumptionTargetRef): TargetRow? = when (ref) {
        is ConsumptionTargetRef.MediaTarget -> dao.mediaTarget(ref.mediaId.value.toString())
        is ConsumptionTargetRef.UnitTarget -> dao.unitTarget(ref.unitId.value.toString())
    }

    val sources: LocalSourceCatalog = object : LocalSourceCatalog {
        override suspend fun load(target: ConsumptionTargetRef): LocalSourceLookup = transaction {
            val row = target(target) ?: return@transaction LocalSourceLookup.NotFound
            val asset =
                dao.localAsset(row.targetRowId) ?: return@transaction LocalSourceLookup.NotFound
            if (asset.family !=
                "VIDEO"
            ) {
                return@transaction LocalSourceLookup.UnsupportedRepresentation
            }
            val locator =
                dao.assetLocator(asset.assetId) ?: return@transaction LocalSourceLookup.NotFound
            val binding = requireNotNull(dao.binding(asset.bindingId))
            val root = requireNotNull(dao.root(locator.rootId)).domain()
            LocalSourceLookup.Found(
                LocalSourceContext(
                    target,
                    SourceId(UUID.fromString(binding.sourceId)),
                    SourceBindingId(UUID.fromString(binding.bindingId)),
                    AssetId(UUID.fromString(asset.assetId)),
                    AssetRevision(asset.revision),
                    RepresentationFamily.VIDEO,
                    root,
                    LocalDocumentLocator(root.id, locator.authority, locator.documentLocator),
                ),
            )
        }
    }

    val progress: ProgressStore = object : ProgressStore {
        override suspend fun load(target: ConsumptionTargetRef): VideoProgressState? = transaction {
            target(target)?.let { state(it.targetRowId, target) }
        }

        override suspend fun checkpointVideo(
            checkpoint: VideoProgressCheckpoint,
        ): ProgressWriteResult = transaction {
            val target = requireNotNull(target(checkpoint.target))
            val previous = dao.progress(target.targetRowId)
            val revision =
                nextProgressRevision(previous?.revision ?: 0, checkpoint.expectedStateRevision)
                    ?: return@transaction ProgressWriteResult.Stale
            val context = checkpoint.context
            val binding = requireNotNull(dao.binding(context.bindingId.value.toString()))
            val asset = requireNotNull(dao.asset(context.assetId.value.toString()))
            require(
                binding.targetRowId == target.targetRowId && asset.bindingId == binding.bindingId,
            )
            require(asset.family == "VIDEO")
            require(context.assetRevision.value <= asset.revision)
            val row =
                ProgressRow(
                    target.targetRowId,
                    checkpoint.completion.name,
                    binding.bindingId,
                    asset.assetId,
                    context.assetRevision.value,
                    revision,
                    checkpoint.observedAtEpochMs,
                )
            if (previous == null) dao.insert(row) else dao.update(row)
            dao.anchor(
                AnchorRow(
                    target.targetRowId,
                    checkpoint.anchor.positionMs,
                    checkpoint.anchor.durationSnapshotMs,
                ),
            )
            ProgressWriteResult.Applied(
                requireNotNull(state(target.targetRowId, checkpoint.target)),
            )
        }

        override suspend fun markVideoCompleted(
            target: ConsumptionTargetRef,
            expectedStateRevision: Long,
            observedAtEpochMs: Long,
        ): ProgressWriteResult = transaction {
            val id = requireNotNull(target(target)).targetRowId
            val previous = dao.progress(id)
            val revision = nextProgressRevision(previous?.revision ?: 0, expectedStateRevision)
                ?: return@transaction ProgressWriteResult.Stale
            val row =
                previous?.copy(
                    completion = "COMPLETED",
                    revision = revision,
                    updatedAt = observedAtEpochMs,
                )
                    ?: ProgressRow(id, "COMPLETED", null, null, null, revision, observedAtEpochMs)
            if (previous == null) dao.insert(row) else dao.update(row)
            ProgressWriteResult.Applied(requireNotNull(state(id, target)))
        }
    }

    private fun state(id: Long, target: ConsumptionTargetRef): VideoProgressState? {
        val row = dao.progress(id) ?: return null
        val anchor = dao.anchor(id)
        return VideoProgressState(
            target,
            anchor?.let {
                VideoResumeAnchor(it.positionMs, it.durationMs)
            },
            CompletionState.valueOf(row.completion),
            anchor?.let {
                VideoResumeContext(
                    SourceBindingId(UUID.fromString(requireNotNull(row.bindingId))),
                    AssetId(UUID.fromString(requireNotNull(row.assetId))),
                    AssetRevision(requireNotNull(row.assetRevision)),
                )
            },
            row.revision,
            row.updatedAt,
        )
    }
}

private fun RootRow.domain() = StorageRoot(
    RootId(UUID.fromString(rootId)),
    LocalRootDescriptor(authority, treeLocator),
    generation,
    LocalAccessState.valueOf(access),
)

private fun encodeGaps(gaps: List<CoverageGap>): String = JSONArray().apply {
    gaps.forEach { gap ->
        put(
            JSONObject().apply {
                put("reason", gap.reason.name)
                gap.locator?.let {
                    put("rootId", it.rootId.value.toString())
                    put("authority", it.providerAuthority)
                    put("documentLocator", it.documentLocator)
                }
            },
        )
    }
}.toString()
