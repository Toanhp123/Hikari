package app.openstory.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.glass.HikariGlassRenderingMode
import app.openstory.designsystem.glass.hikariGlassSurfaceColor
import app.openstory.designsystem.glass.hikariGlassContentColor
import app.openstory.designsystem.glass.glassRenderingMode
import app.openstory.designsystem.layout.HikariWindowClass
import app.openstory.designsystem.layout.classifyWindow
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.motion.LocalHikariMotionPolicy
import app.openstory.designsystem.navigation.HikariFloatingNavigation
import app.openstory.designsystem.navigation.HikariNavigationItem
import app.openstory.designsystem.navigation.validateNavigationSelection
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
