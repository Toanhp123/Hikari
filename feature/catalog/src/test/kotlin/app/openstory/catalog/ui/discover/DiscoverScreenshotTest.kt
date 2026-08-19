package app.openstory.catalog.ui.discover

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.common.id.StoryId
import app.openstory.designsystem.motion.HikariMotionPolicy
import app.openstory.designsystem.theme.HikariTheme
import coil3.EventListener
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
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
                    DiscoverScreen(
                        state = fixture(),
                        onRefresh = {},
                        onSearch = {},
                        onStorySelected = {},
                        onContentTypeSelected = {},
                    )
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
        val items = (1..12).map { index ->
            DiscoverStoryItem(
                storyId = StoryId("fixture-story-$index"),
                title = when (index) {
                    1 -> "The Fox of the Moonlit Archive"
                    2 -> "Asterism Protocol"
                    3 -> "Quiet Sword Saint"
                    4 -> "Cinder Library"
                    5 -> "The Salt Clock"
                    else -> "Fixture Story $index"
                },
                coverUrl = "https://example.test/fixture-$index.png",
                contentType = ContentType.MANGA,
                score = Score(9.7 - index * 0.1, 10.0),
                genres = listOf("Fantasy", "Mystery", "Adventure"),
                publicationStatus = if (index % 4 == 0) {
                    PublicationStatus.COMPLETED
                } else {
                    PublicationStatus.ONGOING
                },
                latestUpdate = CatalogLatestUpdate(
                    atEpochMillis = 10_000L - index,
                    releaseLabel = (130 - index).toString(),
                ),
            )
        }
        return DiscoverUiState(
            popular = items.take(5),
            latestUpdates = items.take(9),
            topRated = items.take(5),
            loading = false,
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
