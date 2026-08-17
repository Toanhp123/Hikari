package app.openstory.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.content.HikariMetadataBadgeGroup
import app.openstory.designsystem.content.HikariSectionHeader
import app.openstory.designsystem.content.HikariSectionTitle
import app.openstory.designsystem.control.HikariContentAction
import app.openstory.designsystem.control.HikariFilterChip
import app.openstory.designsystem.control.HikariIconAction
import app.openstory.designsystem.control.HikariIconActionStyle
import app.openstory.designsystem.control.HikariPrimaryAction
import app.openstory.designsystem.control.HikariUtilityAction
import app.openstory.designsystem.glass.HikariBackdropMode
import app.openstory.designsystem.glass.HikariGlassRenderingMode
import app.openstory.designsystem.glass.hikariGlassSurfaceColor
import app.openstory.designsystem.glass.hikariGlassContentColor
import app.openstory.designsystem.glass.glassRenderingMode
import app.openstory.designsystem.glass.shouldUseBackdropBlur
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.layout.HikariSheetContent
import app.openstory.designsystem.layout.classifyWindow
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.motion.LocalHikariMotionPolicy
import app.openstory.designsystem.navigation.HikariFloatingNavigation
import app.openstory.designsystem.navigation.HikariNavigationItem
import app.openstory.designsystem.navigation.validateNavigationSelection
import app.openstory.designsystem.refresh.HikariPullToRefresh
import app.openstory.designsystem.icon.HikariNavigationGlyphs
import app.openstory.designsystem.theme.HikariTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Density

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HikariProductPrimitivesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun windowClassificationChangesAtApprovedBreakpoints() {
        assertEquals(HikariWindowClass.COMPACT, classifyWindow(360.dp))
        assertEquals(HikariWindowClass.LARGE_PHONE, classifyWindow(412.dp))
        assertEquals(HikariWindowClass.MEDIUM, classifyWindow(600.dp))
    }

    @Test
    fun glassRenderingUsesFallbackBeforeApi31() {
        assertEquals(HikariGlassRenderingMode.TRANSLUCENT, glassRenderingMode(26))
        assertEquals(HikariGlassRenderingMode.TRANSLUCENT, glassRenderingMode(30))
        assertEquals(HikariGlassRenderingMode.BLUR, glassRenderingMode(31))
    }

    @Test
    fun backdropBlurIsEnabledNormallyAndOnlyDisabledByExplicitBenchmarkMode() {
        assertTrue(shouldUseBackdropBlur(31, HikariBackdropMode.ENABLED, hasBackdrop = true))
        assertFalse(
            shouldUseBackdropBlur(
                31,
                HikariBackdropMode.DISABLED_FOR_BENCHMARK,
                hasBackdrop = true,
            ),
        )
        assertFalse(shouldUseBackdropBlur(30, HikariBackdropMode.ENABLED, hasBackdrop = true))
        assertFalse(shouldUseBackdropBlur(31, HikariBackdropMode.ENABLED, hasBackdrop = false))
    }

    @Test
    fun glassSurfaceColorTracksTheActiveTheme() {
        var lightColor = Color.Unspecified
        var darkColor = Color.Unspecified

        compose.setContent {
            HikariTheme(darkTheme = false) {
                val color = hikariGlassSurfaceColor()
                SideEffect { lightColor = color }
            }
            HikariTheme(darkTheme = true) {
                val color = hikariGlassSurfaceColor()
                SideEffect { darkColor = color }
            }
        }

        compose.runOnIdle {
            assertTrue(lightColor != darkColor)
            assertEquals(0xD9, (lightColor.alpha * 255).roundToInt())
            assertEquals(0xD9, (darkColor.alpha * 255).roundToInt())
        }
    }

    @Test
    fun glassContentColorTracksTheActiveTheme() {
        var lightColor = Color.Unspecified
        var darkColor = Color.Unspecified

        compose.setContent {
            HikariTheme(darkTheme = false) {
                val color = hikariGlassContentColor()
                SideEffect { lightColor = color }
            }
            HikariTheme(darkTheme = true) {
                val color = hikariGlassContentColor()
                SideEffect { darkColor = color }
            }
        }

        compose.runOnIdle {
            assertTrue(lightColor != darkColor)
            assertTrue(lightColor != Color.Unspecified)
            assertTrue(darkColor != Color.Unspecified)
        }
    }


    @Test
    fun metadataBadgeGroupLaysBadgesOutHorizontallyBeforeWrapping() {
        compose.setContent {
            HikariTheme {
                Column {
                    Box(Modifier.width(96.dp)) {
                        HikariMetadataBadgeGroup(labels = listOf("A", "B"))
                    }
                    Box(Modifier.width(40.dp)) {
                        HikariMetadataBadgeGroup(labels = listOf("C", "D"))
                    }
                }
            }
        }

        compose.waitForIdle()
        val wideFirstTop = compose.onNodeWithText("A").fetchSemanticsNode().boundsInRoot.top
        val wideSecondTop = compose.onNodeWithText("B").fetchSemanticsNode().boundsInRoot.top
        val narrowFirstTop = compose.onNodeWithText("C").fetchSemanticsNode().boundsInRoot.top
        val narrowSecondTop = compose.onNodeWithText("D").fetchSemanticsNode().boundsInRoot.top

        assertEquals(wideFirstTop, wideSecondTop, 0.5f)
        assertTrue(narrowSecondTop > narrowFirstTop)
    }

    @Test
    fun sharedActionsOwnVisibleChromeAndMinimumTargets() {
        compose.setContent {
            HikariTheme {
                Column {
                    HikariPrimaryAction(
                        onClick = {},
                        modifier = Modifier.testTag("primary-action"),
                    ) { androidx.compose.material3.Text("Primary action") }
                    HikariContentAction(
                        onClick = {},
                        modifier = Modifier.testTag("content-action"),
                    ) { androidx.compose.material3.Text("Content action") }
                    HikariUtilityAction(
                        onClick = {},
                        modifier = Modifier.testTag("utility-action"),
                    ) { androidx.compose.material3.Text("Utility action") }
                }
            }
        }

        compose.onNodeWithTag("primary-action").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("content-action").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("utility-action").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun sectionHeaderSupportsSubtitleAndTonalTrailingAction() {
        compose.setContent {
            HikariTheme {
                HikariSectionHeader(
                    title = "Sources",
                    subtitle = "Linked and catalog sources",
                    action = {
                        HikariIconAction(
                            onClick = {},
                            contentDescription = "Refresh source details",
                            style = HikariIconActionStyle.TONAL,
                        ) { androidx.compose.material3.Text("R") }
                    },
                )
            }
        }

        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNodeWithText("Sources").assert(isHeading)
        compose.onNodeWithText("Linked and catalog sources").fetchSemanticsNode()
        compose.onNodeWithContentDescription("Refresh source details")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun pullToRefreshExposesGuardedRefreshAccessibilityAction() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                Column {
                    HikariPullToRefresh(
                        refreshing = false,
                        onRefresh = { refreshCalls += 1 },
                        modifier = Modifier.size(120.dp).testTag("refresh-idle"),
                    ) {
                        Box(Modifier.size(120.dp))
                    }
                    HikariPullToRefresh(
                        refreshing = true,
                        onRefresh = { refreshCalls += 1 },
                        modifier = Modifier.size(120.dp).testTag("refresh-busy"),
                    ) {
                        Box(Modifier.size(120.dp))
                    }
                }
            }
        }

        val idleRefresh = compose.onNodeWithTag("refresh-idle")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }
        val busyRefresh = compose.onNodeWithTag("refresh-busy")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Refresh" }

        compose.runOnIdle {
            assertTrue(idleRefresh.action())
            assertFalse(busyRefresh.action())
            assertEquals(1, refreshCalls)
        }
        compose.onNodeWithTag("refresh-busy").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Refreshing"),
        )
    }

    @Test
    fun pullGestureInvokesRefreshExactlyOnce() {
        var refreshCalls = 0
        compose.setContent {
            HikariTheme {
                HikariPullToRefresh(
                    refreshing = false,
                    onRefresh = { refreshCalls += 1 },
                    modifier = Modifier.size(200.dp).testTag("refresh-gesture"),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Box(Modifier.height(400.dp))
                    }
                }
            }
        }

        compose.onNodeWithTag("refresh-gesture").performTouchInput { swipeDown() }
        compose.waitForIdle()

        assertEquals(1, refreshCalls)
    }

    @Test
    fun pullToRefreshIndicatorRespectsTopInset() {
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            HikariTheme {
                HikariPullToRefresh(
                    refreshing = true,
                    onRefresh = {},
                    topInset = 32.dp,
                    modifier = Modifier.size(200.dp),
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        compose.waitForIdle()
        val indicatorTop = compose.onNodeWithTag("hikari-pull-refresh-indicator")
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue(indicatorTop >= 32f * density)
    }

    @Test
    fun filterChipOwnsMinimumTouchTarget() {
        compose.setContent {
            HikariTheme {
                HikariFilterChip(
                    selected = false,
                    onClick = {},
                    label = { androidx.compose.material3.Text("Downloaded") },
                )
            }
        }

        compose.onNodeWithText("Downloaded")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun floatingNavigationExposesSelectionAndMinimumTargets() {
        var selected by mutableStateOf("home")
        compose.setContent {
            HikariTheme {
                HikariFloatingNavigation(
                    items = navigationItems,
                    selectedKey = selected,
                    onSelected = { selected = it },
                )
            }
        }

        compose.onNodeWithText("Home")
            .assertIsSelected()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Discover").assertIsNotSelected().performClick()
        compose.onNodeWithText("Discover").assertIsSelected()
        compose.onNodeWithText("Library").assertIsNotSelected()
        compose.runOnIdle { assertEquals("discover", selected) }
        assertEquals(
            1,
            compose.onAllNodes(androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun floatingNavigationRejectsInvalidSelectionModels() {
        assertThrows(IllegalArgumentException::class.java) {
            validateNavigationSelection(navigationItems, "missing")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateNavigationSelection(
                navigationItems + navigationItems.first(),
                "discover",
            )
        }
    }

    @Test
    fun navigationGlyphsAreOwnedByTheDesignSystem() {
        assertEquals(24.dp, HikariNavigationGlyphs.discover.defaultWidth)
        assertEquals(24.dp, HikariNavigationGlyphs.home.defaultWidth)
        assertEquals(24.dp, HikariNavigationGlyphs.library.defaultWidth)
    }

    @Test
    fun sharedSectionAndSheetTitlesExposeHeadingSemantics() {
        compose.setContent {
            HikariTheme {
                androidx.compose.foundation.layout.Column {
                    HikariSectionTitle("Section title")
                    HikariSheetContent("Sheet title") {}
                }
            }
        }

        val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        compose.onNodeWithText("Section title").assert(isHeading)
        compose.onNodeWithText("Sheet title").assert(isHeading)
    }

    @Test
    fun floatingNavigationRetainsMinimumTargetsAtTwoHundredPercentFontScale() {
        compose.setContent {
            HikariTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    HikariFloatingNavigation(navigationItems, "home", {})
                }
            }
        }

        navigationItems.forEach { item ->
            compose.onNodeWithText(item.label)
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun themeProvidesRequestedMotionPolicy() {
        var observed = HikariMotionPolicy(reduceMotion = false)
        compose.setContent {
            HikariTheme(motionPolicy = HikariMotionPolicy(reduceMotion = true)) {
                val policy = LocalHikariMotionPolicy.current
                SideEffect { observed = policy }
                Box(Modifier.size(1.dp).testTag("motion-probe"))
            }
        }

        compose.runOnIdle { assertTrue(observed.reduceMotion) }
        assertFalse(HikariMotionPolicy().reduceMotion)
    }

    private companion object {
        val TestIcon = ImageVector.Builder(
            name = "Test",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = androidx.compose.ui.graphics.SolidColor(Color.White)) {
                moveTo(4f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 20f)
                lineTo(4f, 20f)
                close()
            }
        }.build()

        val navigationItems = listOf(
            HikariNavigationItem("discover", "Discover", TestIcon),
            HikariNavigationItem("home", "Home", TestIcon),
            HikariNavigationItem("library", "Library", TestIcon),
        )
    }
}
