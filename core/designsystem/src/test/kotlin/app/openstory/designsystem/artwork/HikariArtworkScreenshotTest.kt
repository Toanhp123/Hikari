package app.openstory.designsystem.artwork

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.openstory.designsystem.theme.HikariTheme
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HikariArtworkScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadedArtworkRetainsCoverGeometry() {
        var requestCount = 0
        var loading = true
        val imageLoader = successImageLoader { requestCount += 1 }

        compose.setContent {
            HikariTheme {
                val state = rememberHikariArtwork(
                    model = artworkModel,
                    imageLoader = imageLoader,
                )
                SideEffect { loading = state.loading }
                HikariArtwork(
                    state = state,
                    contentDescription = "Moonlit Archive cover",
                    modifier = artworkModifier(),
                )
            }
        }

        compose.waitUntil { requestCount == 1 && !loading }
        compose.waitForIdle()
        assertFalse(loading)
        assertCoverGeometryAndCapture()
    }

    @Test
    fun loadingArtworkRetainsCoverGeometry() {
        var requestCount = 0
        var loading = false
        val imageLoader = loadingImageLoader { requestCount += 1 }

        compose.setContent {
            HikariTheme {
                val state = rememberHikariArtwork(
                    model = artworkModel,
                    imageLoader = imageLoader,
                )
                SideEffect { loading = state.loading }
                HikariArtwork(
                    state = state,
                    contentDescription = "Moonlit Archive cover",
                    modifier = artworkModifier(),
                )
            }
        }

        compose.waitUntil { requestCount == 1 && loading }
        assertTrue(loading)
        assertCoverGeometryAndCapture()
    }

    @Test
    fun fallbackArtworkRetainsCoverGeometry() {
        var requestCount = 0
        var loading = true
        val imageLoader = errorImageLoader { requestCount += 1 }

        compose.setContent {
            HikariTheme {
                val state = rememberHikariArtwork(
                    model = artworkModel,
                    imageLoader = imageLoader,
                )
                SideEffect { loading = state.loading }
                HikariArtwork(
                    state = state,
                    contentDescription = "Moonlit Archive cover",
                    modifier = artworkModifier(),
                )
            }
        }

        compose.waitUntil { requestCount == 1 && !loading }
        assertFalse(loading)
        assertCoverGeometryAndCapture()
    }

    @Test
    fun sharedStateStartsOneRequestForCoverAndBackdrop() {
        var requestCount = 0
        var memoryCacheKey: String? = null
        var diskCacheKey: String? = null
        val imageLoader = successImageLoader { request ->
            requestCount += 1
            memoryCacheKey = request.memoryCacheKey
            diskCacheKey = request.diskCacheKey
        }

        compose.setContent {
            HikariTheme {
                val state = rememberHikariArtwork(
                    model = artworkModel,
                    imageLoader = imageLoader,
                )
                Row {
                    HikariArtwork(
                        state = state,
                        contentDescription = "Moonlit Archive cover",
                        modifier = Modifier.size(width = CoverWidth, height = CoverHeight),
                    )
                    Box(Modifier.size(width = 180.dp, height = CoverHeight)) {
                        HikariArtworkBackdrop(
                            state = state,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }
            }
        }

        compose.waitUntil { requestCount == 1 }
        assertEquals(1, requestCount)
        assertEquals(ExpectedCacheKey, memoryCacheKey)
        assertEquals(ExpectedCacheKey, diskCacheKey)
    }

    private fun assertCoverGeometryAndCapture() {
        compose.onNodeWithTag(ArtworkTag)
            .assertWidthIsEqualTo(CoverWidth)
            .assertHeightIsEqualTo(CoverHeight)
            .captureRoboImage()
    }

    private fun artworkModifier(): Modifier = Modifier
        .size(width = CoverWidth, height = CoverHeight)
        .testTag(ArtworkTag)

    private fun successImageLoader(
        onRequest: (ImageRequest) -> Unit = {},
    ): ImageLoader = fakeImageLoader(
        Interceptor { chain ->
            onRequest(chain.request)
            SuccessResult(
                image = ColorImage(Color(0xFF4C6A78).toArgb()),
                request = chain.request,
            )
        },
    )

    private fun errorImageLoader(
        onRequest: (ImageRequest) -> Unit = {},
    ): ImageLoader = fakeImageLoader(
        Interceptor { chain ->
            onRequest(chain.request)
            ErrorResult(
                image = null,
                request = chain.request,
                throwable = IOException("forced artwork failure"),
            )
        },
    )

    private fun loadingImageLoader(
        onRequest: (ImageRequest) -> Unit = {},
    ): ImageLoader = fakeImageLoader(
        Interceptor { chain ->
            onRequest(chain.request)
            awaitCancellation()
        },
    )

    private fun fakeImageLoader(interceptor: Interceptor): ImageLoader =
        ImageLoader.Builder(RuntimeEnvironment.getApplication())
            .components { add(interceptor) }
            .build()

    private companion object {
        const val ArtworkTag = "artwork"
        const val ExpectedCacheKey =
            "hikari-artwork:story-42:https://example.test/moonlit-archive.jpg"
        val CoverWidth = 120.dp
        val CoverHeight = 180.dp
        val artworkModel = HikariArtworkModel(
            url = "https://example.test/moonlit-archive.jpg",
            stableKey = "story-42",
            title = "Moonlit Archive",
        )
    }
}
