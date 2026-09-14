package app.openstory.navigation

import androidx.navigation3.runtime.NavKey
import app.openstory.common.navigation.RouteEntryId
import app.openstory.common.navigation.RouteLifecycle
import app.openstory.common.navigation.RouteLifecycleChange
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNavigationStateTest {
    @Test
    fun homeIsDefaultAndEachRootOwnsAnIndependentStack() {
        val state = navigationState()

        assertEquals(AppFocusedDestination.HOME, state.focusedDestination)
        assertEquals(HOME_ROOT, state.focusedBackStack.single())

        state.select(AppFocusedDestination.MANGA)
        assertTrue(state.push(storyRoute("manga-child", AppMediaRoute.MANGA)))
        state.select(AppFocusedDestination.LIGHT_NOVEL)
        assertTrue(state.push(storyRoute("novel-child", AppMediaRoute.LIGHT_NOVEL)))
        state.select(AppFocusedDestination.MANGA)

        assertEquals("manga-child", state.focusedBackStack.last().entryId)
        assertEquals(2, state.stackFor(AppFocusedDestination.LIGHT_NOVEL).size)
        assertEquals(1, state.stackFor(AppFocusedDestination.HOME).size)
    }

    @Test
    fun reselectPopsOnlyTheSelectedRootToItsRoot() {
        val state = navigationState()
        state.select(AppFocusedDestination.MANGA)
        state.push(storyRoute("manga-child", AppMediaRoute.MANGA))

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

        state.push(storyRoute("manga-child", AppMediaRoute.MANGA))
        assertTrue(state.popFocusedRoute())
        assertEquals(listOf(MANGA_ROOT), state.focusedBackStack)
    }

    @Test
    fun routeHistoryStaysInsidePerRootAndProcessBounds() {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 5, maxEntriesPerRoot = 3),
        )
        state.select(AppFocusedDestination.MANGA)

        val old = storyRoute("manga-old", AppMediaRoute.MANGA)
        val current = storyRoute("manga-new", AppMediaRoute.MANGA)
        assertTrue(state.push(old))
        assertTrue(state.push(current))
        state.select(AppFocusedDestination.LIGHT_NOVEL)
        val novelChild = storyRoute("novel-child", AppMediaRoute.LIGHT_NOVEL)
        assertTrue(state.push(novelChild))

        assertEquals(listOf(MANGA_ROOT, current), state.stackFor(AppFocusedDestination.MANGA))
        assertEquals(listOf(LIGHT_NOVEL_ROOT, novelChild), state.stackFor(AppFocusedDestination.LIGHT_NOVEL))
        assertEquals(5, state.totalEntryCount)
    }

    @Test
    fun restoredChildStackKeepsItsRootAndTopRoute() {
        val mangaChild = storyRoute("manga-child", AppMediaRoute.MANGA)
        val state = AppNavigationState(
            mangaBackStack = mutableListOf<NavKey>(MANGA_ROOT, mangaChild),
            homeBackStack = mutableListOf<NavKey>(HOME_ROOT),
            lightNovelBackStack = mutableListOf<NavKey>(LIGHT_NOVEL_ROOT),
        )

        state.select(AppFocusedDestination.MANGA)

        assertEquals(listOf(MANGA_ROOT, mangaChild), state.focusedBackStack)
    }

    @Test
    fun malformedRestoredChildEntryIdIsDroppedFailClosed() {
        val malformed = storyRoute("invalid/entry", AppMediaRoute.MANGA)

        val state = AppNavigationState(
            mangaBackStack = mutableListOf<NavKey>(MANGA_ROOT, malformed),
            homeBackStack = mutableListOf<NavKey>(HOME_ROOT),
            lightNovelBackStack = mutableListOf<NavKey>(LIGHT_NOVEL_ROOT),
        )

        assertEquals(listOf(MANGA_ROOT), state.stackFor(AppFocusedDestination.MANGA))
    }

    @Test
    fun duplicateRestoredChildEntryIdIsDroppedFromLaterRoot() {
        val mangaChild = storyRoute("shared-entry", AppMediaRoute.MANGA)
        val novelChild = storyRoute("shared-entry", AppMediaRoute.LIGHT_NOVEL)

        val state = AppNavigationState(
            mangaBackStack = mutableListOf<NavKey>(MANGA_ROOT, mangaChild),
            homeBackStack = mutableListOf<NavKey>(HOME_ROOT),
            lightNovelBackStack = mutableListOf<NavKey>(LIGHT_NOVEL_ROOT, novelChild),
        )

        assertEquals(listOf(MANGA_ROOT, mangaChild), state.stackFor(AppFocusedDestination.MANGA))
        assertEquals(listOf(LIGHT_NOVEL_ROOT), state.stackFor(AppFocusedDestination.LIGHT_NOVEL))
    }

    @Test
    fun rootOnlyPolicyRejectsChildWithoutCorruptingTheRoot() {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 3, maxEntriesPerRoot = 1),
        )
        state.select(AppFocusedDestination.MANGA)

        assertFalse(state.push(storyRoute("manga-child", AppMediaRoute.MANGA)))
        assertEquals(listOf(MANGA_ROOT), state.focusedBackStack)
    }

    @Test
    fun pushRejectsRootRouteAsChild() {
        val state = navigationState()
        state.select(AppFocusedDestination.MANGA)

        assertFalse(state.push(AppRoute.Discover("duplicate-root", AppMediaRoute.MANGA)))
        assertEquals(listOf(MANGA_ROOT), state.focusedBackStack)
    }

    @Test
    fun storyRouteBelongsToTheFocusedRootNotItsMediaContext() {
        val state = navigationState()

        val mangaStoryFromHome = storyRoute("story-home", AppMediaRoute.MANGA)
        assertTrue(state.push(mangaStoryFromHome))
        assertEquals(listOf(HOME_ROOT, mangaStoryFromHome), state.focusedBackStack)

        state.select(AppFocusedDestination.LIGHT_NOVEL)
        val mangaStoryFromLightNovelRoot = storyRoute("story-ln", AppMediaRoute.MANGA)
        assertTrue(state.push(mangaStoryFromLightNovelRoot))
        assertEquals(
            listOf(LIGHT_NOVEL_ROOT, mangaStoryFromLightNovelRoot),
            state.focusedBackStack,
        )
    }

    @Test
    fun pushRejectsDuplicateRouteEntryIdAcrossRootHistories() {
        val state = navigationState()
        state.select(AppFocusedDestination.MANGA)
        assertTrue(state.push(storyRoute("shared-entry", AppMediaRoute.MANGA)))

        state.select(AppFocusedDestination.LIGHT_NOVEL)
        assertFalse(state.push(storyRoute("shared-entry", AppMediaRoute.LIGHT_NOVEL)))
        assertEquals(listOf(LIGHT_NOVEL_ROOT), state.focusedBackStack)
    }

    @Test
    fun tightPerRootBoundNeverEvictsTheImmediateBackParent() = kotlinx.coroutines.test.runTest {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 8, maxEntriesPerRoot = 2),
        )
        val events = mutableListOf<RouteLifecycleChange>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            state.changes.collect(events::add)
        }

        state.select(AppFocusedDestination.MANGA)
        events.clear()
        val parent = storyRoute("parent", AppMediaRoute.MANGA)
        val child = storyRoute("child", AppMediaRoute.MANGA)
        assertTrue(state.push(parent))
        events.clear()

        assertFalse(state.push(child))
        assertEquals(listOf(MANGA_ROOT, parent), state.focusedBackStack)
        assertTrue(events.isEmpty())

        job.cancel()
    }

    @Test
    fun lifecycleEmissionsOnPushPopSwitchAndReselect() = kotlinx.coroutines.test.runTest {
        val state = navigationState()
        val events = mutableListOf<RouteLifecycleChange>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            state.changes.collect { events.add(it) }
        }

        state.select(AppFocusedDestination.MANGA)
        events.clear() // clear the switch event

        val storyA = storyRoute("story-a", AppMediaRoute.MANGA)
        assertTrue(state.push(storyA))
        assertEquals(
            listOf(
                RouteLifecycleChange(RouteEntryId.from("manga-root"), RouteLifecycle.RETAINED),
                RouteLifecycleChange(RouteEntryId.from("story-a"), RouteLifecycle.ACTIVE),
            ),
            events,
        )

        events.clear()
        val storyB = storyRoute("story-b", AppMediaRoute.MANGA)
        assertTrue(state.push(storyB))
        assertEquals(
            listOf(
                RouteLifecycleChange(RouteEntryId.from("story-a"), RouteLifecycle.RETAINED),
                RouteLifecycleChange(RouteEntryId.from("story-b"), RouteLifecycle.ACTIVE),
            ),
            events,
        )

        events.clear()
        assertTrue(state.popFocusedRoute())
        assertEquals(
            listOf(
                RouteLifecycleChange(RouteEntryId.from("story-b"), RouteLifecycle.RELEASED),
                RouteLifecycleChange(RouteEntryId.from("story-a"), RouteLifecycle.ACTIVE),
            ),
            events,
        )

        events.clear()
        state.select(AppFocusedDestination.HOME)
        assertEquals(
            listOf(
                RouteLifecycleChange(RouteEntryId.from("story-a"), RouteLifecycle.RETAINED),
                RouteLifecycleChange(RouteEntryId.from("home-root"), RouteLifecycle.ACTIVE),
            ),
            events,
        )

        events.clear()
        state.select(AppFocusedDestination.MANGA)
        assertEquals(
            listOf(
                RouteLifecycleChange(RouteEntryId.from("home-root"), RouteLifecycle.RETAINED),
                RouteLifecycleChange(RouteEntryId.from("story-a"), RouteLifecycle.ACTIVE),
            ),
            events,
        )

        events.clear()
        assertEquals(AppRootSelection.RESELECTED_AND_POPPED, state.select(AppFocusedDestination.MANGA))
        assertEquals(
            listOf(
                RouteLifecycleChange(RouteEntryId.from("story-a"), RouteLifecycle.RELEASED),
                RouteLifecycleChange(RouteEntryId.from("manga-root"), RouteLifecycle.ACTIVE),
            ),
            events,
        )

        job.cancel()
    }

    @Test
    fun deterministicTrimmingPreservesRootTopAndImmediateBackParent() = kotlinx.coroutines.test.runTest {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 10, maxEntriesPerRoot = 4),
        )
        val releasedEvents = mutableListOf<RouteEntryId>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            state.changes.collect { change ->
                if (change.lifecycle == RouteLifecycle.RELEASED) {
                    releasedEvents.add(change.entryId)
                }
            }
        }

        state.select(AppFocusedDestination.MANGA)
        val s1 = storyRoute("story-1", AppMediaRoute.MANGA)
        val s2 = storyRoute("story-2", AppMediaRoute.MANGA)
        val s3 = storyRoute("story-3", AppMediaRoute.MANGA)
        val s4 = storyRoute("story-4", AppMediaRoute.MANGA)

        assertTrue(state.push(s1))
        assertTrue(state.push(s2))
        assertTrue(state.push(s3))
        assertEquals(listOf(MANGA_ROOT, s1, s2, s3), state.focusedBackStack)

        // Pushing s4 triggers per-root eviction: s1 (oldest ancestor) is evicted
        // s3 (immediate Back parent of s4) and MANGA_ROOT must be preserved
        assertTrue(state.push(s4))
        assertEquals(listOf(MANGA_ROOT, s2, s3, s4), state.focusedBackStack)
        assertEquals(listOf(RouteEntryId.from("story-1")), releasedEvents)

        job.cancel()
    }

    @Test
    fun globalTrimmingEvictsIntermediateAncestorFromInactiveStack() = kotlinx.coroutines.test.runTest {
        val state = navigationState(
            policy = AppNavigationPolicy(maxTotalEntries = 5, maxEntriesPerRoot = 4),
        )
        val releasedEvents = mutableListOf<RouteEntryId>()
        val job = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            state.changes.collect { change ->
                if (change.lifecycle == RouteLifecycle.RELEASED) {
                    releasedEvents.add(change.entryId)
                }
            }
        }

        state.select(AppFocusedDestination.MANGA)
        val m1 = storyRoute("manga-1", AppMediaRoute.MANGA)
        val m2 = storyRoute("manga-2", AppMediaRoute.MANGA)
        assertTrue(state.push(m1))
        assertTrue(state.push(m2))
        // Manga stack: [MANGA_ROOT, m1, m2] (size 3)
        // Home: [HOME_ROOT] (size 1)
        // LN: [LN_ROOT] (size 1)
        // Total = 5 entries = maxTotalEntries

        state.select(AppFocusedDestination.LIGHT_NOVEL)
        val ln1 = storyRoute("ln-1", AppMediaRoute.LIGHT_NOVEL)
        assertTrue(state.push(ln1))

        // Total should stay 5: m1 was intermediate ancestor on Manga and was evicted
        assertEquals(5, state.totalEntryCount)
        assertEquals(listOf(MANGA_ROOT, m2), state.stackFor(AppFocusedDestination.MANGA))
        assertEquals(listOf(LIGHT_NOVEL_ROOT, ln1), state.stackFor(AppFocusedDestination.LIGHT_NOVEL))
        assertEquals(listOf(RouteEntryId.from("manga-1")), releasedEvents)

        job.cancel()
    }

    private fun navigationState(
        policy: AppNavigationPolicy = AppNavigationPolicy(),
    ) = AppNavigationState(
        mangaBackStack = mutableListOf<NavKey>(MANGA_ROOT),
        homeBackStack = mutableListOf<NavKey>(HOME_ROOT),
        lightNovelBackStack = mutableListOf<NavKey>(LIGHT_NOVEL_ROOT),
        policy = policy,
    )

    private fun storyRoute(id: String, media: AppMediaRoute) = AppRoute.Story(
        StoryRouteWire(
            entryId = id,
            storyId = "sid-$id",
            catalogSourceKey = "src-$id",
            sourceStoryId = "src-story-$id",
            originMedia = media,
            previewTitle = null,
            previewArtwork = null,
        ),
    )

    private companion object {
        val MANGA_ROOT = AppRoute.Discover("manga-root", AppMediaRoute.MANGA)
        val HOME_ROOT = AppRoute.Home("home-root")
        val LIGHT_NOVEL_ROOT = AppRoute.Discover("light-novel-root", AppMediaRoute.LIGHT_NOVEL)
    }
}
