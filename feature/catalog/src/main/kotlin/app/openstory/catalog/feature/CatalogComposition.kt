package app.openstory.catalog.feature

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.openstory.catalog.feature.discover.DiscoverRuntime
import app.openstory.catalog.feature.discover.DiscoverRuntimeActivation
import app.openstory.catalog.feature.discover.DiscoverViewModel
import app.openstory.catalog.feature.story.CatalogStoryDetailRuntime
import app.openstory.catalog.feature.story.StoryDetailViewModel
import app.openstory.catalog.feature.assets.CatalogImageLoader
import app.openstory.catalog.feature.assets.LocalCatalogImageLoader
import app.openstory.catalog.feature.trace.AndroidCatalogTraceSink
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogCapabilitySession
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.trace.CatalogTrace
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
            CatalogRuntimeHost(
                session = CatalogRuntimeFactory(
                    context = applicationContext,
                    binding = VariantCatalogBinding.binding,
                ).createSession(),
                onActivationStarted = {
                    VariantCatalogBinding.diagnostics.activationStarted()
                    AndroidCatalogTraceSink.mark(CatalogTrace.ACTIVATION_START)
                },
                onStorageReady = VariantCatalogBinding.diagnostics::storageReady,
            ) to CatalogImageLoader(applicationContext, VariantLocalCoverAssets)
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
    val discoverViewModel = discoverViewModel(route, runtimeHolder)
    val discoverState = discoverViewModel?.state?.collectAsStateWithLifecycle()?.value

    CompositionLocalProvider(LocalCatalogImageLoader provides runtimeHolder.images) {
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
private fun discoverViewModel(
    route: CatalogRoute,
    runtimeHolder: CatalogRuntimeHolder,
): DiscoverViewModel? {
    if (route != CatalogRoute.Discover) return null
    val discoverFactory = remember(runtimeHolder) {
        DiscoverViewModel.factory(runtimeHolder.runtime::discoverRuntime)
    }
    return viewModel(factory = discoverFactory)
}

private fun catalogScreenActions(
    navigation: CatalogNavigationState,
    discoverViewModel: DiscoverViewModel?,
    storyViewModel: StoryDetailViewModel,
) = CatalogScreenActions(
    onMediaSelected = { mediaType -> discoverViewModel?.selectMedia(mediaType) },
    onStorySelected = { ref, coverAssetKey ->
        storyViewModel.open(
            ref = ref,
            coverAssetKey = coverAssetKey,
            onDestinationReady = { navigation.showStory(ref, coverAssetKey) },
        )
    },
    onDiscoverRetry = { discoverViewModel?.retry() },
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
) : AutoCloseable {
    private val activationMutex = Mutex()
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
                    observe = { mediaType -> activation.discoverSession(mediaType).states },
                    refresh = activation::acquireDiscover,
                )
            }

        override fun close() = Unit
    }

    override fun close() = session.close()
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
