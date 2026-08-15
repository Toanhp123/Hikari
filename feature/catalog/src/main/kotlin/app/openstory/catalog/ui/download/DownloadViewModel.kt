package app.openstory.catalog.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val service: DownloadService,
    private val scheduler: DownloadScheduler,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = mutableState.asStateFlow()
    val statuses = service.observeAll().map { records ->
        records.associate { record -> record.key.releaseId to record.state }
    }

    fun download(releaseId: ChapterReleaseId) = command {
        service.queue(releaseId, System.currentTimeMillis())
        scheduler.schedule(releaseId)
    }

    fun downloadRange(releaseIds: List<ChapterReleaseId>) = releaseIds.distinct().forEach(::download)
    fun downloadFiltered(releaseIds: List<ChapterReleaseId>) = downloadRange(releaseIds)

    fun cancel(releaseId: ChapterReleaseId) = command {
        scheduler.cancel(releaseId)
        service.cancel(releaseId, System.currentTimeMillis())
    }

    fun retry(releaseId: ChapterReleaseId) = download(releaseId)

    fun requestRemoval(releaseId: ChapterReleaseId) {
        mutableState.update { it.copy(pendingRemoval = releaseId) }
    }

    fun dismissRemoval() {
        mutableState.update { it.copy(pendingRemoval = null) }
    }

    fun confirmRemoval() {
        val releaseId = mutableState.value.pendingRemoval ?: return
        mutableState.update { it.copy(pendingRemoval = null) }
        cancel(releaseId)
    }

    private fun command(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                mutableState.update { it.copy(failure = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(failure = "download.command_failed") }
            }
        }
    }
}

data class DownloadUiState(
    val pendingRemoval: ChapterReleaseId? = null,
    val failure: String? = null,
)
