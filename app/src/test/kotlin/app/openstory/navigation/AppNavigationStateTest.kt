package app.openstory.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.navigation3.runtime.NavKey

class AppNavigationStateTest {
    @Test
    fun homeIsDefaultAndEachRootOwnsAnIndependentStack() {
        val state = navigationState()

        assertEquals(AppFocusedDestination.HOME, state.focusedDestination)
        assertEquals(HOME_ROOT, state.focusedBackStack.single())

        state.select(AppFocusedDestination.MANGA)
        assertTrue(state.push(AppRoute.Discover("manga-child", AppMediaRoute.MANGA)))
        state.select(AppFocusedDestination.LIGHT_NOVEL)
        assertTrue(state.push(AppRoute.Discover("novel-child", AppMediaRoute.LIGHT_NOVEL)))
        state.select(AppFocusedDestination.MANGA)

        assertEquals("manga-child", state.focusedBackStack.last().entryId)
        assertEquals(2, state.stackFor(AppFocusedDestination.LIGHT_NOVEL).size)
        assertEquals(1, state.stackFor(AppFocusedDestination.HOME).size)
    }

    @Test
    fun reselectPopsOnlyTheSelectedRootToItsRoot() {
        val state = navigationState()
        state.select(AppFocusedDestination.MANGA)
        state.push(AppRoute.Discover("manga-child", AppMediaRoute.MANGA))

        val result = state.select(AppFocusedDestination.MANGA)

        assertEquals(AppRootSelection.RESELECTED_AND_POPPED, result)
        assertEquals(listOf(MANGA_ROOT), state.focusedBackStack)
        assertEquals(listOf(HOME_ROOT), state.stackFor(AppFocusedDestination.HOME))
    }

    @Test
    fun rootBackIsLeftToThePlatformAndNeverTraversesTabHistory() {
        val state = navigationState()
        state.select(AppFocusedDestination.MANGA)

        assertFalse(state.popFocusedRoute())
        assertEquals(AppFocusedDestination.MANGA, state.focusedDestination)

        state.push(AppRoute.Discover("manga-child", AppMediaRoute.MANGA))
        assertTrue(state.popFocusedRoute())
        assertEquals(listOf(MANGA_ROOT), state.focusedBackStack)
    }

    @Test
    fun routeHistoryStaysInsidePerRootAndProcessBounds() {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 4, maxEntriesPerRoot = 2),
        )
        state.select(AppFocusedDestination.MANGA)

        assertTrue(state.push(AppRoute.Discover("manga-old", AppMediaRoute.MANGA)))
        assertTrue(state.push(AppRoute.Discover("manga-new", AppMediaRoute.MANGA)))
        state.select(AppFocusedDestination.LIGHT_NOVEL)
        assertFalse(state.push(AppRoute.Discover("novel-child", AppMediaRoute.LIGHT_NOVEL)))

        assertEquals(listOf(MANGA_ROOT, AppRoute.Discover("manga-new", AppMediaRoute.MANGA)), state.stackFor(AppFocusedDestination.MANGA))
        assertEquals(4, state.totalEntryCount)
    }

    @Test
    fun restoredChildStackKeepsItsRootAndTopRoute() {
        val mangaChild = AppRoute.Discover("manga-child", AppMediaRoute.MANGA)
        val state = AppNavigationState(
            mangaBackStack = mutableListOf<NavKey>(MANGA_ROOT, mangaChild),
            homeBackStack = mutableListOf<NavKey>(HOME_ROOT),
            lightNovelBackStack = mutableListOf<NavKey>(LIGHT_NOVEL_ROOT),
        )

        state.select(AppFocusedDestination.MANGA)

        assertEquals(listOf(MANGA_ROOT, mangaChild), state.focusedBackStack)
    }

    @Test
    fun rootOnlyPolicyRejectsChildWithoutCorruptingTheRoot() {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 3, maxEntriesPerRoot = 1),
        )
        state.select(AppFocusedDestination.MANGA)

        assertFalse(state.push(AppRoute.Discover("manga-child", AppMediaRoute.MANGA)))
        assertEquals(listOf(MANGA_ROOT), state.focusedBackStack)
    }

    private fun navigationState(
        policy: AppNavigationPolicy = AppNavigationPolicy(),
    ) = AppNavigationState(
        mangaBackStack = mutableListOf<NavKey>(MANGA_ROOT),
        homeBackStack = mutableListOf<NavKey>(HOME_ROOT),
        lightNovelBackStack = mutableListOf<NavKey>(LIGHT_NOVEL_ROOT),
        policy = policy,
    )

    private companion object {
        val MANGA_ROOT = AppRoute.Discover("manga-root", AppMediaRoute.MANGA)
        val HOME_ROOT = AppRoute.Home("home-root")
        val LIGHT_NOVEL_ROOT = AppRoute.Discover("light-novel-root", AppMediaRoute.LIGHT_NOVEL)
    }
}
