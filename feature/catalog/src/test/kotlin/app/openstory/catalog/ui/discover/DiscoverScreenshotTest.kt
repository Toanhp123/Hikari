package app.openstory.catalog.ui.discover

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.ranking.CatalogRankContribution
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.designsystem.theme.HikariTheme
import app.openstory.designsystem.motion.HikariMotionPolicy
import com.github.takahirom.roborazzi.captureRoboImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.EventListener
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import coil3.request.ImageRequest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoverScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactDark() = capture(true, "compact-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w412dp-h892dp")
    fun largePhoneDark() = capture(true, "large-phone-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w600dp-h960dp")
    fun mediumDark() = capture(true, "medium-dark.png")

    @Test @Config(sdk = [35], qualifiers = "w360dp-h800dp")
    fun compactLight() = capture(false, "compact-light.png")

    @OptIn(DelicateCoilApi::class)
    private fun capture(dark: Boolean, fileName: String) {
        val originalImageLoader = SingletonImageLoader.get(RuntimeEnvironment.getApplication())
        var artworkRequests = 0
        var artworkSuccesses = 0
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(RuntimeEnvironment.getApplication())
                .eventListener(
                    object : EventListener() {
                        override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                            artworkSuccesses += 1
                        }
                    },
                )
                .components {
                    add(
                        Interceptor { chain ->
                            artworkRequests += 1
                            SuccessResult(
                                image = fixtureArtwork().asImage(),
                                request = chain.request,
                            )
                        },
                    )
                }
                .build(),
        )
        try {
            compose.setContent {
                HikariTheme(
                    darkTheme = dark,
                    motionPolicy = HikariMotionPolicy(reduceMotion = true),
                ) {
                    DiscoverScreen(fixture(), {}, {}, {}, {}, {})
                }
            }
            compose.waitUntil { artworkRequests > 0 && artworkSuccesses == artworkRequests }
            compose.mainClock.advanceTimeBy(1_000L)
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/snapshots/discover/$fileName")
        } finally {
            SingletonImageLoader.setUnsafe(originalImageLoader)
        }
    }

    private fun fixture(): DiscoverUiState {
        val entry = CatalogEntry(
            storyId = StoryId("moonlit_archive"), pluginId = PluginId("catalog.a"),
            sourceId = "trending", title = "The Fox of the Moonlit Archive",
            genres = setOf("Fantasy"), contentType = ContentType.LIGHT_NOVEL,
            languageTags = setOf("en"), coverUrl = "https://example.test/moonlit-archive.png",
            score = Score(8.8, 10.0),
        )
        return projectDiscoverState(
            catalogs = listOf(CatalogHomeSnapshot(PluginId("catalog.a"), "1", 1L, listOf(CatalogHomeSection("trending", "Trending stories", listOf(entry))))),
            rankedStories = listOf(RankedCatalogStory(entry.storyId, 0.88, listOf(CatalogRankContribution(entry, 0.88, 1.0)))),
            refreshReport = DiscoverRefreshReport(failed = mapOf(PluginId("catalog.b") to "offline")),
        )
    }

    private fun fixtureArtwork(): Bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = when {
                    x < width / 3 -> Color(0xFFD7804B)
                    y < height / 2 -> Color(0xFF315E68)
                    else -> Color(0xFF172F38)
                }
                setPixel(x, y, color.toArgb())
            }
        }
    }
}
