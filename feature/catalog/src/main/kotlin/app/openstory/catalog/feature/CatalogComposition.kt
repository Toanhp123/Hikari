package app.openstory.catalog.feature

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.catalog.feature.discover.DiscoverRuntime
import app.openstory.catalog.feature.discover.DiscoverRuntimeActivation
import app.openstory.catalog.feature.discover.DiscoverViewModel
import app.openstory.catalog.feature.story.CatalogStoryDetailRuntime
import app.openstory.catalog.feature.story.StoryDetailViewModel
import app.openstory.catalog.feature.assets.CatalogImageLoader
import app.openstory.catalog.feature.assets.LocalCatalogImageLoader
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogCapabilitySession
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.trace.CatalogTrace
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun CatalogComposition() {
    val applicationContext = LocalContext.current.applicationContext
    CatalogSessionContent(rememberCatalogRuntimeHolder(applicationContext))
}

@Composable
private fun rememberCatalogRuntimeHolder(applicationContext: Context): CatalogRuntimeHolder {
    val runtimeFactory = remember(applicationContext) {
        CatalogRuntimeHolder.factory {
            val runtime = CatalogRuntimeHost(
                session = CatalogRuntimeFactory(
                    context = applicationContext,
                    binding = VariantCatalogBinding.binding,
                ).createSession(),
                onActivationStarted = {
                    VariantCatalogBinding.diagnostics.activationStarted()
                    AndroidCatalogTraceSink.mark(CatalogTrace.ACTIVATION_START)
                },
                onStorageReady = VariantCatalogBinding.diagnostics::storageReady,
                onDiscoverCollectorStarted = VariantCatalogBinding.diagnostics::discoverCollectorStarted,
                onDiscoverCollectorStopped = VariantCatalogBinding.diagnostics::discoverCollectorStopped,
                onClosed = VariantCatalogBinding.diagnostics::runtimeSessionClosed,
            )
            runtime to CatalogImageLoader(
                context = applicationContext,
                localResolver = VariantLocalCoverAssets,
                policyProvider = runtime::assetPolicyProvider,
                onSessionInitialized = VariantCatalogBinding.diagnostics::imageSessionInitialized,
                onSessionClosed = VariantCatalogBinding.diagnostics::imageSessionClosed,
                onDemandStartedCallback = VariantCatalogBinding.diagnostics::coverDemandStarted,
                onDemandStoppedCallback = VariantCatalogBinding.diagnostics::coverDemandStopped,
            )
        }
    }
    return viewModel(factory = runtimeFactory)
}

@Composable
private fun CatalogSessionContent(runtimeHolder: CatalogRuntimeHolder) {
    val routeState = rememberSaveable(stateSaver = CatalogRouteSaver) {
        mutableStateOf<CatalogRoute>(CatalogRoute.Discover)
    }
    val discoverListState = rememberLazyListState()
    val navigation = remember(discoverListState, routeState) {
        CatalogNavigationState(discoverListState, routeState)
    }
    val storyViewModel = rememberStoryDetailViewModel(runtimeHolder)
    val storyState by storyViewModel.state.collectAsStateWithLifecycle()

    val route = navigation.route
    RestoreStoryRoute(route, storyViewModel, navigation)
    val discoverViewModel = rememberDiscoverViewModel(runtimeHolder)
    val discoverState = if (route == CatalogRoute.Discover) {
        discoverViewModel.state.collectAsStateWithLifecycle().value
    } else {
        null
    }
    CatalogLifecycleEffects(route, discoverViewModel, storyViewModel, navigation)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val imageLoader = runtimeHolder.images.takeIf { lifecycleState.isAtLeast(Lifecycle.State.STARTED) }

    CompositionLocalProvider(LocalCatalogImageLoader provides imageLoader) {
        CatalogScreen(
            route = route,
            discoverListState = navigation.discoverListState,
            discoverState = discoverState,
            storyState = storyState,
            actions = catalogScreenActions(navigation, discoverViewModel, storyViewModel),
        )
    }
}

@Composable
private fun rememberStoryDetailViewModel(runtimeHolder: CatalogRuntimeHolder): StoryDetailViewModel {
    val storyFactory = remember(runtimeHolder) {
        StoryDetailViewModel.factory { CatalogStoryDetailRuntime(runtimeHolder.runtime::activate) }
    }
    return viewModel(factory = storyFactory)
}

@Composable
private fun RestoreStoryRoute(
    route: CatalogRoute,
    storyViewModel: StoryDetailViewModel,
    navigation: CatalogNavigationState,
) {
    LaunchedEffect(route) {
        if (route is CatalogRoute.Story) {
            storyViewModel.open(
                ref = route.ref,
                coverAssetKey = route.coverAssetKey,
                onDestinationReady = {},
                onDestinationRejected = navigation::showDiscover,
            )
        }
    }
}

@Composable
private fun rememberDiscoverViewModel(
    runtimeHolder: CatalogRuntimeHolder,
): DiscoverViewModel {
    val discoverFactory = remember(runtimeHolder) {
        DiscoverViewModel.factory(runtimeHolder.runtime::discoverRuntime)
    }
    return viewModel(factory = discoverFactory)
}

@Composable
private fun CatalogLifecycleEffects(
    route: CatalogRoute,
    discoverViewModel: DiscoverViewModel,
    storyViewModel: StoryDetailViewModel,
    navigation: CatalogNavigationState,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, route, discoverViewModel, storyViewModel) {
        fun resumeVisibleDemand() {
            when (route) {
                CatalogRoute.Discover -> discoverViewModel.resume()
                is CatalogRoute.Story -> storyViewModel.open(
                    ref = route.ref,
                    coverAssetKey = route.coverAssetKey,
                    onDestinationReady = {},
                    onDestinationRejected = navigation::showDiscover,
                )
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> resumeVisibleDemand()
                Lifecycle.Event.ON_STOP -> {
                    discoverViewModel.quiesce()
                    storyViewModel.quiesce()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) resumeVisibleDemand()
        onDispose {
            lifecycle.removeObserver(observer)
            when (route) {
                CatalogRoute.Discover -> discoverViewModel.quiesce()
                is CatalogRoute.Story -> storyViewModel.quiesce()
            }
        }
    }
}

private fun catalogScreenActions(
    navigation: CatalogNavigationState,
    discoverViewModel: DiscoverViewModel,
    storyViewModel: StoryDetailViewModel,
) = CatalogScreenActions(
    onMediaSelected = discoverViewModel::selectMedia,
    onStorySelected = { ref, coverAssetKey ->
        storyViewModel.open(
            ref = ref,
            coverAssetKey = coverAssetKey,
            onDestinationReady = { navigation.showStory(ref, coverAssetKey) },
        )
    },
    onDiscoverRefresh = discoverViewModel::refresh,
    onDiscoverRetry = discoverViewModel::retry,
    onStoryRetry = storyViewModel::retry,
    onBack = {
        navigation.showDiscover()
        storyViewModel.closeDestination()
    },
)

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
