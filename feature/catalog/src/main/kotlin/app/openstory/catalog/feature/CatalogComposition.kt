package app.openstory.catalog.feature

import android.content.Context
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.catalog.domain.source.CatalogAuthorityResolver
import app.openstory.catalog.feature.assets.CatalogImageLoader
import app.openstory.catalog.feature.assets.LocalCatalogImageLoader
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverRuntime
import app.openstory.catalog.feature.discover.DiscoverRuntimeActivation
import app.openstory.catalog.feature.discover.DiscoverViewModel
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.feature.trace.CatalogUiTrace
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.CatalogRuntimeHost
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun CatalogComposition(
    mediaType: CatalogMediaType,
    onStorySelected: (StoryRouteArgs) -> Unit,
) {
    val runtimeAccess = rememberCatalogRuntimeAccess()
    CatalogSessionContent(
        runtimeHolder = runtimeAccess.holder,
        trace = runtimeAccess.trace,
        mediaType = mediaType,
        onStorySelected = onStorySelected,
    )
}

class CatalogRuntimeAccess internal constructor(
    internal val holder: CatalogRuntimeHolder,
    internal val trace: CatalogUiTrace,
) {
    suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation =
        holder.activate(sourceKey)

    fun storyCollectorStarted() = VariantCatalogBinding.diagnostics.storyCollectorStarted()

    fun storyCollectorStopped() = VariantCatalogBinding.diagnostics.storyCollectorStopped()

    fun storyUiPublished() = trace.storyUiPublished()

    fun storyHeroMaterialized() = trace.storyHeroMaterialized()

    fun storyBodyMaterialized() = trace.storyBodyMaterialized()
}

@Composable
fun rememberCatalogRuntimeAccess(): CatalogRuntimeAccess {
    val applicationContext = LocalContext.current.applicationContext
    val trace = remember { CatalogUiTrace(AndroidCatalogTraceSink) }
    val holder = rememberCatalogRuntimeHolder(applicationContext, trace)
    return remember(holder, trace) { CatalogRuntimeAccess(holder, trace) }
}

@Composable
private fun rememberCatalogRuntimeHolder(
    applicationContext: Context,
    trace: CatalogUiTrace,
): CatalogRuntimeHolder {
    val runtimeFactory = remember(applicationContext, trace) {
        CatalogRuntimeHolder.factory(
            diagnostics = VariantCatalogBinding.diagnostics,
        ) {
            val runtime = CatalogRuntimeFactory(
                context = applicationContext,
                bindings = VariantCatalogBinding.bindings,
                traceSink = AndroidCatalogTraceSink,
                queryListener = VariantCatalogBinding.queryListener,
                ownershipCallbacks = VariantCatalogBinding.diagnostics.runtimeOwnershipCallbacks,
            ).createHost()
            runtime to CatalogImageLoader(
                context = applicationContext,
                localResolver = VariantLocalCoverAssets,
                remoteTransport = VariantCatalogBinding.remoteCoverTransport(applicationContext),
                policyProvider = {
                    SourceAssetPolicyProvider { sourceKey -> runtime.descriptor(sourceKey)?.artworkPolicy }
                },
                callbacks = VariantCatalogBinding.diagnostics.imageLoaderCallbacks,
            )
        }
    }
    return viewModel(factory = runtimeFactory)
}

@Composable
private fun CatalogSessionContent(
    runtimeHolder: CatalogRuntimeHolder,
    trace: CatalogUiTrace,
    mediaType: CatalogMediaType,
    onStorySelected: (StoryRouteArgs) -> Unit,
) {
    val discoverListState = rememberLazyListState()
    val discoverViewModel = rememberDiscoverViewModel(runtimeHolder, mediaType)
    val discoverState by discoverViewModel.state.collectAsStateWithLifecycle()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, discoverViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> discoverViewModel.resume()
                Lifecycle.Event.ON_STOP -> discoverViewModel.quiesce()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            discoverViewModel.resume()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            discoverViewModel.quiesce()
        }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val imageLoader = runtimeHolder.images.takeIf { lifecycleState.isAtLeast(Lifecycle.State.STARTED) }
    LaunchedEffect(discoverState.content) {
        val content = discoverState.content as? DiscoverContentState.Content
        if (content?.sections?.any { it.cards.isNotEmpty() } == true) {
            trace.discoverContentReady()
        }
    }

    val actions = remember(discoverViewModel, mediaType, onStorySelected) {
        CatalogScreenActions(
            onStorySelected = { card ->
                onStorySelected(
                    StoryRouteArgs(
                        ref = card.ref,
                        originMediaContext = mediaType,
                        preview = StoryRoutePreview(
                            title = card.title,
                            coverLocator = card.coverLocator,
                            coverAssetKey = card.coverAssetKey,
                        ),
                    ),
                )
            },
            onDiscoverRefresh = discoverViewModel::refresh,
            onDiscoverRetry = discoverViewModel::retry,
        )
    }

    CompositionLocalProvider(LocalCatalogImageLoader provides imageLoader) {
        CatalogScreen(
            mediaType = mediaType,
            discoverListState = discoverListState,
            discoverState = discoverState,
            actions = actions,
            onDiscoverCoverReady = trace::discoverCoverReady,
        )
    }
}

