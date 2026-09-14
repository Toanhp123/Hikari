package app.openstory.story.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycle
import app.openstory.common.navigation.RouteLifecycleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class StoryPresentationStore internal constructor(
    private val scope: CoroutineScope,
    lifecycleSource: RouteLifecycleSource,
) : AutoCloseable {
    private val owners = linkedMapOf<RouteEntryId, StoryPresentationOwner>()
    private val lifecycleJob: Job = scope.launch {
        lifecycleSource.changes.collect { change ->
            val owner = owners[change.entryId] ?: return@collect
            when (change.lifecycle) {
                RouteLifecycle.ACTIVE -> owner.activate()
                RouteLifecycle.RETAINED -> owner.quiesce()
                RouteLifecycle.RELEASED -> owners.remove(change.entryId)?.release()
            }
        }
    }

    internal fun ownerFor(
        entryId: RouteEntryId,
        args: StoryRouteArgs,
        catalogFacet: StoryCatalogFacet,
        onUiPublished: () -> Unit = {},
    ): StoryPresentationOwner {
        owners[entryId]?.let { existing ->
            require(existing.args == args) {
                "Route entry $entryId cannot be rebound to different Story arguments"
            }
            return existing
        }

        return StoryPresentationOwner(
            args = args,
            catalogFacet = catalogFacet,
            onUiPublished = onUiPublished,
            coroutineScope = scope,
        ).also { owner ->
            owners[entryId] = owner
            owner.activate()
        }
    }


    override fun close() {
        lifecycleJob.cancel()
        owners.values.forEach(StoryPresentationOwner::release)
        owners.clear()
    }
}

@Composable
fun rememberStoryPresentationStore(
    lifecycleSource: RouteLifecycleSource,
): StoryPresentationStore {
    val scope = rememberCoroutineScope()
    val store = remember(lifecycleSource, scope) {
        StoryPresentationStore(scope, lifecycleSource)
    }
    DisposableEffect(store) {
        onDispose(store::close)
    }
    return store
}
