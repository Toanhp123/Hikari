package app.openstory.reader.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderDocumentRepository
import app.openstory.reader.content.ReaderLoadRequest
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.progress.ProgressUpdate
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.reader.progress.ReadingProgressService
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionPolicy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted assistedArgs: ReaderAssistedArgs,
    private val savedState: SavedStateHandle,
    private val chapters: ChapterRepository,
    private val documents: ReaderDocumentRepository,
    private val progress: ReadingProgressRepository,
    clock: Clock,
) : ViewModel() {
    private val storyId = StoryId(assistedArgs.storyId)
    private val chapterId = CanonicalChapterId(assistedArgs.chapterId)
    private val initialReleaseId = assistedArgs.releaseId?.let(::ChapterReleaseId)
    private val progressService = ReadingProgressService(progress, clock, viewModelScope)
    private var loadJob: Job? = null
    private val mutableState = MutableStateFlow(
        ReaderUiState(fontScale = savedState[FONT_SCALE_KEY] ?: 1f),
    )
    val state: StateFlow<ReaderUiState> = mutableState.asStateFlow()

    init {
        load(savedState.get<String>(RELEASE_ID_KEY)?.let(::ChapterReleaseId) ?: initialReleaseId)
    }

    fun retry() = load(mutableState.value.selectedReleaseId)

    fun selectRelease(releaseId: ChapterReleaseId) {
        savedState[RELEASE_ID_KEY] = releaseId.value
        load(releaseId, flushProgress = true)
    }

    fun increaseFont() = setFontScale(mutableState.value.fontScale + FONT_SCALE_STEP)
    fun decreaseFont() = setFontScale(mutableState.value.fontScale - FONT_SCALE_STEP)

    fun updatePosition(position: ReadingPosition, completed: Boolean) {
        val current = mutableState.value
        val releaseId = current.selectedReleaseId ?: return
        val document = current.document ?: return
        progressService.update(
            ProgressUpdate(storyId, chapterId, releaseId, document.fingerprint, position, completed),
        )
    }

    fun flushProgress() {
        viewModelScope.launch { progressService.flush() }
    }

    private fun load(explicitReleaseId: ChapterReleaseId?, flushProgress: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (flushProgress) progressService.flush()
            mutableState.value = mutableState.value.copy(loading = true, failure = null)
            try {
                val groups = chapters.observeOnce(storyId)
                val index = groups.indexOfFirst { it.chapter.id == chapterId }
                if (index < 0) {
                    fail(READER_CHAPTER_NOT_FOUND)
                    return@launch
                }
                loadGroup(groups, index, explicitReleaseId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                fail(READER_LOAD_FAILED)
            }
        }
    }

    private suspend fun loadGroup(
        groups: List<CanonicalChapterGroup>,
        index: Int,
        explicitReleaseId: ChapterReleaseId?,
    ) {
        val group = groups[index]
        val restored = progress.find(storyId, chapterId)
        val candidates = group.releases.map(::ReleaseCandidate)
        val policy = ReleaseSelectionPolicy(
            explicitReleaseId = explicitReleaseId,
            previousReleaseId = restored?.releaseId,
            previousPluginId = restored?.releaseId?.let { id ->
                group.releases.firstOrNull { it.id == id }?.pluginId
            },
        )
        val fingerprints = restored?.let { mapOf(it.releaseId to it.contentFingerprint) }.orEmpty()
        when (val result = documents.load(ReaderLoadRequest(candidates, policy, fingerprints))) {
            is ReaderLoadResult.Failure -> fail(result.attempts.joinToString { it.code }.ifBlank { READER_EMPTY })
            is ReaderLoadResult.Success -> show(groups, index, group, result, restored)
        }
    }

    private fun show(
        groups: List<CanonicalChapterGroup>,
        index: Int,
        group: CanonicalChapterGroup,
        result: ReaderLoadResult.Success,
        restored: app.openstory.reader.progress.ReadingProgress?,
    ) {
        val releaseId = result.release.release.id
        savedState[RELEASE_ID_KEY] = releaseId.value
        mutableState.value = mutableState.value.copy(
            loading = false,
            chapterLabel = group.chapter.displayLabel,
            document = result.document,
            releases = group.releases.map(ChapterRelease::toUiModel),
            selectedReleaseId = releaseId,
            previousChapterId = groups.getOrNull(index - 1)?.chapter?.id,
            nextChapterId = groups.getOrNull(index + 1)?.chapter?.id,
            restoredBlockId = restored?.takeIf { it.releaseId == releaseId }?.position?.blockId,
            restoredCharacterOffset = restored?.takeIf { it.releaseId == releaseId }?.position?.characterOffset ?: 0,
            availableOffline = result.fromStore,
            failure = null,
        )
    }

    private fun fail(code: String) {
        mutableState.value = mutableState.value.copy(loading = false, document = null, failure = code)
    }

    private fun setFontScale(value: Float) {
        val bounded = value.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
        savedState[FONT_SCALE_KEY] = bounded
        mutableState.value = mutableState.value.copy(fontScale = bounded)
    }

    @AssistedFactory
    interface Factory {
        fun create(args: ReaderAssistedArgs): ReaderViewModel
    }

    private companion object {
        const val RELEASE_ID_KEY = "reader.release-id"
        const val FONT_SCALE_KEY = "reader.font-scale"
        const val READER_CHAPTER_NOT_FOUND = "reader.chapter_not_found"
        const val READER_LOAD_FAILED = "reader.load_failed"
        const val READER_EMPTY = "reader.no_release_available"
    }
}

private suspend fun ChapterRepository.observeOnce(storyId: StoryId): List<CanonicalChapterGroup> =
    snapshot(storyId).let { snapshot ->
        snapshot.chapters.filterNot { it.tombstoned }.map { chapter ->
            CanonicalChapterGroup(chapter, snapshot.releases.filter { it.canonicalChapterId == chapter.id })
        }
    }

private fun ChapterRelease.toUiModel() = ReaderReleaseUiModel(
    id,
    displayLabel,
    pluginId.value,
    languageTag,
)
