package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import app.openstory.designsystem.feedback.HikariSnackbarHost
import app.openstory.designsystem.theme.hikariDimensions
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariAppShellScope
import app.openstory.ui.HikariUtilitySheet

@Composable
fun AppNavHost(
    navigator: AppNavigator,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val discoverSearchFocus = remember { FocusRequester() }
    val utilityFocus = remember { FocusRequester() }
    val discoverCategoryFocus = remember { FocusRequester() }
    val discoverCatalogFocus = remember { FocusRequester() }
    val homeContentFocus = remember { FocusRequester() }
    val libraryFilterFocus = remember { FocusRequester() }
    var showUtilitySheet by remember { mutableStateOf(false) }
    val focus = AppNavFocus(
        discoverSearchFocus,
        utilityFocus,
        discoverCategoryFocus,
        discoverCatalogFocus,
        homeContentFocus,
        libraryFilterFocus,
    )
    HikariAppShell(
        currentRoute = navigator.currentRoute,
        onTopLevelSelected = navigator::selectTopLevel,
        onUtilityRequested = { showUtilitySheet = true },
        utilityFocusRequester = utilityFocus,
        utilityNextFocusRequester = focus.utilityNext(navigator.currentRoute),
        modifier = modifier,
    ) { contentPadding ->
        AppNavigationContent(navigator, focus, snackbarHostState, contentPadding, this)
    }
    if (showUtilitySheet) {
        HikariUtilitySheet(
            onDismiss = { showUtilitySheet = false },
            onDestinationSelected = { route ->
                showUtilitySheet = false
                navigator.navigate(route)
            },
        )
    }
}

@Composable
private fun AppNavigationContent(
    navigator: AppNavigator,
    focus: AppNavFocus,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    shellScope: HikariAppShellScope,
) {
    Box(Modifier.fillMaxSize()) {
        val provider = entryProvider<NavKey> {
            entry<AppRoute.Discover> {
                DiscoverDestination(
                    contentPadding = contentPadding,
                    onSearch = { navigator.navigate(AppRoute.Search) },
                    onStorySelected = { storyId ->
                        navigator.navigate(AppRoute.Story(storyId.value))
                    },
                    searchFocusRequester = focus.discoverSearch,
                    searchNextFocusRequester = focus.utility,
                    categoryFocusRequester = focus.discoverCategory,
                    categoryNextFocusRequester = focus.discoverCatalog,
                    catalogFocusRequester = focus.discoverCatalog,
                    shellScope = shellScope,
                )
            }
            entry<AppRoute.Home> {
                HomeDestination(
                    contentPadding = contentPadding,
                    onDiscover = { navigator.selectTopLevel(TopLevelDestination.Discover) },
                    onStorySelected = { storyId ->
                        navigator.navigate(AppRoute.Story(storyId.value))
                    },
                    onResume = { target ->
                        navigator.navigate(
                            AppRoute.Reader(
                                target.storyId.value,
                                target.chapterId.value,
                                target.releaseId.value,
                            ),
                        )
                    },
                    firstContentFocusRequester = focus.homeContent,
                    shellScope = shellScope,
                )
            }
            entry<AppRoute.Search> {
                SearchDestination(contentPadding, navigator::back) { storyId ->
                    navigator.navigate(AppRoute.Story(storyId.value))
                }
            }
            entry<AppRoute.Library> {
                LibraryDestination(
                    contentPadding = contentPadding,
                    onDiscover = { navigator.selectTopLevel(TopLevelDestination.Discover) },
                    firstFilterFocusRequester = focus.libraryFilter,
                    shellScope = shellScope,
                ) { storyId ->
                    navigator.navigate(AppRoute.Story(storyId.value))
                }
            }
            entry<AppRoute.Downloads> {
                DownloadsDestination(
                    contentPadding = contentPadding,
                    onStorySelected = { navigator.navigate(AppRoute.Story(it.value)) },
                )
            }
            entry<AppRoute.Updates> {
                UpdatesDestination(
                    contentPadding = contentPadding,
                    onStorySelected = { navigator.navigate(AppRoute.Story(it.value)) },
                    onRead = { target ->
                        navigator.navigate(
                            AppRoute.Reader(
                                target.storyId.value,
                                target.chapterId.value,
                                target.releaseId.value,
                            ),
                        )
                    },
                )
            }
            entry<AppRoute.Story> { route ->
                StoryDestination(route, navigator::navigate, contentPadding)
            }
            entry<AppRoute.Reader> { route ->
                ReaderDestination(route, navigator::back)
            }
        }
        NavDisplay(
            modifier = Modifier.fillMaxSize(),
            entries = navigator.navigationState.decoratedEntries(provider),
            onBack = navigator::back,
        )
        HikariSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(
                    if (shouldShowFloatingNavigation(navigator.currentRoute)) {
                        Modifier.navigationBarsPadding().padding(
                            bottom = MaterialTheme.hikariDimensions.floatingNavigationClearance,
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

private data class AppNavFocus(
    val discoverSearch: FocusRequester,
    val utility: FocusRequester,
    val discoverCategory: FocusRequester,
    val discoverCatalog: FocusRequester,
    val homeContent: FocusRequester,
    val libraryFilter: FocusRequester,
) {
    fun utilityNext(route: AppRoute?): FocusRequester = when (route) {
        AppRoute.Home -> homeContent
        AppRoute.Library -> libraryFilter
        else -> discoverCategory
    }
}
