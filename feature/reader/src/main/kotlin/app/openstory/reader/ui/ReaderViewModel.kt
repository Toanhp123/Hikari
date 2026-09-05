package app.openstory.reader.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.assets.ReaderAssetCoordinator
import app.openstory.reader.assets.ReaderCommittedAssetManifestSnapshot
import app.openstory.reader.assets.ReaderPageAssetRequest
import app.openstory.reader.assets.ReaderViewportSnapshot
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.preferences.ReaderPreferences
import app.openstory.reader.preferences.ReaderPreferencesPort
import app.openstory.reader.progress.ProgressUpdate
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.progress.ReadingProgressService
import app.openstory.reader.routing.ReaderForegroundIntent
import app.openstory.reader.routing.ReaderForegroundResult
import app.openstory.reader.routing.ReaderRouteSessionFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted assistedArgs: ReaderAssistedArgs,
    private val savedState: SavedStateHandle,
    private val chapters: ChapterRepository,
    routeSessions: ReaderRouteSessionFactory,
    private val assetCoordinator: ReaderAssetCoordinator,
    private val progress: ReadingProgressRepository,
    clock: Clock,
    private val preferences: ReaderPreferencesPort = DefaultReaderPreferencesPort,
) : ViewModel() {
    private data class CommittedReaderContent(
        val chapterId: CanonicalChapterId,
        val releaseId: ChapterReleaseId,
        val document: ReaderDocument,
    )

    private data class ReaderTransitionTarget(
        val chapterId: CanonicalChapterId,
        val explicitReleaseId: ChapterReleaseId?,
    )

    private val storyId = StoryId(assistedArgs.storyId)
    private val initialChapterId = CanonicalChapterId(savedState[CHAPTER_ID_KEY] ?: assistedArgs.chapterId)
    private val initialReleaseId = savedState.get<String>(RELEASE_ID_KEY)
        ?.let(::ChapterReleaseId)
        ?: assistedArgs.releaseId?.let(::ChapterReleaseId)
    private val routeSession = routeSessions.create(storyId, viewModelScope)
    private val progressService = ReadingProgressService(progress, clock, viewModelScope)
    private var committed: CommittedReaderContent? = null
    private var transitionTarget: ReaderTransitionTarget? = null
    private var failedTarget: ReaderTransitionTarget? = null
    private var loadJob: Job? = null
    private var currentPreferences = ReaderPreferences()
    private var preferenceReady = false
    private var chapterGraphReady = false
    private var latestChapterOrder: List<CanonicalChapterId> = emptyList()
    private var initialLoadStarted = false
    private var handledRouteInvalidationRevision: Long? = null
    private val mutableState = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.preferences.collect { next ->
                currentPreferences = next.copy(fontScale = next.normalizedFontScale)
                routeSession.updateRoutingPreferences(currentPreferences)
                preferenceReady = true
                mutableState.value = mutableState.value.copy(
                    fontScale = currentPreferences.normalizedFontScale,
                    preferenceFailure = null,
                )
                maybeStartInitialLoad()
            }
        }
        viewModelScope.launch {
            chapters.observe(storyId).collect { groups ->
                // Keep the reactive graph boundary equivalent to the legacy snapshot projection:
                // tombstoned canonical chapters are maintenance history, never Reader navigation targets.
                val readerGroups = groups.filterNot { group -> group.chapter.tombstoned }
                routeSession.updateChapterGraph(readerGroups)
                latestChapterOrder = readerGroups.map { group -> group.chapter.id }
                refreshCommittedNavigation()
                chapterGraphReady = true
                maybeStartInitialLoad()
            }
        }
        viewModelScope.launch {
            assetCoordinator.observeCommittedManifest(routeSession.sessionId).collect { snapshot ->
                acceptCommittedAssetSnapshot(snapshot)
            }
        }
    }

    fun retry() {
        val target = failedTarget ?: committed?.let { current ->
            ReaderTransitionTarget(
                chapterId = current.chapterId,
                explicitReleaseId = current.releaseId,
            )
        } ?: ReaderTransitionTarget(initialChapterId, initialReleaseId)
        startLoad(target.chapterId, target.explicitReleaseId, flushProgress = committed != null)
    }

    fun selectRelease(releaseId: ChapterReleaseId) {
        val current = committed ?: return
        if (transitionTarget == null && current.releaseId == releaseId) return
        startLoad(current.chapterId, releaseId, flushProgress = true)
    }

    fun openChapter(chapterId: CanonicalChapterId) {
        val pending = transitionTarget
        val current = committed
        val repeatsPendingAutomaticTarget = pending?.chapterId == chapterId && pending.explicitReleaseId == null
        val initialLoadPending = current == null && initialLoadStarted
        val repeatsInitialTarget = chapterId == initialChapterId && pending != null

        when {
            repeatsPendingAutomaticTarget -> {
                if (failedTarget === pending) {
                    startLoad(chapterId, explicitReleaseId = null, flushProgress = current != null)
                }
            }
            current?.chapterId == chapterId -> {
                if (pending != null) cancelTransitionAndKeepCommitted()
            }
            initialLoadPending && repeatsInitialTarget -> Unit
            else -> startLoad(chapterId, explicitReleaseId = null, flushProgress = current != null)
        }
    }

    fun increaseFont() = setFontScale(mutableState.value.fontScale + FONT_SCALE_STEP)
    fun decreaseFont() = setFontScale(mutableState.value.fontScale - FONT_SCALE_STEP)

    fun updatePosition(position: ReadingPosition, completed: Boolean) {
        val current = committed ?: return
        progressService.update(
            ProgressUpdate(
                storyId = storyId,
                canonicalChapterId = current.chapterId,
                releaseId = current.releaseId,
                contentFingerprint = current.document.fingerprint,
                position = position,
                completed = completed,
            ),
        )
    }

    fun flushProgress() {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { progressService.flush() }
        }
    }

    fun updateAssetViewport(snapshot: ReaderViewportSnapshot): Boolean {
        val accepted = mutableState.value.assets?.matches(snapshot) == true
        if (accepted) assetCoordinator.updateViewport(snapshot)
        return accepted
    }

    fun assetPresented(request: ReaderPageAssetRequest) {
        val assets = mutableState.value.assets ?: return
        if (!assets.matches(request)) return
        assetCoordinator.assetPresented(request)
    }

    fun reloadRouteForInvalidatedAsset(manifestRevision: Long) {
        val current = committed
        if (current != null && shouldReloadInvalidatedAsset(manifestRevision)) {
            handledRouteInvalidationRevision = manifestRevision
            startLoad(
                chapterId = current.chapterId,
                explicitReleaseId = current.releaseId,
                flushProgress = true,
            )
        }
    }

    private fun shouldReloadInvalidatedAsset(manifestRevision: Long): Boolean =
        mutableState.value.assets?.manifestRevision == manifestRevision &&
            transitionTarget == null &&
            handledRouteInvalidationRevision != manifestRevision

    override fun onCleared() {
        routeSession.close()
        super.onCleared()
    }

    private fun maybeStartInitialLoad() {
        if (initialLoadStarted || !preferenceReady || !chapterGraphReady) return
        initialLoadStarted = true
        startLoad(initialChapterId, initialReleaseId, flushProgress = false)
    }

    private fun startLoad(
        chapterId: CanonicalChapterId,
        explicitReleaseId: ChapterReleaseId?,
        flushProgress: Boolean,
    ) {
        loadJob?.cancel()
        val target = ReaderTransitionTarget(
            chapterId = chapterId,
            explicitReleaseId = explicitReleaseId,
        )
        transitionTarget = target
        failedTarget = null
        mutableState.value = mutableState.value.copy(
            loading = committed == null,
            transitionTargetChapterId = chapterId,
            transitionTargetReleaseId = explicitReleaseId,
            failure = null,
            failureRetryable = true,
        )
        loadJob = viewModelScope.launch {
            try {
                if (flushProgress) flushProgressBestEffort()
                when (
                    val result = routeSession.execute(
                        ReaderForegroundIntent(chapterId, explicitReleaseId),
                    )
                ) {
                    is ReaderForegroundResult.Committed -> commit(target, result)
                    is ReaderForegroundResult.Exhausted -> fail(target, result.code, result.retryable)
                    is ReaderForegroundResult.Superseded -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(target, READER_LOAD_FAILED, retryable = true)
            }
        }
    }

    private fun commit(
        target: ReaderTransitionTarget,
        result: ReaderForegroundResult.Committed,
    ) {
        if (!isCurrent(target) || result.identity.targetChapterId != target.chapterId) return
        val nextCommitted = CommittedReaderContent(
            chapterId = result.identity.targetChapterId,
            releaseId = result.release.id,
            document = result.document,
        )
        val nextAssets = result.toAssetUiState(nextCommitted)
        committed = nextCommitted
        transitionTarget = null
        failedTarget = null
        handledRouteInvalidationRevision = null

        // Saved identity is committed identity only. Publish UI only after both keys are updated.
        savedState[CHAPTER_ID_KEY] = nextCommitted.chapterId.value
        savedState[RELEASE_ID_KEY] = nextCommitted.releaseId.value

        val (previousChapterId, nextChapterId) = navigationAround(nextCommitted.chapterId)
        mutableState.value = mutableState.value.copy(
            loading = false,
            committedChapterId = nextCommitted.chapterId,
            chapterLabel = result.chapterGroup.chapter.displayLabel,
            document = nextCommitted.document,
            assets = nextAssets,
            releases = result.chapterGroup.releases.map(ChapterRelease::toUiModel),
            selectedReleaseId = nextCommitted.releaseId,
            previousChapterId = previousChapterId,
            nextChapterId = nextChapterId,
            transitionTargetChapterId = null,
            transitionTargetReleaseId = null,
            restoredBlockId = result.restoration?.blockId,
            restoredCharacterOffset = result.restoration?.characterOffset ?: 0,
            restoredProgressFraction = result.restoration?.progressFraction ?: 0f,
            availableOffline = result.fromLocal,
            failure = null,
            failureRetryable = true,
        )
    }

    private fun acceptCommittedAssetSnapshot(snapshot: ReaderCommittedAssetManifestSnapshot) {
        val current = committed ?: return
        val nextAssets = snapshot.toReaderAssetUiStateIfCurrent(
            activeSessionId = routeSession.sessionId,
            activeChapterId = current.chapterId,
            activeReleaseId = current.releaseId,
            currentManifestRevision = mutableState.value.assets?.manifestRevision,
        ) ?: return
        handledRouteInvalidationRevision = null
        mutableState.value = mutableState.value.copy(assets = nextAssets)
    }

    private fun ReaderForegroundResult.Committed.toAssetUiState(
        current: CommittedReaderContent,
    ): ReaderAssetUiState? {
        val manifest = assetManifest
        val revision = assetManifestRevision
        check((manifest == null) == (revision == null)) {
            "Reader committed asset manifest and revision must be published together."
        }
        if (manifest == null || revision == null) return null
        check(manifest.sessionId == routeSession.sessionId) {
            "Reader asset manifest must belong to the active route session."
        }
        check(manifest.canonicalChapterId == current.chapterId) {
            "Reader asset manifest must belong to the committed chapter."
        }
        check(manifest.selectedReleaseId == current.releaseId) {
            "Reader asset manifest must belong to the committed release."
        }
        return ReaderAssetUiState(manifest, revision)
    }

    private fun ReaderAssetUiState.matches(snapshot: ReaderViewportSnapshot): Boolean =
        manifest.sessionId == snapshot.sessionId &&
            manifestRevision == snapshot.manifestRevision

    private fun ReaderAssetUiState.matches(request: ReaderPageAssetRequest): Boolean =
        manifest.sessionId == request.sessionId &&
            manifestRevision == request.manifestRevision &&
            manifest.descriptors.getOrNull(request.descriptor.imageOrdinal) == request.descriptor

    private fun fail(
        target: ReaderTransitionTarget,
        code: String,
        retryable: Boolean,
    ) {
        if (!isCurrent(target)) return
        failedTarget = target
        mutableState.value = mutableState.value.copy(
            loading = false,
            transitionTargetChapterId = target.chapterId,
            transitionTargetReleaseId = target.explicitReleaseId,
            failure = code,
            failureRetryable = retryable,
        )
    }

    private fun refreshCommittedNavigation() {
        val current = committed ?: return
        val (previousChapterId, nextChapterId) = navigationAround(current.chapterId)
        mutableState.value = mutableState.value.copy(
            previousChapterId = previousChapterId,
            nextChapterId = nextChapterId,
        )
    }

    private fun navigationAround(
        chapterId: CanonicalChapterId,
    ): Pair<CanonicalChapterId?, CanonicalChapterId?> {
        val index = latestChapterOrder.indexOf(chapterId)
        if (index < 0) return null to null
        return latestChapterOrder.getOrNull(index - 1) to latestChapterOrder.getOrNull(index + 1)
    }

    private fun cancelTransitionAndKeepCommitted() {
        loadJob?.cancel()
        loadJob = null
        transitionTarget = null
        failedTarget = null
        mutableState.value = mutableState.value.copy(
            loading = false,
            transitionTargetChapterId = null,
            transitionTargetReleaseId = null,
            failure = null,
            failureRetryable = true,
        )
    }

    private fun isCurrent(target: ReaderTransitionTarget): Boolean = transitionTarget === target

    private suspend fun flushProgressBestEffort() {
        try {
            progressService.flush()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Navigation is not made unavailable because best-effort progress persistence failed.
        }
    }

    private fun setFontScale(value: Float) {
        val bounded = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        mutableState.value = mutableState.value.copy(fontScale = bounded, preferenceFailure = null)
        viewModelScope.launch {
            try {
                preferences.setFontScale(bounded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(
                    fontScale = currentPreferences.normalizedFontScale,
                    preferenceFailure = READER_PREFERENCES_WRITE_FAILED,
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(args: ReaderAssistedArgs): ReaderViewModel
    }

    private companion object {
        const val RELEASE_ID_KEY = "reader.release-id"
        const val CHAPTER_ID_KEY = "reader.chapter-id"
        const val READER_LOAD_FAILED = "reader.load_failed"
        const val READER_PREFERENCES_WRITE_FAILED = "reader.preferences_write_failed"
    }
}

private object DefaultReaderPreferencesPort : ReaderPreferencesPort {
    override val preferences = kotlinx.coroutines.flow.flowOf(ReaderPreferences())

    override suspend fun setFontScale(value: Float) = Unit
}

internal fun ChapterGraphSnapshot.toReaderGroups(): List<CanonicalChapterGroup> {
    val releasesByChapter = releases.groupBy { release -> release.canonicalChapterId }
    return chapters.filterNot { chapter -> chapter.tombstoned }.map { chapter ->
        CanonicalChapterGroup(chapter, releasesByChapter[chapter.id].orEmpty())
    }
}

private fun ChapterRelease.toUiModel() = ReaderReleaseUiModel(
    id,
    displayLabel,
    pluginId.value,
    languageTag,
)