@Composable
private fun rememberDiscoverViewModel(
    runtimeHolder: CatalogRuntimeHolder,
    mediaType: CatalogMediaType,
): DiscoverViewModel {
    val discoverFactory = remember(runtimeHolder, mediaType) {
        DiscoverViewModel.factory(mediaType) { runtimeHolder.discoverRuntime(mediaType) }
    }
    return viewModel(
        key = DiscoverViewModel.key(mediaType),
        factory = discoverFactory,
    )
}

internal class CatalogRuntimeHolder(
    val runtime: CatalogRuntimeHost,
    val images: CatalogImageLoader,
    private val diagnostics: CatalogCompositionDiagnostics,
) : ViewModel() {
    private val activationMutex = Mutex()
    private val activatedSources = mutableSetOf<CatalogSourceKey>()
    private var storageReadyReported = false

    suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation =
        activationMutex.withLock {
            if (sourceKey !in activatedSources) diagnostics.activationStarted()
            runtime.activate(sourceKey).also { activation ->
                if (activation is CatalogCapabilityActivation.Available && !storageReadyReported) {
                    storageReadyReported = true
                    diagnostics.storageReady()
                }
                activatedSources += sourceKey
            }
        }

    fun discoverRuntime(mediaType: CatalogMediaType): DiscoverRuntime {
        return createFrozenDiscoverRuntime(
            mediaType = mediaType,
            authorityResolver = runtime.authorityResolver(),
            activateCatalog = ::activate,
            diagnostics = diagnostics,
        )
    }

    override fun onCleared() {
        images.close()
        runtime.close()
        diagnostics.runtimeSessionClosed()
    }

    companion object {
        fun factory(
            diagnostics: CatalogCompositionDiagnostics,
            createRuntime: () -> Pair<CatalogRuntimeHost, CatalogImageLoader>,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CatalogRuntimeHolder::class.java))
                    val (runtime, images) = createRuntime()
                    return CatalogRuntimeHolder(runtime, images, diagnostics) as T
                }
            }
    }
}

internal fun createFrozenDiscoverRuntime(
    mediaType: CatalogMediaType,
    authorityResolver: CatalogAuthorityResolver,
    activateCatalog: suspend (CatalogSourceKey) -> CatalogCapabilityActivation,
    diagnostics: CatalogCompositionDiagnostics = NoOpCatalogCompositionDiagnostics,
): DiscoverRuntime {
    val frozenSourceKey = authorityResolver.authorityFor(mediaType)
    return object : DiscoverRuntime {
        override suspend fun activate(): DiscoverRuntimeActivation =
            when (val sourceKey = frozenSourceKey) {
                null -> DiscoverRuntimeActivation.Unavailable(
                    app.openstory.catalog.domain.failure.CatalogFailure.SourceUnavailable,
                )
                else -> when (val activation = activateCatalog(sourceKey)) {
                    is CatalogCapabilityActivation.Unavailable ->
                        DiscoverRuntimeActivation.Unavailable(activation.failure)
                    is CatalogCapabilityActivation.Available -> DiscoverRuntimeActivation.Available(
                        observe = { requestedMediaType ->
                            activation.discoverSession(requestedMediaType).states
                                .onStart { diagnostics.discoverCollectorStarted() }
                                .onCompletion { diagnostics.discoverCollectorStopped() }
                        },
                        refresh = { requestedMediaType ->
                            activation.discoverSession(requestedMediaType).refresh()
                        },
                        quiesce = { requestedMediaType ->
                            activation.discoverSession(requestedMediaType).quiesce()
                        },
                    )
                }
            }

        override fun close() = Unit
    }
}
