package app.openstory.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.ui.HikariAppShell
import app.openstory.ui.HikariUtilitySheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun storyNavigationCarriesCanonicalIdentityOnly() {
        val (navigator, state) = navigator()

        navigator.navigate(AppRoute.Story("story-123"))

        assertEquals(AppRoute.Story("story-123"), navigator.currentRoute)
        assertTrue(state.activeBackStack.none { route -> route.toString().contains("pluginId") })
        assertTrue(state.activeBackStack.none { route -> route.toString().contains("sourceId") })
    }

    @Test
    fun selectingTopLevelDestinationRetainsEachTabsNestedHistory() {
        val (navigator, state) = navigator()
        navigator.navigate(AppRoute.Search)

        navigator.selectTopLevel(TopLevelDestination.Library)
        navigator.navigate(AppRoute.Story("library-story"))
        navigator.selectTopLevel(TopLevelDestination.Home)

        assertEquals(AppRoute.Search, navigator.currentRoute)
        assertEquals(listOf(AppRoute.Home, AppRoute.Search), state.backStacks.getValue(AppRoute.Home).toList())
        assertEquals(
            listOf(AppRoute.Library, AppRoute.Story("library-story")),
            state.backStacks.getValue(AppRoute.Library).toList(),
        )

        navigator.selectTopLevel(TopLevelDestination.Library)
        assertEquals(AppRoute.Story("library-story"), navigator.currentRoute)
    }

    @Test
    fun backFromNonStartTopLevelReturnsToRetainedHomeStack() {
        val (navigator, _) = navigator()
        navigator.navigate(AppRoute.Search)
        navigator.selectTopLevel(TopLevelDestination.Library)

        navigator.back()

        assertEquals(AppRoute.Search, navigator.currentRoute)
    }

    @Test
    fun topLevelSwitchRetainsEntryScopedViewModelStore() {
        lateinit var navigationState: AppNavigationState
        var homeViewModel: RetainedNavigationViewModel? = null
        var libraryViewModel: RetainedNavigationViewModel? = null
        val homeRecompositionProbe = mutableStateOf(0)
        composeRule.setContent {
            navigationState = rememberAppNavigationState()
            val provider = entryProvider<NavKey> {
                entry<AppRoute.Discover> {}
                entry<AppRoute.Home> {
                    homeRecompositionProbe.value
                    homeViewModel = viewModel { RetainedNavigationViewModel() }
                }
                entry<AppRoute.Library> {
                    libraryViewModel = viewModel { RetainedNavigationViewModel() }
                }
            }
            PersistentTopLevelNavDisplay(
                navigationState = navigationState,
                entryProvider = provider,
                onBack = {},
            )
        }
        composeRule.waitForIdle()
        val firstHomeViewModel = checkNotNull(homeViewModel)

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Library
        }
        composeRule.waitForIdle()
        val firstLibraryViewModel = checkNotNull(libraryViewModel)
        assertNotSame(firstHomeViewModel, firstLibraryViewModel)

        composeRule.runOnIdle {
            homeViewModel = null
            navigationState.topLevelRoute = AppRoute.Home
            homeRecompositionProbe.value += 1
        }
        composeRule.waitForIdle()

        assertSame(firstHomeViewModel, homeViewModel)
    }

    @Test
    fun persistentTopLevelDisplayKeepsVisitedCompositionAlive() {
        lateinit var navigationState: AppNavigationState
        var discoverCompositions = 0
        var discoverDisposals = 0
        composeRule.setContent {
            navigationState = rememberAppNavigationState()
            val provider = entryProvider<NavKey> {
                entry<AppRoute.Discover> {
                    DisposableEffect(Unit) {
                        discoverCompositions += 1
                        onDispose { discoverDisposals += 1 }
                    }
                }
                entry<AppRoute.Home> {}
                entry<AppRoute.Library> {}
            }
            PersistentTopLevelNavDisplay(
                navigationState = navigationState,
                entryProvider = provider,
                onBack = {},
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, discoverCompositions)

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Discover
        }
        composeRule.waitForIdle()
        assertEquals(1, discoverCompositions)
        assertEquals(0, discoverDisposals)

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Home
        }
        composeRule.waitForIdle()
        assertEquals(1, discoverCompositions)
        assertEquals(0, discoverDisposals)

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Discover
        }
        composeRule.waitForIdle()
        assertEquals(1, discoverCompositions)
        assertEquals(0, discoverDisposals)
    }

    @Test
    fun persistentTopLevelDisplayDoesNotMeasureInactiveVisitedRoute() {
        lateinit var navigationState: AppNavigationState
        var discoverMeasures = 0
        val hostSize = mutableStateOf(120.dp)
        composeRule.setContent {
            navigationState = rememberAppNavigationState()
            val provider = entryProvider<NavKey> {
                entry<AppRoute.Discover> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .layout { measurable, constraints ->
                                discoverMeasures += 1
                                val placeable = measurable.measure(constraints)
                                layout(placeable.width, placeable.height) {
                                    placeable.placeRelative(0, 0)
                                }
                            },
                    )
                }
                entry<AppRoute.Home> {
                    Box(Modifier.fillMaxSize())
                }
                entry<AppRoute.Library> {}
            }
            PersistentTopLevelNavDisplay(
                navigationState = navigationState,
                entryProvider = provider,
                onBack = {},
                modifier = Modifier.size(hostSize.value),
            )
        }
        composeRule.waitForIdle()
        assertEquals(0, discoverMeasures)

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Discover
        }
        composeRule.waitForIdle()
        assertTrue(discoverMeasures > 0)
        val activeDiscoverMeasures = discoverMeasures

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Home
        }
        composeRule.waitForIdle()
        assertEquals(activeDiscoverMeasures, discoverMeasures)

        composeRule.runOnIdle {
            hostSize.value = 160.dp
        }
        composeRule.waitForIdle()
        assertEquals(activeDiscoverMeasures, discoverMeasures)

        composeRule.runOnIdle {
            navigationState.topLevelRoute = AppRoute.Discover
        }
        composeRule.waitForIdle()
        assertTrue(discoverMeasures > activeDiscoverMeasures)
    }

    @Test
    fun utilityDestinationPreservesOriginForBackNavigation() {
        val (navigator, _) = navigator(TopLevelDestination.Library)
        var sheetOpen = true
        composeRule.setContent {
            HikariTheme {
                HikariUtilitySheet(
                    onDismiss = { sheetOpen = false },
                    onDestinationSelected = { route ->
                        sheetOpen = false
                        navigator.navigate(route)
                    },
                )
            }
        }

        composeRule.onNodeWithText("Downloads").performClick()
        composeRule.runOnIdle {
            assertEquals(false, sheetOpen)
            assertEquals(AppRoute.Downloads, navigator.currentRoute)
            navigator.back()
            assertEquals(AppRoute.Library, navigator.currentRoute)
        }
    }

    @Test
    fun storyRouteNeverExposesFloatingNavigation() {
        assertFocusedRouteHasNoFloatingNavigation(AppRoute.Story("story-123"))
    }

    @Test
    fun readerRouteNeverExposesFloatingNavigation() {
        assertFocusedRouteHasNoFloatingNavigation(
            AppRoute.Reader("story-123", "chapter-1", null),
        )
    }

    private fun navigator(
        selected: TopLevelDestination = TopLevelDestination.Home,
    ): Pair<AppNavigator, AppNavigationState> {
        val state = AppNavigationState(
            startRoute = APP_START_ROUTE,
            topLevelRouteState = mutableStateOf(selected.route),
            backStacks = topLevelDestinations.associate { destination ->
                destination.route to NavBackStack<NavKey>(destination.route)
            },
        )
        return AppNavigator(state) to state
    }

    private fun assertFocusedRouteHasNoFloatingNavigation(route: AppRoute) {
        composeRule.setContent {
            HikariTheme {
                HikariAppShell(route, {}, {}) { _ -> }
            }
        }
        composeRule.onAllNodesWithText("Discover").assertCountEquals(0)
        composeRule.onAllNodesWithText("Home").assertCountEquals(0)
        composeRule.onAllNodesWithText("Library").assertCountEquals(0)
    }
}

private class RetainedNavigationViewModel : ViewModel()
