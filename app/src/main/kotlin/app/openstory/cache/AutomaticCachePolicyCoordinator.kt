package app.openstory.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.settings.AppSettingsRepository
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class AutomaticCachePolicyCoordinator @Inject constructor(
    settings: AppSettingsRepository,
    progress: ReadingProgressRepository,
    private val budget: AutomaticCacheBudgetCoordinator,
) {
    private val started = AtomicBoolean(false)
    private val policies = combine(
        settings.settings
            .map { current -> current.normalized().automaticCacheQuotaBytes }
            .distinctUntilChanged(),
        progress.observeAll()
            .map { rows ->
                rows.asSequence()
                    .filter { it.completedAtEpochMillis == null }
                    .mapTo(linkedSetOf()) { it.releaseId }
            }
            .distinctUntilChanged(),
    ) { quotaBytes, protectedReleaseIds ->
        AutomaticCachePolicy(quotaBytes, protectedReleaseIds)
    }.distinctUntilChanged()

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            policies.collect { policy ->
                try {
                    budget.updateQuota(policy.quotaBytes)
                    budget.updateProgressProtectedReleaseIds(policy.progressProtectedReleaseIds)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A later Settings/progress emission retries projection without killing app scope.
                }
            }
        }
    }
}

private data class AutomaticCachePolicy(
    val quotaBytes: Long,
    val progressProtectedReleaseIds: Set<ChapterReleaseId>,
)
