package app.openstory.catalog.feature.seed

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogDiscoverCapability
import app.openstory.catalog.domain.source.CatalogStoryCapability
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred

internal class BenchmarkPinPruneSource(
    private val delegate: BenchmarkCatalogSource,
    private val assertWorkerThread: () -> Unit = ::assertNotMainThread,
) : CatalogDiscoverCapability, CatalogStoryCapability {
    private val pruneArmed = AtomicBoolean(false)
    private val continuePrune = CompletableDeferred<Unit>()
    val pruneEntered = CompletableDeferred<Unit>()

    fun armPrune() {
        check(pruneArmed.compareAndSet(false, true))
    }

    fun releasePrune() {
        continuePrune.complete(Unit)
    }

    override suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition {
        if (mediaType == CatalogMediaType.MANGA && pruneArmed.compareAndSet(true, false)) {
            assertWorkerThread()
            pruneEntered.complete(Unit)
            continuePrune.await()
            return DiscoverAcquisition(emptyList())
        }
        return delegate.acquireDiscover(mediaType)
    }

    override suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition =
        delegate.acquireStoryDetail(ref)
}
