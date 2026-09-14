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
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.domain.read.StoryRoutePreview
import app.openstory.catalog.feature.assets.CatalogImageLoader
import app.openstory.catalog.feature.assets.LocalCatalogImageLoader
import app.openstory.catalog.feature.discover.DiscoverContentState
import app.openstory.catalog.feature.discover.DiscoverRuntime
import app.openstory.catalog.feature.discover.DiscoverRuntimeActivation
import app.openstory.catalog.feature.discover.DiscoverViewModel
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.feature.trace.CatalogUiTrace
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogCapabilitySession
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import java.util.concurrent.atomic.AtomicBoolean
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
    suspend fun activate(): CatalogCapabilityActivation = holder.runtime.activate()

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
        CatalogRuntimeHolder.factory {
            val runtime = CatalogRuntimeHost(
                session = CatalogRuntimeFactory(
                    context = applicationContext,
                    binding = VariantCatalogBinding.binding,
                    traceSink = AndroidCatalogTraceSink,
                    queryListener = VariantCatalogBinding.queryListener,
                    ownershipCallbacks = VariantCatalogBinding.diagnostics.runtimeOwnershipCallbacks,
                ).createSession(),
                onActivationStarted = {
                    VariantCatalogBinding.diagnostics.activationStarted()
                },
                onStorageReady = VariantCatalogBinding.diagnostics::storageReady,
                onDiscoverCollectorStarted = VariantCatalogBinding.diagnostics::discoverCollectorStarted,
                onDiscoverCollectorStopped = VariantCatalogBinding.diagnostics::discoverCollectorStopped,
                onClosed = VariantCatalogBinding.diagnostics::runtimeSessionClosed,
            )
            runtime to CatalogImageLoader(
                context = applicationContext,
                localResolver = VariantLocalCoverAssets,
                remoteTransport = VariantCatalogBinding.remoteCoverTransport(applicationContext),
                policyProvider = runtime::assetPolicyProvider,
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
        DiscoverViewModel.factory(mediaType, runtimeHolder.runtime::discoverRuntime)
    }
    return viewModel(
        key = DiscoverViewModel.key(mediaType),
        factory = discoverFactory,
    )
}

internal class CatalogRuntimeHost(
    private val session: CatalogCapabilitySession,
    private val onActivationStarted: () -> Unit,
    private val onStorageReady: () -> Unit,
    private val onDiscoverCollectorStarted: () -> Unit,
    private val onDiscoverCollectorStopped: () -> Unit,
    private val onClosed: () -> Unit,
) : AutoCloseable {
    private val activationMutex = Mutex()
    private val closed = AtomicBoolean(false)
    private var cachedActivation: CatalogCapabilityActivation? = null

    suspend fun activate(): CatalogCapabilityActivation = activationMutex.withLock {
        cachedActivation?.let { return it }
        onActivationStarted()
        session.activate().also { activation ->
            if (activation is CatalogCapabilityActivation.Available) onStorageReady()
            cachedActivation = activation
        }
    }

    fun discoverRuntime(): DiscoverRuntime = object : DiscoverRuntime {
        override suspend fun activate(): DiscoverRuntimeActivation =
            when (val activation = this@CatalogRuntimeHost.activate()) {
                is CatalogCapabilityActivation.Unavailable ->
                    DiscoverRuntimeActivation.Unavailable(activation.failure)
                is CatalogCapabilityActivation.Available -> DiscoverRuntimeActivation.Available(
                    observe = { mediaType ->
                        activation.discoverSession(mediaType).states
                            .onStart { onDiscoverCollectorStarted() }
                            .onCompletion { onDiscoverCollectorStopped() }
                    },
                    refresh = { mediaType -> activation.discoverSession(mediaType).refresh() },
                    quiesce = { mediaType -> activation.discoverSession(mediaType).quiesce() },
                )
            }

        override fun close() = Unit
    }

    suspend fun assetPolicyProvider(): SourceAssetPolicyProvider? =
        when (val activation = activate()) {
            is CatalogCapabilityActivation.Unavailable -> null
            is CatalogCapabilityActivation.Available -> activation.assetPolicyProvider
        }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        session.close()
        onClosed()
    }
}

internal class CatalogRuntimeHolder(
    val runtime: CatalogRuntimeHost,
    val images: CatalogImageLoader,
) : ViewModel() {
    override fun onCleared() {
        images.close()
        runtime.close()
    }

    companion object {
        fun factory(createRuntime: () -> Pair<CatalogRuntimeHost, CatalogImageLoader>): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(CatalogRuntimeHolder::class.java))
                    val (runtime, images) = createRuntime()
                    return CatalogRuntimeHolder(runtime, images) as T
                }
            }
    }
}
