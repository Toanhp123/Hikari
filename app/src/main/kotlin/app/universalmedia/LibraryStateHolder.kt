package app.universalmedia

import app.universalmedia.core.domain.LibraryQueries
import app.universalmedia.core.domain.LibraryRootSummary
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.model.RootId
import app.universalmedia.feature.library.LibraryCardUi
import app.universalmedia.feature.library.LibraryError
import app.universalmedia.feature.library.LibraryRootUi
import app.universalmedia.feature.library.LibraryScanStatus
import app.universalmedia.feature.library.LibraryUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Registration survives Activity recreation; only durable data restores after process death. */
internal class LibraryStateHolder(
    private val queries: LibraryQueries,
    private val coordinator: LibraryCoordinator,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(LibraryUiState())
    val state = mutableState.asStateFlow()
    private var cardsJob: Job? = null
    private var rootsJob: Job? = null
    private var observationFailed = false

    init {
        refresh()
    }

    fun refresh() {
        cardsJob?.cancel()
        rootsJob?.cancel()
        observationFailed = false
        mutableState.update { it.copy(error = null) }
        cardsJob = observe {
            queries.observeLibraryCards { cards ->
                mutableState.update {
                    it.copy(
                        cards = cards.map { card ->
                            LibraryCardUi(card.mediaId, card.fallbackDisplayName)
                        },
                    )
                }
            }
        }
        rootsJob = observe {
            queries.observeLibraryRoots { roots ->
                mutableState.update { state ->
                    state.copy(roots = roots.map { LibraryRootUi(it.rootId, it.uiStatus()) })
                }
            }
        }
    }

    private fun observe(block: suspend () -> Unit): Job = scope.launch {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            observationFailed = true
            mutableState.update { it.copy(error = LibraryError.LIBRARY) }
        }
    }

    fun register(readEvidence: suspend () -> RootRegistrationEvidence?) {
        if (state.value.isAddingRoot) return
        mutableState.update {
            it.copy(
                isAddingRoot = true,
                error = if (observationFailed) LibraryError.LIBRARY else null,
            )
        }
        scope.launch {
            try {
                val evidence = readEvidence()
                val error = if (evidence ==
                    null
                ) {
                    LibraryError.REGISTRATION
                } else {
                    coordinator.register(evidence)
                }
                mutableState.update {
                    it.copy(
                        error =
                            error ?: if (observationFailed) LibraryError.LIBRARY else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(error = LibraryError.REGISTRATION) }
            } finally {
                mutableState.update { it.copy(isAddingRoot = false) }
            }
        }
    }

    fun retry(rootId: RootId) {
        scope.launch {
            val error = coordinator.retry(rootId)
            mutableState.update {
                it.copy(
                    error =
                        error ?: if (observationFailed) LibraryError.LIBRARY else null,
                )
            }
        }
    }
}

private fun LibraryRootSummary.uiStatus(): LibraryScanStatus {
    val finalOutcome = outcome
    return when {
        access == LocalAccessState.ACCESS_LOST -> LibraryScanStatus.ACCESS_LOST
        access == LocalAccessState.UNAVAILABLE -> LibraryScanStatus.UNAVAILABLE
        !hasRun -> LibraryScanStatus.IDLE
        finalOutcome == null -> LibraryScanStatus.RUNNING
        else -> LibraryScanStatus.valueOf(finalOutcome.name)
    }
}
